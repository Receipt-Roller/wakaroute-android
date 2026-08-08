package com.wakaroute.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wakaroute.app.feature.more.HandoverMode
import com.wakaroute.app.feature.more.HandoverScreen
import com.wakaroute.app.ui.theme.WakaRouteTheme
import com.wakaroute.core.auth.AccountHandover
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one screen state a student can lose work in.
 *
 * Signing in with records still queued is refused, and the refusal has to be
 * *readable*: how much is at stake, the cheap way out first, the destructive
 * one last. It is also the branch least likely to be reached by hand — it needs
 * a device that has been offline — so it is the one most likely to ship broken.
 *
 * Method names are camelCase: backtick names with spaces cannot be dexed below
 * DEX 040, and minSdk is 26.
 */
@RunWith(AndroidJUnit4::class)
class HandoverScreenTest {

    @get:Rule
    val rule = createComposeRule()

    /** A handover whose queue holds one record that will not send. */
    private fun handoverWithUnsentWork(): AccountHandover {
        val queue = LearningActionQueue(
            store = InMemoryPendingActionStore(),
            content = ContentClient(AlwaysOffline, AppEnvironment.Production),
        )
        runBlocking { queue.markComplete("lesson-1") }

        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
            ),
        )

        return AccountHandover(
            auth = AuthSession(
                // Never reached: the refusal happens before sign-in is attempted.
                client = DeviceAuthClient(AlwaysOffline, AppEnvironment.Production),
                store = store,
                deviceIds = StoredDeviceIdProvider(store),
            ),
            queue = queue,
        )
    }

    private fun setSignInScreen() {
        val handover = handoverWithUnsentWork()

        rule.setContent {
            WakaRouteTheme {
                HandoverScreen(handover = handover, mode = HandoverMode.SignIn, onBack = {})
            }
        }

        rule.onNodeWithText("メールアドレス").performTextInput("someone@example.invalid")
        rule.onNodeWithText("パスワード").performTextInput("Wk-test99")

        // Matched by its click action, not its text alone: the top bar title
        // reads 「記録を呼び出す」 too, and the title is the node that is not a
        // button. Scrolled into view first, because the soft keyboard opened by
        // the fields above covers the button.
        rule.onNode(hasText("記録を呼び出す") and hasClickAction())
            .performScrollTo()
            .performClick()
        rule.waitForIdle()
    }

    @Test
    fun unsentWorkIsCountedRatherThanAlludedTo() {
        setSignInScreen()

        // 「送れていない記録があります」 alone leaves a student guessing whether it
        // is one tap of a lesson or a week of study. The number is what makes
        // the next decision theirs.
        rule.onNodeWithText("このスマホに、まだ送れていない学習の記録が 1 件あります。いま記録を呼び出すと、この 1 件は消えてしまいます。")
            .assertExists()
    }

    @Test
    fun theCheapWayOutIsOfferedBeforeTheDestructiveOne() {
        setSignInScreen()

        // Most of these refusals are just "no signal since yesterday". A
        // student who reads 「消して続ける」 first and taps it has thrown away
        // work that one bar of signal would have saved.
        rule.onNodeWithText("もう一度ためす").assertExists()
        rule.onNodeWithText("1 件を消して続ける").assertExists()
    }

    @Test
    fun theStudentIsToldWhatSigningInReplaces() {
        val handover = handoverWithUnsentWork()

        rule.setContent {
            WakaRouteTheme {
                HandoverScreen(handover = handover, mode = HandoverMode.SignIn, onBack = {})
            }
        }

        // Before they type, not after they commit. This device's record is not
        // merged into the one being called up — it is left behind.
        rule.onNodeWithText("このスマホでいま使っている記録は、呼び出した記録に置きかわります。").assertExists()
    }

    @Test
    fun linkingStatesThePasswordRuleUpFront() {
        val handover = handoverWithUnsentWork()

        rule.setContent {
            WakaRouteTheme {
                HandoverScreen(handover = handover, mode = HandoverMode.Link, onBack = {})
            }
        }

        // Learning it from a rejection means typing a password twice and being
        // told off in between.
        rule.onNodeWithText("6文字以上で、大文字・小文字・記号をそれぞれ1つ以上入れてください。").assertExists()

        // And it is the *linking* screen that says it. On sign-in the rule is
        // irrelevant — the password already exists — and repeating it there
        // reads as a demand to change it.
        rule.onNodeWithText("記録を引き継げるようにする").assertExists()
    }

    private object AlwaysOffline : HttpClient {
        override suspend fun send(request: HttpRequest): HttpResponse = throw ApiError.Offline
    }
}
