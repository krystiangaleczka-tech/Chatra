package app.chatra.di

import app.chatra.core.model.ConversationId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class BlockedPreview(val id: String, val label: String)
data class TrashPreview(val id: ConversationId, val title: String, val entryId: String = id.value, val expiresAt: Long? = null, val purging: Boolean = false)

/** Wspólny kontrakt porządku: Room w full, jawne fixture w demo. Mutacje raportują błędy wywołującemu. */
interface OrganizationPreview {
    val blocked: StateFlow<List<BlockedPreview>>
    val trash: StateFlow<List<TrashPreview>>
    val editable: Boolean
    suspend fun block(address: app.chatra.core.model.Address) {}
    suspend fun purge(entryId: String) {}
    suspend fun restoreEntry(entryId: String) { restore(ConversationId(entryId)) }
    suspend fun unblock(id: String)
    suspend fun restore(id: ConversationId)
    suspend fun addToTrash(id: ConversationId, title: String)
}

class MemoryOrganizationPreview(override val editable: Boolean = false, blocked: List<BlockedPreview> = emptyList(), trash: List<TrashPreview> = emptyList()) : OrganizationPreview {
    override val blocked = MutableStateFlow(blocked)
    override val trash = MutableStateFlow(trash)
    override suspend fun unblock(id: String) { if (editable) blocked.update { list -> list.filterNot { it.id == id } } }
    override suspend fun restore(id: ConversationId) { if (editable) trash.update { list -> list.filterNot { it.id == id } } }
    override suspend fun addToTrash(id: ConversationId, title: String) {
        if (editable) trash.update { list -> if (list.any { it.id == id }) list else list + TrashPreview(id, title) }
    }
}
