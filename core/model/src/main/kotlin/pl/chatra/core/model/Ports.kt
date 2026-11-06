package pl.chatra.core.model

import java.time.Instant

/** Stable seams for adapters. No Android APIs, Room entities, or UI dependency here. */
fun interface Clock { fun now(): Instant }

interface SubscriptionSource {
    suspend fun available(): List<SimSnapshot>
    suspend fun defaultSubscription(): SimSnapshot?
}

interface ConversationRepository {
    suspend fun get(id: ConversationId): Conversation?
    suspend fun settings(id: ConversationId): ConversationSettings?
    /** optimistic lock; never silently overwrite concurrent updates */
    suspend fun compareAndSetSettings(expectedVersion: Long?, next: ConversationSettings): Boolean
    /** clear local pin/mute/override/shortcuts on deletion; provider remains separate */
    suspend fun removeLocalMetadata(id: ConversationId)
}

/** Provider SMS/MMS is canonical; these functions are READ operations. */
interface ProviderHistory {
    suspend fun page(kind: ProviderKind, beforeId: Long?, limit: Int): List<MessageRef.Provider>
    suspend fun find(ref: MessageRef.Provider): MessageRef.Provider?
}

interface DraftRepository {
    suspend fun get(id: DraftId): Draft?
    suspend fun save(draft: Draft)
    suspend fun delete(id: DraftId)
}

interface SendOperationRepository {
    suspend fun get(id: OperationId): SendOperation?
    /** Atomic compare-and-set of version AND state in persistent storage. */
    suspend fun compareAndSet(expectedVersion: Long, expectedState: SendState, next: SendOperation): Boolean
    suspend fun insert(operation: SendOperation): Boolean
    suspend fun recordAttempt(attempt: SendAttempt)
}

interface ScheduleRepository {
    suspend fun upsert(schedule: ScheduledMessage, expectedVersion: Long?): Boolean
    suspend fun get(id: ScheduleId): ScheduledMessage?
    suspend fun cancel(id: ScheduleId, expectedVersion: Long): Boolean
}

/** Submitted means handed to the platform, not delivered. */
sealed interface TransportResult {
    data class Accepted(val receiptKey: String) : TransportResult
    data class DefinitelyRejected(val reason: DomainFailure) : TransportResult
    data object AcceptanceUnknown : TransportResult
}

data class DispatchRequest(
    val operationId: OperationId,
    val payloadSnapshotKey: String,
    val recipients: List<ParticipantKey>,
    val sim: SimSnapshot?
) { init { require(payloadSnapshotKey.isNotBlank() && recipients.isNotEmpty()) } }

interface SmsTransport {
    suspend fun submit(request: DispatchRequest): TransportResult
}

interface MmsTransport {
    suspend fun submit(request: DispatchRequest): TransportResult
}

/** HD activation and upload policy are separate from SMS/MMS SIM errors. */
interface HdTransport {
    suspend fun submit(request: DispatchRequest): TransportResult
}

/** UI knows stable IDs and public statuses, not provider URIs/telephone data in routes. */
sealed interface UiState {
    data object Loading : UiState
    data class Inbox(val conversationIds: List<ConversationId>, val unreadCount: Int) : UiState {
        init { require(unreadCount >= 0) }
    }
    data class ConversationView(val id: ConversationId, val highlighted: MessageRef?) : UiState
    data class Failure(val reason: DomainFailure) : UiState
}

sealed interface UiAction {
    data class OpenConversation(val id: ConversationId) : UiAction
    data class JumpToMessage(val id: ConversationId, val ref: MessageRef) : UiAction
    data class MarkUnread(val id: ConversationId, val unread: Boolean) : UiAction
    data class SendDraft(val id: DraftId) : UiAction
    data class CancelSend(val id: OperationId) : UiAction
}
