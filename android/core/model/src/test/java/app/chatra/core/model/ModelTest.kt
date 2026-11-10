package app.chatra.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SendStateTest {
    private fun op(state: SendState, version: Int = 1) = SendOperation(
        OperationId("op"), null, Transport.SMS, RecipientMode.Single, listOf(Address("+48601234567")),
        "x", emptyList(), null, state, version, Instant.EPOCH,
    )

    @Test fun cancelOnlyBeforeDispatch() {
        assertTrue(SendState.PENDING_UNDO.canMoveTo(SendState.CANCELLED))
        assertFalse(SendState.DISPATCHING.canMoveTo(SendState.CANCELLED))
        assertFalse(SendState.SENT.isCancellable)
    }

    @Test fun deliveredIsNeverDowngraded() {
        assertFalse(SendState.DELIVERED.canMoveTo(SendState.SENT))
        assertTrue(SendState.DELIVERED.isTerminal)
    }

    @Test fun deliveryProofWinsOverConflictingSentFailure() {
        assertEquals(SendState.DELIVERED, aggregate(listOf(SendPart(AttemptId("a"), 0, 1, PartStatus.Failed, PartDelivery.Delivered))))
    }

    @Test fun uncertainNeverRetriesAutomaticallyButAllowsDecision() {
        assertTrue(SendState.DISPATCH_UNCERTAIN.canMoveTo(SendState.DISPATCHING))
        assertTrue(SendState.DISPATCH_UNCERTAIN.canMoveTo(SendState.CANCELLED))
    }

    @Test fun transitionChecksStateAndVersion() {
        val o = op(SendState.PENDING_UNDO, 3)
        assertTrue(o.transition(SendState.PENDING_UNDO, 3, SendState.CANCELLED) is Transition.Applied)
        assertTrue(o.transition(SendState.PENDING_UNDO, 2, SendState.CANCELLED) is Transition.Rejected)
        assertTrue(o.transition(SendState.SCHEDULED, 3, SendState.CANCELLED) is Transition.Rejected)
        val applied = o.transition(SendState.PENDING_UNDO, 3, SendState.DISPATCHING) as Transition.Applied
        assertEquals(4, applied.operation.version)
    }

    @Test fun partsAggregate() {
        val a = AttemptId("a")
        assertEquals(SendState.SENT, aggregate(listOf(SendPart(a, 0, 2, PartStatus.Sent), SendPart(a, 1, 2, PartStatus.Sent))))
        assertEquals(SendState.PARTIALLY_SENT, aggregate(listOf(SendPart(a, 0, 2, PartStatus.Sent), SendPart(a, 1, 2, PartStatus.Failed))))
        assertEquals(SendState.DELIVERED, aggregate(listOf(SendPart(a, 0, 1, PartStatus.Sent, PartDelivery.Delivered))))
    }
}

class InboxTest {
    private fun c(id: String, pinned: Boolean, minutes: Long, unread: Int = 0, trashed: Boolean = false) = ConversationSummary(
        ConversationId(id), emptyList(), id, Instant.EPOCH.plusSeconds(minutes * 60), ConversationSummary.Preview.Text(""),
        unread, pinned, trashed = trashed,
    )

    @Test fun pinnedFirstThenRecent() {
        val list = listOf(c("a", false, 5), c("b", true, 1), c("c", false, 9), c("d", true, 3)).inboxOrder()
        assertEquals(listOf("d", "b", "c", "a"), list.map { it.id.value })
    }

    @Test fun unreadCountsConversationsNotMessages() {
        // Fixture S02: cztery nieprzeczytane wiadomości w trzech wątkach → „Nieprzeczytane · 3”.
        val list = listOf(c("mama", true, 1, unread = 2), c("paczka", false, 2, unread = 1), c("kurier", false, 3, unread = 1), c("x", false, 4))
        assertEquals(3, list.unreadConversationCount())
    }

    @Test fun trashedHidden() {
        assertEquals(1, listOf(c("a", false, 1), c("b", false, 2, trashed = true)).inboxOrder().size)
    }
}

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val at = Instant.parse("2026-10-31T09:00:00Z")
    private fun s(policy: LatenessPolicy) = ScheduledMessage(ScheduleId("s"), OperationId("o"), at, LocalDateTime.of(2026, 10, 31, 10, 0), zone, policy, 1)

    @Test fun askNeverSendsLate() {
        assertEquals(DueDecision.NotYet, s(LatenessPolicy.Ask).decide(at.minusSeconds(1)))
        assertEquals(DueDecision.Send, s(LatenessPolicy.Ask).decide(at.plusSeconds(30)))
        assertTrue(s(LatenessPolicy.Ask).decide(at.plus(Duration.ofMinutes(20))) is DueDecision.NeedsDecision)
    }

    @Test fun withinHourWindow() {
        assertEquals(DueDecision.Send, s(LatenessPolicy.SendWithinHour).decide(at.plus(Duration.ofMinutes(59))))
        assertTrue(s(LatenessPolicy.SendWithinHour).decide(at.plus(Duration.ofMinutes(61))) is DueDecision.NeedsDecision)
    }

    @Test fun dstGapAndOverlap() {
        assertTrue(resolveLocal(LocalDateTime.of(2026, 3, 29, 2, 30), zone) is LocalTimeResolution.Gap)
        assertTrue(resolveLocal(LocalDateTime.of(2026, 10, 25, 2, 30), zone) is LocalTimeResolution.Overlap)
        assertTrue(resolveLocal(LocalDateTime.of(2026, 10, 31, 10, 0), zone) is LocalTimeResolution.Exact)
    }
}

class AddressAndSimTest {
    @Test fun normalizesPhones() {
        assertEquals("+48601234567", Address("+48 601 234 567").normalized)
        assertEquals("+48601234567", Address("0048 601-234-567").normalized)
        assertEquals(Address.Kind.Alphanumeric, Address("Paczka24").kind)
        assertFalse(Address("Paczka24").canReply)
        assertEquals(participantsKey(listOf(Address("+48 1"), Address("+48 2"))), participantsKey(listOf(Address("+482"), Address("+481"))))
    }

    private val sim1 = SimCard(SubscriptionId(1), 0, "Prywatna")
    private val sim2 = SimCard(SubscriptionId(2), 1, "Praca")

    @Test fun simChoicePrefersDraftThenConversation() {
        assertEquals(SimChoice.Ready(sim2), chooseSim(listOf(sim1, sim2), SubscriptionId(2), SubscriptionId(1), null))
        assertEquals(SimChoice.Ready(sim1), chooseSim(listOf(sim1, sim2), null, SubscriptionId(1), SubscriptionId(2)))
        assertEquals(SimChoice.NoSim, chooseSim(emptyList(), null, null, null))
    }

    @Test fun missingSimNeedsConfirmation() {
        assertTrue(chooseSim(listOf(sim1), SubscriptionId(2), null, null) is SimChoice.NeedsConfirmation)
        assertTrue(chooseSim(listOf(sim1), null, null, SubscriptionId(2)) is SimChoice.NeedsConfirmation)
        assertTrue(chooseSim(listOf(sim1, sim2), null, null, null) is SimChoice.NeedsConfirmation)
        assertEquals(SimChoice.Ready(sim1), chooseSim(listOf(sim1), null, null, null))
    }
}
