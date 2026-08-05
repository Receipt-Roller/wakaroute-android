package com.wakaroute.core.auth

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.sendDecoding

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

    private suspend fun <T> post(
        path: String,
        body: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T = http.sendDecoding(
        HttpRequest(
            method = HttpRequest.Method.POST,
            url = environment.manabu2BaseUrl.trimEnd('/') + path,
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body = body,
        ),
        serializer,
    )
}
