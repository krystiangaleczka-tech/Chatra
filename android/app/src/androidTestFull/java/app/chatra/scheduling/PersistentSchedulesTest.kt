package app.chatra.scheduling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import app.chatra.telephony.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.time.*

class PersistentSchedulesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChatraDatabase
    private lateinit var history: SystemHistory
    private lateinit var queue: PersistentSmsQueue
    private lateinit var drafts: RoomDrafts
    private lateinit var repo: PersistentSchedules
    private lateinit var card: SimCard
    private lateinit var scope: CoroutineScope
    private var calls = 0
    private val sentBodies = mutableListOf<String>()
    private var blocked: String? = null
    private val clock = object : app.chatra.core.model.Clock {
        var instant = Instant.parse("2026-11-13T08:00:00Z")
        var timezone = ZoneId.of("Europe/Warsaw")
        override fun now() = instant
        override fun zone() = timezone
        override fun elapsedRealtimeMillis() = instant.toEpochMilli()
    }
    private val alarms = object : ScheduleAlarms {
        var permitted = true; var fail = false; var bootId = 1
        val entries = mutableMapOf<String, ScheduledRow>()
        override fun boot() = bootId
        override fun available() = permitted
        override fun register(row: ScheduledRow) { if (fail) error("Registration failed"); entries[row.id] = row }
        override fun cancel(row: ScheduledRow) { if (entries[row.id]?.token == row.token) entries.remove(row.id) }
    }
    @android.annotation.SuppressLint("MissingPermission")
    @Before fun prepare() {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val platform = SystemPlatform(context); assumeTrue(platform.isHeld())
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val info = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().first()
        card = SimCard(SubscriptionId(info.subscriptionId), info.simSlotIndex, "QA")
        db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val contacts = object : ContactsDirectory { override fun suggestions() = flowOf(emptyList<Participant>()); override suspend fun lookup(address: Address): Participant? = null }
        history = SystemHistory(context, db, platform, contacts, scope); drafts = RoomDrafts(db)
        val sims = object : SimRepository { override fun cards() = flowOf(listOf(card)) }
        val work = object : SmsWork { override fun schedule(id: String, deadline: Long) {}; override fun cancel(id: String) {} }
        val time = object : SmsQueueTime { override fun wall() = clock.now().toEpochMilli(); override fun elapsed() = clock.elapsedRealtimeMillis(); override fun boot() = alarms.bootId }
        queue = PersistentSmsQueue(context, db, platform, history, sims, PlatformSmsText(context), drafts, SmsTransport { _, _, parts, _, _ -> calls++; sentBodies += parts.joinToString("") }, work, time)
        repo = repository()
        queue.dispatchAllowed = { repo.permit(it) }
    }
    private fun repository() = PersistentSchedules(db, queue, drafts, clock, alarms, ScheduleConditions { blocked })
    @After fun cleanup() = runBlocking {
        if (!::db.isInitialized) return@runBlocking
        scope.coroutineContext[Job]!!.cancelAndJoin()
        db.dao().operations().first().forEach { op -> db.dao().attempts(op.id).mapNotNull { it.providerId }.forEach { context.contentResolver.delete(Uri.parse("content://sms/$it"), null, null) } }
        db.close()
    }
    private fun draft(body: String = "Schedule QA") = Draft(DraftId.new(), null, listOf(Address("+48555000535")), body, RecipientMode.Single, emptyList(), card.id, clock.now(), subscriptionLabel = card.label)
    private suspend fun plan(body: String = "Schedule QA", policy: LatenessPolicy = LatenessPolicy.Ask): ScheduledRow {
        val at = clock.now().plusSeconds(120)
        val id = repo.schedule(draft(body), at, at.atZone(clock.zone()).toLocalDateTime(), clock.zone(), policy)
        return db.schedules().row(id.value)!!
    }
    private suspend fun sent(op: String) {
        db.dao().attempts(op).forEach { attempt ->
            repeat(attempt.partCount) { part -> queue.callback(Intent("app.chatra.SMS_STATUS", Uri.parse("chatra-callback://sms/${attempt.id}/$part/sent/${attempt.callbackToken}")), android.app.Activity.RESULT_OK) }
        }
    }
    @Test fun sameDeadlineAndDuplicateAlarmHaveAtMostOneTransportPerOperation() = runBlocking {
        val one = plan("one"); val two = plan("two"); assertNotEquals(one.token, two.token)
        clock.instant = Instant.ofEpochMilli(one.instant)
        coroutineScope { (List(8) { async { repo.fire(ScheduleId(one.id), one.version, one.token) } } + async { repo.fire(ScheduleId(two.id), two.version, two.token) }).awaitAll() }
        assertEquals(2, calls); assertEquals(setOf("one", "two"), sentBodies.toSet())
        sent(one.operationId); sent(two.operationId)
        assertNotNull(repo.lastSent().first())
    }
    @Test fun savedSnapshotSurvivesAlarmFailureRestartAndPermissionRecovery() = runBlocking {
        alarms.fail = true; val original = draft("Trwały tekst")
        val at = clock.now().plusSeconds(120)
        val id = repo.schedule(original, at, at.atZone(clock.zone()).toLocalDateTime(), clock.zone(), LatenessPolicy.Ask)
        assertTrue(drafts.isClosed(original.id)); assertNull(drafts.find(original.id))
        assertNotNull(db.schedules().row(id.value)!!.error)
        assertEquals("Trwały tekst", db.dao().operation(db.schedules().row(id.value)!!.operationId)!!.body)
        alarms.fail = false; repo = repository(); repo.reconcile("startup")
        assertTrue(db.schedules().row(id.value)!!.registered); assertNull(db.schedules().row(id.value)!!.error)
        assertEquals(0, calls)
    }
    @Test fun recoveryDistinguishesAskFromHourWindowAt59_60_61Minutes() = runBlocking {
        for (minutes in listOf(59L, 60L, 61L)) {
            clock.instant = clock.instant.plusSeconds(2 * 86400)
            val ask = plan("ask $minutes"); val hour = plan("hour $minutes", LatenessPolicy.SendWithinHour)
            alarms.bootId++
            clock.instant = Instant.ofEpochMilli(ask.instant).plusSeconds(minutes * 60)
            val before = calls
            repo.reconcile("BOOT_COMPLETED")
            assertEquals("MISSED_NEEDS_DECISION", db.dao().operation(ask.operationId)!!.state)
            assertEquals(if (minutes <= 60) 1 else 0, calls - before)
            if (minutes <= 60) sent(hour.operationId) else assertEquals("MISSED_NEEDS_DECISION", db.dao().operation(hour.operationId)!!.state)
        }
    }
    @Test fun deviceRebootDoesNotAutoSendAskEvenInsideNormalAlarmTolerance() = runBlocking {
        val row = plan(); alarms.bootId++
        clock.instant = Instant.ofEpochMilli(row.instant).plusSeconds(1)
        repo.fire(ScheduleId(row.id), row.version, row.token) // Persisted work may beat boot reconciliation.
        repo.reconcile("BOOT_COMPLETED")
        assertEquals(0, calls); assertEquals("MISSED_NEEDS_DECISION", db.dao().operation(row.operationId)!!.state)
    }
    @Test fun deletingUncertainScheduleRetainsEvidenceAndAcceptsLateSuccess() = runBlocking {
        val row = plan(); clock.instant = Instant.ofEpochMilli(row.instant)
        repo.fire(ScheduleId(row.id), row.version, row.token)
        queue.interrupted(row.operationId)
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(row.operationId)!!.state)
        val current = db.schedules().row(row.id)!!
        assertTrue(repo.delete(ScheduleId(row.id), current.version))
        assertNull(db.schedules().row(row.id))
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(row.operationId)!!.state)
        sent(row.operationId)
        assertEquals("SENT", db.dao().operation(row.operationId)!!.state)
        assertEquals(1, calls)
    }
    @Test fun unavailableSimRevokedAlarmAndDelayedSendNowRequireDecision() = runBlocking {
        val row = plan(); blocked = "SIM missing"; clock.instant = Instant.ofEpochMilli(row.instant)
        repo.fire(ScheduleId(row.id), row.version, row.token); assertEquals(0, calls)
        blocked = null; repo.reconcile(); assertEquals(0, calls)
        assertTrue(repo.sendNow(ScheduleId(row.id), card.id, row.version))
        clock.instant = clock.instant.plusSeconds(121)
        queue.dispatch(row.operationId); assertEquals(0, calls)
        assertEquals("MISSED_NEEDS_DECISION", db.dao().operation(row.operationId)!!.state)
        val future = plan("revoked"); alarms.permitted = false; clock.instant = Instant.ofEpochMilli(future.instant)
        repo.fire(ScheduleId(future.id), future.version, future.token); assertEquals(0, calls)
    }
    @Test fun editInvalidatesOldAlarmAndStaleScreenCannotDeleteOrSendNewVersion() = runBlocking {
        val row = plan("old"); val op = db.dao().operation(row.operationId)!!
        val current = drafts.snapshot(op).copy(text = "new")
        val at = clock.now().plusSeconds(600)
        assertTrue(repo.edit(ScheduleId(row.id), row.version, current, at, at.atZone(clock.zone()).toLocalDateTime(), clock.zone(), LatenessPolicy.Ask))
        queue.dispatch(row.operationId) // A stale WorkManager wake must not mark the future edit missed.
        assertFalse(repo.permit(op))
        assertEquals("SCHEDULED", db.dao().operation(row.operationId)!!.state)
        clock.instant = Instant.ofEpochMilli(row.instant)
        repo.fire(ScheduleId(row.id), row.version, row.token)
        assertFalse(repo.delete(ScheduleId(row.id), row.version)); assertFalse(repo.sendNow(ScheduleId(row.id), card.id, row.version))
        assertEquals("new", db.dao().operation(row.operationId)!!.body); assertEquals(0, calls)
        val newRow = db.schedules().row(row.id)!!
        assertTrue(repo.delete(ScheduleId(row.id), newRow.version)); assertNull(db.schedules().row(row.id))
        assertNotNull(op.conversationId?.let { db.dao().conversation(it) })
    }
    @Test fun dstGapIsRejectedAndStoredInstantDoesNotMoveWithDeviceZone() = runBlocking {
        val gap = LocalDateTime.of(2027,3,28,2,30); val zone = ZoneId.of("Europe/Warsaw")
        val at = (resolveLocal(gap,zone) as LocalTimeResolution.Gap).suggested
        try { repo.schedule(draft(),at,gap,zone,LatenessPolicy.Ask); fail("DST gap accepted") } catch (_: IllegalArgumentException) {}
        assertTrue(db.schedules().rows().first().isEmpty())
        val row = plan(); clock.timezone = ZoneId.of("America/New_York"); repo.reconcile()
        val restored = db.schedules().row(row.id)!!
        assertEquals(row.instant,restored.instant); assertEquals("Europe/Warsaw",restored.zone)
    }
    @Test fun editAndDispatchRaceNeverChangesSubmittedSnapshotOrSendsTwice() = runBlocking {
        repeat(8) { iteration ->
            clock.instant = clock.instant.plusSeconds(86400)
            val row = plan("old $iteration"); clock.instant = Instant.ofEpochMilli(row.instant)
            val at = clock.now().plusSeconds(180)
            val updated = drafts.snapshot(db.dao().operation(row.operationId)!!).copy(text = "new $iteration")
            val before = calls
            val edited = coroutineScope {
                val edit = async(Dispatchers.IO) { repo.edit(ScheduleId(row.id), row.version, updated, at, at.atZone(clock.zone()).toLocalDateTime(), clock.zone(), LatenessPolicy.Ask) }
                val fire = async(Dispatchers.IO) { repo.fire(ScheduleId(row.id), row.version, row.token) }
                fire.await(); edit.await()
            }
            assertTrue(calls-before in 0..1)
            if (edited) { assertEquals(before,calls); assertEquals("new $iteration", db.dao().operation(row.operationId)!!.body) }
            else { assertEquals(before+1,calls); assertEquals("old $iteration",sentBodies.last()); sent(row.operationId) }
        }
    }
}
