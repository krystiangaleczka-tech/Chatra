package app.chatra.online

import app.chatra.core.database.*
import app.chatra.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import org.json.JSONArray

/** One inbox and one read model for canonical SMS/MMS and private HD records. */
internal class HdHistory(
    private val base: ConversationRepository, private val db: ChatraDatabase,
    private val media: AttachmentStore,
    /** Gdy podany, wszyscy odbiorcy skrzynki dzielą jedno źródło z ostatnią listą (patrz [inbox]). */
    shareScope: CoroutineScope? = null,
    private val allowed: () -> Boolean,
) : ConversationRepository by base {
    private suspend fun merge(summary: ConversationSummary, rows: List<HdRecord>): ConversationSummary {
        val latest = rows.maxByOrNull { it.createdAt } ?: return summary
        val unread = rows.count { it.incoming && !it.read }
        val manual = db.dao().conversation(summary.id.value)?.manualUnread == true
        val providerUnread = if (manual) db.dao().unreadRefs(summary.id.value).size else summary.unreadCount
        return summary.copy(lastActivity = maxOf(summary.lastActivity, Instant.ofEpochMilli(latest.createdAt)),
            preview = if (latest.createdAt >= summary.lastActivity.toEpochMilli()) ConversationSummary.Preview.Text("Załączniki Chatra HD") else summary.preview,
            unreadCount = maxOf(providerUnread + unread, if (manual) 1 else 0))
    }

    /**
     * Skrzynka czyta ostatnią wiadomość każdego wątku z providera — przy setkach rozmów to sekundy pracy.
     * Lista i wyszukiwarka dzielą więc jedno źródło, a powrót na listę od razu dostaje ostatni wynik,
     * zamiast liczyć wszystko od nowa (świeża lista przychodzi zaraz po nim).
     */
    private val sharedInbox = shareScope?.let { scope -> inboxSource().shareIn(scope, SharingStarted.WhileSubscribed(30_000), replay = 1) }

    override fun inbox(): Flow<List<ConversationSummary>> = sharedInbox ?: inboxSource()

    private fun inboxSource(): Flow<List<ConversationSummary>> = combine(base.inbox(), db.hd().visible()) { sms, rows ->
        if (!allowed()) emptyList() else {
            val grouped = rows.groupBy { it.conversationId }
            val summaries = sms.associateBy { it.id.value }.toMutableMap()
            for ((id, hd) in grouped) {
                val summary = summaries[id] ?: base.conversation(ConversationId(id)).first() ?: continue
                summaries[id] = merge(summary, hd)
            }
            summaries.values.toList().inboxOrder()
        }
    }.flowOn(Dispatchers.IO)

    override fun conversation(id: ConversationId): Flow<ConversationSummary?> = combine(base.conversation(id), db.hd().visible()) { summary, rows ->
        if (!allowed()) null else summary?.let { merge(it, rows.filter { r -> r.conversationId == id.value }) }
    }.flowOn(Dispatchers.IO)

    override fun messages(id: ConversationId): Flow<List<Message>> = combine(base.messages(id), db.hd().visible()) { messages, all ->
        if (!allowed()) emptyList() else {
            val hd = all.filter { it.conversationId == id.value }.map { render(id, it) }
            (messages + hd).sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.ref.toString() })
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun render(id: ConversationId, row: HdRecord): Message {
        val files = JSONArray(row.manifest)
        val local = HdMailbox.ids(row.localFiles)
        val attachments = (0 until files.length()).map { index ->
            val fileId = local.getOrNull(index)
            val file = fileId?.let { media.get(it) }
            val mime = files.getJSONObject(index).optString("mime").ifBlank { "application/octet-stream" }
            AttachmentSummary(fileId ?: AttachmentId("hd-slot:${row.id}:$index"), file?.mime ?: mime, "Plik HD ${index + 1}", file?.uri)
        }
        return Message(MessageRef.Hd(row.id), id, if (row.incoming) Direction.Incoming else Direction.Outgoing,
            Address(row.sender), "", Instant.ofEpochMilli(row.createdAt), row.read,
            status = if (!row.incoming) DeliveryStatus.Sent else DeliveryStatus.None,
            attachments = attachments,
            hd = HdInfo(files.length(), (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") },
                downloadedBy = if (!row.incoming && row.downloadedAt != null) row.recipient else null,
                envelopeId = row.id, stage = row.state, bytes = row.bytes))
    }

    override suspend fun firstUnread(id: ConversationId): UnreadAnchor? = withContext(Dispatchers.IO) {
        if (!allowed()) return@withContext null
        val provider = base.firstUnread(id)
        val hd = db.hd().firstUnread(id.value)?.let { UnreadAnchor(MessageRef.Hd(it.id), Instant.ofEpochMilli(it.createdAt)) }
        listOfNotNull(provider?.takeUnless { it.manual }, hd).minByOrNull { it.timestamp }?.let { return@withContext it }
        if (db.dao().conversation(id.value)?.manualUnread != true) return@withContext null
        val fallback = db.hd().snapshot(id.value).filter { it.incoming }.maxByOrNull { it.createdAt }
            ?.let { UnreadAnchor(MessageRef.Hd(it.id), Instant.ofEpochMilli(it.createdAt), manual = true) }
        listOfNotNull(provider, fallback).maxByOrNull { it.timestamp }
    }

    override suspend fun exportMessages(id: ConversationId, limit: Int): List<Message> = withContext(Dispatchers.IO) {
        check(allowed()) { "Brak dostępu do historii." }
        val provider = base.exportMessages(id, limit)
        val hd = db.hd().snapshot(id.value).sortedBy { it.createdAt }.takeLast(limit).map { render(id, it) }
        (provider + hd).sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.ref.toString() }).takeLast(limit)
    }

    override suspend fun markRead(id: ConversationId) { base.markRead(id); db.hd().read(id.value) }
    override suspend fun markShown(id: ConversationId, refs: Set<MessageRef>) {
        if (!allowed()) return
        val hd = refs.filterIsInstance<MessageRef.Hd>()
        val manualAnchor = if (hd.isNotEmpty() && db.dao().conversation(id.value)?.manualUnread == true) {
            firstUnread(id)?.takeIf { it.manual }
        } else null
        val provider = refs.filterIsInstance<MessageRef.Provider>().toSet()
        if (provider.isNotEmpty()) base.markShown(id, provider)
        if (hd.isNotEmpty()) db.hd().shown(id.value, hd.map { it.id })
        if (manualAnchor != null && hd.any { it == manualAnchor.ref }) base.clearManualUnread(id)
    }
    override suspend fun markAllRead() { base.markAllRead(); db.hd().all().map { it.conversationId }.distinct().forEach { db.hd().read(it) } }
}

/** HD descriptors come from the conversation's manifest, including files that have yet to arrive. */
internal class HdGallery(
    private val base: MediaGallery,
    private val db: ChatraDatabase,
    private val media: AttachmentStore,
    private val allowed: () -> Boolean,
) : MediaGallery {
    private data class Slot(val row: HdRecord, val position: Int, val mime: String, val fileId: AttachmentId?) {
        val source = MessageRef.Hd(row.id)
        val label = "Plik HD ${position + 1}"
    }

    private fun slots(row: HdRecord): List<Slot> {
        val manifest = JSONArray(row.manifest)
        val ids = HdMailbox.ids(row.localFiles)
        return List(manifest.length()) { i ->
            val mime = manifest.getJSONObject(i).optString("mime").ifBlank { "application/octet-stream" }
            Slot(row, i, mime, ids.getOrNull(i))
        }
    }

    private suspend fun preview(slot: Slot): GalleryItem {
        val file = slot.fileId?.let { media.preview(it) }
        val state = when {
            file != null -> GalleryAvailability.Ready
            slot.row.state == "EXPIRED" -> GalleryAvailability.Expired
            slot.fileId != null || slot.row.state in setOf("LOCAL", "SENT", "READY") -> GalleryAvailability.Missing
            slot.row.state in setOf("QUEUED", "DOWNLOADING", "RECEIPT") -> GalleryAvailability.Downloading
            else -> GalleryAvailability.AwaitingDownload
        }
        return GalleryItem(slot.fileId ?: AttachmentId("hd-slot:${slot.row.id}:${slot.position}"),
            slot.source, file?.mime ?: slot.mime, file?.uri.orEmpty(), slot.label, Transport.HD, state)
    }

    override suspend fun page(conversation: ConversationId, kind: MediaKind, before: Long?, limit: Int): GalleryPage =
        withContext(Dispatchers.IO) {
            require(limit in 1..100)
            check(allowed()) { "Brak dostępu do historii." }
            if (kind == MediaKind.Links) return@withContext base.page(conversation, kind, before, limit)

            val hd = db.hd().snapshot(conversation.value)
                .sortedWith(compareByDescending<HdRecord> { it.createdAt }.thenByDescending { it.id })
                .flatMap(::slots).filter { kind.includes(it.mime) }
            // Negative cursors are offsets in the HD entries. Nonnegative cursors belong to MMS.
            val offset = if (before == null) 0 else if (before < 0) (-before - 1).coerceAtMost(hd.size.toLong()).toInt() else hd.size
            val hdPage = hd.drop(offset).take(limit).map { preview(it) }
            val remaining = limit - hdPage.size
            val provider = base.page(conversation, kind, before?.takeIf { it >= 0 }, maxOf(remaining, 1))
            val nextHd = offset + hdPage.size
            val next = when {
                nextHd < hd.size -> -(nextHd + 1L)
                remaining == 0 && provider.total > 0 -> -(hd.size + 1L)
                else -> provider.nextBefore
            }
            GalleryPage(hdPage + provider.items.take(remaining), hd.size + provider.total, next)
        }

    override suspend fun readable(id: AttachmentId): GalleryItem? = readable(id, null)

    override suspend fun readable(item: GalleryItem): GalleryItem? =
        if (item.source is MessageRef.Hd) readable(item.id, item.source as MessageRef.Hd)
        else base.readable(item)

    private suspend fun readable(id: AttachmentId, source: MessageRef.Hd?): GalleryItem? = withContext(Dispatchers.IO) {
        if (!allowed()) return@withContext null
        val row = db.hd().visibleWithFile(id.value).firstOrNull { (source == null || it.id == source.id) && id in HdMailbox.ids(it.localFiles) }
            ?: return@withContext if (source == null) base.readable(id) else null
        val position = HdMailbox.ids(row.localFiles).indexOf(id)
        val file = media.get(id) ?: return@withContext null
        if (!allowed() || db.hd().visibleWithFile(id.value).none { it.id == row.id }) return@withContext null
        GalleryItem(id, MessageRef.Hd(row.id), file.mime, file.uri, "Plik HD ${position + 1}", Transport.HD)
    }
}
