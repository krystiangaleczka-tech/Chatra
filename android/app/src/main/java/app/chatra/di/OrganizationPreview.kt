package app.chatra.di

import app.chatra.core.model.ConversationId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class BlockedPreview(val id: String, val label: String)
data class TrashPreview(val id: ConversationId, val title: String)

/** Kontrakt podwidoków. Trwałe blokady/tombstone w CH042/CH043; tu jawny prototyp demo. */
interface OrganizationPreview {
    val blocked: StateFlow<List<BlockedPreview>>
    val trash: StateFlow<List<TrashPreview>>
    val editable: Boolean
    fun unblock(id: String)
    fun restore(id: ConversationId)
    fun addToTrash(id: ConversationId, title: String)
}

class MemoryOrganizationPreview(override val editable: Boolean = false, blocked: List<BlockedPreview> = emptyList(), trash: List<TrashPreview> = emptyList()) : OrganizationPreview {
    override val blocked = MutableStateFlow(blocked)
    override val trash = MutableStateFlow(trash)
    override fun unblock(id: String) { if (editable) blocked.update { list -> list.filterNot { it.id == id } } }
    override fun restore(id: ConversationId) { if (editable) trash.update { list -> list.filterNot { it.id == id } } }
    override fun addToTrash(id: ConversationId, title: String) {
        if (editable) trash.update { list -> if (list.any { it.id == id }) list else list + TrashPreview(id, title) }
    }
}
