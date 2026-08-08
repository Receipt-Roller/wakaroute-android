package com.wakaroute.core.auth

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.sendDecoding
import com.wakaroute.core.net.sendExpectingNoContent

/**
 * The three endpoints that produce credentials.
 *
 * Nothing here decides *when* to call them — that is [AuthSession], and keeping
 * the decision in one place is the whole point. Two callers refreshing at once
 * is what signs a student out of every device they own.
 */
class DeviceAuthClient(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) {

    /**
     * `POST /api/v1/devices/register`.
     *
     * Safe to call again with the same `deviceId`: the server returns the
     * **same account** with a fresh secret and `isNewAccount: false`. Empty
     * accounts do not pile up, which makes re-registration the correct recovery
     * when the local state is unclear.
     */
    suspend fun register(deviceId: String): DeviceRegistration {
        require(deviceId.length >= StoredDeviceIdProvider.MINIMUM_LENGTH) {
            "deviceId must be at least ${StoredDeviceIdProvider.MINIMUM_LENGTH} characters"
        }

        return post(
            path = "/api/v1/devices/register",
            body = WakaRouteJson.encodeToString(
                RegisterRequest.serializer(),
                RegisterRequest(clientId = environment.clientId, deviceId = deviceId),
            ),
            serializer = DeviceRegistration.serializer(),
        )
    }

    /**
     * `POST /api/v1/auth/refresh`.
     *
     * **The refresh token is single-use.** Every call invalidates the one it was
     * given and returns a new one. Presenting a spent token is read by the
     * server as theft, and it revokes *every* session on the account — which
     * reaches the student as "the app randomly logs me out".
     *
     * So: never call this twice with the same token, and never retry it on
     * timeout. [AuthSession] enforces both; do not call this directly.
     */
    suspend fun refresh(refreshToken: String): AuthPayload = post(
        path = "/api/v1/auth/refresh",
        body = WakaRouteJson.encodeToString(
            RefreshRequest.serializer(),
            RefreshRequest(refreshToken),
        ),
        serializer = AuthPayload.serializer(),
    )

    /**
     * `POST /api/v1/devices/token` — recovery when the refresh token is gone or
     * spent.
     *
     * This is why `deviceSecret` had to be saved before anything else: it is the
     * only way back that does not create a new account.
     */
    suspend fun tokenFromSecret(deviceId: String, deviceSecret: String): AuthPayload = post(
        path = "/api/v1/devices/token",
        body = WakaRouteJson.encodeToString(
            DeviceTokenRequest.serializer(),
            DeviceTokenRequest(
                clientId = environment.clientId,
                deviceId = deviceId,
                deviceSecret = deviceSecret,
            ),
        ),
        serializer = AuthPayload.serializer(),
    )

    /**
     * `POST /api/v1/me/link` — adds an email to the account this device holds.
     *
     * Safe at any time: **the user id does not change.** This adds a way in; it
     * does not move the account, so nothing has to be migrated and nothing can
     * be stranded. Verified against production 2026-08-05.
     *
     * The only call here that needs a token, and it is passed in rather than
     * fetched: this client is deliberately given the *unauthenticated* http
     * client, because the thing that attaches tokens is built on top of it.
     */
    suspend fun link(
        accessToken: String,
        email: String,
        password: String,
        displayName: String = "",
    ): AccountLink = post(
        path = "/api/v1/me/link",
        body = WakaRouteJson.encodeToString(
            LinkAccountRequest.serializer(),
            LinkAccountRequest(email = email, password = password, displayName = displayName),
        ),
        serializer = AccountLink.serializer(),
        accessToken = accessToken,
    )

    /**
     * `POST /api/v1/auth/login` — signs in on a new device.
     *
     * **`clientId` is deliberately omitted.** The 実装ガイド says to send it, and
     * the server then answers `403 user_credential_required` — 「This client
     * cannot use password sign-in」 — for the `wakaroute` client. Omitting it
     * returns a token with the same four scopes, **the same user id**, and a
     * refresh token, with the account's progress intact. Confirmed against
     * production on 2026-08-05, both branches, and iOS ships the same omission.
     *
     * This is LMS-DEV t-d1bea80. Until it is settled the omission is an
     * **unconfirmed workaround, not a documented contract** — restore the
     * parameter once password sign-in is enabled for this client.
     */
    suspend fun signIn(email: String, password: String): AuthPayload = post(
        path = "/api/v1/auth/login",
        body = WakaRouteJson.encodeToString(
            SignInRequest.serializer(),
            SignInRequest(email = email, password = password),
        ),
        serializer = AuthPayload.serializer(),
    )

    /**
     * `DELETE /api/v1/me` — closes the account.
     *
     * Destroys the identity, releases the linked email, deletes 志望校, revokes
     * every token, and removes the device registration — so the next launch
     * creates a **genuinely new** learner rather than recovering this one.
     *
     * That last part is the one to distrust. `/devices/register` is idempotent
     * by `deviceId` on purpose, which is correct for recovery and fatal here:
     * if the device row outlived the user, the next launch would hand the
     * student back the account they just deleted. Verified against production
     * — re-registering the same `deviceId` afterwards returns
     * `isNewAccount: true` with a different user id.
     *
     * Learning history is retained, detached from any person.
     *
     * **There is no undo.**
     */
    suspend fun deleteAccount(accessToken: String) = http.sendExpectingNoContent(
        HttpRequest(
            method = HttpRequest.Method.DELETE,
            url = environment.manabu2BaseUrl.trimEnd('/') + "/api/v1/me",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $accessToken",
            ),
        ),
    )

    private suspend fun <T> post(
        path: String,
        body: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        accessToken: String? = null,
    ): T = http.sendDecoding(
        HttpRequest(
            method = HttpRequest.Method.POST,
            url = environment.manabu2BaseUrl.trimEnd('/') + path,
            headers = buildMap {
                put("Content-Type", "application/json")
                put("Accept", "application/json")
                accessToken?.let { put("Authorization", "Bearer $it") }
            },
            body = body,
        ),
        serializer,
    )
}
