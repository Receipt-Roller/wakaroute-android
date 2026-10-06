package com.wakaroute.app.feature.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakaroute.app.data.CardDataSource
import com.wakaroute.app.data.CardsLoad
import com.wakaroute.app.data.CardsState
import com.wakaroute.app.data.Deck
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.Centered
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.cards.CardDeck
import kotlinx.coroutines.launch

/** カードの入口 — 5教科のデッキ、範囲、これまでの進み. The same layout and words as iOS. */
@Composable
fun CardsScreen(
    cards: CardsState,
    onOpenDeck: (Deck) -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    val load by cards.load.collectAsStateWithLifecycle()
    val progress by cards.progress.collectAsStateWithLifecycle()
    val scope by cards.scope.collectAsStateWithLifecycle()

    val coroutines = rememberCoroutineScope()
    LaunchedEffect(Unit) { cards.refresh() }

    CardScaffold(title = "カード", onBack = onBack) { padding ->
        when (val current = load) {
            CardsLoad.Loading -> Centered(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("カードを準備しています")
                }
            }

            is CardsLoad.Failed -> Centered(padding) {
                FailedNote(current.message, onRetry = { coroutines.launch { cards.refresh() } })
            }

            is CardsLoad.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                ReadableColumn(spacing = 16.dp) {
                    Text("範囲", style = MaterialTheme.typography.titleMedium)
                    GradePicker(selected = scope.grade, onSelect = { cards.choose(scope.copy(grade = it)) })

                    Column {
                        for (deck in Deck.entries) {
                            val inScope = CardDeck.inScope(current.cards(deck), scope)
                            DeckRow(
                                deck = deck,
                                total = inScope.size,
                                started = inScope.count { progress[it.id] != null },
                                learned = inScope.count { progress[it.id]?.isLearned == true },
                                onClick = { onOpenDeck(deck) },
                            )
                            HorizontalDivider()
                        }
                    }

                    Note("1回で ${CardDeck.SESSION_SIZE} 枚まで出します。5回続けてわかると「おぼえた」になり、出なくなります。")

                    LinkRow(title = "データの出典とライセンス", onClick = onOpenLicenses)

                    // Saying this plainly is the whole mitigation: there is no
                    // server to keep card progress on, so it cannot be carried.
                    Note("カードの学習状態はこの端末にだけ保存されます。学習記録の引き継ぎには含まれないため、機種変更すると最初からになります。")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradePicker(selected: Int?, onSelect: (Int?) -> Unit) {
    val options = listOf<Pair<Int?, String>>(null to "すべて", 1 to "中1", 2 to "中2", 3 to "中3")

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (grade, label) ->
            SegmentedButton(
                selected = selected == grade,
                onClick = { onSelect(grade) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun DeckRow(deck: Deck, total: Int, started: Int, learned: Int, onClick: () -> Unit) {
    // Both numbers, because 「わかった」 moves one and 「おぼえた」 the other.
    // Showing only the second makes a working deck look stuck.
    val counts = "$total 枚　学習中 $started 枚・おぼえた $learned 枚"

    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(deck.title, deck.subjectLabel, counts).joinToString("、")
            }
            .padding(vertical = 14.dp),
    ) { flexible ->
        Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = listOfNotNull(deck.title, deck.subjectLabel).joinToString("　"),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(counts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * 出典とライセンス. NGSL and KANJIDIC2 are CC BY-SA, so this has to be reachable
 * from the screens that show their data. The wording is the server's, verbatim.
 */
@Composable
fun CardLicenseScreen(cards: CardsState, onBack: () -> Unit) {
    val load by cards.load.collectAsStateWithLifecycle()
    val sources = (load as? CardsLoad.Ready)?.sources.orEmpty()

    CardScaffold(title = "出典とライセンス", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            ReadableColumn(spacing = 24.dp) {
                for (source in sources) SourceSection(source)
            }
        }
    }
}

@Composable
private fun SourceSection(source: CardDataSource) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(source.title, style = MaterialTheme.typography.titleMedium)
        for (license in source.licenses) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                license.attribution?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                (license.spdxId ?: license.name)?.let { Note("ライセンス: $it") }
                license.url?.let { Note(it) }
            }
        }
        if (source.asOf.isNotEmpty()) Note("データ基準日 ${source.asOf}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    AdaptiveRow(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) { flexible ->
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = flexible)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FailedNote(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("もう一度読み込む") }
    }
}
