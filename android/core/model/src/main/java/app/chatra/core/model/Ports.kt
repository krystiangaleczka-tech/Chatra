package app.chatra.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Granice architektury (CH005). UI zależy wyłącznie od tych kontraktów, więc działa z repozytorium
 * demonstracyjnym (fixture CH006) albo z providerem Telephony; logika wysyłania działa bez UI.
 */
interface ConversationRepository {
    fun inbox(): Flow<List<ConversationSummary>>
    fun conversation(id: ConversationId): Flow<ConversationSummary?>
    fun messages(id: ConversationId): Flow<List<Message>>
    suspend fun markRead(id: ConversationId)
    suspend fun setPinned(id: ConversationId, pinned: Boolean)
    suspend fun moveToTrash(id: ConversationId)
    suspend fun conversationFor(recipients: List<Address>): ConversationId
    fun historyStatus(): Flow<HistoryStatus> = kotlinx.coroutines.flow.flowOf(HistoryStatus.Ready)
    suspend fun refresh() {}
    suspend fun loadOlder(id: ConversationId) {}
    fun hasOlder(id: ConversationId): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    fun preferredSim(id: ConversationId): Flow<SubscriptionId?> = kotlinx.coroutines.flow.flowOf(null)
    suspend fun preferSim(id: ConversationId, sim: SubscriptionId, label: String) {}
    suspend fun markUnread(id: ConversationId) {}
    suspend fun markAllRead() { inbox().first().filter { it.isUnread }.forEach { markRead(it.id) } }
    suspend fun markShown(id: ConversationId, refs: Set<MessageRef>) { markRead(id) }
}

sealed interface HistoryStatus {
    data object Loading : HistoryStatus
    data object Ready : HistoryStatus
    data object PermissionRequired : HistoryStatus
    data class Error(val message: String) : HistoryStatus
}

interface DraftRepository {
    val durable: Boolean get() = false
    fun draft(conversationId: ConversationId): Flow<Draft?>
    fun all(): Flow<List<Draft>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun find(id: DraftId): Draft? = all().first().firstOrNull { it.id == id }
    suspend fun isClosed(id: DraftId): Boolean = false
    suspend fun save(draft: Draft)
    suspend fun delete(id: DraftId)
}

interface SimRepository {
    fun cards(): Flow<List<SimCard>>
    fun defaultSubscription(): Flow<SubscriptionId?> = kotlinx.coroutines.flow.flowOf(null)
}

interface ContactsDirectory {
    fun suggestions(): Flow<List<Participant>>
    suspend fun lookup(address: Address): Participant?
}

/** Kolejka wysyłek: jedyna droga do transportu z kompozytora, harmonogramu i powiadomień. */
interface SendQueue {
    fun operations(): Flow<List<SendOperation>>
    suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId
    suspend fun cancel(id: OperationId): Boolean
    suspend fun retry(id: OperationId): Boolean
    suspend fun retry(id: OperationId, sim: SubscriptionId): Boolean = retry(id)
    suspend fun reconcile(id: OperationId) {}
    suspend fun copyToDraft(id: OperationId): Draft? = null
}

interface ScheduleRepository {
    fun scheduled(): Flow<List<ScheduledItem>>

    /**
     * Zapis z formularza S05: operacja SCHEDULED + harmonogram (Instant, pierwotny LocalDateTime, strefa).
     * Kontrakt testowy CH014; trwały zapis z alarmem i rekoncyliacją dostarcza CH035.
     */
    suspend fun schedule(draft: Draft, instant: Instant, local: LocalDateTime, zone: ZoneId, policy: LatenessPolicy): ScheduleId

    /** Stopka S06; null, gdy nic zaplanowanego jeszcze nie wyszło. */
    fun lastSent(): Flow<LastScheduledSent?>

    /**
     * „Wyślij teraz” (S06) przez tę samą kolejkę co kompozytor. Jedno uruchomienie na wersję zlecenia:
     * drugie wywołanie po przejściu do DISPATCHING zwraca false. [sim] wymagane, gdy zapisana karta zniknęła.
     */
    suspend fun sendNow(id: ScheduleId, sim: SubscriptionId? = null): Boolean

    /** Zmiana terminu: nowa wersja zlecenia, stary alarm unieważniony (token alarmu czyszczony). */
    suspend fun reschedule(id: ScheduleId, instant: Instant, local: LocalDateTime, zone: ZoneId): Boolean

    /** Usuwa harmonogram i anuluje jego operację; rozmowa i wcześniejsze wiadomości zostają. */
    suspend fun delete(id: ScheduleId): Boolean
}

/** Zgoda na dokładne alarmy (SCHEDULE_EXACT_ALARM). Bez niej S05 nie obiecuje wysyłki o czasie (CH014, CH035). */
interface ExactAlarmGate {
    fun canScheduleExact(): Boolean
    fun openSettings()
}

/** Pozycja listy S06: harmonogram + migawka operacji. */
data class ScheduledItem(val schedule: ScheduledMessage, val operation: SendOperation, val title: String)

/** Kontrolowany zegar: produkcyjny i stały 29.10.2026 w fixture (CH006). */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
    /** Czas monotoniczny do odliczania okna anulowania w działającym procesie. */
    fun elapsedRealtimeMillis(): Long
}

/** Wynik transportu dla jednej części / odbiorcy; HD ma osobne wyniki niż SIM (specyfikacja 7). */
sealed interface TransportResult {
    data object Accepted : TransportResult
    data class Failed(val code: Int, val permanent: Boolean) : TransportResult
    data class Uncertain(val reason: String) : TransportResult
}
