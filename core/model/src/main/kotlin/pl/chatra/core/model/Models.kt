package pl.chatra.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Opaque, local IDs. Never put phone numbers or message bodies in navigation paths. */
@JvmInline value class ConversationId(val value: UUID)
@JvmInline value class DraftId(val value: UUID)
@JvmInline value class OperationId(val value: UUID)
@JvmInline value class AttemptId(val value: UUID)
@JvmInline value class ScheduleId(val value: UUID)
@JvmInline value class AttachmentId(val value: UUID)
@JvmInline value class HdMessageId(val value: UUID)
@JvmInline value class ShortcutId(val value: UUID)

/** Stable, app-local participant key; not necessarily an E.164 number (short codes exist). */
@JvmInline value class ParticipantKey(val value: String) {
    init { require(value.isNotBlank()) { "Participant key must not be blank" } }
}

enum class ProviderKind { SMS, MMS }

/** Provider IDs are only unique together with kind, and only within this installation. */
sealed interface MessageRef {
    data class Provider(val kind: ProviderKind, val id: Long, val threadId: Long?) : MessageRef {
        init { require(id > 0); require(threadId == null || threadId > 0) }
    }
    data class Hd(val id: HdMessageId) : MessageRef
}

data class Conversation(
    val id: ConversationId,
    val participants: Set<ParticipantKey>,
    val providerThreadId: Long? = null
) {
    init { require(participants.isNotEmpty()); require(providerThreadId == null || providerThreadId > 0) }
}

/** Local metadata only; no copy of canonical SMS/MMS message content. */
data class ConversationSettings(
    val conversationId: ConversationId,
    val themeOverride: String? = null, // null means inherit global theme
    val accentOverrideArgb: Long? = null,
    val preferredSubscriptionId: Int? = null,
    val pinnedAt: Instant? = null,
    val mutedUntil: Instant? = null,
    val unreadOverride: Boolean? = null, // null means follow provider / read projection
    val shortcutId: ShortcutId? = null,
    val version: Long = 0
) {
    init { require(version >= 0); require(preferredSubscriptionId == null || preferredSubscriptionId >= 0) }
}

enum class RecipientMode { SINGLE, SEPARATE_SMS, GROUP_MMS }
enum class TransportKind { SMS, MMS, HD }

data class Draft(
    val id: DraftId,
    val conversationId: ConversationId?,
    val recipients: List<ParticipantKey>,
    val text: String,
    val recipientMode: RecipientMode,
    val attachmentIds: List<AttachmentId>,
    val subscriptionId: Int?,
    val updatedAt: Instant
) {
    init { require(subscriptionId == null || subscriptionId >= 0) }
}

enum class AttachmentVariant { ORIGINAL, LOCATION_REMOVED_COPY, MMS_COPY, THUMBNAIL }
enum class Sanitization { NOT_REQUESTED, PENDING, COMPLETED, FAILED }
enum class LocalAccess { AVAILABLE, MISSING, PERMISSION_LOST }

data class Attachment(
    val id: AttachmentId,
    val privateUri: String,
    val mimeType: String,
    val bytes: Long,
    val sha256: String?,
    val variant: AttachmentVariant,
    val sanitization: Sanitization,
    val access: LocalAccess,
    val sourceMessage: MessageRef? = null
) {
    init {
        require(privateUri.isNotBlank() && mimeType.isNotBlank())
        require(bytes >= 0)
        require(sha256 == null || sha256.matches(Regex("[a-fA-F0-9]{64}")))
    }
}

data class SimSnapshot(
    val subscriptionId: Int,
    val displayName: String,
    val capturedAt: Instant
) { init { require(subscriptionId >= 0) } }

enum class SendState {
    DRAFT, PENDING_UNDO, SCHEDULED, DUE, DISPATCHING,
    SENT, DELIVERED, FAILED, PARTIALLY_SENT, MISSED_NEEDS_DECISION,
    DISPATCH_UNCERTAIN, CANCELLED
}

/** An immutable snapshot is referenced here, never a mutable editor state. */
data class SendOperation(
    val id: OperationId,
    val payloadSnapshotKey: String,
    val transport: TransportKind,
    val recipientMode: RecipientMode,
    val state: SendState,
    val version: Long,
    val createdAt: Instant,
    val undoDeadline: Instant? = null
) {
    init {
        require(payloadSnapshotKey.isNotBlank() && version >= 0)
        require(state != SendState.PENDING_UNDO || undoDeadline != null)
        require(undoDeadline == null || !undoDeadline.isBefore(createdAt))
    }
}

enum class PartStatus { PENDING, SENT, DELIVERED, FAILED, UNCERTAIN }

data class SendPart(
    val index: Int,
    val total: Int,
    val sentStatus: PartStatus,
    val deliveryStatus: PartStatus,
    val callbackToken: String
) {
    init { require(index >= 0 && total > 0 && index < total && callbackToken.isNotBlank()) }
}

data class SendAttempt(
    val id: AttemptId,
    val operationId: OperationId,
    val recipientKey: ParticipantKey,
    val subscription: SimSnapshot?,
    val startedAt: Instant,
    val resultCode: String?,
    val uncertaintyReason: String?,
    val parts: List<SendPart>
)

enum class LatenessPolicy { ASK, SEND_WITHIN_ONE_HOUR_IF_CONFIRMED }

data class ScheduledMessage(
    val id: ScheduleId,
    val operationId: OperationId,
    val sendAt: Instant,
    val wallTime: LocalDateTime,
    val zone: ZoneId,
    val latenessPolicy: LatenessPolicy,
    val version: Long,
    val nextAlarmToken: String?
) { init { require(version >= 0) } }

sealed interface DomainFailure {
    data object SmsRoleRequired : DomainFailure
    data object SimUnavailable : DomainFailure
    data object PermissionDenied : DomainFailure
    data object ProviderUnavailable : DomainFailure
    data object ScheduleNeedsDecision : DomainFailure
    data object HdNotActivated : DomainFailure
    data object PayloadTooLarge : DomainFailure
    data class TransportRejected(val technicalCode: String) : DomainFailure
    data object DispatchAcceptanceUnknown : DomainFailure
}
