package app.chatra.core.testing

import app.chatra.core.model.DecisionReason
import app.chatra.core.model.ScheduleId
import app.chatra.core.model.SendState
import app.chatra.core.model.decisionReason
import app.chatra.core.model.scheduledOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** S06 (CH015): lista, Wyślij teraz, Zmień termin, Usuń na repozytorium demonstracyjnym. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledActionsTest {
    private val tomek = ScheduleId("s-tomek")
    private val mama = ScheduleId("s-mama")

    @Test fun fixtureOverviewMatchesS06() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = true)
        val o = scheduledOverview(repo.scheduled().first())
        assertEquals(listOf("Tomek Wiśniewski"), o.decisions.map { it.title })
        assertEquals(DecisionReason.Missed, o.decisions.single().decisionReason())
        assertEquals(listOf("Mama", "Brygada rowerowa", "Ola Nowak", "Marek (praca)"), o.pending.map { it.title })
        assertEquals("Ola Nowak", repo.lastSent().first()?.title)
    }

    @Test fun sendNowStartsExactlyOnce() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = true)
        assertTrue(repo.sendNow(tomek))
        assertFalse(repo.sendNow(tomek))
        val op = repo.scheduled().first().single { it.schedule.id == tomek }.operation
        assertEquals(SendState.DISPATCHING, op.state)
        // Jedna wiadomość w rozmowie, mimo dwóch stuknięć.
        assertEquals(1, repo.messages(Fixture.Ids.Tomek).first().count { it.operationId == op.id })
        // Znika z decyzji.
        assertTrue(scheduledOverview(repo.scheduled().first()).decisions.isEmpty())
    }

    @Test fun sendNowCompletesAndUpdatesFooter() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = false)
        assertTrue(repo.sendNow(tomek))
        advanceTimeBy(10_000) // repozytorium działa w backgroundScope
        assertEquals(SendState.DELIVERED, repo.scheduled().first().single { it.schedule.id == tomek }.operation.state)
        assertEquals("Tomek Wiśniewski", repo.lastSent().first()?.title)
    }

    @Test fun deleteKeepsConversationAndHistory() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = true)
        val history = repo.messages(Fixture.Ids.Mama).first()
        assertTrue(repo.delete(mama))
        assertNull(repo.scheduled().first().firstOrNull { it.schedule.id == mama })
        assertNotNull(repo.conversation(Fixture.Ids.Mama).first())
        assertTrue(repo.inbox().first().any { it.id == Fixture.Ids.Mama })
        assertEquals(history, repo.messages(Fixture.Ids.Mama).first())
        assertFalse(repo.delete(mama))
    }

    @Test fun rescheduleBumpsVersionAndClearsAlarm() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = true)
        val before = repo.scheduled().first().single { it.schedule.id == tomek }
        val local = LocalDateTime.of(2026, 10, 30, 18, 0)
        assertTrue(repo.reschedule(tomek, local.atZone(FixedClock.WARSAW).toInstant(), local, FixedClock.WARSAW))
        val after = repo.scheduled().first().single { it.schedule.id == tomek }
        assertEquals(SendState.SCHEDULED, after.operation.state)
        assertEquals(before.schedule.version + 1, after.schedule.version)
        assertNull(after.schedule.nextAlarmToken)
        // Przeniesiona do oczekujących, posortowana po terminie (pt. 18:00 między Mamą a Brygadą).
        val o = scheduledOverview(repo.scheduled().first())
        assertTrue(o.decisions.isEmpty())
        assertEquals(listOf("Mama", "Tomek Wiśniewski", "Brygada rowerowa", "Ola Nowak", "Marek (praca)"), o.pending.map { it.title })
    }
}
