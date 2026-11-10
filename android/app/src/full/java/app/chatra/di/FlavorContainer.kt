package app.chatra.di

import android.Manifest
import android.app.Application
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import app.chatra.core.model.*
import app.chatra.platform.*
import app.chatra.prefs.DataStorePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId

/** Realna rola i zgody, bez fixture. Historia/transporty dopiero w kolejnych zadaniach. */
fun createContainer(app: Application, referenceFrame: Boolean, roleScenario: String? = null, exactAlarms: Boolean = true): AppContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val platform = SystemPlatform(app)
    val prefs = DataStorePreferences(app, scope, "full")
    val organization = MemoryOrganizationPreview()
    val clock = object : Clock {
        override fun now(): Instant = Instant.now()
        override fun zone(): ZoneId = ZoneId.systemDefault()
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    }
    val conversations = object : ConversationRepository {
        override fun inbox() = flowOf(emptyList<ConversationSummary>())
        override fun conversation(id: ConversationId) = flowOf<ConversationSummary?>(null)
        override fun messages(id: ConversationId) = flowOf(emptyList<Message>())
        override suspend fun markRead(id: ConversationId) { }
        override suspend fun setPinned(id: ConversationId, pinned: Boolean) { }
        override suspend fun moveToTrash(id: ConversationId) { }
        override suspend fun conversationFor(recipients: List<Address>): ConversationId = error("Historia wiadomości nie jest jeszcze dostępna.")
    }
    val drafts = object : DraftRepository {
        override fun draft(conversationId: ConversationId) = flowOf<Draft?>(null)
        override suspend fun save(draft: Draft) = error("Trwały magazyn szkiców nie jest jeszcze dostępny.")
        override suspend fun delete(id: DraftId) { }
    }
    val queue = object : SendQueue {
        override fun operations() = flowOf(emptyList<SendOperation>())
        override suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId {
            check(SmsAccessSnapshot(platform.isHeld(), false,
                ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED).canDispatch) { "Brak roli SMS lub zgody na wysyłanie." }
            error("Transport wiadomości nie jest jeszcze dostępny.")
        }
        override suspend fun cancel(id: OperationId) = false
        override suspend fun retry(id: OperationId) = false
    }
    val schedules = object : ScheduleRepository {
        override fun scheduled() = flowOf(emptyList<ScheduledItem>())
        override fun lastSent() = flowOf<LastScheduledSent?>(null)
        override suspend fun schedule(draft: Draft, instant: Instant, local: java.time.LocalDateTime, zone: ZoneId, policy: LatenessPolicy): ScheduleId = error("Harmonogram nie jest jeszcze dostępny.")
        override suspend fun sendNow(id: ScheduleId, sim: SubscriptionId?) = false
        override suspend fun reschedule(id: ScheduleId, instant: Instant, local: java.time.LocalDateTime, zone: ZoneId) = false
        override suspend fun delete(id: ScheduleId) = false
    }
    val sims = object : SimRepository {
        private val cards = platform.changes.map {
            if (!platform.allowed(AccessFeature.Sims)) emptyList() else try {
                app.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                    .map { SimCard(SubscriptionId(it.subscriptionId), it.simSlotIndex, it.displayName?.toString().orEmpty()) }
            } catch (_: SecurityException) { emptyList() }
        }.flowOn(Dispatchers.IO)
        override fun cards(): Flow<List<SimCard>> = cards
    }
    val contacts = object : ContactsDirectory {
        private fun read(): List<Participant> {
            if (!platform.allowed(AccessFeature.Contacts)) return emptyList()
            return try {
                val rows = mutableListOf<Participant>()
                app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { cursor ->
                    while (cursor.moveToNext() && rows.size < 100) {
                        val address = Address(cursor.getString(0).orEmpty())
                        if (isOutgoingNumber(address.raw)) rows += Participant(address, cursor.getString(1))
                    }
                }
                rows.distinctBy { it.address.normalized }
            } catch (_: SecurityException) { emptyList() }
        }
        override fun suggestions() = platform.changes.map { read() }.flowOn(Dispatchers.IO)
        override suspend fun lookup(address: Address): Participant? = withContext(Dispatchers.IO) { read().firstOrNull { it.address.normalized == address.normalized } }
    }
    return object : AppContainer {
        override val conversations = conversations
        override val drafts = drafts
        override val sims = sims
        override val contacts = contacts
        override val sendQueue = queue
        override val schedules = schedules
        override val search = object : SearchRepository { override fun search(query: SearchQuery) = flowOf(SearchResults()) }
        override val clock = clock
        override val smsRole = platform
        override val platform = platform
        override val exactAlarms = object : ExactAlarmGate {
            override fun canScheduleExact() = app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            override fun openSettings() { app.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        override val schedulePreset: SchedulePreset? = null
        override val appearance = prefs.appearance
        override val sending = prefs.sending
        override val organization = organization
        override val housekeeping = flowOf(Housekeeping(0, 0, 30))
        override fun avatarTone(id: ConversationId): String? = null
        override fun externalDraft(id: String): ExternalCompose? = platform.externalDraft(id)
        override val referenceFrame = false
        override val isDemo = false
        override val canSendMessages = false
    }
}
