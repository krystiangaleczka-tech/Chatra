package app.chatra.core.testing

import app.chatra.core.model.DeliveryStatus
import app.chatra.core.model.Draft
import app.chatra.core.model.DraftId
import app.chatra.core.model.RecipientMode
import app.chatra.core.model.SendState
import app.chatra.core.model.inboxOrder
import app.chatra.core.model.unreadConversationCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FixtureTest {
    @Test fun inboxMatchesS02OrderAndCounter() {
        val order = Fixture.inbox.inboxOrder().map { it.title }
        assertEquals(listOf("Mama", "Ola Nowak", "Paczka24", "Tomek Wiśniewski", "+48 601 234 567", "Kasia Zielińska", "Brygada rowerowa"), order)
        assertEquals(3, Fixture.inbox.unreadConversationCount())
        // Cztery nieprzeczytane wiadomości w trzech wątkach (S02, kontrakt licznika).
        assertEquals(4, Fixture.inbox.sumOf { it.unreadCount })
    }

    @Test fun scheduledHasOneDecisionAndFourWaiting() {
        assertEquals(1, Fixture.scheduled.count { it.operation.state == SendState.MISSED_NEEDS_DECISION })
        assertEquals(4, Fixture.scheduled.count { it.operation.state == SendState.SCHEDULED })
    }

    @Test fun demoRunsAreDeterministic() = runTest {
        val a = InMemoryChatra(FixedClock(), backgroundScope, frozen = true).inbox().first()
        val b = InMemoryChatra(FixedClock(), backgroundScope, frozen = true).inbox().first()
        assertEquals(a, b)
    }

    @Test fun sendGoesThroughUndoWindowWithoutTelephony() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope)
        val draft = Draft(DraftId("d"), Fixture.Ids.Mama, listOf(Fixture.mama.address), "Odbiorę cię.", RecipientMode.Single, emptyList(), Fixture.SIM1.id, FixedClock.FIXTURE_NOW)
        val id = repo.enqueue(draft, undoSeconds = 3)
        assertEquals(SendState.PENDING_UNDO, repo.operations().first().first { it.id == id }.state)
        advanceTimeBy(2_000)
        assertEquals(SendState.PENDING_UNDO, repo.operations().first().first { it.id == id }.state)
        advanceTimeBy(10_000) // repozytorium działa w backgroundScope, którego advanceUntilIdle nie czeka
        assertEquals(SendState.DELIVERED, repo.operations().first().first { it.id == id }.state)
        assertEquals(DeliveryStatus.Delivered, repo.messages(Fixture.Ids.Mama).first().last().status)
    }

    @Test fun cancelRestoresEditableDraft() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope)
        val draft = Draft(DraftId("d"), Fixture.Ids.Mama, listOf(Fixture.mama.address), "Odbiorę cię.", RecipientMode.Single, emptyList(), Fixture.SIM1.id, FixedClock.FIXTURE_NOW)
        val id = repo.enqueue(draft, undoSeconds = 3)
        assertTrue(repo.cancel(id))
        advanceUntilIdle()
        assertEquals(SendState.CANCELLED, repo.operations().first().first { it.id == id }.state)
        assertEquals("Odbiorę cię.", repo.draft(Fixture.Ids.Mama).first()?.text)
        assertTrue(repo.messages(Fixture.Ids.Mama).first().none { it.operationId == id })
    }
}
