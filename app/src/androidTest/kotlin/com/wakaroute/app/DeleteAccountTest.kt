package com.wakaroute.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wakaroute.app.feature.more.MoreScreen
import com.wakaroute.app.ui.theme.WakaRouteTheme
import com.wakaroute.core.auth.AccountDeletion
import com.wakaroute.core.auth.AuthSession
import com.wakaroute.core.auth.DeviceAuthClient
import com.wakaroute.core.auth.InMemorySecretStore
import com.wakaroute.core.auth.SecretStore
import com.wakaroute.core.auth.StoredDeviceIdProvider
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import com.wakaroute.core.offline.InMemoryPendingActionStore
import com.wakaroute.core.offline.LearningActionQueue
import com.wakaroute.core.profile.ProfileClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 学習記録の削除, as a student meets it.
 *
 * Required by App Store Review 5.1.1(v) and Google Play's data-deletion policy.
 * These assert the two things that make it safe rather than merely present: the
 * confirmation **names what is lost**, and a failure says the records are still
 * there.
 *
 * Method names are camelCase — backtick names with spaces cannot be dexed below
 * DEX 040, and minSdk is 26.
 */
@RunWith(AndroidJUnit4::class)
class DeleteAccountTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setScreen(deleteFails: ApiError? = null) {
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-old",
            ),
        )
        val http = FakeApi(deleteFails)
        val auth = AuthSession(
            client = DeviceAuthClient(http, AppEnvironment.Production),
            store = store,
            deviceIds = StoredDeviceIdProvider(store),
        )
        val queue = LearningActionQueue(
            store = InMemoryPendingActionStore(),
            content = ContentClient(http, AppEnvironment.Production),
        )

        rule.setContent {
            WakaRouteTheme {
                MoreScreen(
                    environment = AppEnvironment.Production,
                    auth = auth,
                    profile = ProfileClient(http, AppEnvironment.Production),
                    deletion = AccountDeletion(auth, queue),
                    onOpenDocument = {},
                    onLink = {},
                    onSignIn = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theConfirmationNamesWhatIsLost() {
        setScreen()

        rule.onNodeWithText("学習記録を削除する").performScrollTo().performClick()

        // 「本当によろしいですか」 tells a 中学生 nothing about what they are
        // agreeing to. This is the only irreversible control in the app.
        rule.onNodeWithText("学習記録を削除しますか？").assertIsDisplayed()
        rule.onNodeWithText(
            "学習の記録（レッスン・クイズ）、志望校、勉強した時間がすべて消えます。もとに戻すことはできません。",
        ).assertIsDisplayed()

        // 「やめる」 rather than 「キャンセル」 — a loanword a 中学生 has to stop
        // and parse, in front of a decision that cannot be undone.
        rule.onNodeWithText("やめる").assertIsDisplayed()
    }

    @Test
    fun backingOutDeletesNothing() {
        setScreen()

        rule.onNodeWithText("学習記録を削除する").performScrollTo().performClick()
        rule.onNodeWithText("やめる").performClick()

        // Still offered, so nothing happened.
        rule.onNodeWithText("学習記録を削除する").assertIsDisplayed()
    }

    @Test
    fun aSuccessfulDeleteSaysSo() {
        setScreen()

        rule.onNodeWithText("学習記録を削除する").performScrollTo().performClick()
        rule.onNodeWithText("削除する").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("削除しました。このアプリは、また新しく始められます。").assertIsDisplayed()
    }

    @Test
    fun aFailedDeleteSaysTheRecordsAreStillThere() {
        setScreen(deleteFails = ApiError.Offline)

        rule.onNodeWithText("学習記録を削除する").performScrollTo().performClick()
        rule.onNodeWithText("削除する").performClick()
        rule.waitForIdle()

        // The student has just been told 「消えます。もとに戻せません」 and then
        // seen it fail. Without this they have every reason to assume the worst
        // — and the truth is that nothing was lost.
        rule.onNodeWithText("インターネットにつながっていないようです。記録はそのまま残っています。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** Auth and profile succeed; only the delete is scripted to fail. */
    private class FakeApi(private val deleteFails: ApiError?) : HttpClient {
        override suspend fun send(request: HttpRequest): HttpResponse {
            if (request.method == HttpRequest.Method.DELETE) {
                deleteFails?.let { throw it }
                return HttpResponse(204, "")
            }

            if (request.url.endsWith("/api/v1/me")) {
                // Device-only, so both handover rows are offered alongside the
                // delete — the layout this screen actually ships with.
                return HttpResponse(
                    200,
                    """{"user":{"id":"TEST-USER-0001","email":"","displayName":null}}""",
                )
            }

            return HttpResponse(
                200,
                """
                {
                  "accessToken": "access-1",
                  "expiresAt": "2099-01-01T00:00:00Z",
                  "refreshToken": "refresh-1",
                  "scopes": ["read:catalog"],
                  "user": { "id": "TEST-USER-0001" }
                }
                """.trimIndent(),
            )
        }
    }
}
