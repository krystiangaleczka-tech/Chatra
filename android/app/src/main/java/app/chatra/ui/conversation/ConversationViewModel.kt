package app.chatra.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.AttachmentId
import app.chatra.core.model.Clock
import app.chatra.core.model.MediaVariant
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationRepository
import app.chatra.core.model.ConversationSummary
import app.chatra.core.model.Draft
import app.chatra.core.model.DraftId
import app.chatra.core.model.DraftRepository
import app.chatra.core.model.Message
import app.chatra.core.model.OperationId
import app.chatra.core.model.RecipientMode
import app.chatra.core.model.SendOperation
import app.chatra.core.model.SendQueue
import app.chatra.core.model.LatenessPolicy
import app.chatra.core.model.ScheduleRepository
import app.chatra.core.model.SendingPrefs
import app.chatra.core.model.SimCard
import app.chatra.core.model.SimChoice
import app.chatra.core.model.SimRepository
import app.chatra.core.model.SmsCounter
import app.chatra.core.model.SubscriptionId
import app.chatra.core.model.chooseSim
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.chatra.core.model.SmsTextAnalyzer
import app.chatra.core.model.ModelSmsTextAnalyzer
import app.chatra.core.model.MessageRef

data class ConversationState(
    val summary: ConversationSummary? = null,
    val messages: List<Message> = emptyList(),
    val operations: Map<OperationId, SendOperation> = emptyMap(),
    val sims: List<SimCard> = emptyList(),
    val sim: SimChoice = SimChoice.NoSim,
    val text: String = "",
    val counter: SmsCounter.Count = SmsCounter.count(""),
    val counterEnabled: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val sendingAvailable: Boolean = true,
    val hasOlder: Boolean = false,
) {
    val canSend: Boolean get() = sendingAvailable && !busy && text.isNotBlank() && text.length <= 16384 && counter.preservesText && sim is SimChoice.Ready && summary?.participants?.any { it.address.canReply } == true
}

class ConversationViewModel(
    private val id: ConversationId,
    private val conversations: ConversationRepository,
    private val drafts: DraftRepository,
    simRepository: SimRepository,
    private val queue: SendQueue,
    private val schedules: ScheduleRepository,
    private val sending: StateFlow<SendingPrefs>,
    val clock: Clock,
    private val smsText: SmsTextAnalyzer = ModelSmsTextAnalyzer,
    private val mmsAvailable: Boolean = true,
    sendAccess: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true),
    private val draftWriteScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {
    private val text = MutableStateFlow<String?>(null)
    private val chosenSim = MutableStateFlow<SubscriptionId?>(null)
    private var draftId: DraftId? = null
    private var saveJob: Job? = null
    private val busy = MutableStateFlow(true)
    private var revision = 0L
    private var draftBase: Draft? = null
    val savedDrafts = drafts.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val error = MutableStateFlow<String?>(null)
    private var selectedLabel: String? = null

    init {
        viewModelScope.launch {
            try {
            val d = drafts.draft(id).first()
            draftId = d?.id
            revision = d?.revision ?: 0
            draftBase = d
            if (text.value == null) text.value = d?.text.orEmpty()
            chosenSim.value = chosenSim.value ?: d?.subscriptionId
            selectedLabel = d?.subscriptionLabel
            busy.value = false
            } catch (_: Exception) { error.value = "Nie udało się wczytać szkicu. Otwórz rozmowę ponownie." }
        }
    }

    private val base = combine(conversations.conversation(id), conversations.messages(id), queue.operations(), simRepository.cards(), conversations.hasOlder(id)) { s, m, ops, sims, older ->
        ConversationState(summary = s, messages = m, operations = ops.associateBy { it.id }, sims = sims, hasOlder = older)
    }

    private val simSettings = combine(chosenSim, conversations.preferredSim(id), simRepository.defaultSubscription()) { chosen, preferred, default -> Triple(chosen, preferred, default) }
    private val form = combine(base, text, simSettings, sending) { b, t, selection, prefs ->
        val txt = t.orEmpty()
        val sim = chooseSim(b.sims, selection.first, selection.second, prefs.defaultSubscriptionId ?: selection.third)
        b.copy(
            sim = sim,
            text = txt,
            counter = smsText.count(txt, (sim as? SimChoice.Ready)?.sim?.id),
            counterEnabled = prefs.smsCounterEnabled,
            sendingAvailable = mmsAvailable || b.summary?.isGroupMms != true,
        )
    }
    val state: StateFlow<ConversationState> = combine(form, busy, error, sendAccess) { form, b, e, allowed -> form.copy(busy = b, error = e, sendingAvailable = form.sendingAvailable && allowed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationState())

    /** Otwarty wątek oznaczamy po rzeczywistym pokazaniu treści (S02). */
    fun onShown(refs: Set<MessageRef>) {
        viewModelScope.launch { runCatching { conversations.markShown(id, refs) } }
    }
    fun loadOlder() { viewModelScope.launch { conversations.loadOlder(id) } }

    fun leave(onDone: () -> Unit) { if (!busy.value) viewModelScope.launch { saveJob?.join(); onDone() } }

    fun onText(value: String) {
        if (busy.value) return
        text.value = value
        error.value = null
        persistDraft()
    }

    fun selectSim(sim: SubscriptionId) {
        if (busy.value) return
        chosenSim.value = sim
        selectedLabel = state.value.sims.firstOrNull { it.id == sim }?.label
        persistDraft()
        viewModelScope.launch { conversations.preferSim(id, sim, selectedLabel.orEmpty()) }
    }

    private fun persistDraft() {
        val s = state.value
        val recipients = s.summary?.participants?.map { it.address } ?: return
        val did = draftId ?: DraftId.new().also { draftId = it }
        if (chosenSim.value == null) (s.sim as? SimChoice.Ready)?.sim?.let { chosenSim.value = it.id; selectedLabel = it.label }
        revision++
        val draft = Draft(did, id, recipients, text.value.orEmpty(), mode(s), draftBase?.attachmentIds.orEmpty(), chosenSim.value, clock.now(),
            mediaVariant = draftBase?.mediaVariant, attachmentBytes = draftBase?.attachmentBytes ?: 0,
            subscriptionLabel = selectedLabel, revision = revision, recipientNames = draftBase?.recipientNames.orEmpty(), recipientInput = draftBase?.recipientInput.orEmpty())
        saveJob = (draftWriteScope ?: viewModelScope).launch {
            try { drafts.save(draft) }
            catch (_: Exception) { error.value = "Nie udało się zapisać szkicu. Spróbuj ponownie przed zamknięciem aplikacji." }
        }
    }

    fun restore(d: Draft) {
        if (busy.value || d.id == draftId) return
        if (!text.value.isNullOrEmpty() || !draftBase?.attachmentIds.isNullOrEmpty()) persistDraft()
        draftId = d.id; draftBase = d; revision = d.revision
        text.value = d.text; chosenSim.value = d.subscriptionId; selectedLabel = d.subscriptionLabel
        persistDraft()
    }

    private fun mode(s: ConversationState) = when {
        (s.summary?.participants?.size ?: 1) <= 1 -> RecipientMode.Single
        s.summary?.isGroupMms == true -> RecipientMode.GroupMms
        else -> RecipientMode.SeparateSms
    }

    /** Kliknięcie Wyślij utrwala operację PENDING_UNDO; szkic usuwamy dopiero po przyjęciu zlecenia. */
    fun send() {
        val s = state.value
        if (!s.canSend || busy.value || s.text != text.value) return
        val sim = (s.sim as SimChoice.Ready).sim
        val recipients = s.summary?.participants?.map { it.address } ?: return
        busy.value = true
        val did = draftId ?: DraftId.new().also { draftId = it }
        viewModelScope.launch {
            try {
                val draft = Draft(did, id, recipients, s.text, mode(s), draftBase?.attachmentIds.orEmpty(), sim.id, clock.now(), subscriptionLabel = sim.label, revision = revision)
                queue.enqueue(draft, sending.value.undoSeconds)
                draftId = null; draftBase = null; revision = 0
                if (text.value == s.text) text.value = ""
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Nie udało się dodać SMS do kolejki. Treść pozostała w szkicu; sprawdź rolę, zgody i SIM." }
            finally { busy.value = false }
        }
    }

    /** Zdjęcia z S04: osobna operacja z wariantem; tekst z pola zostaje w szkicu. */
    fun sendMedia(attachments: List<AttachmentId>, bytes: Long, variant: MediaVariant) {
        if (!mmsAvailable) { error.value = "Wysyłanie MMS i HD nie jest jeszcze dostępne."; return }
        val s = state.value
        val sim = (s.sim as? SimChoice.Ready)?.sim ?: return
        val recipients = s.summary?.participants?.map { it.address } ?: return
        if (attachments.isEmpty()) return
        viewModelScope.launch {
            queue.enqueue(
                Draft(DraftId.new(), id, recipients, "", mode(s), attachments, sim.id, clock.now(), mediaVariant = variant, attachmentBytes = bytes),
                sending.value.undoSeconds,
            )
        }
    }

    /**
     * Zapis S05: treść [body] (szkic rozmowy albo preset planszy), odbiorcy i SIM rozmowy.
     * Gdy treść pochodzi z kompozytora, szkic przechodzi do harmonogramu i pole się czyści.
     */
    fun schedule(
        body: String,
        fromComposer: Boolean,
        instant: java.time.Instant,
        local: java.time.LocalDateTime,
        policy: LatenessPolicy,
        onDone: () -> Unit,
    ) {
        val s = state.value
        val sim = (s.sim as? SimChoice.Ready)?.sim ?: return
        val recipients = s.summary?.participants?.map { it.address } ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            val draft = Draft(draftId ?: DraftId.new(), id, recipients, body, mode(s), emptyList(), sim.id, clock.now())
            schedules.schedule(draft, instant, local, clock.zone(), policy)
            if (fromComposer) {
                draftId = null
                text.value = ""
            }
            onDone()
        }
    }

    fun insertText(value: String) = onText(if (text.value.isNullOrBlank()) value else "${text.value} $value")

    fun cancel(op: OperationId) {
        viewModelScope.launch {
            try {
                if (queue.cancel(op)) {
                    val d = drafts.draft(id).first()
                    if (text.value.isNullOrBlank()) d?.let(::restore)
                    else error.value = "Anulowano. Przywrócony tekst znajdziesz w Szkicach; obecny tekst został zachowany."
                } else error.value = "Wysyłanie już się rozpoczęło. Nie można go anulować."
            } catch (_: Exception) { error.value = "Nie udało się anulować. Sprawdź status wiadomości." }
        }
    }
    fun retry(op: OperationId, sim: SubscriptionId? = null) {
        viewModelScope.launch {
            try {
                val accepted = if (sim == null) queue.retry(op) else queue.retry(op, sim)
                if (!accepted) error.value = "Nie można bezpiecznie ponowić tej wiadomości. Sprawdź jej status."
            } catch (_: Exception) { error.value = "Wybierz dostępną kartę SIM i sprawdź rolę oraz uprawnienia SMS." }
        }
    }
    fun reconcile(op: OperationId) {
        viewModelScope.launch {
            try { queue.reconcile(op); error.value = "Sprawdzono dostępne dowody. Brak potwierdzenia nie oznacza, że SMS nie został wysłany." }
            catch (_: Exception) { error.value = "Nie udało się sprawdzić statusu. Spróbuj ponownie." }
        }
    }
    fun copyToDraft(op: OperationId) {
        viewModelScope.launch {
            try {
                val d = queue.copyToDraft(op) ?: return@launch
                if (text.value.isNullOrBlank()) restore(d)
                error.value = "Kopia jest w Szkicach. Ponowna wysyłka może dostarczyć drugą wiadomość."
            } catch (_: Exception) { error.value = "Nie udało się utworzyć kopii szkicu." }
        }
    }
}
