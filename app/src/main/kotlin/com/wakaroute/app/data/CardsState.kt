package com.wakaroute.app.data

import com.wakaroute.core.cards.CardDeck
import com.wakaroute.core.cards.CardLibrary
import com.wakaroute.core.cards.CardLicense
import com.wakaroute.core.cards.CardProgress
import com.wakaroute.core.cards.CardSubjects
import com.wakaroute.core.cards.KanjiCard
import com.wakaroute.core.cards.StudyCard
import com.wakaroute.core.cards.SubjectCard
import com.wakaroute.core.cards.WordCard
import com.wakaroute.core.net.ApiError
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The five decks, in the same 教科 order as the rest of the app. */
enum class Deck(val title: String, val subjectLabel: String?) {
    Kanji("漢字カード", "国語"),
    Math("数学カード", null),
    Words("単語カード", "英語"),
    Science("理科カード", null),
    Social("社会カード", null),
}

/** One downloaded set and what it is published under, for the licence screen. */
data class CardDataSource(val title: String, val asOf: String, val licenses: List<CardLicense>)

sealed interface CardsLoad {
    data object Loading : CardsLoad
    data class Failed(val message: String) : CardsLoad
    data class Ready(
        val words: List<WordCard>,
        val kanji: List<KanjiCard>,
        val subjectCards: List<SubjectCard>,
        val sources: List<CardDataSource>,
    ) : CardsLoad {
        fun cards(deck: Deck): List<StudyCard> = when (deck) {
            Deck.Kanji -> kanji
            Deck.Words -> words
            Deck.Math -> subjectCards.filter { it.subject == CardSubjects.MATH }
            Deck.Science -> subjectCards.filter { it.subject == CardSubjects.SCIENCE }
            Deck.Social -> subjectCards.filter { it.subject == CardSubjects.SOCIAL_STUDIES }
        }
    }
}

/**
 * The card sets, the student's progress through them, and the range they chose.
 *
 * Shared by the deck list and the study screen, so a 「わかった」 is already in
 * the counts when the student comes back.
 */
class CardsState(private val library: CardLibrary) {
    private val _load = MutableStateFlow<CardsLoad>(CardsLoad.Loading)
    val load: StateFlow<CardsLoad> = _load.asStateFlow()

    private val _progress = MutableStateFlow(CardProgress())
    val progress: StateFlow<CardProgress> = _progress.asStateFlow()

    /** Everything by default, so nothing is hidden before the student chooses. */
    private val _scope = MutableStateFlow(CardDeck.Scope())
    val scope: StateFlow<CardDeck.Scope> = _scope.asStateFlow()

    fun choose(scope: CardDeck.Scope) {
        _scope.value = scope
    }

    /** Device first; the network is only asked whether the stored sets are stale. */
    suspend fun refresh() {
        if (_load.value !is CardsLoad.Ready) _load.value = CardsLoad.Loading

        _load.value = try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val words = async { library.words() }
                    val kanji = async { library.kanji() }
                    val subjects = async { library.subjectCards() }

                    _progress.value = library.progress()
                    CardsLoad.Ready(
                        words = words.await().cards,
                        kanji = kanji.await().cards,
                        subjectCards = subjects.await().cards,
                        sources = listOf(
                            CardDataSource("単語カード", words.await().asOf, words.await().licenses),
                            CardDataSource("漢字カード", kanji.await().asOf, kanji.await().licenses),
                            CardDataSource("数学・理科・社会カード", subjects.await().asOf, subjects.await().licenses),
                        ),
                    )
                }
            }
        } catch (e: ApiError) {
            CardsLoad.Failed("カードを読み込めませんでした。通信を確かめて、もう一度ためしてください。")
        }
    }

    /** One sitting from [deck], in the current range. Dealt once, when the sitting starts. */
    fun session(deck: Deck): List<StudyCard> {
        val ready = _load.value as? CardsLoad.Ready ?: return emptyList()
        return CardDeck.session(ready.cards(deck), _scope.value, _progress.value, LocalDate.now())
    }

    suspend fun answer(cardId: String, correct: Boolean) {
        _progress.value = withContext(Dispatchers.IO) { library.record(cardId, correct) }
    }
}
