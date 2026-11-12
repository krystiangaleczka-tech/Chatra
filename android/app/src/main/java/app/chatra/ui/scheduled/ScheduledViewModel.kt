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

    fun sendNow(id: ScheduleId, sim: SubscriptionId?, expectedVersion: Int? = null) = act(id) {
        if (!(if (expectedVersion == null) schedules.sendNow(id, sim) else schedules.sendNow(id, sim, expectedVersion))) notices.update { it + (id to "Nie udało się rozpocząć wysyłki. Wybierz kartę SIM i spróbuj ponownie.") }
    }

    fun reschedule(id: ScheduleId, local: LocalDateTime, expectedVersion: Int? = null) = act(id) {
        val zone = clock.zone()
        when (val v = validateSchedule(local, zone, clock.now())) {
            ScheduleValidation.Past -> notices.update { it + (id to "Ten termin już minął. Wybierz późniejszy.") }
            else -> {
                val instant = v.instant ?: return@act
                val stored = if (v is ScheduleValidation.Gap) v.suggestedLocal else local
                val accepted = if (expectedVersion == null) schedules.reschedule(id, instant, stored, zone) else schedules.reschedule(id, instant, stored, zone, expectedVersion)
                if (!accepted) notices.update { it + (id to "Zlecenie zmieniło się lub wysyłanie już się rozpoczęło. Sprawdź aktualną wersję.") }
            }
        }
    }

    fun delete(id: ScheduleId, expectedVersion: Int? = null) = act(id) {
        val deleted = if (expectedVersion == null) schedules.delete(id) else schedules.delete(id, expectedVersion)
        if (!deleted) notices.update { it + (id to "Nie można usunąć tej wersji zlecenia. Sprawdź bieżący status.") }
    }
    fun edit(item: ScheduledItem, body: String, sim: SubscriptionId, local: LocalDateTime, policy: app.chatra.core.model.LatenessPolicy, onSaved: () -> Unit) = act(item.schedule.id) {
        val instant = validateSchedule(local, clock.zone(), clock.now()).instant ?: error("Wybierz termin w przyszłości.")
        val op = item.operation
        val draft = app.chatra.core.model.Draft(app.chatra.core.model.DraftId("edit:${item.schedule.id.value}"), op.conversationId, op.recipients, body, op.recipientMode, op.attachmentIds, sim, clock.now(), mediaVariant = if (op.attachmentIds.isNotEmpty()) app.chatra.core.model.MediaVariant.Mms else null)
        if (schedules.edit(item.schedule.id, item.schedule.version, draft, instant, local, clock.zone(), policy)) onSaved()
        else notices.update { it + (item.schedule.id to "Zlecenie zmieniło się albo wysyłanie już się rozpoczęło. Wczytaj aktualną wersję.") }
    }

    private fun act(id: ScheduleId, block: suspend () -> Unit) {
        if (id in busy.value) return
        busy.update { it + id }
        notices.update { it - id }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) { notices.update { it + (id to (e.message ?: "Nie udało się zapisać zmiany. Zlecenie zachowuje poprzednią wersję.")) } }
            finally {
                busy.update { it - id }
            }
        }
    }
}
