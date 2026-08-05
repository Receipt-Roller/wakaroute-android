package com.wakaroute.core.net

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class HttpRequest(
    val method: Method,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
) {
    enum class Method { GET, POST, PUT, DELETE }
}

data class HttpResponse(val status: Int, val body: String)

/**
 * The seam between our code and the network, so repositories and their rules
 * can be tested without one.
 */
interface HttpClient {
    suspend fun send(request: HttpRequest): HttpResponse
}

/**
 * The real client.
 *
 * Timeouts are set deliberately rather than left at OkHttp's defaults: a
 * student on a train needs "we could not reach the server" reasonably soon, not
 * a spinner that outlasts their attention.
 */
class OkHttpHttpClient private constructor(
    private val client: OkHttpClient,
) : HttpClient {

    // OkHttp stays inside this module. Exposing it — even as a default
    // parameter — would put it on every consumer's compile classpath and make
    // the choice of HTTP library part of the app's API.
    constructor() : this(defaultClient())

    override suspend fun send(request: HttpRequest): HttpResponse {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }

        val body = request.body?.toRequestBody(JSON_MEDIA_TYPE)
        builder.method(request.method.name, body)

        return client.newCall(builder.build()).await()
    }

    private suspend fun Call.await(): HttpResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }

        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    continuation.resume(HttpResponse(it.code, it.body?.string().orEmpty()))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e.asApiError())
            }
        })
    }

    /**
     * OkHttp reports "no network" and "the request took too long" as different
     * exception types; both are recoverable and neither should reach the user
     * as a stack trace.
     */
    private fun IOException.asApiError(): ApiError = when (this) {
        is SocketTimeoutException -> ApiError.TimedOut
        is UnknownHostException -> ApiError.Offline
        else -> ApiError.Unknown(message ?: this::class.java.simpleName)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
