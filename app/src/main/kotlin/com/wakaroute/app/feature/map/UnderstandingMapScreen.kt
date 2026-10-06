package com.wakaroute.app.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.app.ui.design.StandingChip
import com.wakaroute.core.map.DomainProgress
import com.wakaroute.core.map.LearnerProgress
import com.wakaroute.core.map.SchoolSubject
import com.wakaroute.core.map.SubjectMapState
import com.wakaroute.app.data.UnderstandingMapState
import com.wakaroute.core.map.domainProgress

/**
 * The 理解マップ, one card per 教科.
 *
 * Only 教科 with a map appear. One without authored prerequisite edges is
 * left out rather than labelled 準備中 — and never handed an empty graph: on
 * one, every 要素 is `Ready`, and this screen would cheerfully report
 * 「つまずきはありません」 about a subject nobody has mapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnderstandingMapScreen(
    mapState: UnderstandingMapState,
    onOpenDomain: (SchoolSubject, String) -> Unit,
    onBack: () -> Unit,
) {
    val states by mapState.states.collectAsStateWithLifecycle()

    // Structure first, the student's record after. Only if this device already
    // has an account — the first authenticated call is what creates a MANABU2
    // learner, and opening a map must not do that.
    LaunchedEffect(Unit) {
        // Graphs first: a 教科 whose edges have just been published appears
        // before the record is layered on. Needs no account.
        mapState.refreshPublishedGraphs()
        mapState.refreshProgress()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("理解マップ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReadableColumn(spacing = 16.dp) {
                Text(
                    text = "教科の中がどんな項目に分かれていて、何が何の前提になっているのかを見られます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                for ((subject, state) in states) {
                    if (state is SubjectMapState.Available) SubjectCard(subject, state, onOpenDomain)
                }

                // Said once, at the bottom, rather than beside every 要素 — and it
                // has to say **which** of the three situations this is. All three
                // leave everything at まだ, and only one of them is about the
                // student.
                ProgressNote(states)
            }
        }
    }
}

/**
 * Whose fact the empty map is.
 *
 * 「まだ」 against every 要素 can mean three different things, and a student
 * cannot tell them apart from the rows alone:
 *
 * - no account yet — nothing has been recorded anywhere
 * - the record could not be read — it exists, we failed
 * - the record is real and they have not started
 *
 * Only the third is about them. Drawing the first two as an ordinary empty
 * record would tell a student they have done nothing, which for the first two
 * is not something the app is in a position to claim.
 */
@Composable
private fun ProgressNote(states: List<Pair<SchoolSubject, SubjectMapState>>) {
    val progress = states
        .mapNotNull { (_, state) -> (state as? SubjectMapState.Available)?.progress }
        .firstOrNull()
        ?: return

    val message = when (progress) {
        LearnerProgress.NotConnected ->
            "いまは項目とつながりだけを表示しています。学習の記録が始まると、ここに反映されます。"

        LearnerProgress.Unavailable ->
            "学習の記録をいま読み込めませんでした。項目とつながりだけを表示しています。"

        is LearnerProgress.Known ->
            if (progress.record.isEmpty) {
                "まだ学習の記録はありません。項目を開くと、何が何の前提になっているかを見られます。"
            } else {
                "あなたの学習の記録にあわせて表示しています。"
            }
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
    )
}

@Composable
private fun SubjectCard(
    subject: SchoolSubject,
    state: SubjectMapState.Available,
    onOpenDomain: (SchoolSubject, String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = subject.label, style = MaterialTheme.typography.titleLarge)

            val progress = state.subject.domainProgress(state.progress.recordOrEmpty)
            for (domain in progress) {
                HorizontalDivider()
                DomainRow(domain) { onOpenDomain(subject, domain.domain.code) }
            }

            Text(
                text = "つながりの作成日: ${state.asOf}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DomainRow(progress: DomainProgress, onClick: () -> Unit) {
    // Spoken as one sentence.
    //
    // Without this the row announces nothing at all: `clickable` alone leaves
    // the tappable node empty and scatters the label, the count and the standing
    // across separate children, so a TalkBack user hears silence on the one
    // thing they can act on. Verified against the accessibility node tree, not
    // assumed — the screen looks correct either way.
    //
    // The 領域 letter is deliberately absent. It is a label on the web map, and
    // spelling out 「エー」 before every 領域 is noise.
    val announcement = "${progress.domain.name}、${progress.totalElements}項目、${progress.standing.label}"

    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = announcement }
            .padding(vertical = 12.dp),
    ) { flexible ->
        Column(
            modifier = flexible,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${progress.domain.code}　${progress.domain.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${progress.totalElements}項目",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StandingChip(progress.standing)

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
