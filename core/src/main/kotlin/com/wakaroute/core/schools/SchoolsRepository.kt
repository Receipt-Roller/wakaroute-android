package com.wakaroute.core.schools

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import java.net.URLEncoder

/**
 * Reads the school catalogue.
 *
 * This is the one WakaRoute endpoint that is live today, and it needs no
 * authentication — which is why Phase 1 can be built against real data without
 * any of the Phase 2 credential handling.
 */
interface SchoolsRepository {
    suspend fun search(query: SchoolSearchQuery): SchoolSearchPage
    suspend fun detail(id: String): SchoolDetail
}

class HttpSchoolsRepository(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) : SchoolsRepository {

    override suspend fun search(query: SchoolSearchQuery): SchoolSearchPage = retryingReads {
        http.sendDecoding(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = url("/api/schools", query.queryParameters()),
                headers = mapOf("Accept" to "application/json"),
            ),
            SchoolSearchPage.serializer(),
        )
    }

    /**
     * One school by its permanent id.
     *
     * A 404 means the id is no longer in the catalogue. It is surfaced rather
     * than swallowed: once 志望校 exist in Phase 2, a saved school quietly
     * vanishing is worth telling a student about.
     */
    override suspend fun detail(id: String): SchoolDetail = retryingReads {
        http.sendDecoding(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = url("/api/schools/${encode(id)}"),
                headers = mapOf("Accept" to "application/json"),
            ),
            SchoolDetail.serializer(),
        )
    }

    /**
     * Builds the URL here rather than in the screen.
     *
     * §6 requires it, and the reason shows up the first time a student searches
     * for a school with a space or a 「＆」 in its name: hand-assembled query
     * strings drop the rest of the parameters.
     */
    private fun url(path: String, parameters: List<Pair<String, String>> = emptyList()): String {
        val base = environment.wakarouteBaseUrl.trimEnd('/') + path
        if (parameters.isEmpty()) return base

        return parameters.joinToString("&", prefix = "$base?") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
