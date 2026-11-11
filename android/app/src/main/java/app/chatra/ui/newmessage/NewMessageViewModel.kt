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
    val counter: SmsCounter.Count = SmsCounter.count(""),
    val counterEnabled: Boolean = true,
) {
    val canSend get() = sendingAvailable && !busy && text.isNotBlank() && text.length <= 16384 && counter.preservesText && recipients.isNotEmpty() && recipients.size <= 20 && input.isBlank() && recipients.all { isOutgoingNumber(it.address.raw) } && sim is SimChoice.Ready
    val operationLabel get() = if (recipients.size > 1 && mode == RecipientMode.GroupMms) "1 grupowy MMS" else if (recipients.size == 1) "1 SMS" else "${recipients.size} osobne SMS"
}

/** Nowy szkic ma osobny SavedStateHandle: anulowanie arkusza zachowuje odbiorców, treść i SIM. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
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
    private val busy = MutableStateFlow(c.drafts.durable)
    private val writeScope get() = c.draftWriteScope ?: viewModelScope
    private var draftId = DraftId(saved.get<String>("draftId") ?: external?.let { "external:${it.id}" } ?: DraftId.new().value)
    private var revision = saved.get<Long>("revision") ?: 0L
    private var loaded = !c.drafts.durable
    private var draftBase: Draft? = null
    private var saveJob: kotlinx.coroutines.Job? = null
    val drafts = c.drafts.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val error = MutableStateFlow<String?>(null)
    private val form = combine(addresses, names, input, text, mode) { a, n, i, t, m ->
        NewMessageState(a.mapIndexed { index, raw -> Participant(Address(raw), n.getOrNull(index)?.takeIf { it.isNotBlank() }) }, i, t, RecipientMode.valueOf(m))
    }
    private val defaultSim = combine(c.sending.value, c.sims.defaultSubscription()) { prefs, system -> prefs.defaultSubscriptionId ?: system }
    private val matchingContacts = input.debounce { if (c.isDemo) 0L else 180L }
        .flatMapLatest { c.contacts.search(it) }
    private val data = combine(c.sims.cards(), matchingContacts, defaultSim, chosen) { sims, contacts, default, sim ->
        Triple(sims, contacts, chooseSim(sims, sim?.let(::SubscriptionId), null, default))
    }
    val state = combine(form, data, busy, error, c.sendAccess) { f, d, b, e, access ->
        f.copy(sims = d.first, sim = d.third, busy = b, error = e, sendingAvailable = access && (c.isDemo || f.mode != RecipientMode.GroupMms),
            counterEnabled = c.sending.value.value.smsCounterEnabled,
            counter = c.smsText.count(f.text, (d.third as? SimChoice.Ready)?.sim?.id),
            suggestions = d.second.filter { p -> f.recipients.none { outgoingRecipientKey(it.address) == outgoingRecipientKey(p.address) } })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NewMessageState())

    init {
        saved["draftId"] = draftId.value
        if (c.drafts.durable) viewModelScope.launch {
            try {
                val d = c.drafts.find(draftId) ?: if (external == null && !c.drafts.isClosed(draftId)) c.drafts.all().first().firstOrNull { it.conversationId == null } else null
                if (d != null) applyDraft(d)
                else if (c.drafts.isClosed(draftId)) {
                    saved["text"] = ""; saved["addresses"] = arrayListOf<String>(); saved["names"] = arrayListOf<String>()
                    draftId = DraftId.new(); draftBase = null; revision = 0; saved["draftId"] = draftId.value
                }
                loaded = true
            } catch (_: Exception) { error.value = "Nie udało się wczytać szkicu. Otwórz edytor ponownie." }
            finally { busy.value = !loaded }
        }
    }
    private fun applyDraft(d: Draft) {
        draftBase = d
        draftId = d.id; revision = d.revision; saved["draftId"] = d.id.value; saved["revision"] = revision
        saved["addresses"] = ArrayList(d.recipients.map { it.raw }); saved["names"] = ArrayList(d.recipientNames)
        saved["text"] = d.text; saved["input"] = d.recipientInput; saved["mode"] = d.recipientMode.name
        saved["sim"] = d.subscriptionId?.value; saved["simLabel"] = d.subscriptionLabel
    }
    fun restore(d: Draft) {
        if (busy.value || d.id == draftId) return
        if (text.value.isNotEmpty() || addresses.value.isNotEmpty() || input.value.isNotEmpty() || !draftBase?.attachmentIds.isNullOrEmpty()) persist()
        applyDraft(d)
        persist()
    }
    private fun snapshot(): Draft = Draft(draftId, null, addresses.value.map(::Address), text.value, RecipientMode.valueOf(mode.value), draftBase?.attachmentIds.orEmpty(),
        chosen.value?.let(::SubscriptionId), c.clock.now(), subscriptionLabel = saved["simLabel"], revision = revision,
        recipientNames = names.value, recipientInput = input.value, mediaVariant = draftBase?.mediaVariant, attachmentBytes = draftBase?.attachmentBytes ?: 0)
    private fun persist() {
        if (!c.drafts.durable || !loaded) return
        revision++; saved["revision"] = revision
        val d = snapshot()
        saveJob = writeScope.launch {
            try { c.drafts.save(d) }
            catch (_: Exception) { error.value = "Nie udało się zapisać szkicu. Spróbuj ponownie przed zamknięciem aplikacji." }
        }
    }
    init {
        viewModelScope.launch {
            state.collect { s ->
                if (chosen.value == null && (s.recipients.isNotEmpty() || s.text.isNotBlank())) (s.sim as? SimChoice.Ready)?.sim?.let { sim(it.id) }
            }
        }
    }

    fun leave(onDone: () -> Unit) { if (!busy.value) viewModelScope.launch { saveJob?.join(); onDone() } }

    fun input(value: String) { if (busy.value) return; saved["input"] = value; error.value = null; persist() }
    fun text(value: String) { if (busy.value) return; saved["text"] = value; error.value = null; persist() }
    fun mode(value: RecipientMode) { if (busy.value) return; saved["mode"] = value.name; persist() }
    fun sim(value: SubscriptionId) { if (busy.value) return; saved["sim"] = value.value; saved["simLabel"] = state.value.sims.firstOrNull { it.id == value }?.label; persist() }
    fun remove(index: Int) {
        if (busy.value) return
        saved["addresses"] = ArrayList(addresses.value.filterIndexed { i, _ -> i != index })
        saved["names"] = ArrayList(names.value.filterIndexed { i, _ -> i != index }); persist()
    }
    fun add(participant: Participant) {
        if (busy.value) return
        if (!isOutgoingNumber(participant.address.raw)) { error.value = "Podaj poprawny numer telefonu."; return }
        if (addresses.value.none { outgoingRecipientKey(Address(it)) == outgoingRecipientKey(participant.address) }) {
            if (addresses.value.size >= 20) { error.value = "Możesz dodać maksymalnie 20 odbiorców."; return }
            saved["addresses"] = ArrayList(addresses.value + participant.address.raw)
            saved["names"] = ArrayList(names.value + participant.displayName.orEmpty())
        }
        saved["input"] = ""
        error.value = null
        persist()
    }
    fun addInput() {
        val raw = input.value.trim()
        if (!isOutgoingNumber(raw)) { error.value = "Podaj poprawny numer telefonu lub wybierz kontakt."; return }
        viewModelScope.launch { add(c.contacts.lookup(Address(raw)) ?: Participant(Address(raw))) }
    }
    fun send(onDone: (ConversationId) -> Unit) {
        val s = state.value
        if (!s.canSend || busy.value || s.text != text.value || text.value.isBlank() || addresses.value.isEmpty()) return
        busy.value = true
        viewModelScope.launch {
            val card = (s.sim as SimChoice.Ready).sim
            val draft = Draft(if (c.drafts.durable) draftId else DraftId.new(), null, s.recipients.map { it.address }, s.text, s.mode, draftBase?.attachmentIds.orEmpty(), card.id, c.clock.now(), subscriptionLabel = card.label, revision = revision, recipientNames = names.value, recipientInput = input.value, mediaVariant = draftBase?.mediaVariant, attachmentBytes = draftBase?.attachmentBytes ?: 0)
            val plan = if (c.drafts.durable) listOf(draft) else draft.newMessagePlan()
            var last: ConversationId? = null
            val accepted = mutableListOf<Address>()
            try {
                for (part in plan) {
                    val id = c.conversations.conversationFor(if (c.drafts.durable) part.recipients.take(1) else part.recipients)
                    c.sendQueue.enqueue(if (c.drafts.durable) part else part.copy(conversationId = id), c.sending.value.value.undoSeconds)
                    accepted += part.recipients
                    last = id
                }
                draftId = DraftId.new(); draftBase = null; revision = 0; saved["draftId"] = draftId.value; saved["revision"] = 0L
                saved["addresses"] = arrayListOf<String>(); saved["names"] = arrayListOf<String>(); saved["text"] = ""
                last?.let(onDone)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Po częściowym powodzeniu następne Wyślij obejmie tylko nieprzyjętych odbiorców.
                val remaining = s.recipients.filter { p -> accepted.none { outgoingRecipientKey(it) == outgoingRecipientKey(p.address) } }
                saved["addresses"] = ArrayList(remaining.map { it.address.raw }); saved["names"] = ArrayList(remaining.map { it.displayName.orEmpty() })
                error.value = "Nie udało się dodać wszystkich wiadomości do kolejki. Pozostali odbiorcy zostali w szkicu."
            } finally { busy.value = false; if (error.value != null) persist() }
        }
    }
}
