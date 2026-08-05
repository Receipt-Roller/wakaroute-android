package com.wakaroute.core.profile

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import kotlinx.serialization.Serializable

/**
 * The signed-in learner — **three fields, and no more**.
 *
 * `GET /api/v1/me` used to return the organisation's entire member list to any
 * learner token, each entry carrying `hourlyRate` and a **usable**
 * `invitationToken` (LMS-DEV t-d1bea74). That is fixed and deployed; verified
 * against production on 2026-08-05 — `members` is gone, the organisation's
 * contract data is gone, and `/organizations/{id}/members` answers a learner
 * token with 403.
 *
 * This class stays minimal anyway, for a reason the backend team gave
 * themselves when they chose a fixed response over a scope-branched one: a
 * response that varies is one where **the next field somebody adds to the
 * domain DTO ships silently**. `user` is still that domain DTO — 16 fields on
 * production today, all of them about the caller, none of them ours to want.
 * Declaring three means a seventeenth cannot arrive here by accident.
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
