package dev.opencarl.study

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Card(
    val id: Long,
    val front: String,
    val back: String,
    val deck: String = "default",
    val ease: Double = 2.5,
    val intervalDays: Int = 0,
    val dueAt: Long = System.currentTimeMillis(),
    val reps: Int = 0,
    val lapses: Int = 0,
)

/** Flashcards with a compact SM-2 scheduler so the tutor can remember misses. */
class StudyStore(private val fileProvider: () -> File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file: File get() = fileProvider()

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    fun load() {
        val target = file
        if (!target.exists()) return
        _cards.value = runCatching { json.decodeFromString<List<Card>>(target.readText()) }
            .getOrDefault(emptyList())
    }

    fun add(front: String, back: String, deck: String = "default"): Card {
        val card = Card(
            id = (_cards.value.maxOfOrNull { it.id } ?: 0L) + 1,
            front = front.trim(),
            back = back.trim(),
            deck = deck.ifBlank { "default" },
        )
        _cards.value = _cards.value + card
        persist()
        return card
    }

    fun due(limit: Int = 10, deck: String? = null): List<Card> {
        val now = System.currentTimeMillis()
        return _cards.value
            .filter { it.dueAt <= now && (deck == null || it.deck == deck) }
            .sortedBy { it.dueAt }
            .take(limit)
    }

    /** quality: 0 blackout, 3 correct with effort, 5 instant. */
    fun grade(id: Long, quality: Int): Card? {
        val card = _cards.value.firstOrNull { it.id == id } ?: return null
        val q = quality.coerceIn(0, 5)
        var ease = card.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        if (ease < 1.3) ease = 1.3
        val interval = when {
            q < 3 -> 1
            card.intervalDays == 0 -> 1
            card.intervalDays == 1 -> 6
            else -> (card.intervalDays * ease).toInt().coerceAtMost(365)
        }
        val updated = card.copy(
            ease = ease,
            intervalDays = interval,
            dueAt = System.currentTimeMillis() + interval * 86_400_000L,
            reps = card.reps + 1,
            lapses = card.lapses + if (q < 3) 1 else 0,
        )
        _cards.value = _cards.value.map { if (it.id == id) updated else it }
        persist()
        return updated
    }

    fun stats(): String {
        val all = _cards.value
        if (all.isEmpty()) return "no cards yet"
        val dueNow = all.count { it.dueAt <= System.currentTimeMillis() }
        val decks = all.groupBy { it.deck }.map { (deck, cards) -> "$deck:${cards.size}" }
        val hardest = all.maxByOrNull { it.lapses }
            ?.let { " worst: ${it.front.take(40)} (${it.lapses} lapses)" }
            .orEmpty()
        return "${all.size} cards, $dueNow due, " + decks.joinToString(" ") + hardest
    }

    fun remove(id: Long): Boolean {
        val next = _cards.value.filterNot { it.id == id }
        val removed = next.size != _cards.value.size
        _cards.value = next
        if (removed) persist()
        return removed
    }

    private fun persist() {
        val target = file
        target.parentFile?.mkdirs()
        runCatching { target.writeText(json.encodeToString(_cards.value)) }
    }
}
