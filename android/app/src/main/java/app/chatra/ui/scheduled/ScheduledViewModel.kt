package app.chatra.ui.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.Clock
import app.chatra.core.model.LastScheduledSent
import app.chatra.core.model.ScheduleId
import app.chatra.core.model.ScheduleRepository
import app.chatra.core.model.ScheduleValidation
import app.chatra.core.model.ScheduledItem
import app.chatra.core.model.SimCard
import app.chatra.core.model.SimRepository
import app.chatra.core.model.SubscriptionId
import app.chatra.core.model.scheduledOverview
import app.chatra.core.model.validateSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class ScheduledState(
    val loading: Boolean = true,
    val decisions: List<ScheduledItem> = emptyList(),
    val pending: List<ScheduledItem> = emptyList(),
    val lastSent: LastScheduledSent? = null,
    val sims: List<SimCard> = emptyList(),
    /** Zlecenia, dla których trwa akcja: drugie stuknięcie nic nie robi. */
    val busy: Set<ScheduleId> = emptySet(),
    /** Komunikat pod kartą decyzji (np. termin z przeszłości). */
    val notices: Map<ScheduleId, String> = emptyMap(),
)

/** S06 Zaplanowane (CH015). */
class ScheduledViewModel(
    private val schedules: ScheduleRepository,
    sims: SimRepository,
    private val clock: Clock,
) : ViewModel() {
    private val busy = MutableStateFlow<Set<ScheduleId>>(emptySet())
    private val notices = MutableStateFlow<Map<ScheduleId, String>>(emptyMap())

    val state: StateFlow<ScheduledState> = combine(schedules.scheduled(), schedules.lastSent(), sims.cards(), busy, notices) { items, last, cards, b, n ->
        val o = scheduledOverview(items)
        ScheduledState(false, o.decisions, o.pending, last, cards, b, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduledState())

    fun sendNow(id: ScheduleId, sim: SubscriptionId?) = act(id) {
        if (!schedules.sendNow(id, sim)) notices.update { it + (id to "Nie udało się rozpocząć wysyłki. Wybierz kartę SIM i spróbuj ponownie.") }
    }

    fun reschedule(id: ScheduleId, local: LocalDateTime) = act(id) {
        val zone = clock.zone()
        when (val v = validateSchedule(local, zone, clock.now())) {
            ScheduleValidation.Past -> notices.update { it + (id to "Ten termin już minął. Wybierz późniejszy.") }
            else -> {
                val instant = v.instant ?: return@act
                val stored = if (v is ScheduleValidation.Gap) v.suggestedLocal else local
                schedules.reschedule(id, instant, stored, zone)
            }
        }
    }

    fun delete(id: ScheduleId) = act(id) { schedules.delete(id) }

    private fun act(id: ScheduleId, block: suspend () -> Unit) {
        if (id in busy.value) return
        busy.update { it + id }
        notices.update { it - id }
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.update { it - id }
            }
        }
    }
}
