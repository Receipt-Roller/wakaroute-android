package com.wakaroute.core.net

import kotlinx.serialization.Serializable

/**
 * RFC 7807 error body.
 *
 * Branch on [code]. [detail] is human-facing prose and the backend guide warns
 * its wording changes, so a client that switches on it breaks silently on a
 * copy edit.
 */
@Serializable
data class ProblemDetails(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val code: String? = null,
)

/**
 * Every failure the networking layer can produce.
 *
 * Kept as distinct types because §7 of the 開発ガイド requires the UI to tell
 * "no connection" apart from "the server is broken" apart from "nothing
 * matched" — one generic error would collapse three different things a student
 * can do something about into one they cannot.
 */
sealed class ApiError(message: String) : Exception(message) {

    /** No usable connection. */
    data object Offline : ApiError("通信できません")

    data object TimedOut : ApiError("時間内に応答がありませんでした")

    /** The server answered with a non-2xx status. */
    data class Http(val status: Int, val problem: ProblemDetails?) :
        ApiError("HTTP $status${problem?.code?.let { " ($it)" } ?: ""}")

    /**
     * A response arrived but did not match the expected shape.
     *
     * Never fatal: §6 requires that an undecodable response not crash the app.
     * The catalogue publishes structure before data and adds fields over time.
     */
    data class Decoding(val reason: String) : ApiError("応答を読み取れませんでした: $reason")

    data class Unknown(val reason: String) : ApiError(reason)

    /** The server's `code`, when it supplied one. */
    val code: String?
        get() = (this as? Http)?.problem?.code

    /**
     * Whether retrying the identical request might succeed.
     *
     * 4xx other than 408 and 429 are permanent. Treating them as retryable is
     * what lets one dead record block a whole queue forever — the failure mode
     * the 実装ガイド calls out for the Phase 2 offline queue, and the reason
     * this distinction lives in the error type rather than at a call site.
     */
    val isTransient: Boolean
        get() = when (this) {
            is Offline, is TimedOut -> true
            is Http -> status >= 500 || status == 429 || status == 408
            is Decoding, is Unknown -> false
        }
}
