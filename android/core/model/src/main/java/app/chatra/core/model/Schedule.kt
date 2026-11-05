package app.chatra.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Polityka po spóźnieniu (S05/S06): domyślnie pytanie; wariant „do 1 h” wysyła w oknie. */
enum class LatenessPolicy(val window: Duration) {
    Ask(Duration.ZERO),
    SendWithinHour(Duration.ofHours(1)),
}

/**
 * Harmonogram zapisuje Instant, pierwotny LocalDateTime i ZoneId: po zmianie strefy wysyłamy
 * w tej samej chwili i pokazujemy przeliczony czas (S05, strefa czasu).
 */
data class ScheduledMessage(
    val id: ScheduleId,
    val operationId: OperationId,
    val instant: Instant,
    val localDateTime: LocalDateTime,
    val zoneId: ZoneId,
    val latenessPolicy: LatenessPolicy,
    val version: Int,
    val nextAlarmToken: String? = null,
)

sealed interface DueDecision {
    data object Send : DueDecision
    data object NotYet : DueDecision
    data class NeedsDecision(val reason: String) : DueDecision
}

/**
 * Decyzja w chwili [now]. Wariant Ask nie wysyła zaległości automatycznie, także po restarcie.
 * Wariant SendWithinHour wysyła tylko, gdy faktyczny start mieści się w oknie.
 */
fun ScheduledMessage.decide(now: Instant): DueDecision {
    if (now.isBefore(instant)) return DueDecision.NotYet
    val late = Duration.between(instant, now)
    return when {
        late <= TOLERANCE -> DueDecision.Send
        latenessPolicy == LatenessPolicy.SendWithinHour && late <= latenessPolicy.window -> DueDecision.Send
        else -> DueDecision.NeedsDecision("late ${late.toMinutes()} min")
    }
}

/** Opóźnienie systemowe alarmu nie jest „spóźnieniem” wymagającym decyzji. */
private val TOLERANCE: Duration = Duration.ofMinutes(2)

/** Rozwiązanie lokalnego czasu użytkownika: nieistniejąca godzina przy zmianie DST wymaga korekty. */
sealed interface LocalTimeResolution {
    data class Exact(val instant: Instant) : LocalTimeResolution
    data class Gap(val suggested: Instant) : LocalTimeResolution
    data class Overlap(val earlier: Instant, val later: Instant) : LocalTimeResolution
}

fun resolveLocal(local: LocalDateTime, zone: ZoneId): LocalTimeResolution {
    val rules = zone.rules
    val offsets = rules.getValidOffsets(local)
    return when (offsets.size) {
        1 -> LocalTimeResolution.Exact(local.atOffset(offsets[0]).toInstant())
        0 -> LocalTimeResolution.Gap(local.atZone(zone).toInstant())
        else -> LocalTimeResolution.Overlap(local.atOffset(offsets[0]).toInstant(), local.atOffset(offsets[1]).toInstant())
    }
}
