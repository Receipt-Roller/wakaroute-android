package com.wakaroute.app.feature.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakaroute.app.data.CardsState
import com.wakaroute.app.data.Deck
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.Centered
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.cards.CardReview
import com.wakaroute.core.cards.CardSubjects
import com.wakaroute.core.cards.KanjiCard
import com.wakaroute.core.cards.StudyCard
import com.wakaroute.core.cards.SubjectCard
import com.wakaroute.core.cards.WordCard
import kotlinx.coroutines.launch

/**
 * One sitting with a deck.
 *
 * The card turns over on a tap. 「まだ」 and 「わかった」 appear only once the back
 * has been seen — grading a card you have not read is not an answer.
 */
@Composable
fun CardStudyScreen(cards: CardsState, deckName: String, onBack: () -> Unit) {
    val deck = remember(deckName) { Deck.entries.firstOrNull { it.name == deckName } }
    // Dealt once, when the sitting starts. Re-dealing after each answer would
    // pull the card just answered straight back out of the deck.
    val session = remember(deck) { deck?.let(cards::session).orEmpty() }

    var index by remember(deck) { mutableIntStateOf(0) }
    var showingBack by remember(deck) { mutableStateOf(false) }
    var answered by remember(deck) { mutableIntStateOf(0) }
    val coroutines = rememberCoroutineScope()

    CardScaffold(title = deck?.title ?: "カード", onBack = onBack) { padding ->
        when {
            session.isEmpty() -> Centered(padding) {
                Message(
                    title = "いまは出すカードがありません",
                    body = "この範囲のカードは今日のぶんが終わっています。範囲を変えるか、また明日どうぞ。",
                )
            }

            index >= session.size -> Centered(padding) {
                Message(
                    title = "おつかれさま",
                    body = "$answered 枚やりました。わかったカードも、間をあけてもう一度出ます。\n" +
                        "${CardReview.LEARNED_BOX}回続けてわかると「おぼえた」になります。",
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                ReadableColumn(spacing = 20.dp) {
                    Text(
                        text = "${index + 1} / ${session.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics { contentDescription = "${session.size} 枚中 ${index + 1} 枚目" },
                    )

                    CardFace(
                        card = session[index],
                        showingBack = showingBack,
                        onReveal = { showingBack = true },
                    )

                    if (showingBack) {
                        Answers { correct ->
                            val card = session[index]
                            coroutines.launch { cards.answer(card.id, correct) }
                            answered++
                            showingBack = false
                            index++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFace(card: StudyCard, showingBack: Boolean, onReveal: () -> Unit) {
    Surface(
        onClick = onReveal,
        enabled = !showingBack,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (!showingBack) stateDescription = "タップして答えを見る" },
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Front(card)

            if (showingBack) {
                HorizontalDivider()
                Back(card)
            } else {
                Text(
                    text = "タップして答えを見る",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Front(card: StudyCard) {
    when (card) {
        is KanjiCard -> Text(
            text = card.character,
            fontSize = 96.sp,
            modifier = Modifier.semantics { contentDescription = "漢字 ${card.character}" },
        )

        is WordCard -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.lemma, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            card.pronunciations.firstOrNull()?.let { Note(it) }
        }

        is SubjectCard -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardSubjects.domainName(card.subject, card.domain)?.let { Note(it) }
            Text(card.prompt, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Back(card: StudyCard) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (card) {
            // A kanji has several readings; showing one would teach a half-truth.
            is KanjiCard -> {
                if (card.onReadings.isNotEmpty()) Labeled("音読み", card.onReadings.joinToString("・"))
                if (card.kunReadings.isNotEmpty()) Labeled("訓読み", card.kunReadings.joinToString("・"))
                card.strokeCount?.let { Labeled("画数", "$it 画") }
            }

            is WordCard -> WordBack(card)

            is SubjectCard -> {
                Text(card.answer, style = MaterialTheme.typography.titleMedium)
                card.explanation?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Dictionary wording, and long. The first sense is the one a 中学生 needs; the
 * rest are there but folded away.
 */
@Composable
private fun WordBack(card: WordCard) {
    var showingAll by remember(card.id) { mutableStateOf(false) }

    Text(card.meaningsJa.first(), style = MaterialTheme.typography.titleMedium)

    val others = card.meaningsJa.drop(1)
    if (others.isNotEmpty()) {
        if (showingAll) {
            for (meaning in others) Note(meaning)
        } else {
            TextButton(onClick = { showingAll = true }) { Text("ほかの意味 ${others.size} 件") }
        }
    }
    if (card.partsOfSpeech.isNotEmpty()) Note(card.partsOfSpeech.joinToString("・"))
}

@Composable
private fun Labeled(label: String, value: String) {
    AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = flexible, textAlign = TextAlign.End)
    }
}

/** Side by side normally; stacked at large text, where a row would wrap a character at a time. */
@Composable
private fun Answers(onAnswer: (correct: Boolean) -> Unit) {
    AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
        // Never colour alone: the word and the symbol both carry it.
        Button(
            onClick = { onAnswer(false) },
            modifier = flexible,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("まだ", modifier = Modifier.padding(start = 8.dp))
        }
        Button(onClick = { onAnswer(true) }, modifier = flexible) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text("わかった", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
