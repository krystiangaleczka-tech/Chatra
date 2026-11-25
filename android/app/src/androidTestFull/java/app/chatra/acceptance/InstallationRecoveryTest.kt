package app.chatra.acceptance

import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Telephony
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.ChatraApplication
import app.chatra.R
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.database.OutgoingOperation
import app.chatra.core.database.SavedDraft
import app.chatra.core.database.ScheduledRow
import app.chatra.core.database.TrashEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.ZoneOffset

class InstallationRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val sender = "+12025550161"
    private val marker = "Emulator QA CH061"

    @Test fun installedAppExcludesPrivateDomainsFromCloudAndDeviceTransfer() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val exclusions = mutableMapOf<String, MutableSet<String>>()
        context.resources.getXml(R.xml.data_extraction_rules).use { xml ->
            var section = ""
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "cloud-backup", "device-transfer" -> section = xml.name
                    "exclude" -> {
                        assertEquals(".", xml.getAttributeValue(null, "path"))
                        exclusions.getOrPut(section) { mutableSetOf() }.add(xml.getAttributeValue(null, "domain"))
                    }
                }
            }
        }
        val domains = setOf("file", "database", "sharedpref", "root", "external",
            "device_file", "device_database", "device_sharedpref", "device_root")
        assertEquals(mapOf("cloud-backup" to domains, "device-transfer" to domains), exclusions)
    }

    private fun requireResetRun() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("resetQa") == "true")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Test fun seedPrivateDataAndProvider() = runBlocking<Unit> {
        requireResetRun()
        val resolver = context.contentResolver
        resolver.delete(Telephony.Sms.CONTENT_URI, "address=? AND body=?", arrayOf(sender, marker))
        assertNotNull(resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put("address", sender)
            put("body", marker)
            put("date", System.currentTimeMillis())
            put("read", 1)
        }))
        val at = Instant.now().plusSeconds(3600)
        val db = ChatraDatabase.open(context)
        try {
            db.dao().saveDraft(SavedDraft("recovery-draft", null, "fixture"))
            db.dao().insertOperation(OutgoingOperation("recovery-op", "recovery-plan", null, "[\"5558\"]",
                "", "Single", -1, "QA", "SCHEDULED", 0, 0, 0))
            db.schedules().insert(ScheduledRow("recovery-schedule", "recovery-op", at.toEpochMilli(),
                at.atOffset(ZoneOffset.UTC).toLocalDateTime().toString(), "UTC", "Ask", 0, "recovery-token", "QA", 0))
            db.organization().insert(TrashEntry("recovery-trash", "recovery-thread", "QA", 0, Long.MAX_VALUE))
            assertNotNull(db.dao().savedDraft("recovery-draft"))
            assertNotNull(db.schedules().row("recovery-schedule"))
            assertNotNull(db.organization().entry("recovery-trash"))
        } finally { db.close() }
    }

    @Test fun apkReplacementPreservesLocalStateWithoutSending() = runBlocking<Unit> {
        requireResetRun()
        val expectedVersionCode = InstrumentationRegistry.getArguments().getString("expectedVersionCode")
        assumeTrue(expectedVersionCode != null)
        assertEquals(requireNotNull(expectedVersionCode).toLong(),
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        val platform = app.chatra.platform.SystemPlatform(context)
        assertTrue(platform.isHeld())
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id"), "address=? AND body=?",
            arrayOf(sender, marker), null)!!.use { assertEquals(1, it.count) }
        val db = ChatraDatabase.open(context)
        try {
            assertEquals("fixture", db.dao().savedDraft("recovery-draft")?.payload)
            assertEquals("recovery-op", db.schedules().row("recovery-schedule")?.operationId)
            assertEquals("SCHEDULED", db.dao().operation("recovery-op")?.state)
            assertEquals("recovery-thread", db.organization().entry("recovery-trash")?.conversationId)
            assertTrue(db.dao().attempts("recovery-op").isEmpty())
        } finally { db.close() }
    }

    @Test fun systemHistorySurvivesWithoutLocalOperations() = runBlocking<Unit> {
        requireResetRun()
        val resolver = context.contentResolver
        try {
            val providerId = resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id"), "address=? AND body=?",
                arrayOf(sender, marker), null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }
            val db = ChatraDatabase.open(context)
            try {
                assertTrue(db.dao().drafts().first().isEmpty())
                assertTrue(db.dao().operations().first().isEmpty())
                assertTrue(db.schedules().rows().first().isEmpty())
                assertTrue(db.organization().trash().first().isEmpty())
            } finally { db.close() }
            val history = (context.applicationContext as ChatraApplication).container(false).conversations
            withTimeout(20_000) {
                val conversation = history.inbox().first { rows ->
                    rows.any { row -> row.participants.any { it.address.raw == sender } }
                }.single { row -> row.participants.any { it.address.raw == sender } }
                val ref = app.chatra.core.model.MessageRef.Provider(
                    app.chatra.core.model.ProviderRef(app.chatra.core.model.ProviderTransport.SMS, providerId))
                val restored = history.messages(conversation.id).first { rows -> rows.any { it.ref == ref } }
                assertEquals(marker, restored.single { it.ref == ref }.body)
            }
        } finally {
            resolver.delete(Telephony.Sms.CONTENT_URI, "address=? AND body=?", arrayOf(sender, marker))
        }
    }
}
