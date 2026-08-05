package com.wakaroute.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The credentials half of a registration or refresh response.
 *
 * `user` carries `email` and `displayName` too. They are not modelled: an
 * unlinked account has neither, and the client has nothing to do with them.
 * §9 says collect the minimum the feature needs, and this is the file where
 * that is easiest to violate by accident.
 */
@Serializable
data class AuthPayload(
    val accessToken: String,
    /** ISO 8601. Parsed with [com.wakaroute.core.net.ApiTimestamp], never `Instant.parse`. */
    val expiresAt: String? = null,
    val refreshToken: String? = null,
    val scopes: List<String> = emptyList(),
    val user: User? = null,
) {
    @Serializable
    data class User(val id: String)
}

/**
 * `POST /api/v1/devices/register`.
 *
 * **`deviceSecret` appears here and nowhere else, ever.** The 実装ガイド is
 * explicit: save it before doing anything else with this response. Slip another
 * step in first and the account exists on the server while the device has no
 * way to prove it is the one that owns it — unrecoverable, and invisible until
 * the next launch.
 */
@Serializable
data class DeviceRegistration(
    val deviceSecret: String,
    val isNewAccount: Boolean = false,
    val auth: AuthPayload,
)

@Serializable
data class RegisterRequest(
    val clientId: String,
    val deviceId: String,
    val platform: String = "android",
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class DeviceTokenRequest(
    val clientId: String,
    val deviceId: String,
    val deviceSecret: String,
)

/** What the app holds once registration has succeeded. */
data class Session(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long?,
    @SerialName("userId") val userId: String?,
) {
    /**
     * Treated as expired a minute early.
     *
     * A token that expires while the request is in flight comes back as a 401
     * the client then has to untangle. Refreshing slightly early costs one
     * extra call an hour and removes that whole class of race.
     */
    fun isFresh(nowEpochSeconds: Long): Boolean {
        val expiry = expiresAtEpochSeconds ?: return false
        return nowEpochSeconds < expiry - EXPIRY_MARGIN_SECONDS
    }

    private companion object {
        const val EXPIRY_MARGIN_SECONDS = 60L
    }
}
