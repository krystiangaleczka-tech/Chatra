package app.chatra.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleFormTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(warsaw).toInstant()

    @Test
    fun shortcutsFromFixtureThursday() {
        // Czwartek 29.10.2026 14:40 → jutro 30.10 8:00, sobota 31.10 10:00, poniedziałek 2.11 9:00 (S05).
        val s = scheduleShortcuts(at(2026, 10, 29, 14, 40), warsaw).map { it.local }
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 30, 8, 0), LocalDateTime.of(2026, 10, 31, 10, 0), LocalDateTime.of(2026, 11, 2, 9, 0)),
            s,
        )
    }

    @Test
    fun saturdayShortcutOnSaturdayIsNextWeek() {
        val s = scheduleShortcuts(at(2026, 10, 31, 9, 0), warsaw)
        assertEquals(LocalDateTime.of(2026, 11, 7, 10, 0), s[1].local)
        assertEquals(LocalDateTime.of(2026, 11, 1, 8, 0), s[0].local)
    }

    @Test
    fun shortcutsAfterMidnightUseNewDay() {
        // Tuż po północy „jutro” to kolejny dzień, nie ten, który właśnie się zaczął.
        val s = scheduleShortcuts(at(2026, 10, 30, 0, 5), warsaw)
        assertEquals(LocalDateTime.of(2026, 10, 31, 8, 0), s[0].local)
    }

    @Test
    fun pastAndTooCloseAreRejected() {
        val now = at(2026, 10, 29, 14, 40)
        assertEquals(ScheduleValidation.Past, validateSchedule(LocalDateTime.of(2026, 10, 29, 14, 0), warsaw, now))
        assertEquals(ScheduleValidation.Past, validateSchedule(LocalDateTime.of(2026, 10, 29, 14, 40), warsaw, now))
        assertEquals(ScheduleValidation.Valid(at(2026, 10, 31, 10, 0)), validateSchedule(LocalDateTime.of(2026, 10, 31, 10, 0), warsaw, now))
    }

    @Test
    fun springForwardGapMovesToExistingHour() {
        // 28.03.2027 02:00 → 03:00 w Warszawie: 02:30 nie istnieje, wysyłka o 03:30.
        val v = validateSchedule(LocalDateTime.of(2027, 3, 28, 2, 30), warsaw, at(2027, 3, 1, 12, 0))
        assertTrue(v is ScheduleValidation.Gap)
        assertEquals(LocalDateTime.of(2027, 3, 28, 3, 30), (v as ScheduleValidation.Gap).suggestedLocal)
    }

    @Test
    fun fallBackOverlapPicksFirstOccurrence() {
        // 25.10.2026 03:00 → 02:00: 02:30 występuje dwa razy, wybieramy wcześniejsze (czas letni, +02:00).
        val v = validateSchedule(LocalDateTime.of(2026, 10, 25, 2, 30), warsaw, at(2026, 10, 1, 12, 0))
        assertTrue(v is ScheduleValidation.Overlap)
        v as ScheduleValidation.Overlap
        assertEquals(java.time.Duration.ofHours(1), java.time.Duration.between(v.instant, v.later))
        assertEquals("2026-10-25T00:30:00Z", v.instant.toString())
    }
}
