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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.AppServices
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ComingSoonChip
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.documents.BundledDocument
import com.wakaroute.core.map.SchoolSubject
import com.wakaroute.core.map.SubjectMapState

/**
 * The Phase 1 home screen.
 *
 * Not the iOS home screen. That one shows 志望校, today's study minutes, a
 * streak and 「つぎにやること」, all of which come from MANABU2 — which Phase 1
 * does not talk to. Porting it would mean drawing 0分 and 0日 as though they
 * were a student's record, and 「つぎにやること」 with no evidence behind it is
 * exactly the invented number 共通判断規則 §7 rules out.
 *
 * So this screen offers what works, and says plainly what does not. The
 * distinction it has to keep is between **まだ記録がありません** (about the
 * student) and **準備中** (about the app) — only the second is true today, and
 * showing the first would blame a student for a feature nobody has built.
 */
@Composable
fun HomeScreen(
    services: AppServices,
    onOpenMap: () -> Unit,
    onOpenSchools: () -> Unit,
    onOpenDocument: (BundledDocument) -> Unit,
) {
    val mathState = remember { services.understandingMap.state(SchoolSubject.Math) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReadableColumn(spacing = 16.dp) {
            Text(
                text = "いま使えること",
                style = MaterialTheme.typography.headlineSmall,
            )

            ActionCard(
                icon = Icons.Outlined.AccountTree,
                title = "理解マップ",
                body = when (mathState) {
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
 * What the finished app will do, listed honestly as 準備中.
 *
 * Kept on the home screen rather than hidden, because the alternative is a
 * student wondering whether they missed a button. Drawn without numbers of any
 * kind: a 0 beside 学習時間 reads as a record, not as an absence.
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
            PreparingRow(Icons.Filled.EditNote, "レッスンとクイズ")
            PreparingRow(Icons.Filled.Timer, "学習時間の記録")
            PreparingRow(Icons.Filled.Flag, "志望校の登録")
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
