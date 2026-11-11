package app.chatra.core.model

import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

enum class SearchFilter { All, Codes, Parcels, Attachments }
data class SearchQuery(val text: String = "", val filter: SearchFilter = SearchFilter.All, val sender: ConversationId? = null, val since: Instant? = null)
data class SearchHit(val message: Message, val title: String)
data class QuickHit(val label: String, val value: String, val source: MessageRef)
data class SearchResults(val hits: List<SearchHit> = emptyList(), val quick: List<QuickHit> = emptyList(), val indexing: Boolean = false)

/** CH016: lokalny kontrakt. Indeks produkcyjny i jego cykl życia należą do CH038. */
interface SearchRepository {
    fun search(query: SearchQuery): Flow<SearchResults>
}

fun searchFold(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

/** Zakresy w oryginalnym tekście, również gdy znak jest zapisany jako litera + combining mark. */
fun searchRanges(text: String, query: String): List<IntRange> {
    val needle = searchFold(query.trim())
    if (needle.isEmpty()) return emptyList()
    val folded = StringBuilder()
    val starts = mutableListOf<Int>()
    val ends = mutableListOf<Int>()
    var index = 0
    while (index < text.length) {
        val start = index
        index += Character.charCount(text.codePointAt(index))
        while (index < text.length && Character.getType(text.codePointAt(index)) in setOf(6, 7, 8)) index += Character.charCount(text.codePointAt(index))
        val part = searchFold(text.substring(start, index))
        part.forEach { folded.append(it); starts += start; ends += index }
    }
    val out = mutableListOf<IntRange>()
    var offset = 0
    while (offset <= folded.length - needle.length) {
        val found = folded.indexOf(needle, offset)
        if (found < 0) break
        out += starts[found] until ends[found + needle.length - 1]
        offset = found + needle.length
    }
    return out
}

fun SearchHit.matches(query: SearchQuery): Boolean {
    val body = searchFold(message.body)
    return query.text.isNotBlank() && (searchFold(query.text.trim()) in body || searchFold(query.text.trim()) in searchFold(title)) &&
        (query.sender == null || message.conversationId == query.sender) &&
        (query.since == null || message.timestamp >= query.since) &&
        when (query.filter) {
            SearchFilter.All -> true
            SearchFilter.Codes -> Regex("\\b\\d{3}[ -]?\\d{3}\\b").containsMatchIn(message.body) || "kod" in body
            SearchFilter.Parcels -> listOf("paczk", "kurier", "przesyl", "automat").any { it in body }
            SearchFilter.Attachments -> message.attachments.isNotEmpty()
        }
}
