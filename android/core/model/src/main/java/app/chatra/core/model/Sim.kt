package app.chatra.core.model

/** Karta SIM z SubscriptionManager. Numer bywa niedostępny; nie zakładamy, że da się go odczytać. */
data class SimCard(
    val id: SubscriptionId,
    val slotIndex: Int,
    val displayName: String,
    val available: Boolean = true,
) {
    /** „SIM 1 · Prywatna” w kompozytorze. */
    val label: String get() = "SIM ${slotIndex + 1} · $displayName"
    val shortLabel: String get() = "SIM ${slotIndex + 1}"
}

/**
 * Wybór SIM: świadomy wybór w szkicu > preferencja rozmowy > domyślna; po zniknięciu karty
 * wymagamy potwierdzenia zamiast cichego przełączenia (specyfikacja 8).
 */
sealed interface SimChoice {
    data class Ready(val sim: SimCard) : SimChoice
    data class NeedsConfirmation(val previous: SubscriptionId?, val options: List<SimCard>) : SimChoice
    data object NoSim : SimChoice
}

fun chooseSim(cards: List<SimCard>, draft: SubscriptionId?, conversation: SubscriptionId?, default: SubscriptionId?): SimChoice {
    val available = cards.filter { it.available }
    if (available.isEmpty()) return SimChoice.NoSim
    for (wanted in listOfNotNull(draft, conversation)) {
        val match = available.firstOrNull { it.id == wanted }
        if (match != null) return SimChoice.Ready(match)
        return SimChoice.NeedsConfirmation(wanted, available)
    }
    available.firstOrNull { it.id == default }?.let { return SimChoice.Ready(it) }
    return if (available.size == 1) SimChoice.Ready(available[0]) else SimChoice.NeedsConfirmation(null, available)
}
