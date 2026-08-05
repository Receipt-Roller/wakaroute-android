package com.wakaroute.core.schools

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.FakeHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolsRepositoryTest {

    private val environment = AppEnvironment.Production

    @Test
    fun `a page decodes`() = runTest {
        val client = FakeHttpClient(200, PAGE_FIXTURE)
        val page = HttpSchoolsRepository(client, environment).search(SchoolSearchQuery())

        assertEquals(2, page.items.size)
        assertEquals("2025-05-01", page.asOf)
        assertEquals("TEST-0000001", page.items.first().id)
        assertEquals(SchoolOwnership.Public, page.items.first().ownership)
    }

    @Test
    fun `numbers sent as strings still decode`() = runTest {
        // §10: numeric fields come back quoted on some endpoints. Declaring them
        // plainly as Int empties a student's whole search over one field.
        val client = FakeHttpClient(200, PAGE_FIXTURE)
        val page = HttpSchoolsRepository(client, environment).search(SchoolSearchQuery())

        assertEquals(2, page.totalCount)
        assertEquals(24, page.pageSize)
        assertEquals(35.0, page.items.first().latitude!!, 0.001)
    }

    @Test
    fun `missing optional fields are absent rather than fatal`() = runTest {
        // 分校 genuinely lack columns in the 文部科学省 CSV the catalogue is built
        // from. A required field here turns a documented gap into a dead screen.
        val client = FakeHttpClient(200, """{"totalCount":1,"page":1,"pageSize":24,"totalPages":1,"items":[{"id":"TEST-0000003","name":"テスト分校"}]}""")
        val school = HttpSchoolsRepository(client, environment).search(SchoolSearchQuery()).items.single()

        assertEquals("テスト分校", school.name)
        assertNull(school.prefecture)
        assertNull(school.ownership)
        assertNull(school.ownershipDisplay)
    }

    @Test
    fun `the server label wins over our own enum`() = runTest {
        // Disagreeing with the web site about whether a school is 公立 would be
        // worse than not classifying it at all.
        val client = FakeHttpClient(200, """{"items":[{"id":"TEST-0000004","name":"テスト高校","ownership":"public","ownershipLabel":"公立（市立）"}]}""")
        val school = HttpSchoolsRepository(client, environment).search(SchoolSearchQuery()).items.single()

        assertEquals("公立（市立）", school.ownershipDisplay)
    }

    @Test
    fun `an empty result is a page, not an error`() = runTest {
        val client = FakeHttpClient(200, """{"asOf":"2025-05-01","totalCount":0,"page":1,"pageSize":24,"totalPages":0,"items":[]}""")
        val page = HttpSchoolsRepository(client, environment).search(SchoolSearchQuery(keyword = "存在しない高校"))

        assertEquals(0, page.totalCount)
        assertTrue(page.items.isEmpty())
        assertEquals(false, page.hasMorePages)
    }

    @Test
    fun `the query is built by the repository, not the screen`() = runTest {
        val client = FakeHttpClient(200, PAGE_FIXTURE)
        HttpSchoolsRepository(client, environment).search(
            SchoolSearchQuery(keyword = "東京 都立", prefectureCode = "13", ownership = SchoolOwnership.Public),
        )

        val url = client.requests.single().url
        assertTrue(url.startsWith("https://wakaroute.com/api/schools?"))
        // Encoded, so a school name with a space or an ampersand does not drop
        // the rest of the parameters.
        assertTrue(url.contains("q=%E6%9D%B1%E4%BA%AC+%E9%83%BD%E7%AB%8B"))
        assertTrue(url.contains("prefecture=13"))
        assertTrue(url.contains("ownership=public"))
        assertTrue(url.contains("page=1"))
        assertTrue(url.contains("pageSize=24"))
    }

    @Test
    fun `blank filters are left out entirely`() = runTest {
        // Sending `q=` is not the same request as sending no `q`, and only one
        // of them is what an empty search box means.
        val client = FakeHttpClient(200, PAGE_FIXTURE)
        HttpSchoolsRepository(client, environment).search(SchoolSearchQuery(keyword = "   "))

        assertTrue(client.requests.single().url.contains("q=").not())
    }

    @Test
    fun `page size is clamped to what the API accepts`() {
        // The API rejects anything above 48. A screen computing this from a grid
        // width would otherwise turn a layout change into a 400.
        assertEquals(48, SchoolSearchQuery(pageSize = 200).normalizedPageSize)
        assertEquals(1, SchoolSearchQuery(pageSize = 0).normalizedPageSize)
        assertEquals(1, SchoolSearchQuery(page = -3).normalizedPage)
    }

    @Test
    fun `detail tolerates the collections the catalogue has not filled in`() = runTest {
        // Every collection was empty for every school sampled on 2026-08-02.
        // The catalogue publishes structure before data; absence is ordinary.
        val client = FakeHttpClient(200, """{"school":{"id":"TEST-0000001","name":"テスト第一高等学校"}}""")
        val detail = HttpSchoolsRepository(client, environment).detail("TEST-0000001")

        assertTrue(detail.examSchedules.isEmpty())
        assertTrue(detail.admissions.isEmpty())
        assertTrue(detail.deviationScores.isEmpty())
        assertNull(detail.latestDeviationScore)
    }

    @Test
    fun `the school id is escaped into the path`() = runTest {
        val client = FakeHttpClient(200, """{"school":{"id":"a b","name":"テスト"}}""")
        HttpSchoolsRepository(client, environment).detail("a b")

        assertEquals("https://wakaroute.com/api/schools/a+b", client.requests.single().url)
    }

    private companion object {
        /** Invented ids and names. Nothing here came from production. */
        const val PAGE_FIXTURE = """
        {
          "asOf": "2025-05-01",
          "totalCount": "2",
          "page": 1,
          "pageSize": "24",
          "totalPages": 1,
          "items": [
            {
              "id": "TEST-0000001",
              "name": "テスト第一高等学校",
              "nameKana": "テストダイイチコウトウガッコウ",
              "prefectureCode": "13",
              "prefecture": "東京都",
              "address": "東京都テスト区1-1-1",
              "ownership": "public",
              "ownershipLabel": "公立",
              "campusTypeLabel": "本校",
              "latitude": "35.0",
              "longitude": "139.0",
              "tags": []
            },
            {
              "id": "TEST-0000002",
              "name": "テスト第二高等学校",
              "prefectureCode": "13",
              "prefecture": "東京都",
              "ownership": "private"
            }
          ]
        }
        """
    }
}
