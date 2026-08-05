package com.wakaroute.core.goals

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import com.wakaroute.core.net.WakaRouteJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `PUT` replaces the entire list — there is no add or remove endpoint — so every
 * change is a read-modify-write, and that is where 志望校 get lost.
 *
 * Shapes verified against production on 2026-08-05.
 */
class TargetSchoolsRepositoryTest {

    @Test
    fun `the production shape decodes`() = runTest {
        val http = FakeApi(TWO_SCHOOLS)
        val list = repository(http).load()

        assertEquals(2, list.goals.size)
        assertEquals("wk_d113299901111", list.first!!.externalId)
        assertEquals(0, list.first!!.rank)
        assertEquals("2027-02-21", list.bindingDeadline)
        assertEquals(200, list.daysRemaining)
    }

    @Test
    fun `rank and daysRemaining decode when the server quotes them`() = runTest {
        // The API's own OpenAPI document declares both as integer-or-string.
        val http = FakeApi(
            """
            {"goals":[{"type":"high_school","source":"wakaroute","externalId":"wk_1","name":"テスト高等学校","rank":"3","targetDate":null}],
             "bindingDeadline":null,"daysRemaining":"12"}
            """.trimIndent(),
        )
        val list = repository(http).load()

        assertEquals(3, list.goals.single().rank)
        assertEquals(12, list.daysRemaining)
    }

    @Test
    fun `adding sends the existing goals back plus the new one`() = runTest {
        // Anything left out of the PUT is deleted. This is the mistake that
        // silently wipes a student's other 志望校.
        val http = FakeApi(TWO_SCHOOLS)
        repository(http).add("wk_new", "テスト第三高等学校", "2027-03-01")

        val sent = http.lastWrittenGoals()
        assertEquals(3, sent.size)
        assertEquals(
            listOf("wk_d113299901111", "wk_d113299902147", "wk_new"),
            sent.map { it["externalId"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `a newly added school does not displace 第一志望`() = runTest {
        // Appended, never promoted. Which school is 第一志望 is the student's
        // decision, and reordering it for them is not a small thing.
        val http = FakeApi(TWO_SCHOOLS)
        repository(http).add("wk_new", "テスト第三高等学校")

        assertEquals("wk_d113299901111", http.lastWrittenGoals().first()["externalId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `adding a school already on the list updates it instead of duplicating`() = runTest {
        val http = FakeApi(TWO_SCHOOLS)
        repository(http).add("wk_d113299901111", "東京都立つばさ総合高等学校", "2028-02-20")

        val sent = http.lastWrittenGoals()
        assertEquals(2, sent.size)
        assertEquals(1, sent.count { it["externalId"]!!.jsonPrimitive.content == "wk_d113299901111" })
        assertEquals("2028-02-20", sent.last()["targetDate"]!!.jsonPrimitive.content)
    }

    @Test
    fun `removing keeps everything else`() = runTest {
        val http = FakeApi(TWO_SCHOOLS)
        repository(http).remove("wk_d113299901111")

        val sent = http.lastWrittenGoals()
        assertEquals(listOf("wk_d113299902147"), sent.map { it["externalId"]!!.jsonPrimitive.content })
    }

    @Test
    fun `removing the last one sends an empty list rather than skipping the write`() = runTest {
        val http = FakeApi(ONE_SCHOOL)
        repository(http).remove("wk_d113299901111")

        assertEquals(0, http.lastWrittenGoals().size)
    }

    @Test
    fun `rank is never sent — the server derives it from the order`() = runTest {
        val http = FakeApi(TWO_SCHOOLS)
        repository(http).add("wk_new", "テスト第三高等学校")

        assertTrue(http.lastWrittenGoals().none { it.containsKey("rank") })
    }

    @Test
    fun `reordering follows the ids given and drops unknown ones`() = runTest {
        val http = FakeApi(TWO_SCHOOLS)
        repository(http).reorder(listOf("wk_d113299902147", "wk_d113299901111", "wk_never_added"))

        assertEquals(
            listOf("wk_d113299902147", "wk_d113299901111"),
            http.lastWrittenGoals().map { it["externalId"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `an over-long name is truncated rather than losing the whole list`() = runTest {
        // The API returns 400 above 200 characters, and a 400 here would take
        // the student's other 志望校 with it.
        val http = FakeApi(EMPTY)
        repository(http).add("wk_1", "あ".repeat(500))

        assertEquals(200, http.lastWrittenGoals().single()["name"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun `an empty list is a normal state, not an error`() = runTest {
        val list = repository(FakeApi(EMPTY)).load()

        assertTrue(list.isEmpty)
        assertNull(list.first)
        assertNull(list.daysRemaining)
        assertFalse(list.contains("wk_1"))
    }

    @Test
    fun `the write goes to the documented route with the documented type`() = runTest {
        val http = FakeApi(EMPTY)
        repository(http).add("wk_1", "テスト高等学校")

        val write = http.writes.single()
        assertEquals("https://api.manabu2.com/api/v1/me/target-schools", write.url)
        assertEquals(HttpRequest.Method.PUT, write.method)
        assertEquals(
            "high_school",
            Json.parseToJsonElement(write.body!!).jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    // --- helpers -----------------------------------------------------------

    private fun repository(http: HttpClient) =
        HttpTargetSchoolsRepository(http, AppEnvironment.Production)

    private class FakeApi(private val body: String) : HttpClient {
        val writes = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            if (request.method == HttpRequest.Method.PUT) {
                writes += request
                // The server echoes the stored list back.
                return HttpResponse(200, request.body!!.asStoredList())
            }
            return HttpResponse(200, body)
        }

        fun lastWrittenGoals() =
            Json.parseToJsonElement(writes.last().body!!).jsonObject["goals"]!!.jsonArray
                .map { it.jsonObject }

        /** Mimics the server assigning `rank` and `type` from the order sent. */
        private fun String.asStoredList(): String {
            val goals = Json.parseToJsonElement(this).jsonObject["goals"]!!.jsonArray
                .mapIndexed { index, element ->
                    val o = element.jsonObject
                    TargetSchool(
                        externalId = o["externalId"]!!.jsonPrimitive.content,
                        name = o["name"]!!.jsonPrimitive.content,
                        rank = index,
                        targetDate = o["targetDate"]?.jsonPrimitive?.contentOrNullSafe(),
                    )
                }
            return WakaRouteJson.encodeToString(
                TargetSchoolList.serializer(),
                TargetSchoolList(goals = goals),
            )
        }

        private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
            if (this is kotlinx.serialization.json.JsonNull) null else content
    }

    private companion object {
        const val EMPTY = """{"goals":[],"bindingDeadline":null,"daysRemaining":null}"""

        const val ONE_SCHOOL = """
        {"goals":[{"type":"high_school","source":"wakaroute","externalId":"wk_d113299901111",
                   "name":"東京都立つばさ総合高等学校","rank":0,"targetDate":"2027-02-21"}],
         "bindingDeadline":"2027-02-21","daysRemaining":200}
        """

        /** Copied from a real response, then reduced to the fields we model. */
        const val TWO_SCHOOLS = """
        {"goals":[{"type":"high_school","source":"wakaroute","externalId":"wk_d113299901111",
                   "name":"東京都立つばさ総合高等学校","rank":0,"targetDate":"2027-02-21"},
                  {"type":"high_school","source":"wakaroute","externalId":"wk_d113299902147",
                   "name":"東京都立芦花高等学校","rank":1,"targetDate":null}],
         "bindingDeadline":"2027-02-21","daysRemaining":200}
        """
    }
}
