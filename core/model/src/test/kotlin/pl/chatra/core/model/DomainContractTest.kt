package pl.chatra.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private fun id() = UUID.randomUUID()
private fun checkThrows(block: () -> Unit) {
    try { block() } catch (_: IllegalArgumentException) { return }
    error("Expected IllegalArgumentException")
}

private fun newOperation(state: SendState) = SendOperation(
    OperationId(id()), "snapshot-key", TransportKind.SMS, RecipientMode.SINGLE,
    state, 0, Instant.parse("2026-10-29T10:00:00Z"),
    if (state == SendState.PENDING_UNDO) Instant.parse("2026-10-29T10:00:03Z") else null
)

fun main() {
    val sms = MessageRef.Provider(ProviderKind.SMS, 42, 7)
    val mms = MessageRef.Provider(ProviderKind.MMS, 42, 7)
    check(sms != mms) { "SMS and MMS ID collisions must be impossible" }
    checkThrows { MessageRef.Provider(ProviderKind.SMS, 0, null) }
    checkThrows { ParticipantKey(" ") }
    checkThrows { Conversation(ConversationId(id()), emptySet()) }

    val conversationId = ConversationId(id())
    val inherited = ConversationSettings(conversationId)
    check(inherited.themeOverride == null && inherited.unreadOverride == null)
    val manuallyUnread = inherited.copy(unreadOverride = true, pinnedAt = Instant.EPOCH)
    check(manuallyUnread.unreadOverride == true && manuallyUnread.pinnedAt != null)
    check(manuallyUnread.themeOverride == null)
    checkThrows { ConversationSettings(conversationId, preferredSubscriptionId = -1) }

    val draft = Draft(DraftId(id()), null, emptyList(), "", RecipientMode.SINGLE,
        emptyList(), null, Instant.EPOCH)
    check(draft.recipients.isEmpty()) { "Empty new-message draft must be supported" }
    checkThrows { Attachment(AttachmentId(id()), "content://local/1", "image/jpeg", 1,
        "not-a-sha256", AttachmentVariant.ORIGINAL, Sanitization.NOT_REQUESTED, LocalAccess.AVAILABLE) }
    val attachment = Attachment(AttachmentId(id()), "content://local/1", "image/jpeg", 3,
        null, AttachmentVariant.ORIGINAL, Sanitization.NOT_REQUESTED, LocalAccess.AVAILABLE, sms)
    check(attachment.sourceMessage == sms)

    val pending = newOperation(SendState.PENDING_UNDO)
    val cancelled = SendTransitions.advance(pending, SendState.CANCELLED)
    check(cancelled.version == 1L && !SendTransitions.canCancel(cancelled))
    checkThrows { SendTransitions.advance(cancelled, SendState.DISPATCHING) }
    val dispatching = SendTransitions.advance(pending, SendState.DISPATCHING)
    check(!SendTransitions.canCancel(dispatching))
    checkThrows { SendTransitions.advance(dispatching, SendState.CANCELLED) }
    val delivered = SendTransitions.advance(dispatching, SendState.DELIVERED)
    checkThrows { SendTransitions.advance(delivered, SendState.SENT) }
    val uncertain = SendTransitions.advance(dispatching, SendState.DISPATCH_UNCERTAIN)
    checkThrows { SendTransitions.advance(uncertain, SendState.DISPATCHING) }
    val failed = SendTransitions.advance(dispatching, SendState.FAILED)
    checkThrows { SendTransitions.advance(failed, SendState.DISPATCHING) }
    check(!pending.technicalSummary().contains("snapshot-key"))

    val scheduled = newOperation(SendState.SCHEDULED)
    val due = SendTransitions.advance(scheduled, SendState.DUE)
    check(SendTransitions.advance(due, SendState.MISSED_NEEDS_DECISION).version == 2L)
    val zone = ZoneId.of("Europe/Warsaw")
    val schedule = ScheduledMessage(ScheduleId(id()), scheduled.id,
        Instant.parse("2026-10-31T09:00:00Z"), LocalDateTime.of(2026, 10, 31, 10, 0),
        zone, LatenessPolicy.ASK, 0, null)
    check(schedule.zone == zone && schedule.wallTime.hour == 10)
    check(DispatchRequest(OperationId(id()), "snap", listOf(ParticipantKey("local-1")), null).sim == null)
    checkThrows { DispatchRequest(OperationId(id()), "snap", emptyList(), null) }

    println("CH005 domain contract: 25 assertions PASS")
}
