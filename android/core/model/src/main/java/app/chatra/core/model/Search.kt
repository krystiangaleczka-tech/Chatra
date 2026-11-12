package app.chatra.core.model

import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

enum class SearchFilter { All, Codes, Parcels, Attachments }
enum class SearchContent { Any, Text, Images, Videos, Audio, Documents }
data class SearchCursor(val timestamp: Long, val rowid: Long)
data class SearchQuery(
    val text: String = "", val filter: SearchFilter = SearchFilter.All,
    val sender: ConversationId? = null, val since: Instant? = null,
    val until: Instant? = null, val senderAddress: String? = null,
    val content: SearchContent = SearchContent.Any, val subscriptionId: SubscriptionId? = null,
    val after: SearchCursor? = null, val pageSize: Int = 40,
) {
    val hasFilters: Boolean get() = filter != SearchFilter.All || sender != null || since != null || until != null || senderAddress != null || content != SearchContent.Any || subscriptionId != null
}
data class SearchHit(val message: Message, val title: String)
enum class PatternConfidence { Contextual, PatternOnly }
data class QuickHit(val label: String, val value: String, val source: MessageRef, val sender: String = "", val confidence: PatternConfidence = PatternConfidence.Contextual, val conversationId: ConversationId? = null, val message: Message? = null)
data class SearchResults(val hits: List<SearchHit> = emptyList(), val quick: List<QuickHit> = emptyList(), val indexing: Boolean = false, val indexed: Int = 0, val totalDocuments: Int = 0, val next: SearchCursor? = null)

/** Local search contract; the full flavor maintains a rebuildable Room/FTS index. */
interface SearchRepository {
    suspend fun rebuild() {}
    val changes: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    fun search(query: SearchQuery): Flow<SearchResults>
}

fun searchFold(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ł', 'l').replace("ß", "ss").replace('ς', 'σ'), Normalizer.Form.NFKD)
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

fun searchTerms(text: String): List<String> = Regex("[\\p{L}\\p{N}]+").findAll(searchFold(text)).map { it.value }.take(12).toList()

fun searchTermRanges(text: String, query: String): List<IntRange> {
    val terms = searchTerms(query)
    return (if (terms.isEmpty()) searchRanges(text, query) else terms.flatMap { searchRanges(text, it) }).distinct().sortedBy { it.first }
}

fun SearchHit.matches(query: SearchQuery): Boolean {
    val terms = searchTerms(query.text)
    val searchable = searchFold(message.body + " " + title + " " + message.attachments.joinToString(" ") { it.label + " " + it.mimeType })
    val words = Regex("[\\p{L}\\p{N}]+").findAll(searchable).map { it.value }.toList()
    val textMatches = when {
        query.text.isBlank() -> query.hasFilters
        terms.isEmpty() -> searchFold(query.text.trim()) in searchable
        else -> terms.all { term -> words.any { it.startsWith(term) } }
    }
    return textMatches &&
        (query.sender == null || message.conversationId == query.sender) &&
        (query.senderAddress == null || message.direction == Direction.Incoming && message.sender?.let { recipientKey(it.raw) } == recipientKey(query.senderAddress)) &&
        (query.since == null || message.timestamp >= query.since) &&
        (query.until == null || message.timestamp < query.until) &&
        (query.subscriptionId == null || message.subscriptionId == query.subscriptionId) &&
        when (query.filter) {
            SearchFilter.All -> true
            SearchFilter.Codes -> recognizeQuick(message.body).any { it.kind == SearchFilter.Codes }
            SearchFilter.Parcels -> parcelContext(message.body) || recognizeQuick(message.body).any { it.kind == SearchFilter.Parcels }
            SearchFilter.Attachments -> message.attachments.isNotEmpty()
        } && when (query.content) {
            SearchContent.Any -> true
            SearchContent.Text -> message.body.isNotBlank()
            SearchContent.Images -> message.attachments.any { it.mimeType.startsWith("image/") }
            SearchContent.Videos -> message.attachments.any { it.mimeType.startsWith("video/") }
            SearchContent.Audio -> message.attachments.any { it.mimeType.startsWith("audio/") }
            SearchContent.Documents -> message.attachments.any { !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/") && !it.mimeType.startsWith("audio/") }
        }
}

/** A contextual pattern is never evidence that the sender is authentic. Offsets refer to source UTF-16. */
data class QuickPattern(val kind: SearchFilter, val value: String, val range: IntRange, val confidence: PatternConfidence = PatternConfidence.Contextual)
fun recognizeQuick(body: String): List<QuickPattern> {
    val result = mutableListOf<QuickPattern>()
    val code = Regex("(?iu)(?:\\bkod(?:u|em)?(?:[ \\t]+(?:odbioru|dost[eę]pu|weryfikacyjny|potwierdzenia))?|\\bpin|\\botp)[ \\t]*[:=\\-]?[ \\t]*([0-9]{3}[ -][0-9]{3}|[0-9]{4,8})(?![\\p{L}\\p{N}])")
    for (match in code.findAll(body)) {
        val group = match.groups[1] ?: continue
        if (group.range.first > 0 && body[group.range.first - 1] == '+') continue
        result += QuickPattern(SearchFilter.Codes, group.value, group.range)
    }
    val parcel = Regex("(?iu)(?:\\b(?:nr|numer)[ \\t]+(?:przesy[lł]ki|paczki)|\\btracking(?:[ \\t]+number)?|\\bprzesy[lł]ka)[ \\t]*[:#=]?[ \\t]*([A-Z0-9]{8,30})(?![A-Z0-9])")
    for (match in parcel.findAll(body)) {
        val group = match.groups[1] ?: continue
        if (group.value.any(Char::isDigit)) result += QuickPattern(SearchFilter.Parcels, group.value, group.range)
    }
    return result.distinctBy { it.kind to it.value }
}

fun parcelContext(body: String): Boolean = listOf("paczk", "przesyl", "kurier", "paczkomat", "automat paczk").any { it in searchFold(body) }

data class SearchDateWindow(val since: Instant?, val until: Instant?)
fun searchDateWindow(from: java.time.LocalDate?, through: java.time.LocalDate?, zone: java.time.ZoneId): SearchDateWindow {
    require(from == null || through == null || !through.isBefore(from)) { "Data końcowa poprzedza początkową." }
    return SearchDateWindow(from?.atStartOfDay(zone)?.toInstant(), through?.plusDays(1)?.atStartOfDay(zone)?.toInstant())
}
