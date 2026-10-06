package com.wakaroute.app

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wakaroute.app.feature.map.UnderstandingMapScreen
import com.wakaroute.app.ui.theme.WakaRouteTheme
import com.wakaroute.app.data.UnderstandingMapState
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.map.bundledUnderstandingMap
import com.wakaroute.core.map.LiveUnderstandingMap
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader is actually given.
 *
 * These assertions run against Compose's **merged** semantics tree, which is
 * the tree handed to accessibility services. That distinction matters: an
 * `adb shell uiautomator dump` of the same screen shows every row as an
 * unlabelled clickable wrapper with its text in non-focusable children —
 * including Material's own `NavigationBar`, which plainly does work. Judging
 * accessibility from that dump produces confident, wrong conclusions.
 *
 * Method names are camelCase here, unlike the JVM tests. Backtick names
 * containing spaces cannot be dexed below DEX 040, and minSdk is 26.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setMapScreen() {
        val bundled = bundledUnderstandingMap()
        val state = UnderstandingMapState(
            bundled = bundled,
            live = LiveUnderstandingMap(ContentClient(NoNetwork, AppEnvironment.Production), bundled),
        )

        rule.setContent {
            WakaRouteTheme {
                UnderstandingMapScreen(mapState = state, onOpenDomain = { _, _ -> }, onBack = {})
            }
        }
    }

    /**
     * Unreachable, as on a train.
     *
     * The screen is allowed to try — the record is fetched now that every
     * install has an account. What these tests assert is that failing to reach
     * it costs the record and never the structure, which is bundled.
     */
    private object NoNetwork : HttpClient {
        override suspend fun send(request: HttpRequest): HttpResponse = throw ApiError.Offline
    }

    @Test
    fun domainRowIsOneLabelledTarget() {
        setMapScreen()

        // The label and the click action must be on the **same** node. Split
        // across two, a student hears the row and then has to hunt for the
        // thing that responds to it.
        rule.onNodeWithContentDescription("数と式、8項目、これから").assertHasClickAction()
    }

    @Test
    fun everyDomainReadsAsOneSentence() {
        setMapScreen()

        // Name, size, standing — in that order, as one utterance rather than
        // three fragments a listener has to reassemble.
        for (announcement in listOf(
            "数と式、8項目、これから",
            "図形、7項目、これから",
            "関数、6項目、これから",
            "データの活用、5項目、これから",
        )) {
            rule.onNodeWithContentDescription(announcement).assertExists()
        }
    }

    @Test
    fun domainLetterIsPrintedButNotSpoken() {
        setMapScreen()

        // 「A　数と式」 is a label borrowed from the web map. Reading 「エー」 before
        // every 領域 is noise, so the spoken form drops it and the printed form
        // keeps it.
        rule.onNodeWithText("A　数と式").assertExists()
        rule.onNodeWithContentDescription("A　数と式").assertDoesNotExist()
    }

    @Test
    fun subjectsWithoutEdgesAreNotDrawnAsUsable() {
        setMapScreen()

        // The rule this whole screen exists to protect: a 教科 with no authored
        // prerequisite edges must not present any 領域 to open. If an empty
        // graph ever gets substituted, it appears here and the screen starts
        // telling students there is nothing in their way. Such a 教科 is not
        // shown at all.
        rule.onNodeWithText("数学").assertExists()
        for (subject in listOf("国語", "英語", "理科", "社会")) {
            rule.onNodeWithText(subject).assertDoesNotExist()
        }
    }
}
