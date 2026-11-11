package app.chatra.ui.newmessage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.*
import app.chatra.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NewMessageState(
    val recipients: List<Participant> = emptyList(), val input: String = "", val text: String = "",
    val mode: RecipientMode = RecipientMode.SeparateSms, val sims: List<SimCard> = emptyList(),
    val sim: SimChoice = SimChoice.NoSim, val suggestions: List<Participant> = emptyList(), val busy: Boolean = false, val error: String? = null,
    val sendingAvailable: Boolean = true,
) {
    val canSend get() = sendingAvailable && !busy && text.isNotBlank() && recipients.isNotEmpty() && input.isBlank() && recipients.all { isOutgoingNumber(it.address.raw) } && sim is SimChoice.Ready
    val operationLabel get() = if (recipients.size > 1 && mode == RecipientMode.GroupMms) "1 grupowy MMS" else if (recipients.size == 1) "1 SMS" else "${recipients.size} osobne SMS"
}

/** Nowy szkic ma osobny SavedStateHandle: anulowanie arkusza zachowuje odbiorców, treść i SIM. */
class NewMessageViewModel(private val saved: SavedStateHandle, private val c: AppContainer, preset: List<Participant>, external: ExternalCompose? = null) : ViewModel() {
    init {
        if (!saved.contains("addresses")) {
            val recipients = external?.recipients?.map { Participant(it) } ?: preset
            saved["addresses"] = ArrayList(recipients.map { it.address.raw })
            saved["names"] = ArrayList(recipients.map { it.displayName.orEmpty() })
            if (external != null) saved["text"] = external.text
            if (preset.isNotEmpty()) saved["sim"] = 1
        }
    }
    private val addresses = saved.getStateFlow("addresses", arrayListOf<String>())
    private val names = saved.getStateFlow("names", arrayListOf<String>())
    private val input = saved.getStateFlow("input", "")
    private val text = saved.getStateFlow("text", "")
    private val mode = saved.getStateFlow("mode", RecipientMode.SeparateSms.name)
    private val chosen = saved.getStateFlow<Int?>("sim", null)
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val form = combine(addresses, names, input, text, mode) { a, n, i, t, m ->
        NewMessageState(a.mapIndexed { index, raw -> Participant(Address(raw), n.getOrNull(index)?.takeIf { it.isNotBlank() }) }, i, t, RecipientMode.valueOf(m))
    }
    private val data = combine(c.sims.cards(), c.contacts.suggestions(), c.sending.value, chosen) { sims, contacts, prefs, sim ->
        Triple(sims, contacts, chooseSim(sims, sim?.let(::SubscriptionId), null, prefs.defaultSubscriptionId))
    }
    val state = combine(form, data, busy, error) { f, d, b, e ->
        f.copy(sims = d.first, sim = d.third, busy = b, error = e, sendingAvailable = c.canSendMessages,
            suggestions = d.second.filter { p -> f.recipients.none { it.address.normalized == p.address.normalized } && (f.input.isBlank() || searchFold(f.input) in searchFold(p.label) || f.input.filter(Char::isDigit).let { it.isNotEmpty() && it in p.address.normalized }) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NewMessageState())

    fun input(value: String) { saved["input"] = value; error.value = null }
    fun text(value: String) { saved["text"] = value; error.value = null }
    fun mode(value: RecipientMode) { saved["mode"] = value.name }
    fun sim(value: SubscriptionId) { saved["sim"] = value.value }
    fun remove(index: Int) {
        saved["addresses"] = ArrayList(addresses.value.filterIndexed { i, _ -> i != index })
        saved["names"] = ArrayList(names.value.filterIndexed { i, _ -> i != index })
    }
    fun add(participant: Participant) {
        if (!isOutgoingNumber(participant.address.raw)) { error.value = "Podaj poprawny numer telefonu."; return }
        if (addresses.value.none { Address(it).normalized == participant.address.normalized }) {
            saved["addresses"] = ArrayList(addresses.value + participant.address.raw)
            saved["names"] = ArrayList(names.value + participant.displayName.orEmpty())
        }
        saved["input"] = ""
        error.value = null
    }
    fun addInput() {
        val raw = input.value.trim()
        if (!isOutgoingNumber(raw)) { error.value = "Podaj poprawny numer telefonu lub wybierz kontakt."; return }
        viewModelScope.launch { add(c.contacts.lookup(Address(raw)) ?: Participant(Address(raw))) }
    }
    fun send(onDone: (ConversationId) -> Unit) {
        val s = state.value
        if (!s.canSend || busy.value || text.value.isBlank() || addresses.value.isEmpty()) return
        busy.value = true
        viewModelScope.launch {
            val draft = Draft(DraftId.new(), null, s.recipients.map { it.address }, s.text, s.mode, emptyList(), (s.sim as SimChoice.Ready).sim.id, c.clock.now())
            val plan = draft.newMessagePlan()
            var last: ConversationId? = null
            val accepted = mutableListOf<Address>()
            try {
                for (part in plan) {
                    val id = c.conversations.conversationFor(part.recipients)
                    c.sendQueue.enqueue(part.copy(conversationId = id), c.sending.value.value.undoSeconds)
                    accepted += part.recipients
                    last = id
                }
                saved["addresses"] = arrayListOf<String>(); saved["names"] = arrayListOf<String>(); saved["text"] = ""
                last?.let(onDone)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Po częściowym powodzeniu następne Wyślij obejmie tylko nieprzyjętych odbiorców.
                val remaining = s.recipients.filter { p -> accepted.none { it.normalized == p.address.normalized } }
                saved["addresses"] = ArrayList(remaining.map { it.address.raw }); saved["names"] = ArrayList(remaining.map { it.displayName.orEmpty() })
                error.value = "Nie udało się dodać wszystkich wiadomości do kolejki. Pozostali odbiorcy zostali w szkicu."
            } finally { busy.value = false }
        }
    }
}
