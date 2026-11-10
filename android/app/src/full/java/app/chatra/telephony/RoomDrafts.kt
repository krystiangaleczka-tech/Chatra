package app.chatra.telephony

import app.chatra.core.database.ChatraDatabase
import app.chatra.core.database.SavedDraft
import app.chatra.core.model.*
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class RoomDrafts(private val db: ChatraDatabase) : DraftRepository {
    override fun draft(conversationId: ConversationId) = db.dao().draft(conversationId.value).map { row -> row?.let {
        val j = JSONObject(it.payload)
        Draft(DraftId(it.id), it.conversationId?.let(::ConversationId), addresses(j.getString("recipients")), j.getString("text"), RecipientMode.valueOf(j.getString("mode")), emptyList(),
            if (j.isNull("sim")) null else SubscriptionId(j.getInt("sim")), Instant.ofEpochMilli(j.getLong("at")), subscriptionLabel = j.optString("simLabel").takeIf(String::isNotBlank))
    } }
    override suspend fun save(draft: Draft) {
        require(draft.attachmentIds.isEmpty()) { "Trwałe załączniki wymagają CH031." }
        val payload = JSONObject().put("recipients", JSONArray(draft.recipients.map { it.raw }).toString()).put("text", draft.text)
            .put("mode", draft.recipientMode.name).put("sim", draft.subscriptionId?.value ?: JSONObject.NULL)
            .put("simLabel", draft.subscriptionLabel.orEmpty()).put("at", draft.updatedAt.toEpochMilli()).toString()
        db.dao().saveDraft(SavedDraft(draft.id.value, draft.conversationId?.value, payload))
    }
    override suspend fun delete(id: DraftId) { db.dao().deleteDraft(id.value) }
}
