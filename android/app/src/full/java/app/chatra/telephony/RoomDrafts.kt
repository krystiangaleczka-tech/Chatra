package app.chatra.telephony

import androidx.room.withTransaction
import app.chatra.core.database.*
import app.chatra.core.model.*
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Versioned drafts and immutable operation snapshots share one codec. URI ownership is CH031. */
internal object DraftCodec {
    fun encode(d: Draft): String = JSONObject().apply {
        put("recipients", JSONArray(d.recipients.map { it.raw }).toString()); put("text", d.text)
        put("mode", d.recipientMode.name); put("sim", d.subscriptionId?.value ?: JSONObject.NULL)
        put("simLabel", d.subscriptionLabel.orEmpty()); put("at", d.updatedAt.toEpochMilli()); put("instant", d.updatedAt.toString())
        put("attachments", JSONArray(d.attachmentIds.map { it.value })); put("bytes", d.attachmentBytes)
        put("variant", d.mediaVariant?.name ?: JSONObject.NULL); put("revision", d.revision)
        put("names", JSONArray(d.recipientNames)); put("input", d.recipientInput)
    }.toString()
    fun decode(payload: String): Draft = decode(SavedDraft("snapshot", null, payload))
    fun decode(row: SavedDraft): Draft {
        val j = JSONObject(row.payload)
        fun strings(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return Draft(DraftId(row.id), row.conversationId?.let(::ConversationId), addresses(j.getString("recipients")), j.getString("text"),
            RecipientMode.valueOf(j.getString("mode")), strings("attachments").map(::AttachmentId),
            if (j.isNull("sim")) null else SubscriptionId(j.getInt("sim")), j.optString("instant").takeIf(String::isNotEmpty)?.let(Instant::parse) ?: Instant.ofEpochMilli(j.getLong("at")),
            j.optString("variant").let { MediaVariant.entries.firstOrNull { v -> v.name == it } }, j.optLong("bytes"),
            j.optString("simLabel").takeIf(String::isNotBlank), j.optLong("revision"), strings("names"), j.optString("input"))
    }
}

class RoomDrafts(private val db: ChatraDatabase) : DraftRepository {
    private val dao = db.dao()
    override val durable = true
    override fun draft(conversationId: ConversationId) = dao.draft(conversationId.value).map { it?.let(DraftCodec::decode) }
    override fun all() = dao.drafts().map { rows -> rows.map(DraftCodec::decode) }
    override suspend fun find(id: DraftId) = dao.savedDraft(id.value)?.let(DraftCodec::decode)
    override suspend fun isClosed(id: DraftId) = dao.closedDraft(id.value) != 0
    override suspend fun save(draft: Draft) = db.withTransaction {
        if (isClosed(draft.id)) return@withTransaction
        val old = dao.savedDraft(draft.id.value)
        if (old != null && old.revision >= draft.revision) return@withTransaction
        dao.saveDraft(SavedDraft(draft.id.value, draft.conversationId?.value, DraftCodec.encode(draft), draft.revision, draft.updatedAt.toEpochMilli()))
    }
    /** Called in the same transaction as accepting the operation; delayed writers cannot revive it. */
    internal suspend fun consume(draft: Draft) {
        val newer = find(draft.id)?.takeIf { it.revision > draft.revision }
        if (newer != null) save(newer.copy(id = DraftId.new()))
        dao.closeDraft(ClosedDraft(draft.id.value)); dao.deleteDraft(draft.id.value)
    }
    internal fun snapshot(row: OutgoingOperation): Draft = row.snapshot?.let {
        DraftCodec.decode(SavedDraft(row.draftKey, row.conversationId, it))
    } ?: Draft(DraftId(row.draftKey), row.conversationId?.let(::ConversationId), addresses(row.recipients), row.body,
        RecipientMode.valueOf(row.mode), emptyList(), SubscriptionId(row.subscriptionId), Instant.ofEpochMilli(row.createdAt), subscriptionLabel = row.subscriptionLabel)
    internal suspend fun restore(row: OutgoingOperation): Draft = snapshot(row).copy(id = DraftId.new(), updatedAt = Instant.now()).also { save(it) }
    override suspend fun delete(id: DraftId) = db.withTransaction {
        dao.closeDraft(ClosedDraft(id.value)); dao.deleteDraft(id.value)
    }
}
