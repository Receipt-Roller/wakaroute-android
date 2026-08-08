package com.wakaroute.core.auth

import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.ApiTimestamp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only thing allowed to obtain or renew a token.
 *
 * Everything about this class is shaped by one property of the backend: **a
 * refresh token can be used exactly once.** Each call returns a new one and
 * kills the old. Present a spent token and the server treats it as theft and
 * revokes every session on the account.
 *
 * That turns three ordinary-looking pieces of code into account-wide sign-outs:
 *
 * 1. **Two screens refreshing at once.** Both read the same stored token, both
 *    send it, the second is a replay. Prevented by [mutex] — every path to a
 *    token goes through it, and the ones that queue re-read the result instead
 *    of refreshing again.
 * 2. **Retrying a refresh that timed out.** The request may well have succeeded
 *    with the response lost, so the token is already spent. Never retried here;
 *    recovery goes through `/devices/token` instead, which is idempotent.
 * 3. **Losing the new token.** It is written to the store before it is returned,
 *    so a crash between the two costs a recovery round trip, not the account.
 *
 * The symptom of getting any of this wrong is "the app signs me out sometimes",
 * which is close to undiagnosable from a bug report.
 */
class AuthSession(
    private val client: DeviceAuthClient,
    private val store: SecretStore,
    private val deviceIds: DeviceIdProvider,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()

    /** Cached so an in-date token costs nothing. Never the source of truth for the refresh token. */
    @Volatile
    private var session: Session? = null

    /** The signed-in user id, once a token has been obtained. */
    fun userId(): String? = session?.userId

    /**
     * A usable access token, registering the device if this is the first launch.
     *
     * Callers should treat this as cheap and call it per request rather than
     * holding the result — holding it is how a stale token reaches the wire.
     */
    suspend fun accessToken(): String {
        session?.takeIf { it.isFresh(now()) }?.let { return it.accessToken }

        return mutex.withLock {
            // Re-checked inside the lock. Callers that queued behind a refresh
            // must use its result, not start another one — that second refresh
            // is the replay that revokes everything.
            session?.takeIf { it.isFresh(now()) }?.let { return@withLock it.accessToken }

            renewLocked().accessToken
        }
    }

    /**
     * Forces a renewal, for a request that came back 401 despite a token that
     * looked fresh — a clock skew, or a session revoked server-side.
     *
     * [seenToken] is the token that failed. If it no longer matches what we
     * hold, another caller has already renewed and this one simply takes the
     * new value; renewing again would spend a perfectly good token.
     */
    suspend fun renewAfterUnauthorized(seenToken: String): String = mutex.withLock {
        session?.takeIf { it.accessToken != seenToken }?.let { return@withLock it.accessToken }
        renewLocked().accessToken
    }

    /**
     * Whether an account already exists that this install can reach.
     *
     * Screens check this before fetching, because fetching is what *creates*
     * an account — see [accessToken]. Getting it wrong in either direction is
     * visible to the student: false when true hides their record, true when
     * false registers a learner who was only browsing.
     *
     * **Both credentials count.** A device secret is the anonymous route in;
     * a refresh token is the signed-in one, and [signIn] deliberately deletes
     * the secret. Checking only the secret would tell a student who had just
     * restored three years of work that they had none.
     */
    fun hasAccount(): Boolean =
        store.read(SecretStore.DEVICE_SECRET) != null || store.read(SecretStore.REFRESH_TOKEN) != null

    /**
     * Attaches an email so the record survives a new phone.
     *
     * **The user id does not change.** This adds a way in; it does not move the
     * account, so it is safe at any time and nothing needs migrating.
     */
    suspend fun linkAccount(
        email: String,
        password: String,
        displayName: String = "",
    ): AccountLink = client.link(
        // Deliberately outside the mutex: this stores nothing, and taking the
        // lock here would deadlock against the renewal that [accessToken] does.
        accessToken = accessToken(),
        email = email,
        password = password,
        displayName = displayName,
    )

    /**
     * Signs in as a linked account, **replacing the one this install had**.
     *
     * Whatever this device recorded under its anonymous identity becomes
     * unreachable the moment this succeeds. Anything not yet uploaded goes with
     * it — so callers must deal with that first. [AccountHandover] does; do not
     * call this directly from a screen.
     *
     * The device secret is cleared: this install is no longer the anonymous
     * learner it registered as, and keeping the secret would let a later
     * recovery silently switch back.
     */
    suspend fun signIn(email: String, password: String): Session = mutex.withLock {
        val payload = client.signIn(email = email, password = password)

        store.delete(SecretStore.DEVICE_SECRET)
        store.delete(SecretStore.REFRESH_TOKEN)

        persist(payload)
    }

    /**
     * Closes the account and forgets every credential on this device.
     *
     * **The server is asked first.** Clearing local state first would leave an
     * account on the server that the student can no longer reach — and so can
     * no longer delete, which is the opposite of what they asked for.
     *
     * Irreversible. Callers go through [AccountDeletion], which also disposes
     * of the records this device is still holding.
     */
    suspend fun deleteAccount() {
        // Outside the mutex: obtaining the token may itself need a renewal, and
        // the lock is not reentrant.
        val token = accessToken()

        client.deleteAccount(token)

        mutex.withLock {
            store.delete(SecretStore.DEVICE_SECRET)
            store.delete(SecretStore.REFRESH_TOKEN)
            session = null
        }
    }

    /**
     * Renewal, in the order that survives each step failing.
     *
     * Must only be called while holding [mutex].
     */
    private suspend fun renewLocked(): Session {
        val deviceId = deviceIds.deviceId()

        val refreshToken = store.read(SecretStore.REFRESH_TOKEN)
        if (refreshToken != null) {
            try {
                return persist(client.refresh(refreshToken))
            } catch (e: ApiError) {
                // Deliberately not retried, whatever the cause. A timeout means
                // the request may have succeeded with the response lost, so the
                // token is likely already spent — and re-presenting it is the
                // exact action that signs the student out everywhere.
                //
                // Dropped first so no later path can find and reuse it.
                store.delete(SecretStore.REFRESH_TOKEN)
            }
        }

        val deviceSecret = store.read(SecretStore.DEVICE_SECRET)
        if (deviceSecret != null) {
            try {
                return persist(client.tokenFromSecret(deviceId, deviceSecret))
            } catch (e: ApiError.Http) {
                // 401 means the secret is no longer valid — the device was
                // revoked, or the entry survived a reinstall that the server
                // does not recognise. Re-registering with the same deviceId
                // returns the same account, so nothing is lost by falling
                // through. Any other status is a real failure and propagates.
                if (e.status != 401) throw e
            }
        }

        return registerLocked(deviceId)
    }

    /**
     * First launch, or recovery after the secret became unusable.
     *
     * Re-registering with an unchanged `deviceId` returns the **same** account
     * (`isNewAccount: false`), so this cannot silently orphan a student's
     * records.
     */
    private suspend fun registerLocked(deviceId: String): Session {
        val registration = client.register(deviceId)

        // Before anything else touches this response. The secret is returned
        // exactly once; lose it here and the account exists on the server with
        // no way for this device to prove it owns it.
        store.write(SecretStore.DEVICE_SECRET, registration.deviceSecret)

        return persist(registration.auth)
    }

    /** Writes the tokens down before handing them out. */
    private fun persist(payload: AuthPayload): Session {
        payload.refreshToken?.let { store.write(SecretStore.REFRESH_TOKEN, it) }

        val session = Session(
            accessToken = payload.accessToken,
            refreshToken = payload.refreshToken,
            // Parsed leniently: an unreadable expiry means "refresh next time"
            // rather than a crash, which is the safe direction to fail in.
            expiresAtEpochSeconds = ApiTimestamp.parse(payload.expiresAt)?.epochSecond,
            userId = payload.user?.id,
        )
        this.session = session
        return session
    }
}
