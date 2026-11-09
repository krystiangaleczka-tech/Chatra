package app.chatra.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Skróty S05 liczone względem bieżącej daty w strefie urządzenia (CH014): jutro 8:00,
 * najbliższa sobota 10:00, najbliższy poniedziałek 9:00. Daty z planszy to tylko fixture 29.10.2026.
 */
data class ScheduleShortcut(val kind: Kind, val local: LocalDateTime) {
    enum class Kind { Tomorrow, Saturday, Monday }
}

fun scheduleShortcuts(now: Instant, zone: ZoneId): List<ScheduleShortcut> {
    val today = now.atZone(zone).toLocalDate()
    return listOf(
        ScheduleShortcut(ScheduleShortcut.Kind.Tomorrow, today.plusDays(1).atTime(8, 0)),
        ScheduleShortcut(ScheduleShortcut.Kind.Saturday, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).atTime(10, 0)),
        ScheduleShortcut(ScheduleShortcut.Kind.Monday, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(9, 0)),
    )
}

/** Wynik walidacji terminu przed zapisem (specyfikacja S05: data przyszła, DST). */
sealed interface ScheduleValidation {
    /** Chwila wysłania, gdy da się ją zapisać. */
    val instant: Instant?

    data class Valid(override val instant: Instant) : ScheduleValidation

    /** Termin minął albo jest bliżej niż [MinLead]. */
    data object Past : ScheduleValidation {
        override val instant: Instant? = null
    }

    /** Godzina nie istnieje (przestawienie zegara do przodu): wysyłka w [suggestedLocal]. */
    data class Gap(override val instant: Instant, val suggestedLocal: LocalDateTime) : ScheduleValidation

    /** Godzina występuje dwa razy (cofnięcie zegara): wybrane pierwsze wystąpienie. */
    data class Overlap(override val instant: Instant, val later: Instant) : ScheduleValidation
}

/** Najmniejsze wyprzedzenie: krótszego terminu nie da się wiarygodnie zaplanować alarmem. */
val MinLead: Duration = Duration.ofMinutes(1)

fun validateSchedule(local: LocalDateTime, zone: ZoneId, now: Instant): ScheduleValidation {
    val result = when (val r = resolveLocal(local, zone)) {
        is LocalTimeResolution.Exact -> ScheduleValidation.Valid(r.instant)
        is LocalTimeResolution.Gap -> ScheduleValidation.Gap(r.suggested, r.suggested.atZone(zone).toLocalDateTime())
        is LocalTimeResolution.Overlap -> ScheduleValidation.Overlap(r.earlier, r.later)
    }
    val instant = result.instant ?: return ScheduleValidation.Past
    return if (instant.isBefore(now.plus(MinLead))) ScheduleValidation.Past else result
}
