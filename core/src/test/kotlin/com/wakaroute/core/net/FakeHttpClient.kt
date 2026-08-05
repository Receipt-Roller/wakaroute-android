package com.wakaroute.core.net

/**
 * A scripted [HttpClient] for tests.
 *
 * Every fixture in this repository is obviously invented. The repository is
 * public, and iOS came within one commit of publishing a production `/me`
 * response that contained a real organisation administrator's id.
 */
class FakeHttpClient(
    private val responses: List<Result<HttpResponse>>,
) : HttpClient {

    val requests = mutableListOf<HttpRequest>()
    private var index = 0

    constructor(status: Int, body: String) : this(listOf(Result.success(HttpResponse(status, body))))

    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        val response = responses[minOf(index, responses.lastIndex)]
        index++
        return response.getOrThrow()
    }

    val requestCount: Int get() = requests.size

    companion object {
        fun failing(vararg errors: ApiError) = FakeHttpClient(errors.map { Result.failure(it) })

        fun failingThenSucceeding(error: ApiError, status: Int, body: String) = FakeHttpClient(
            listOf(Result.failure(error), Result.success(HttpResponse(status, body))),
        )
    }
}
