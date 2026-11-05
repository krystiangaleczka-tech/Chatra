package app.chatra.core.model

/** Warianty wysyłki zdjęć (S04): wykluczające się, zmiana natychmiast aktualizuje opis i CTA. */
enum class MediaVariant { OriginalHd, NoLocationCopy, Mms }

enum class AccessState { Available, Lost, Unsupported, TooLarge, ProcessingFailed }

data class Attachment(
    val id: AttachmentId,
    val privateUri: String?,
    val sourceUri: String?,
    val mimeType: String,
    val bytes: Long,
    val sha256: String?,
    val variant: MediaVariant,
    val locationSanitized: Boolean,
    val accessState: AccessState,
    val order: Int,
)
