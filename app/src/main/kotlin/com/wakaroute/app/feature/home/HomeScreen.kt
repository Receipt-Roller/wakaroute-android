package com.wakaroute.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakaroute.app.AppServices
import com.wakaroute.app.data.TargetSchoolsUi
import com.wakaroute.core.goals.TargetSchoolList
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ComingSoonChip
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.documents.BundledDocument
import com.wakaroute.core.map.SchoolSubject
import com.wakaroute.core.map.HomeDigest
import com.wakaroute.core.map.LearnerProgress
import com.wakaroute.core.map.SubjectMapState

/**
 * ホーム — 志望校, 「つぎにやること」, and the way in to everything else.
 *
 * The rule this screen keeps is that **nothing appears until there is evidence
 * for it**. 「つぎにやること」 is absent until the student has a record to reason
 * from, because rows generated from an empty one would say 「ここから始めると」
 * about the same three 要素 on every launch — guidance with nothing behind it,
 * which §7 rules out as squarely as an invented number would be.
 *
 * The other distinction it has to keep is between **まだ記録がありません** (about
 * the student) and **準備中** (about the app). Both leave the screen looking
 * empty; only one of them is something the student did.
 */
@Composable
fun HomeScreen(
    services: AppServices,
    onOpenMap: () -> Unit,
    onOpenSchools: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenElement: (SchoolSubject, String, String) -> Unit,
    onOpenDocument: (BundledDocument) -> Unit,
) {
    val mapStates by services.understandingMap.states.collectAsStateWithLifecycle()
    val mathState = mapStates.firstOrNull { it.first == SchoolSubject.Math }?.second

    LaunchedEffect(Unit) {
        services.understandingMap.refreshPublishedGraphs()
        services.understandingMap.refreshIfAccountExists()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReadableColumn(spacing = 16.dp) {
            TargetSchoolsSection(services, onOpenSchools, onOpenGoals)

            // Only once there is a record to reason from. With an empty one
            // every row would read 「ここから始めると」 for the same three 要素 on
            // every launch, which is noise rather than guidance — and the map
            // says the same thing better.
            (mathState as? SubjectMapState.Available)
                ?.takeIf { it.progress is LearnerProgress.Known && !it.progress.recordOrEmpty.isEmpty }
                ?.let { available ->
                    NextStepsSection(
                        steps = HomeDigest.nextSteps(available.subject, available.progress.recordOrEmpty),
                        onOpenElement = { step ->
                            onOpenElement(
                                SchoolSubject.Math,
                                step.element.domainCode,
                                step.element.id.value,
                            )
                        },
                    )
                }

            Text(
                text = "いま使えること",
                style = MaterialTheme.typography.headlineSmall,
            )

            ActionCard(
                icon = Icons.Outlined.AccountTree,
                title = "理解マップ",
                body = when (mathState) {
                    null -> "準備中です。"
                    is SubjectMapState.Available ->
                        "数学の${mathState.subject.elements.size}項目と、その前提関係を見られます。" +
                            "ほかの4教科は準備中です。"

                    // The graph is bundled, so this is a bug in the build rather
                    // than a network problem. Said plainly instead of blaming
                    // the connection.
                    is SubjectMapState.Unavailable -> "いま表示できません。アプリの更新をお待ちください。"
                    SubjectMapState.ComingSoon -> "準備中です。"
                },
                onClick = onOpenMap,
            )

            ActionCard(
                icon = Icons.Filled.School,
                title = "高校を探す",
                body = "全国の高校を、キーワード・都道府県・設置区分でさがせます。",
                onClick = onOpenSchools,
            )

            ActionCard(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "高校受験とは",
                body = "何がどんな順番で決まっていくのか、全体の形を説明しています。",
                onClick = { onOpenDocument(BundledDocument.ExamGuide) },
            )

            PreparingSection()
        }
    }
}

/**
 * 志望校 and the count of days to the first exam.
 *
 * The count is **the server's `daysRemaining`**, shown as it arrives. Computing
 * it here would eventually disagree — a phone in another timezone, or one whose
 * clock is wrong — and two screens giving a student different numbers of days
 * until their exam is worse than either number on its own.
 *
 * Loaded only when the device already has an account. Registration happens the
 * first time a student adds a 志望校, so a student who has never done that sees
 * the invitation below and causes no network call at all.
 */
@Composable
private fun TargetSchoolsSection(
    services: AppServices,
    onOpenSchools: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    val state by services.targetSchools.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { services.targetSchools.refreshIfAccountExists() }

    when (val current = state) {
        // Before any account exists, and while loading. Both draw the same
        // invitation rather than a spinner: there is nothing a student needs to
        // wait for, and a spinner on the first screen reads as a fault.
        TargetSchoolsUi.NotRegistered, TargetSchoolsUi.Loading ->
            TargetSchoolsInvitation(onOpenSchools)

        is TargetSchoolsUi.Failed -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "志望校を読み込めませんでした。${current.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        is TargetSchoolsUi.Loaded ->
            if (current.list.isEmpty) {
                TargetSchoolsInvitation(onOpenSchools)
            } else {
                TargetSchoolsCard(current.list, onOpenGoals)
            }
    }
}

@Composable
private fun TargetSchoolsInvitation(onOpenSchools: () -> Unit) {
    ActionCard(
        icon = Icons.Filled.Flag,
        title = "志望校を登録する",
        // No promise about what registering will unlock beyond what it does
        // today: the count of days. 学習の記録 is still 準備中 and saying it here
        // would be selling something that does not exist yet.
        body = "気になる高校を志望校に登録すると、入試までの日数がここに出ます。",
        onClick = onOpenSchools,
    )
}

@Composable
private fun TargetSchoolsCard(list: TargetSchoolList, onOpenGoals: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        onClick = onOpenGoals,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "志望校",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            list.goals.forEachIndexed { index, goal ->
                Text(
                    // 第一志望 is the only rank worth naming. Numbering the rest
                    // turns an ordered list into a ranking of schools, which is
                    // not what a student meant by putting them in an order.
                    text = if (index == 0) "第一志望　${goal.name}" else goal.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            list.daysRemaining?.let { days ->
                Text(
                    text = when {
                        days > 0 -> "入試まであと $days 日（${list.bindingDeadline}）"
                        days == 0 -> "入試は今日です"
                        // A date in the past is a real state: the catalogue
                        // carries several years, and a student may not have
                        // updated their list. Not drawn as an error.
                        else -> "入試日（${list.bindingDeadline}）は過ぎています"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // The card is tappable, and nothing about a filled surface says so.
            // Spelled out rather than hinted with a chevron, because 「並べ替え」
            // is not a thing a student would think to try on a summary card.
            Text(
                text = "タップして順番を変える・取り消す",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * What is still not built.
 *
 * Kept accurate rather than kept short. Every item removed from this list is a
 * feature that now works — and an item left on it after it ships is worse than
 * no list at all, because a student who reads 準備中 stops looking for it.
 *
 * レッスンとクイズ, 学習時間の記録 and 志望校の登録 have all left this list.
 */
@Composable
private fun PreparingSection() {
    Text(
        text = "準備中",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )

    Text(
        text = "つぎの機能はまだ動いていません。できあがったらこの画面に出てきます。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PreparingRow(Icons.Filled.Notifications, "学習リマインダー")
            PreparingRow(Icons.Filled.People, "保護者との連携")
        }
    }
}

@Composable
private fun PreparingRow(icon: ImageVector, label: String) {
    AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = flexible,
        )
        ComingSoonChip()
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$title。$body" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) { flexible ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(
                modifier = flexible,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
