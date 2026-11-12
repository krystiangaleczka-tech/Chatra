package app.chatra.core.model

import kotlinx.coroutines.flow.StateFlow

interface TemplateRepository {
    val items: StateFlow<List<Template>>
    suspend fun save(template: Template)
    suspend fun delete(id: TemplateId)
}

data class StoredMedia(val id: AttachmentId, val uri: String, val mime: String, val bytes: Long, val sha256: String, val createdAt: Long, val originalId: AttachmentId? = null, val sanitized: Boolean = false)

interface AttachmentStore {
    suspend fun importUri(uri: String): StoredMedia
    suspend fun get(id: AttachmentId): StoredMedia?
    suspend fun derive(id: AttachmentId, variant: MediaVariant, maxBytes: Long? = null): StoredMedia
    suspend fun collectUnused(protectedIds: Set<AttachmentId>, olderThan: Long)
    fun cameraUri(): String
}

data class NotificationPrefs(val previews: Boolean = false, val mutedUntil: Map<String, Long> = emptyMap()) {
    fun muted(id: String, now: Long): Boolean = (mutedUntil[id] ?: 0L) > now
}

fun appendTemplate(text: String, template: String): String = if (text.isBlank()) template else "$text $template"
