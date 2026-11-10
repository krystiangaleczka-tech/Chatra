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
import kotlinx.coroutines.delay
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
) : ViewModel() {
    private val text = MutableStateFlow<String?>(null)
    private val chosenSim = MutableStateFlow<SubscriptionId?>(null)
    private var draftId: DraftId? = null
    private var saveJob: Job? = null
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var selectedLabel: String? = null

    init {
        viewModelScope.launch {
            val d = drafts.draft(id).first()
            draftId = d?.id
            if (text.value == null) text.value = d?.text.orEmpty()
            chosenSim.value = chosenSim.value ?: d?.subscriptionId
            selectedLabel = d?.subscriptionLabel
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

    fun onText(value: String) {
        text.value = value
        error.value = null
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            persistDraft()
        }
    }

    fun selectSim(sim: SubscriptionId) {
        chosenSim.value = sim
        selectedLabel = state.value.sims.firstOrNull { it.id == sim }?.label
        viewModelScope.launch { conversations.preferSim(id, sim, selectedLabel.orEmpty()); persistDraft() }
    }

    private suspend fun persistDraft() {
        val s = state.value
        val recipients = s.summary?.participants?.map { it.address } ?: return
        val t = text.value.orEmpty()
        val did = draftId ?: DraftId.new().also { draftId = it }
        if (t.isBlank()) {
            drafts.delete(did)
            return
        }
        if (chosenSim.value == null) (s.sim as? SimChoice.Ready)?.sim?.let { chosenSim.value = it.id; selectedLabel = it.label }
        drafts.save(Draft(did, id, recipients, t, mode(s), emptyList(), (s.sim as? SimChoice.Ready)?.sim?.id ?: chosenSim.value, clock.now(), subscriptionLabel = selectedLabel ?: (s.sim as? SimChoice.Ready)?.sim?.label))
    }

    private fun mode(s: ConversationState) = when {
        (s.summary?.participants?.size ?: 1) <= 1 -> RecipientMode.Single
        s.summary?.isGroupMms == true -> RecipientMode.GroupMms
        else -> RecipientMode.SeparateSms
    }

    /** Kliknięcie Wyślij utrwala operację PENDING_UNDO; szkic usuwamy dopiero po przyjęciu zlecenia. */
    fun send() {
        val s = state.value
        if (!s.canSend || busy.value) return
        val sim = (s.sim as SimChoice.Ready).sim
        val recipients = s.summary?.participants?.map { it.address } ?: return
        saveJob?.cancel()
        busy.value = true
        val did = draftId ?: DraftId.new().also { draftId = it }
        viewModelScope.launch {
            try {
                val draft = Draft(did, id, recipients, s.text, mode(s), emptyList(), sim.id, clock.now(), subscriptionLabel = sim.label)
                queue.enqueue(draft, sending.value.undoSeconds)
                draftId = null
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
        saveJob?.cancel()
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
            if (queue.cancel(op)) {
                val d = drafts.draft(id).first()
                draftId = d?.id
                text.value = d?.text.orEmpty()
            }
        }
    }

    fun retry(op: OperationId) {
        viewModelScope.launch { if (!queue.retry(op)) error.value = "Nie można jeszcze bezpiecznie ponowić tej wiadomości. Sprawdź jej status przed kolejną wysyłką." }
    }

    override fun onCleared() {
        // Zamknięcie edytora zapisuje szkic (specyfikacja 5).
        if (text.value != null) kotlinx.coroutines.runBlocking { persistDraft() }
    }
}
