package app.chatra.online

import app.chatra.core.database.*
import app.chatra.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.Instant
import org.json.JSONArray

/** One inbox and one read model for canonical SMS/MMS and private HD records. */
internal class HdHistory(
    private val base: ConversationRepository, private val db: ChatraDatabase,
    private val media: AttachmentStore, private val allowed: () -> Boolean,
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

    override fun inbox(): Flow<List<ConversationSummary>> = combine(base.inbox(), db.hd().visible()) { sms, rows ->
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
            val hd = all.filter { it.conversationId == id.value }.map { row ->
                val files = JSONArray(row.manifest)
                val local = HdMailbox.ids(row.localFiles).mapNotNull { media.get(it) }
                Message(MessageRef.Hd(row.id), id, if (row.incoming) Direction.Incoming else Direction.Outgoing,
                    Address(row.sender), "", Instant.ofEpochMilli(row.createdAt), row.read,
                    status = if (!row.incoming) DeliveryStatus.Sent else DeliveryStatus.None,
                    attachments = local.map { AttachmentSummary(it.id, it.mime, "Załącznik HD", it.uri) },
                    hd = HdInfo(files.length(), (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") },
                        downloadedBy = if (!row.incoming && row.downloadedAt != null) row.recipient else null,
                        envelopeId = row.id, stage = row.state, bytes = row.bytes))
            }
            (messages + hd).sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.ref.toString() })
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun markRead(id: ConversationId) { base.markRead(id); db.hd().read(id.value) }
    override suspend fun markShown(id: ConversationId, refs: Set<MessageRef>) {
        base.markShown(id, refs)
        db.hd().shown(id.value, refs.filterIsInstance<MessageRef.Hd>().map { it.id })
    }
    override suspend fun markAllRead() { base.markAllRead(); db.hd().all().map { it.conversationId }.distinct().forEach { db.hd().read(it) } }
}

/** The same local file ID always points back to its HD message. Hidden sources cannot be opened. */
internal class HdGallery(private val base: MediaGallery, private val db: ChatraDatabase,
    private val media: AttachmentStore, private val allowed: () -> Boolean) : MediaGallery {
    private suspend fun items(conversation: ConversationId?, kind: MediaKind): List<GalleryItem> =
        if (!allowed()) emptyList() else db.hd().visible().first().filter { conversation == null || it.conversationId == conversation.value }
            .sortedByDescending { it.createdAt }.flatMap { row -> HdMailbox.ids(row.localFiles).mapNotNull { id -> media.get(id)?.let { file ->
                if (kind == MediaKind.Images && !file.mime.startsWith("image/") || kind == MediaKind.Videos && !file.mime.startsWith("video/")) null
                else GalleryItem(id, MessageRef.Hd(row.id), file.mime, file.uri, "Załącznik HD", Transport.HD)
            } } }.distinctBy { it.id }

    override suspend fun page(conversation: ConversationId, kind: MediaKind, before: Long?, limit: Int): GalleryPage {
        require(limit in 1..200)
        if (!allowed()) return GalleryPage(emptyList(), 0, null)
        val hd = items(conversation, kind)
        val offset = (before ?: 0).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val hdPage = hd.drop(offset).take(limit)
        val providerOffset = (offset - hd.size).coerceAtLeast(0)
        var cursor: Long? = null
        var skipped = 0
        var page = base.page(conversation, kind, cursor, 200)
        while (skipped + page.items.size <= providerOffset && page.nextBefore != null) {
            skipped += page.items.size; cursor = page.nextBefore
            page = base.page(conversation, kind, cursor, 200)
        }
        val provider = page.items.drop((providerOffset - skipped).coerceAtLeast(0)).take(limit - hdPage.size)
        val result = hdPage + provider
        val total = hd.size + page.total
        return GalleryPage(result, total, (offset + result.size).toLong().takeIf { it < total && result.isNotEmpty() })
    }
    override suspend fun readable(id: AttachmentId): GalleryItem? = if (!allowed()) null else items(null, MediaKind.All).firstOrNull { it.id == id } ?: base.readable(id)
}
