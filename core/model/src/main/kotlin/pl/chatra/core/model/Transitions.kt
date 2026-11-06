package pl.chatra.core.model

/** Contract-level guard. Storage must enforce compare-and-set transactionally. */
object SendTransitions {
    private val allowed = mapOf(
        SendState.DRAFT to setOf(SendState.PENDING_UNDO, SendState.SCHEDULED),
        SendState.PENDING_UNDO to setOf(SendState.CANCELLED, SendState.DISPATCHING, SendState.DISPATCH_UNCERTAIN),
        SendState.SCHEDULED to setOf(SendState.DUE, SendState.CANCELLED),
        SendState.DUE to setOf(SendState.DISPATCHING, SendState.MISSED_NEEDS_DECISION, SendState.CANCELLED),
        SendState.MISSED_NEEDS_DECISION to setOf(SendState.DUE, SendState.CANCELLED),
        SendState.DISPATCHING to setOf(
            SendState.SENT, SendState.DELIVERED, SendState.FAILED,
            SendState.PARTIALLY_SENT, SendState.DISPATCH_UNCERTAIN
        ),
        SendState.SENT to setOf(SendState.DELIVERED, SendState.PARTIALLY_SENT),
        SendState.PARTIALLY_SENT to setOf(SendState.DELIVERED),
        SendState.FAILED to emptySet(), // retry creates a NEW operation/attempt only after user intent
        SendState.DISPATCH_UNCERTAIN to emptySet(), // never automatically retry
        SendState.DELIVERED to emptySet(),
        SendState.CANCELLED to emptySet()
    )

    /** Returns a new value; caller must persist with expected version/state. */
    fun advance(current: SendOperation, target: SendState): SendOperation {
        require(target in allowed.getValue(current.state)) {
            "Illegal send transition: ${current.state} -> $target"
        }
        return current.copy(state = target, version = current.version + 1)
    }

    /** Only the durable store, not a UI timer, may claim the transition. */
    fun canCancel(current: SendOperation): Boolean =
        SendState.CANCELLED in allowed.getValue(current.state)
}

/** Non-sensitive projection for logging; never log payloadSnapshotKey or participants. */
fun SendOperation.technicalSummary(): String =
    "operationState=$state version=$version transport=$transport"
