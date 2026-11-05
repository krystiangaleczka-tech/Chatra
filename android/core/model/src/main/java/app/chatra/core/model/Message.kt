package app.chatra.core.model

import java.time.Instant

enum class Direction { Incoming, Outgoing }

/** Status pokazywany przy wiadomości wychodzącej; wyprowadzany z [SendState] operacji. */
enum class DeliveryStatus { None, PendingUndo, Scheduled, Sending, Sent, Delivered, Failed, PartiallySent, Uncertain }

data class Message(
    val ref: MessageRef,
    val conversationId: ConversationId,
    val direction: Direction,
    val sender: Address?,
    val body: String,
    val timestamp: Instant,
    val read: Boolean,
    val subscriptionId: SubscriptionId? = null,
    val status: DeliveryStatus = DeliveryStatus.None,
    val operationId: OperationId? = null,
    val attachments: List<AttachmentSummary> = emptyList(),
    /** Wiadomość HD doręczona przez Chatrę: pobranie nie jest odczytem SMS. */
    val hd: HdInfo? = null,
)

/** Wiadomość jest z providera (SMS/MMS), z HD Chatry albo jeszcze lokalna (operacja w toku). */
sealed interface MessageRef {
    data class Provider(val ref: ProviderRef) : MessageRef
    data class Hd(val id: String) : MessageRef
    data class Local(val operationId: OperationId) : MessageRef
}

data class AttachmentSummary(val id: AttachmentId, val mimeType: String, val label: String)

data class HdInfo(val itemCount: Int, val downloadedBy: String? = null)
