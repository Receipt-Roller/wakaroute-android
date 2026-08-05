package com.wakaroute.core.net

import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer

/**
 * Sends a request and decodes a successful body, turning anything else into an
 * [ApiError] that carries the server's Problem Details.
 */
suspend fun <T> HttpClient.sendDecoding(request: HttpRequest, serializer: KSerializer<T>): T {
    val response = try {
        send(request)
    } catch (e: ApiError) {
        throw e
    } catch (e: Exception) {
        throw ApiError.Unknown(e.message ?: e::class.java.simpleName)
    }

    if (response.status !in 200..299) {
        val problem = runCatching {
            WakaRouteJson.decodeFromString(ProblemDetails.serializer(), response.body)
        }.getOrNull()
        throw ApiError.Http(response.status, problem)
    }

    return try {
        WakaRouteJson.decodeFromString(serializer, response.body)
    } catch (e: Exception) {
        // §6: an undecodable response is an error state, never a crash.
        throw ApiError.Decoding(e.message ?: e::class.java.simpleName)
    }
}

/**
 * Retries a read a bounded number of times, with a growing wait.
 *
 * Deliberately restricted to reads. §6 permits retrying only 「GETの一時的失敗」,
 * and the reason is specific: a write retried after a timeout may already have
 * been applied. On this backend that is not hypothetical — a re-sent quiz
 * submission is recorded as a second attempt, and a re-presented refresh token
 * signs the student out of every device.
 *
 * [attempts] counts the first try. Three attempts means at most two retries,
 * which is enough to ride out a handover between cell towers without leaving a
 * student watching a spinner.
 */
suspend fun <T> retryingReads(
    attempts: Int = 3,
    initialBackoffMillis: Long = 400,
    block: suspend () -> T,
): T {
    require(attempts >= 1) { "attempts must be at least 1" }

    var backoff = initialBackoffMillis
    repeat(attempts - 1) {
        try {
            return block()
        } catch (e: ApiError) {
            if (!e.isTransient) throw e
            delay(backoff)
            backoff *= 2
        }
    }
    return block()
}
