package app.chatra.scheduling

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.ChatraApplication
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.time.*

/** Run explicitly against a disposable AVD. 5558 is an emulator port, never a physical recipient. */
class NativeScheduleAlarmTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("schedule-native-qa",Context.MODE_PRIVATE)
    private fun container()=(context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
    @android.annotation.SuppressLint("MissingPermission")
    private fun draft(body:String):Draft {
        assumeTrue(Build.HARDWARE in listOf("ranchu","goldfish"))
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        val sim=context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().first()
        return Draft(DraftId.new(),null,listOf(Address("5558")),body,RecipientMode.Single,emptyList(),SubscriptionId(sim.subscriptionId),Instant.now())
    }
    @Test fun exactWakeUsesSharedQueueOnceWithNoVisibleActivity()=runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeAlarm")=="true")
        val c=container();assumeTrue(c.exactAlarms.canScheduleExact());assumeTrue(c.smsRole.isHeld())
        val at=Instant.now().plusSeconds(65);val zone=ZoneId.systemDefault()
        val id=c.schedules.schedule(draft("Scheduled AVD QA"),at,at.atZone(zone).toLocalDateTime(),zone,LatenessPolicy.Ask)
        val db=ChatraDatabase.open(context)
        try {
            val row=db.schedules().row(id.value)!!
            android.util.Log.i("ChatraScheduleQA","READY:${row.id}:${row.instant}")
            val result=withTimeout(110000){c.sendQueue.operations().first { list->list.any { it.id.value==row.operationId && it.state in setOf(SendState.SENT,SendState.DELIVERED,SendState.FAILED,SendState.DISPATCH_UNCERTAIN) } }.single { it.id.value==row.operationId }}
            val attempts=db.dao().attempts(row.operationId)
            assertEquals(1,attempts.size)
            assertFalse(result.createdAt.isBefore(at))
            assertTrue(result.state in setOf(SendState.SENT,SendState.DELIVERED))
            android.util.Log.i("ChatraScheduleQA","FINISHED:${result.state}:attempts=${attempts.size}")
        } finally {db.close()}
    }
    @Test fun prepareFutureScheduleForReboot()=runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rebootPrepare")=="true")
        val c=container();assumeTrue(c.exactAlarms.canScheduleExact())
        val at=Instant.now().plusSeconds(600);val zone=ZoneId.systemDefault()
        val id=c.schedules.schedule(draft("Reboot AVD QA"),at,at.atZone(zone).toLocalDateTime(),zone,LatenessPolicy.Ask)
        val db=ChatraDatabase.open(context)
        try {
            val row=db.schedules().row(id.value)!!
            prefs.edit().putString("id",id.value).putInt("boot",row.boot).putLong("instant",row.instant).commit()
            assertTrue(row.registered)
        } finally {db.close()}
    }
    @Test fun inspectBootReceiverBeforeOpeningContainerAndCancel()=runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rebootInspect")=="true")
        val id=prefs.getString("id",null)!!;val db=ChatraDatabase.open(context)
        try {
            val boot=android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
            withTimeout(45000) {while(db.schedules().row(id)?.boot!=boot)delay(100)}
            val row=db.schedules().row(id)!!
            assertNotEquals(prefs.getInt("boot",-1),row.boot);assertTrue(row.registered);assertEquals(prefs.getLong("instant",-1),row.instant)
            assertTrue(db.dao().attempts(row.operationId).isEmpty())
            assertTrue(container().schedules.delete(ScheduleId(id),row.version))
        } finally {db.close()}
    }
}
