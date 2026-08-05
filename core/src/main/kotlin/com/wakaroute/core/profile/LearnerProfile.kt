package com.wakaroute.core.profile

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import kotlinx.serialization.Serializable

/**
 * The signed-in learner — **four fields, and no more**.
 *
 * `GET /api/v1/me` returns far more than this. Verified against production on
 * 2026-08-05, a plain learner token also receives an `organizations` array
 * carrying the organisation's **entire member list** (35 people on our own
 * tenant), each entry with `userId`, `title`, `hourlyRate`, role flags and an
 * `invitationToken` — plus the organisation's `subscriptionPlan`, `seatCount`
 * and `companyOverview`. This is LMS-DEV t-d1bea74, and it is worse than that
 * ticket describes.
 *
 * Nothing here can fix that. What it can do is refuse to participate: the
 * response is decoded into this type, `organizations` is never declared, and so
 * other people's identifiers never enter the app's memory, its caches, or a
 * crash report — of which there are none, but the principle holds.
 *
 * **Do not add fields to this class from the `/me` response without asking why
 * a 中学生's app needs them.**
 */
@Serializable
data class LearnerProfile(
    val id: String,
    /** Empty until the student links an email. Not a display fallback — see [isLinked]. */
    val email: String = "",
    val displayName: String = "",
) {
    /**
     * Whether this account can survive a new phone.
     *
     * A device-only account dies with the install. A 中1 student who starts here
     * sits their exams in 中3 and will change phones at least once in between,
     * which is what the linking prompt exists for.
     */
    val isLinked: Boolean get() = email.isNotBlank()
}

/** Wraps the response so `organizations` is dropped at the boundary. */
@Serializable
private data class MeResponse(val user: LearnerProfile)

class ProfileClient(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) {
    suspend fun profile(): LearnerProfile = retryingReads {
        http.sendDecoding(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = environment.manabu2BaseUrl.trimEnd('/') + "/api/v1/me",
                headers = mapOf("Accept" to "application/json"),
            ),
            MeResponse.serializer(),
        ).user
    }
}
