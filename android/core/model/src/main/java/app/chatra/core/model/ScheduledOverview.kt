package app.chatra.core.model

import java.time.Instant

/** Stopka S06: ostatnio wysłana zaplanowana wiadomość. */
data class LastScheduledSent(val title: String, val at: Instant, val delivered: Boolean)

/** Dlaczego zaplanowana wysyłka czeka na użytkownika (S06: oddzielne komunikaty). */
enum class DecisionReason {
    /** Termin minął, gdy telefon był wyłączony lub nie mógł wysłać; nic nie wyszło. */
    Missed,

    /** Próba wysyłki zakończyła się błędem. */
    Failed,

    /** Nie wiadomo, czy wiadomość wyszła; ponowienie może zdublować SMS. */
    Uncertain,
}

fun ScheduledItem.decisionReason(): DecisionReason? = when (operation.state) {
    SendState.MISSED_NEEDS_DECISION -> DecisionReason.Missed
    SendState.FAILED, SendState.PARTIALLY_SENT -> DecisionReason.Failed
    SendState.DISPATCH_UNCERTAIN -> DecisionReason.Uncertain
    else -> null
}

/**
 * Lista S06: wymagające decyzji nad oczekującymi, obie rosnąco po terminie. Wysyłane, wysłane
 * i anulowane znikają z listy (wysłane trafiają do stopki).
 */
data class ScheduledOverview(val decisions: List<ScheduledItem>, val pending: List<ScheduledItem>) {
    val isEmpty: Boolean get() = decisions.isEmpty() && pending.isEmpty()
}

fun scheduledOverview(items: List<ScheduledItem>): ScheduledOverview {
    val sorted = items.sortedWith(compareBy({ it.schedule.instant }, { it.schedule.id.value }))
    return ScheduledOverview(
        decisions = sorted.filter { it.decisionReason() != null },
        pending = sorted.filter { it.operation.state == SendState.SCHEDULED || it.operation.state == SendState.DUE },
    )
}

/**
 * Karta SIM wysyłki „teraz”: zapisana, jeśli nadal dostępna; inaczej użytkownik wybiera świadomie
 * (specyfikacja S06: utracona SIM), więc zwracamy null zamiast cichego zastępstwa.
 */
fun ScheduledItem.simForSendNow(cards: List<SimCard>): SimCard? =
    cards.firstOrNull { it.available && it.id == operation.subscriptionId }
