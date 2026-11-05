package app.chatra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.chatra.core.model.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

val LocalClock = staticCompositionLocalOf<Clock> { error("Clock not provided") }

/** Polskie skróty z plansz; tabela zamiast danych lokalizacji urządzenia, by wynik był stały. */
object PolishDates {
    private val months = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")
    private val monthsFull = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    private val days = mapOf(
        DayOfWeek.MONDAY to "pon.", DayOfWeek.TUESDAY to "wt.", DayOfWeek.WEDNESDAY to "śr.", DayOfWeek.THURSDAY to "czw.",
        DayOfWeek.FRIDAY to "pt.", DayOfWeek.SATURDAY to "sob.", DayOfWeek.SUNDAY to "niedz.",
    )

    fun month(d: LocalDate) = months[d.monthValue - 1]
    fun monthFull(d: LocalDate) = monthsFull[d.monthValue - 1]
    fun weekday(d: LocalDate) = days.getValue(d.dayOfWeek)
    fun time(z: ZonedDateTime) = "%d:%02d".format(z.hour, z.minute)

    /** „31 paź”, z rokiem tylko gdy inny niż bieżący. */
    fun dayMonth(d: LocalDate, today: LocalDate) = "${d.dayOfMonth} ${month(d)}" + if (d.year != today.year) " ${d.year}" else ""

    /** „sob., 31 paź 2026” (S05). */
    fun weekdayDayMonthYear(d: LocalDate) = "${weekday(d)}, ${d.dayOfMonth} ${month(d)} ${d.year}"
}

fun Instant.local(zone: ZoneId): ZonedDateTime = atZone(zone)

/** Czas w wierszu skrzynki: dziś godzina, w tym tygodniu skrót dnia, wcześniej data. */
fun formatListTime(at: Instant, clock: Clock): String {
    val zone = clock.zone()
    val t = at.atZone(zone)
    val today = clock.now().atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(t.toLocalDate(), today)
    return when {
        days == 0L -> PolishDates.time(t)
        days in 1..6 -> PolishDates.weekday(t.toLocalDate())
        else -> PolishDates.dayMonth(t.toLocalDate(), today)
    }
}

@Composable
fun formatListTime(at: Instant): String = formatListTime(at, LocalClock.current)

/** „dziś”, „wczoraj”, albo „22 paź” (wyniki wyszukiwania S07). */
fun formatRelativeDay(at: Instant, clock: Clock): String {
    val zone = clock.zone()
    val d = at.atZone(zone).toLocalDate()
    val today = clock.now().atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(d, today)) {
        0L -> "dziś"
        1L -> "wczoraj"
        else -> PolishDates.dayMonth(d, today)
    }
}

/** Separator dnia w rozmowie: „Dziś”, „Wczoraj”, „wt., 27 paź”. */
fun formatDaySeparator(d: LocalDate, clock: Clock): String {
    val today = clock.now().atZone(clock.zone()).toLocalDate()
    return when (ChronoUnit.DAYS.between(d, today)) {
        0L -> "Dziś"
        1L -> "Wczoraj"
        else -> "${PolishDates.weekday(d)}, ${PolishDates.dayMonth(d, today)}"
    }
}
