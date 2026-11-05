package app.chatra.core.testing

import app.chatra.core.model.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Kontrolowany zegar fixture: czwartek 29 października 2026, 14:40 w Warszawie (CH006). */
class FixedClock(
    private var instant: Instant = FIXTURE_NOW,
    private val zone: ZoneId = WARSAW,
) : Clock {
    private var elapsed = 1_000_000L

    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone
    override fun elapsedRealtimeMillis(): Long = elapsed

    fun advanceMillis(ms: Long) {
        instant = instant.plusMillis(ms)
        elapsed += ms
    }

    companion object {
        val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
        val FIXTURE_NOW: Instant = LocalDateTime.of(2026, 10, 29, 14, 40).atZone(WARSAW).toInstant()
        fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant = LocalDateTime.of(y, mo, d, h, mi).atZone(WARSAW).toInstant()
    }
}
