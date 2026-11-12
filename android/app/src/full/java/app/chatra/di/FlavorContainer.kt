package app.chatra.di

import android.Manifest
import app.chatra.local.*
import app.chatra.mms.MmsEngine
import app.chatra.scheduling.*
import app.chatra.search.*
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
import app.chatra.local.SystemContacts
import app.chatra.core.database.ChatraDatabase
import app.chatra.prefs.DataStorePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId

interface FullTelephonyContainer : AppContainer {
    val persistentSchedules: PersistentSchedules
    val incomingSms: IncomingSms
    val mms: MmsEngine
    val smsQueue: PersistentSmsQueue
}

/** Systemowa historia, projekcja Room i trwała kolejka SMS; bez fixture. */
fun createContainer(app: Application, referenceFrame: Boolean, roleScenario: String? = null, exactAlarms: Boolean = true): AppContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val platform = SystemPlatform(app)
    val notificationPrefs = LocalNotificationPrefs(app)
    val prefs = DataStorePreferences(app, scope, "full")
    val organization = MemoryOrganizationPreview()
    val clock = object : Clock {
        override fun now(): Instant = Instant.now()
        override fun zone(): ZoneId = ZoneId.systemDefault()
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    }
    val contacts = SystemContacts(app, platform, scope)
    val db = ChatraDatabase.open(app)
    val sims = SystemSim(app, platform, scope)
    val conversations = SystemHistory(app, db, platform, contacts, scope)
    val drafts = RoomDrafts(db)
    val smsText = PlatformSmsText(app)
    val mediaStore = PrivateMediaStore(app)
    val queue = PersistentSmsQueue(app, db, platform, conversations, sims, smsText, drafts)
    val mmsEngine = MmsEngine(app, db, mediaStore, conversations, platform)
    queue.mms = mmsEngine
    val schedules = PersistentSchedules(db, queue, drafts, clock, PlatformScheduleAlarms(app), PlatformScheduleConditions(app, platform), PlatformScheduleNotice(app))
    queue.dispatchAllowed = schedules::permit
    val search = SystemSearch(db, ProviderSearchSource(app, db, conversations, contacts, platform), scope)
    val incoming = IncomingSms(app, db, platform, conversations)
    MessageNotifications(app, db, platform, scope, notificationPrefs, sims)
    scope.launch(Dispatchers.IO) {
        ScheduleRecoveryWorker.periodic(app)
        schedules.reconcile("startup")
        mmsEngine.recoverDownloads()
        queue.awaitReady()
        val protected = db.dao().drafts().first().flatMap { DraftCodec.decode(it).attachmentIds } + db.dao().operations().first().flatMap { row -> row.snapshot?.let { DraftCodec.decode(it).attachmentIds }.orEmpty() }
        mediaStore.collectUnused(protected.toSet(), System.currentTimeMillis() - 86_400_000)
        combine(platform.changes, sims.cards()) { _, _ -> Unit }.collect { incoming.recover(); queue.recover(false); schedules.reconcile() }
    }
    return object : FullTelephonyContainer {
        override val persistentSchedules = schedules
        override val gallery = ProviderMediaGallery(app, db, platform)
        override val mmsDownloads = mmsEngine
        override val mms = mmsEngine
        override val mmsAvailable = true
        override val incomingSms = incoming
        override val smsQueue = queue
        override val smsText = smsText
        override val templates = LocalTemplates(app)
        override val media = mediaStore
        override val notifications = notificationPrefs
        override val conversations = conversations
        override val draftWriteScope = scope
        override val drafts = drafts
        override val sims = sims
        override val contacts = contacts
        override val sendQueue = queue
        override val schedules = schedules
        override val search = search
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
