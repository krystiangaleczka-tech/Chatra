package app.chatra.core.model

import java.time.Instant

/** Szkic: tekst, odbiorcy, załączniki i wybrana SIM wracają po ponownym wejściu (specyfikacja 5). */
data class Draft(
    val id: DraftId,
    val conversationId: ConversationId?,
    val recipients: List<Address>,
    val text: String,
    val recipientMode: RecipientMode,
    val attachmentIds: List<AttachmentId>,
    val subscriptionId: SubscriptionId?,
    val updatedAt: Instant,
    /** Wariant zdjęć z S04; null dla samego tekstu. */
    val mediaVariant: MediaVariant? = null,
    /** Łączny rozmiar załączników (opis i limit transportu). */
    val attachmentBytes: Long = 0,
) {
    /** Pusty tekst bez załączników nie wysyła. */
    val isSendable: Boolean get() = (text.isNotBlank() || attachmentIds.isNotEmpty()) && recipients.isNotEmpty()
}
