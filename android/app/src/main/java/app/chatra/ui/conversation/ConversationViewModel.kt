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

data class ConversationState(
    val summary: ConversationSummary? = null,
    val messages: List<Message> = emptyList(),
    val operations: Map<OperationId, SendOperation> = emptyMap(),
    val sims: List<SimCard> = emptyList(),
    val sim: SimChoice = SimChoice.NoSim,
    val text: String = "",
    val counter: SmsCounter.Count = SmsCounter.count(""),
    val counterEnabled: Boolean = true,
) {
    val canSend: Boolean get() = text.isNotBlank() && sim is SimChoice.Ready && summary?.participants?.any { it.address.canReply } == true
}

class ConversationViewModel(
    private val id: ConversationId,
    private val conversations: ConversationRepository,
    private val drafts: DraftRepository,
    simRepository: SimRepository,
    private val queue: SendQueue,
    private val sending: StateFlow<SendingPrefs>,
    val clock: Clock,
) : ViewModel() {
    private val text = MutableStateFlow<String?>(null)
    private val chosenSim = MutableStateFlow<SubscriptionId?>(null)
    private var draftId: DraftId? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val d = drafts.draft(id).first()
            draftId = d?.id
            if (text.value == null) text.value = d?.text.orEmpty()
            chosenSim.value = chosenSim.value ?: d?.subscriptionId
        }
    }

    private val base = combine(conversations.conversation(id), conversations.messages(id), queue.operations(), simRepository.cards()) { s, m, ops, sims ->
        ConversationState(summary = s, messages = m, operations = ops.associateBy { it.id }, sims = sims)
    }

    val state: StateFlow<ConversationState> = combine(base, text, chosenSim, sending) { b, t, sim, prefs ->
        val txt = t.orEmpty()
        b.copy(
            sim = chooseSim(b.sims, sim, null, prefs.defaultSubscriptionId),
            text = txt,
            counter = SmsCounter.count(txt),
            counterEnabled = prefs.smsCounterEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationState())

    /** Otwarty wątek oznaczamy po rzeczywistym pokazaniu treści (S02). */
    fun onShown() {
        viewModelScope.launch { conversations.markRead(id) }
    }

    fun onText(value: String) {
        text.value = value
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            persistDraft()
        }
    }

    fun selectSim(sim: SubscriptionId) {
        chosenSim.value = sim
        viewModelScope.launch { persistDraft() }
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
        drafts.save(Draft(did, id, recipients, t, mode(s), emptyList(), (s.sim as? SimChoice.Ready)?.sim?.id ?: chosenSim.value, clock.now()))
    }

    private fun mode(s: ConversationState) = when {
        (s.summary?.participants?.size ?: 1) <= 1 -> RecipientMode.Single
        s.summary?.isGroupMms == true -> RecipientMode.GroupMms
        else -> RecipientMode.SeparateSms
    }

    /** Kliknięcie Wyślij utrwala operację PENDING_UNDO; szkic usuwamy dopiero po przyjęciu zlecenia. */
    fun send() {
        val s = state.value
        if (!s.canSend) return
        val sim = (s.sim as SimChoice.Ready).sim
        val recipients = s.summary?.participants?.map { it.address } ?: return
        saveJob?.cancel()
        viewModelScope.launch {
            val draft = Draft(draftId ?: DraftId.new(), id, recipients, s.text, mode(s), emptyList(), sim.id, clock.now())
            queue.enqueue(draft, sending.value.undoSeconds)
            draftId = null
            text.value = ""
        }
    }

    /** Zdjęcia z S04: osobna operacja z wariantem; tekst z pola zostaje w szkicu. */
    fun sendMedia(attachments: List<AttachmentId>, bytes: Long, variant: MediaVariant) {
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
        viewModelScope.launch { queue.retry(op) }
    }

    override fun onCleared() {
        // Zamknięcie edytora zapisuje szkic (specyfikacja 5).
        if (text.value != null) kotlinx.coroutines.runBlocking { persistDraft() }
    }
}
