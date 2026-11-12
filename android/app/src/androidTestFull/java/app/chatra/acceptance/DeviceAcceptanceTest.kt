package app.chatra.acceptance

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.ChatraApplication
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import app.chatra.platform.SystemPlatform
import app.chatra.search.ProviderSearchSource
import app.chatra.search.SystemSearch
import app.chatra.telephony.SystemHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.*

/**
 * Physical-device acceptance. Every case is opt-in through an instrumentation argument and never
 * runs on an AVD. The recipient is supplied by the operator (`-e recipient ...`) and is never
 * stored in source or written to logs. Search runs read-only against the real provider with an
 * in-memory index, so the user's database and history are not modified.
 */
class DeviceAcceptanceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val prefs get() = context.getSharedPreferences("device-acceptance-qa", Context.MODE_PRIVATE)
    private fun container() = (context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
    private fun log(line: String) = Log.i("ChatraDeviceQA", line)
    private fun physical() = assumeTrue(Build.HARDWARE !in listOf("ranchu", "goldfish"))

    @android.annotation.SuppressLint("MissingPermission")
    private fun draft(body: String): Draft {
        physical()
        val recipient = args.getString("recipient"); assumeTrue(!recipient.isNullOrBlank())
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val sims = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
        val sim = args.getString("subscription")?.toInt()?.let { id -> sims.single { it.subscriptionId == id } } ?: sims.first()
        log("SIM:slot=${sim.simSlotIndex}:sub=${sim.subscriptionId}:embedded=${sim.isEmbedded}:active=${sims.size}")
        return Draft(DraftId.new(), null, listOf(Address(recipient!!)), body, RecipientMode.Single, emptyList(), SubscriptionId(sim.subscriptionId), Instant.now())
    }

    @Test fun realHistoryIndexAndQueryLatency() = runBlocking<Unit> {
        assumeTrue(args.getString("deviceSearch") == "true"); physical()
        val platform = SystemPlatform(context); assumeTrue(platform.isHeld())
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val contacts = object : ContactsDirectory {
                override fun suggestions() = flowOf(emptyList<Participant>())
                override suspend fun lookup(address: Address): Participant? = null
            }
            val history = SystemHistory(context, db, platform, contacts, scope)
            val engine = SystemSearch(db, ProviderSearchSource(context, db, history, contacts, platform), scope, autoIndex = false)
            val historyStart = SystemClock.elapsedRealtime()
            withTimeout(600_000) { history.historyStatus().first { it == HistoryStatus.Ready } }
            log("HISTORY_READY_MS=${SystemClock.elapsedRealtime() - historyStart}")
            val start = SystemClock.elapsedRealtime()
            withTimeout(600_000) { engine.rebuildNow() }
            log("INDEX_REAL_MS=${SystemClock.elapsedRealtime() - start}:DOCS=${db.search().count()}:ERROR=${engine.progress.value.error != null}")
            assertNull(engine.progress.value.error); assertTrue(db.search().count() > 0)
            val second = SystemClock.elapsedRealtime()
            withTimeout(600_000) { engine.rebuildNow() }
            log("REINDEX_UNCHANGED_MS=${SystemClock.elapsedRealtime() - second}")
            // Generic Polish fragments; only counts and timings are logged, never content.
            for (text in listOf("a", "kod", "pacz", "dzie", "zamow", "ł")) {
                val q = SearchQuery(text, pageSize = 40)
                val t = SystemClock.elapsedRealtime()
                val page = withTimeout(15_000) { engine.search(q).first { !it.indexing } }
                val firstMs = SystemClock.elapsedRealtime() - t
                var nextMs = -1L
                page.next?.let { cursor ->
                    val t2 = SystemClock.elapsedRealtime()
                    val next = withTimeout(15_000) { engine.search(q.copy(after = cursor)).first { !it.indexing } }
                    nextMs = SystemClock.elapsedRealtime() - t2
                    assertTrue(page.hits.map { it.message.ref }.intersect(next.hits.map { it.message.ref }.toSet()).isEmpty())
                }
                log("QUERY:len=${text.length}:hits=${page.hits.size}:quick=${page.quick.size}:firstMs=$firstMs:nextMs=$nextMs")
                assertTrue(firstMs < 1_000)
            }
            for (filter in SearchFilter.entries) {
                val t = SystemClock.elapsedRealtime()
                val page = withTimeout(15_000) { engine.search(SearchQuery("", filter, pageSize = 40)).first { !it.indexing } }
                log("FILTER:${filter.name}:hits=${page.hits.size}:quick=${page.quick.size}:ms=${SystemClock.elapsedRealtime() - t}")
            }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }

    @Test fun realScheduledSmsIsSentOnceAfterDeadline() = runBlocking<Unit> {
        assumeTrue(args.getString("deviceSchedule") == "true")
        val c = container(); assertTrue("exact alarm access", c.exactAlarms.canScheduleExact()); assertTrue("SMS role", c.smsRole.isHeld())
        val at = Instant.now().plusSeconds(70); val zone = ZoneId.systemDefault()
        val id = c.schedules.schedule(draft("Chatra: automatyczny test harmonogramu CH035 (${Build.MODEL})"), at, at.atZone(zone).toLocalDateTime(), zone, LatenessPolicy.Ask)
        val db = ChatraDatabase.open(context)
        try {
            val row = db.schedules().row(id.value)!!
            log("READY:${row.id}:${row.instant}:registered=${row.registered}")
            val result = withTimeout(180_000) { c.sendQueue.operations().first { list -> list.any { it.id.value == row.operationId && it.state in setOf(SendState.SENT, SendState.DELIVERED, SendState.FAILED, SendState.DISPATCH_UNCERTAIN) } }.single { it.id.value == row.operationId } }
            val attempts = db.dao().attempts(row.operationId)
            log("FINISHED:${result.state}:attempts=${attempts.size}:lateMs=${Duration.between(at, result.createdAt).toMillis()}")
            assertEquals(1, attempts.size); assertFalse(result.createdAt.isBefore(at))
            assertTrue(result.state in setOf(SendState.SENT, SendState.DELIVERED))
        } finally { db.close() }
    }

    /** Step 1 of force stop: schedule soon, then the operator runs `am force-stop` and waits past the deadline. */
    @Test fun prepareScheduleForForceStop() = runBlocking<Unit> {
        assumeTrue(args.getString("forceStopPrepare") == "true")
        val c = container(); assertTrue("exact alarm access", c.exactAlarms.canScheduleExact())
        val at = Instant.now().plusSeconds(90); val zone = ZoneId.systemDefault()
        val id = c.schedules.schedule(draft("Chatra: test force stop CH036 (nie powinien wyjść)"), at, at.atZone(zone).toLocalDateTime(), zone, LatenessPolicy.Ask)
        val db = ChatraDatabase.open(context)
        try {
            val row = db.schedules().row(id.value)!!; assertTrue(row.registered)
            prefs.edit().putString("forceStop", id.value).putLong("forceStopInstant", row.instant).commit()
            log("FORCE_STOP_READY:${row.id}:${row.instant}")
        } finally { db.close() }
    }

    /** Step 2: after force stop and the missed deadline nothing was sent; restart recovery asks instead of sending. */
    @Test fun inspectForceStopRecoveryRequiresDecision() = runBlocking<Unit> {
        assumeTrue(args.getString("forceStopInspect") == "true"); physical()
        val id = prefs.getString("forceStop", null)!!
        assertTrue(System.currentTimeMillis() > prefs.getLong("forceStopInstant", Long.MAX_VALUE))
        val db = ChatraDatabase.open(context)
        try {
            val before = db.schedules().row(id)!!
            assertTrue(db.dao().attempts(before.operationId).isEmpty())
            log("FORCE_STOP_BEFORE_RECOVERY:state=${db.dao().operation(before.operationId)!!.state}:attempts=0")
            val c = container()
            withTimeout(60_000) { while (db.dao().operation(before.operationId)!!.state != "MISSED_NEEDS_DECISION") delay(200) }
            delay(5_000)
            assertTrue(db.dao().attempts(before.operationId).isEmpty())
            log("FORCE_STOP_AFTER_RECOVERY:state=MISSED_NEEDS_DECISION:attempts=0")
            val row = db.schedules().row(id)!!
            assertTrue(c.schedules.delete(ScheduleId(id), row.version))
        } finally { db.close() }
    }

    /** Reboot step 1: a future schedule that must survive a secured (locked) boot without being sent. */
    @Test fun prepareScheduleForSecuredReboot() = runBlocking<Unit> {
        assumeTrue(args.getString("rebootPrepare") == "true")
        val c = container(); assertTrue("exact alarm access", c.exactAlarms.canScheduleExact())
        val at = Instant.now().plusSeconds(args.getString("delaySeconds")?.toLong() ?: 1_800); val zone = ZoneId.systemDefault()
        val id = c.schedules.schedule(draft("Chatra: test restartu CH036 (nie powinien wyjść)"), at, at.atZone(zone).toLocalDateTime(), zone, LatenessPolicy.Ask)
        val db = ChatraDatabase.open(context)
        try {
            val row = db.schedules().row(id.value)!!; assertTrue(row.registered)
            prefs.edit().putString("reboot", id.value).putInt("rebootBoot", row.boot).putLong("rebootInstant", row.instant).commit()
            log("REBOOT_READY:${row.id}:boot=${row.boot}:${row.instant}")
        } finally { db.close() }
    }

    /**
     * Reboot step 2 (after the user unlocked). A future deadline is re-registered for the new boot with the
     * same Instant; a deadline missed while the phone was rebooting/locked needs a decision (Ask). Never sent.
     */
    @Test fun inspectSecuredRebootRecovery() = runBlocking<Unit> {
        assumeTrue(args.getString("rebootInspect") == "true"); physical()
        val id = prefs.getString("reboot", null)!!; val db = ChatraDatabase.open(context)
        try {
            val boot = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
            assertNotEquals(prefs.getInt("rebootBoot", -1), boot)
            // Missed only if the deadline fell before this boot started (phone off); a deadline after boot belongs to the new boot.
            val bootStart = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val instant = prefs.getLong("rebootInstant", Long.MAX_VALUE)
            val missed = instant < bootStart
            val first = db.schedules().row(id)!!
            val attempts = db.dao().attempts(first.operationId)
            log("REBOOT_BEFORE_CONTAINER:missedWhileOff=$missed:deadlineAfterBootMs=${instant - bootStart}:rowBoot=${first.boot}:state=${db.dao().operation(first.operationId)!!.state}:attempts=${attempts.size}")
            if (!missed && attempts.isNotEmpty()) {
                // Re-registered alarm fired in the new boot: exactly once, not before the deadline.
                assertEquals(1, attempts.size); assertEquals(boot, first.boot)
                assertTrue(attempts.single().startedAt >= instant)
                assertTrue(db.dao().operation(first.operationId)!!.state in setOf("SENT", "DELIVERED"))
                log("REBOOT_FIRED_IN_NEW_BOOT:boot=$boot:lateMs=${attempts.single().startedAt - instant}:attempts=1:state=${db.dao().operation(first.operationId)!!.state}")
                return@runBlocking
            }
            assertTrue(attempts.isEmpty())
            if (missed) {
                val c = container()
                withTimeout(60_000) { while (db.dao().operation(first.operationId)!!.state != "MISSED_NEEDS_DECISION") delay(200) }
                delay(5_000)
                val row = db.schedules().row(id)!!
                assertTrue(db.dao().attempts(row.operationId).isEmpty())
                assertEquals(prefs.getLong("rebootInstant", -1), row.instant)
                log("REBOOT_MISSED_NEEDS_DECISION:boot=$boot:instantUnchanged=true:attempts=0")
                assertTrue(c.schedules.delete(ScheduleId(id), row.version))
            } else {
                withTimeout(60_000) { while (db.schedules().row(id)?.boot != boot) delay(100) }
                val row = db.schedules().row(id)!!
                assertTrue(row.registered); assertEquals(prefs.getLong("rebootInstant", -1), row.instant)
                assertTrue(db.dao().attempts(row.operationId).isEmpty())
                log("REBOOT_RECOVERED:boot=${row.boot}:registered=true:instantUnchanged=true:attempts=0")
                assertTrue(container().schedules.delete(ScheduleId(id), row.version))
            }
        } finally { db.close() }
    }
}
