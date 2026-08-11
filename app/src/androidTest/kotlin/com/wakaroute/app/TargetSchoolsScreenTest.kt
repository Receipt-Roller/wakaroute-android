package com.wakaroute.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wakaroute.app.data.TargetSchoolsState
import com.wakaroute.app.feature.goals.TargetSchoolsScreen
import com.wakaroute.app.ui.theme.WakaRouteTheme
import com.wakaroute.core.goals.TargetSchool
import com.wakaroute.core.goals.TargetSchoolList
import com.wakaroute.core.goals.TargetSchoolsRepository
import com.wakaroute.core.net.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 志望校 reordering, from the point of view of someone who cannot see it.
 *
 * The screen uses arrow buttons rather than drag-and-drop precisely so a screen
 * reader can drive it (§8), which only holds if each button says **which**
 * school it moves — three identically-shaped controls per row are
 * indistinguishable otherwise. That is what these assert.
 */
@RunWith(AndroidJUnit4::class)
class TargetSchoolsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var repository: FakeRepository

    private fun setScreen(vararg names: String) {
        repository = FakeRepository(names.toList())
        val state = TargetSchoolsState(repository)

        rule.setContent {
            WakaRouteTheme {
                TargetSchoolsScreen(
                    targetSchools = state,
                    onFindSchools = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun everyControlSaysWhichSchoolItActsOn() {
        setScreen("テスト第一高等学校", "テスト第二高等学校")

        for (name in listOf("テスト第一高等学校", "テスト第二高等学校")) {
            rule.onNodeWithContentDescription("${name}を上へ").assertExists()
            rule.onNodeWithContentDescription("${name}を下へ").assertExists()
            rule.onNodeWithContentDescription("${name}を志望校から外す").assertExists()
        }
    }

    @Test
    fun theEndsOfTheListDisableTheMoveThatWouldFallOff() {
        setScreen("テスト第一高等学校", "テスト第二高等学校")

        // Disabled rather than removed. A control that disappears shifts the
        // two beside it, and the next tap lands on the wrong one.
        rule.onNodeWithContentDescription("テスト第一高等学校を上へ").assertIsNotEnabled()
        rule.onNodeWithContentDescription("テスト第一高等学校を下へ").assertIsEnabled()
        rule.onNodeWithContentDescription("テスト第二高等学校を上へ").assertIsEnabled()
        rule.onNodeWithContentDescription("テスト第二高等学校を下へ").assertIsNotEnabled()
    }

    @Test
    fun movingUpSendsTheNewOrderAndPromotesFirstChoice() {
        setScreen("テスト第一高等学校", "テスト第二高等学校")

        rule.onNodeWithContentDescription("テスト第二高等学校を上へ").performClick()
        rule.waitForIdle()

        assertEquals(
            listOf("id-テスト第二高等学校", "id-テスト第一高等学校"),
            repository.lastReorder,
        )
        rule.onNodeWithContentDescription("第一志望、テスト第二高等学校").assertExists()
    }

    @Test
    fun positionIsSpokenAsWordsRatherThanAFraction() {
        setScreen("テスト第一高等学校", "テスト第二高等学校")

        // 「1/2」 read aloud is not obviously a position, and 第一志望 is the
        // part that carries meaning.
        rule.onNodeWithContentDescription("第一志望、テスト第一高等学校").assertExists()
        rule.onNodeWithContentDescription("2番目、テスト第二高等学校").assertExists()
    }

    @Test
    fun aFailedMoveRestoresTheOrderAndSaysSo() {
        setScreen("テスト第一高等学校", "テスト第二高等学校")
        repository.failWrites = true

        rule.onNodeWithContentDescription("テスト第二高等学校を上へ").performClick()
        rule.waitForIdle()

        // The move must not be left showing as though it had saved.
        rule.onNodeWithContentDescription("第一志望、テスト第一高等学校").assertExists()
        rule.onNodeWithText("保存できませんでした。もとの順番に戻しています。").assertExists()
    }

    @Test
    fun anEmptyListInvitesRatherThanReportsAProblem() {
        setScreen()

        rule.onNodeWithText("まだ志望校を登録していません。").assertExists()
        rule.onNodeWithText("高校を探す").assertExists()
    }

    private class FakeRepository(names: List<String>) : TargetSchoolsRepository {
        var failWrites = false
        var lastReorder: List<String>? = null

        private var goals = names.mapIndexed { index, name ->
            TargetSchool(externalId = "id-$name", name = name, rank = index)
        }

        override suspend fun load() = TargetSchoolList(goals = goals)

        override suspend fun add(schoolId: String, name: String, examDate: String?) =
            error("not used")

        override suspend fun remove(schoolId: String): TargetSchoolList {
            if (failWrites) throw ApiError.Offline
            goals = goals.filterNot { it.externalId == schoolId }
            return TargetSchoolList(goals = goals)
        }

        override suspend fun reorder(schoolIds: List<String>): TargetSchoolList {
            lastReorder = schoolIds
            if (failWrites) throw ApiError.Offline

            val byId = goals.associateBy { it.externalId }
            goals = schoolIds.mapIndexedNotNull { index, id -> byId[id]?.copy(rank = index) }
            return TargetSchoolList(goals = goals)
        }
    }
}
