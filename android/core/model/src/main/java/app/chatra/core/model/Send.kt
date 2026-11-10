package app.chatra.core.model

import java.time.Instant

/** Stany trwałej wysyłki (specyfikacja 9). */
enum class SendState {
    DRAFT, PENDING_UNDO, SCHEDULED, DUE, DISPATCHING, SENT, DELIVERED, FAILED, PARTIALLY_SENT,
    MISSED_NEEDS_DECISION, DISPATCH_UNCERTAIN, CANCELLED;

    val isTerminal: Boolean get() = this in setOf(DELIVERED, CANCELLED)

    /** Po przekazaniu transportowi nie obiecujemy cofnięcia (granica anulowania). */
    val isCancellable: Boolean get() = this in setOf(DRAFT, PENDING_UNDO, SCHEDULED, DUE, MISSED_NEEDS_DECISION)

    fun canMoveTo(next: SendState): Boolean = next in allowed.getValue(this)

    companion object {
        private val allowed: Map<SendState, Set<SendState>> = mapOf(
            DRAFT to setOf(PENDING_UNDO, SCHEDULED, CANCELLED),
            PENDING_UNDO to setOf(DISPATCHING, CANCELLED, MISSED_NEEDS_DECISION),
            SCHEDULED to setOf(SCHEDULED, DUE, CANCELLED, MISSED_NEEDS_DECISION),
            DUE to setOf(DISPATCHING, MISSED_NEEDS_DECISION, CANCELLED, SCHEDULED),
            DISPATCHING to setOf(SENT, FAILED, PARTIALLY_SENT, DISPATCH_UNCERTAIN, DELIVERED),
            SENT to setOf(DELIVERED),
            DELIVERED to emptySet(), // callbacki nie cofają DELIVERED do SENT
            FAILED to setOf(PENDING_UNDO, DISPATCHING, CANCELLED),
            PARTIALLY_SENT to setOf(DISPATCHING, DELIVERED, CANCELLED),
            MISSED_NEEDS_DECISION to setOf(DISPATCHING, SCHEDULED, CANCELLED),
            DISPATCH_UNCERTAIN to setOf(SENT, DELIVERED, FAILED, DISPATCHING, CANCELLED),
            CANCELLED to emptySet(),
        )
    }
}

enum class Transport { SMS, MMS, HD }

/** Osobne SMS do wielu osób są domyślne; grupowy MMS tylko po świadomym wyborze (S08). */
enum class RecipientMode { Single, SeparateSms, GroupMms }

data class SendOperation(
    val id: OperationId,
    val conversationId: ConversationId?,
    val transport: Transport,
    val recipientMode: RecipientMode,
    val recipients: List<Address>,
    val body: String,
    val attachmentIds: List<AttachmentId>,
    val subscriptionId: SubscriptionId?,
    val state: SendState,
    val version: Int,
    val createdAt: Instant,
    /** Termin końca okna anulowania (PENDING_UNDO); zapisany trwale, UI odlicza do niego. */
    val undoDeadline: Instant? = null,
    /** Identyfikator uruchomienia urządzenia z chwili zapisu terminu (granica po restarcie). */
    val bootId: String? = null,
    val failureCode: Int? = null,
    val canRetry: Boolean = false,
    val deliveryFailed: Boolean = false,
    val undoElapsedDeadline: Long? = null,
    val retryOf: OperationId? = null,
)

/** Wynik przejścia stanu: odrzucony, gdy stan lub wersja nie zgadzają się z oczekiwanymi. */
sealed interface Transition {
    data class Applied(val operation: SendOperation) : Transition
    data class Rejected(val reason: String) : Transition
}

/** Optymistyczne przejście: porównanie oczekiwanego stanu i wersji, potem nowa wersja. */
fun SendOperation.transition(expected: SendState, expectedVersion: Int, next: SendState, update: (SendOperation) -> SendOperation = { it }): Transition =
    when {
        state != expected -> Transition.Rejected("state $state != $expected")
        version != expectedVersion -> Transition.Rejected("version $version != $expectedVersion")
        !state.canMoveTo(next) -> Transition.Rejected("$state -> $next not allowed")
        else -> Transition.Applied(update(copy(state = next, version = version + 1)))
    }

data class SendRecipient(val operationId: OperationId, val address: Address, val state: SendState, val providerRef: ProviderRef? = null)

data class SendAttempt(
    val id: AttemptId,
    val operationId: OperationId,
    val recipient: Address,
    val subscriptionId: SubscriptionId?,
    val subscriptionLabel: String?,
    val startedAt: Instant,
    val resultCode: Int? = null,
    val uncertaintyReason: String? = null,
)

enum class PartStatus { Pending, Sent, Failed, Unknown }
enum class PartDelivery { Unknown, Delivered, Failed }

data class SendPart(
    val attemptId: AttemptId,
    val index: Int,
    val total: Int,
    val sent: PartStatus = PartStatus.Pending,
    val delivery: PartDelivery = PartDelivery.Unknown,
)

/** Zagregowany wynik części: wszystkie wysłane → SENT, wszystkie doręczone → DELIVERED. */
fun aggregate(parts: List<SendPart>): SendState? = when {
    parts.isEmpty() -> null
    parts.all { it.delivery == PartDelivery.Delivered } -> SendState.DELIVERED
    parts.all { it.sent == PartStatus.Sent || it.delivery == PartDelivery.Delivered } -> SendState.SENT
    parts.any { it.sent == PartStatus.Pending && it.delivery != PartDelivery.Delivered } -> null
    parts.any { it.sent == PartStatus.Unknown && it.delivery != PartDelivery.Delivered } -> SendState.DISPATCH_UNCERTAIN
    parts.any { it.sent == PartStatus.Sent || it.delivery == PartDelivery.Delivered } -> SendState.PARTIALLY_SENT
    parts.all { it.sent == PartStatus.Failed } -> SendState.FAILED
    else -> null
}

/** Wall clock is for display only. Reboot or legacy rows need an explicit decision. */
fun undoRemaining(deadline: Long?, savedBoot: Int, boot: Int, elapsed: Long): Long? =
    if (deadline == null || boot < 0 || savedBoot != boot) null else (deadline - elapsed).coerceAtLeast(0)
