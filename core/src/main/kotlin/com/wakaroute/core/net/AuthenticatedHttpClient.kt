package com.wakaroute.core.net

import com.wakaroute.core.auth.AuthSession

/**
 * Attaches the bearer token, and renews once if the server rejects it.
 *
 * The single retry is on **401 only**, and it renews through [AuthSession]
 * rather than re-sending the same token — the session decides whether a
 * renewal is even needed, because another request may already have done it.
 *
 * Retrying is safe here in a way it is not elsewhere: a request rejected with
 * 401 was not processed, so re-sending it cannot duplicate anything. That is
 * not true of a request that timed out, which is why this retries a status and
 * never an error.
 */
class AuthenticatedHttpClient(
    private val underlying: HttpClient,
    private val session: AuthSession,
) : HttpClient {

    override suspend fun send(request: HttpRequest): HttpResponse {
        val token = session.accessToken()
        val response = underlying.send(request.withBearer(token))

        if (response.status != 401) return response

        val renewed = session.renewAfterUnauthorized(token)
        return underlying.send(request.withBearer(renewed))
    }

    /**
     * Replaces rather than adds.
     *
     * A retried request already carries the old header, and two `Authorization`
     * headers is a 400 that reads like a server fault.
     */
    private fun HttpRequest.withBearer(token: String): HttpRequest {
        val headers = this.headers.filterKeys { !it.equals("Authorization", ignoreCase = true) }
        return copy(headers = headers + ("Authorization" to "Bearer $token"))
    }
}
