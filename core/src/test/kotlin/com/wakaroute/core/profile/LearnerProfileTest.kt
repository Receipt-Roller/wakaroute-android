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
    fun `the current production shape decodes`() = runTest {
        // Field-for-field the real response as of 2026-08-05, after the fix for
        // LMS-DEV t-d1bea74. Note `displayName` arrives as **null**, not "" —
        // an unlinked account has never been given one.
        val profile = client(ME_AFTER_FIX).profile()

        assertEquals("TEST-USER-0001", profile.id)
        assertEquals("", profile.email)
        assertEquals("", profile.displayName)
        assertFalse(profile.isLinked)
    }

    @Test
    fun `the pre-fix shape would still not have leaked into the app`() = runTest {
        // Kept after the server was fixed. It is the regression test for the
        // client's half of that incident: if `organizations` is ever declared
        // here, this stops proving anything and the next server-side mistake
        // reaches a student's phone unopposed.
        val profile = client(ME_WITH_ORGANISATION).profile()

        assertEquals("TEST-USER-0001", profile.id)
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
        /** The response as production returns it today. Ids invented. */
        const val ME_AFTER_FIX = """
        {
          "user": {
            "id": "TEST-USER-0001", "email": "", "displayName": null,
            "profileImageUrl": "/images/default-avatar.png", "preferredCulture": "ja-JP",
            "organizationId": null, "department": null,
            "completedLessons": 0, "completedProjects": 0, "lastAccessedAt": null,
            "isDeactivated": false, "isConsultant": false, "consultantTenantCount": 0,
            "isRagManager": false, "canCreateAgents": false
          },
          "organizations": [
            { "id": "TEST-ORG-0001", "name": "テスト組織",
              "isOrgAdmin": false, "isCurriculumManager": false, "isHr": false,
              "isExecutive": false, "isDeveloper": false, "isLearner": true }
          ]
        }
        """

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
