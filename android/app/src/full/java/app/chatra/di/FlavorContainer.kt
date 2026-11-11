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
import app.chatra.telephony.*
import app.chatra.core.database.ChatraDatabase
import app.chatra.prefs.DataStorePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId

interface FullTelephonyContainer : AppContainer {
    val incomingSms: IncomingSms
    val smsQueue: PersistentSmsQueue
}

/** Systemowa historia, projekcja Room i trwała kolejka SMS; bez fixture. */
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
    val schedules = object : ScheduleRepository {
        override fun scheduled() = flowOf(emptyList<ScheduledItem>())
        override fun lastSent() = flowOf<LastScheduledSent?>(null)
        override suspend fun schedule(draft: Draft, instant: Instant, local: java.time.LocalDateTime, zone: ZoneId, policy: LatenessPolicy): ScheduleId = error("Harmonogram nie jest jeszcze dostępny.")
        override suspend fun sendNow(id: ScheduleId, sim: SubscriptionId?) = false
        override suspend fun reschedule(id: ScheduleId, instant: Instant, local: java.time.LocalDateTime, zone: ZoneId) = false
        override suspend fun delete(id: ScheduleId) = false
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
    val db = ChatraDatabase.open(app)
    val sims = SystemSim(app, platform, scope)
    val conversations = SystemHistory(app, db, platform, contacts, scope)
    val drafts = RoomDrafts(db)
    val smsText = PlatformSmsText(app)
    val queue = PersistentSmsQueue(app, db, platform, conversations, sims, smsText, drafts)
    val incoming = IncomingSms(app, db, platform, conversations)
    MessageNotifications(app, db, platform, scope)
    scope.launch(Dispatchers.IO) {
        queue.awaitReady()
        platform.changes.collect { incoming.recover(); queue.recover(false) }
    }
    return object : FullTelephonyContainer {
        override val incomingSms = incoming
        override val smsQueue = queue
        override val smsText = smsText
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
        override val canSendMessages = true
        override val sendAccess = platform.changes.map { app.smsRead(platform) && app.smsSend(platform) }
    }
}
