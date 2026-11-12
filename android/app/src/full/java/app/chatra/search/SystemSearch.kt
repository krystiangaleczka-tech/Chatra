package app.chatra.search

import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.telephony.*
import app.chatra.platform.SystemPlatform
import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

interface SearchSource {
    val changes: Flow<Int>
    fun allowed(): Boolean
    suspend fun references(): List<ProviderMessageRef>
    suspend fun load(refs: List<ProviderMessageRef>): List<SearchHit>
}
class ProviderSearchSource(private val context: Context, private val db: ChatraDatabase,
    private val history: SystemHistory, private val contacts: ContactsDirectory, private val platform: SystemPlatform) : SearchSource {
    @Volatile private var meta: Map<String, ConversationMeta> = emptyMap()
    @Volatile private var directory: Map<String, Participant> = emptyMap()
    override val changes = combine(history.indexChanges, contacts.suggestions(), platform.changes) { generation, _, access -> generation + access }
    override fun allowed() = context.smsRead(platform)
    override suspend fun references(): List<ProviderMessageRef> {
        check(allowed()) { "Brak dostępu do historii." }
        val status = history.historyStatus().first { it != HistoryStatus.Loading }
        check(status == HistoryStatus.Ready) { "Historia czeka na ponowne odczytanie." }
        meta = db.dao().conversations().first().associateBy { it.id }
        directory = contacts.suggestions().first().associateBy { recipientKey(it.address.raw) }
        return db.dao().references().first()
    }
    override suspend fun load(refs: List<ProviderMessageRef>): List<SearchHit> {
        check(allowed())
        if (meta.isEmpty()) meta = db.dao().conversations().first().associateBy { it.id }
        return history.searchMessages(refs).mapNotNull { message ->
            val meta = meta[message.conversationId.value] ?: return@mapNotNull null
            if (meta.trashed) return@mapNotNull null
            val title = addresses(meta.participants).joinToString(", ") { directory[recipientKey(it.raw)]?.label ?: it.raw }
            SearchHit(message, title)
        }
    }
}

data class IndexProgress(val indexing: Boolean = true, val indexed: Int = 0, val total: Int = 0, val error: String? = null)
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SystemSearch(private val db: ChatraDatabase, private val source: SearchSource, scope: CoroutineScope, autoIndex: Boolean = true) : SearchRepository {
    private val dao = db.search()
    private val mutex = Mutex()
    private val request = MutableStateFlow(0)
    val progress = MutableStateFlow(IndexProgress())
    override val changes = MutableStateFlow(0)
    init {
        if (autoIndex) scope.launch(Dispatchers.IO) {
            var completedRequest = 0
            combine(source.changes, request) { a, b -> a to b }.debounce(100).collectLatest { (_, revision) -> rebuildNow(force = revision != completedRequest); completedRequest = revision }
        }
    }
    override suspend fun rebuild() { request.value++ }
    suspend fun rebuildNow(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!source.allowed()) { progress.value = IndexProgress(false); return@withLock }
            progress.value = IndexProgress()
            try {
                val refs = source.references()
                val generation = UUID.randomUUID().toString()
                progress.value = IndexProgress(true, 0, refs.size)
                val before = dao.count()
                var mutated = false
                var done = 0
                for (page in refs.chunked(200)) {
                    coroutineContext.ensureActive(); check(source.allowed())
                    val documents = source.load(page).map { document(it, generation) }
                    val hashes = dao.hashes(documents.map { it.rowid }).associate { it.rowid to it.hash }
                    val changed = documents.filter { hashes[it.rowid] != it.hash }
                    val reindexed = if (force) documents else changed
                    if (reindexed.isNotEmpty()) mutated = true
                    db.withTransaction {
                        dao.documents(changed)
                        dao.fts(reindexed.map { d -> SearchFts(d.rowid, searchFold(d.body + " " + d.title + " " + mediaText(d.media))) })
                        dao.seen(documents.filter { hashes[it.rowid] == it.hash }.map { it.rowid }, generation)
                    }
                    done += page.size; progress.value = IndexProgress(true, done, refs.size)
                }
                check(source.allowed())
                db.withTransaction { dao.sweepFts(generation); dao.sweep(generation) }
                progress.value = IndexProgress(false, done, refs.size)
                if (mutated || dao.count() != before) changes.value++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { progress.value = progress.value.copy(indexing = false, error = "Nie udało się odtworzyć indeksu. Historia pozostaje bez zmian.") }
        }
    }
    private fun mediaText(json: String): String = JSONArray(json).let { a -> (0 until a.length()).joinToString(" ") { i -> a.getJSONObject(i).let { it.getString("mime") + " " + it.getString("label") } } }
    private fun document(hit: SearchHit, generation: String): SearchDocument {
        val m = hit.message; val ref = (m.ref as MessageRef.Provider).ref
        require(ref.providerId in 1..Long.MAX_VALUE / 2)
        val id = ref.providerId * 2 + if (ref.transport == ProviderTransport.MMS) 1 else 0
        val media = JSONArray(m.attachments.map { attachment ->
            val category = when {
                attachment.mimeType.startsWith("image/") -> SearchContent.Images
                attachment.mimeType.startsWith("video/") -> SearchContent.Videos
                attachment.mimeType.startsWith("audio/") -> SearchContent.Audio
                else -> SearchContent.Documents
            }
            JSONObject().put("mime", attachment.mimeType).put("label", attachment.label).put("category", category.name)
        }).toString()
        val patterns = recognizeQuick(m.body)
        val code = patterns.firstOrNull { it.kind == SearchFilter.Codes }?.value
        val parcel = patterns.firstOrNull { it.kind == SearchFilter.Parcels }?.value ?: if (parcelContext(m.body)) "" else null
        val signature = listOf(m.body, hit.title, m.sender?.raw, m.subscriptionId?.value, media, m.timestamp, m.direction).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        val hex = "0123456789abcdef"
        val hash = buildString(64) { digest.forEach { byte -> val b = byte.toInt() and 255; append(hex[b ushr 4]); append(hex[b and 15]) } }
        return SearchDocument(id, ref.transport.name, ref.providerId, m.conversationId.value, m.body, hit.title,
            if (m.direction == Direction.Incoming) m.sender?.let { recipientKey(it.raw) }.orEmpty() else "", m.timestamp.toEpochMilli(), m.subscriptionId?.value, media,
            m.attachments.isNotEmpty(), code, parcel, hash, generation, m.direction == Direction.Incoming)
    }
    private fun sql(q: SearchQuery): SimpleSQLiteQuery {
        require(q.pageSize in 1..100)
        require(q.text.length <= 512)
        require(q.since?.let { start -> q.until?.let { end -> start < end } } != false) { "Nieprawidłowy zakres dat." }
        val where = mutableListOf<String>(); val args = mutableListOf<Any?>()
        val terms = searchTerms(q.text)
        // Adjacent phrases are AND in both SQLite FTS3/4 query syntax builds.
        if (terms.isNotEmpty()) { where += "d.rowid IN (SELECT rowid FROM SearchFts WHERE SearchFts MATCH ?)"; args += terms.joinToString(" ") { "\"$it*\"" } }
        else if (q.text.isNotBlank()) { where += "instr(d.body,?)>0"; args += q.text.trim() }
        q.sender?.let { where += "d.conversationId=?"; args += it.value }
        q.senderAddress?.let { where += "d.sender=?"; args += recipientKey(it) }
        q.since?.let { where += "d.timestamp>=?"; args += it.toEpochMilli() }
        q.until?.let { where += "d.timestamp<?"; args += it.toEpochMilli() }
        q.subscriptionId?.let { where += "d.subscriptionId=?"; args += it.value }
        when (q.filter) {
            SearchFilter.All -> Unit
            SearchFilter.Codes -> where += "d.code IS NOT NULL"
            SearchFilter.Parcels -> where += "d.parcel IS NOT NULL"
            SearchFilter.Attachments -> where += "d.hasAttachments=1"
        }
        when (q.content) {
            SearchContent.Any -> Unit
            SearchContent.Text -> where += "length(trim(d.body))>0"
            else -> {
                where += "d.media LIKE ?"
                args += "%\"category\":\"${q.content.name}\"%"
            }
        }
        q.after?.let { where += "(d.timestamp<? OR (d.timestamp=? AND d.rowid<?))"; args += it.timestamp; args += it.timestamp; args += it.rowid }
        return SimpleSQLiteQuery("SELECT d.* FROM SearchDocument d${if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")} ORDER BY d.timestamp DESC,d.rowid DESC LIMIT ${q.pageSize + 1}", args.toTypedArray())
    }
    override fun search(query: SearchQuery): Flow<SearchResults> = flow {
        check(source.allowed()) { "Brak dostępu do wiadomości." }
        val initial = query.text.isBlank() && !query.hasFilters
        val q = if (initial) query.copy(filter = SearchFilter.Codes, pageSize = 4) else query
        emitAll(dao.observe(sql(q)).debounce(50).mapLatest { documents ->
            check(source.allowed())
            val page = documents.take(q.pageSize)
            val refs = page.map { ProviderMessageRef(it.kind, it.providerId, it.conversationId, it.timestamp, it.incoming, true, "") }
            val actual = source.load(refs).filter { it.matches(q) }
            val order = page.map { it.rowid }.withIndex().associate { it.value to it.index }
            val hits = actual.sortedBy { h -> val r = (h.message.ref as MessageRef.Provider).ref; order[r.providerId * 2 + if (r.transport == ProviderTransport.MMS) 1 else 0] ?: Int.MAX_VALUE }
            val quick = hits.flatMap { hit -> recognizeQuick(hit.message.body).map { pattern ->
                QuickHit(if (pattern.kind == SearchFilter.Codes) "Kod · ${hit.title}" else "Przesyłka · ${hit.title}", pattern.value, hit.message.ref,
                    hit.message.sender?.raw ?: hit.title, pattern.confidence, hit.message.conversationId, hit.message)
            } }.distinctBy { it.source to it.value }.take(4)
            val next = if (documents.size > q.pageSize) page.lastOrNull()?.let { SearchCursor(it.timestamp, it.rowid) } else null
            check(source.allowed())
            SearchResults(if (initial) emptyList() else hits, quick, next = if (initial) null else next)
        }.combine(progress) { results, p ->
            check(source.allowed())
            check(p.error == null) { p.error.orEmpty() }
            results.copy(indexing = p.indexing, indexed = p.indexed, totalDocuments = p.total)
        })
    }.flowOn(Dispatchers.IO)
}
