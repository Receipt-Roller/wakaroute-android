package com.wakaroute.core.profile

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.FakeHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnerProfileTest {

    @Test
    fun `only the learner's own four fields survive decoding`() = runTest {
        // The real response also carries the organisation's entire member list.
        // Nothing here can stop the server sending it (LMS-DEV t-d1bea74); what
        // this proves is that it does not enter the app.
        val profile = client(ME_WITH_ORGANISATION).profile()

        assertEquals("TEST-USER-0001", profile.id)
        assertEquals("", profile.email)
        assertFalse(profile.isLinked)
    }

    @Test
    fun `a linked account is recognised by its email, not its display name`() = runTest {
        // displayName can be set without linking. Only an email means the
        // account survives a new phone, which is the thing the student is being
        // told about.
        assertTrue(client(linked(email = "student@example.invalid")).profile().isLinked)
        assertFalse(client(linked(email = "", displayName = "たろう")).profile().isLinked)
        assertFalse(client(linked(email = "   ")).profile().isLinked)
    }

    @Test
    fun `fields we do not model cannot be read back`() = runTest {
        // A compile-time guarantee stated as a test: if someone adds
        // `organizations` to LearnerProfile, this stops describing the truth
        // and the comment above it becomes a lie. Reviewers should notice.
        val declared = LearnerProfile::class.java.declaredFields.map { it.name }.toSet()

        assertEquals(setOf("id", "email", "displayName"), declared - "Companion" - "\$stable")
    }

    private fun client(body: String) =
        ProfileClient(FakeHttpClient(200, body), AppEnvironment.Production)

    private fun linked(email: String, displayName: String = "") = """
        {"user":{"id":"TEST-USER-0001","email":"$email","displayName":"$displayName"}}
    """.trimIndent()

    private companion object {
        /**
         * Reduced from a real response. The member entries are invented — the
         * production body contained 35 real people, and §11 forbids committing
         * that.
         */
        const val ME_WITH_ORGANISATION = """
        {
          "user": {
            "id": "TEST-USER-0001", "email": "", "displayName": "",
            "organizationId": "TEST-ORG-0001", "completedLessons": 0, "isConsultant": false
          },
          "organizations": [
            {
              "id": "TEST-ORG-0001", "name": "テスト組織", "subscriptionPlan": "Free", "seatCount": 5,
              "members": [
                { "userId": "TEST-OTHER-0001", "isOrgAdmin": true, "hourlyRate": null,
                  "invitationToken": "TEST-INVITE-0001" }
              ]
            }
          ]
        }
        """
    }
}
