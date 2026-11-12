package app.chatra.platform

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.app.RemoteInput
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.ChatraApplication
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class NotificationLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private lateinit var c: FullTelephonyContainer
    private val replyText = "QA notification reply ${java.util.UUID.randomUUID()}"
    private val testAddress = "+48555" + (100000 + (java.util.UUID.randomUUID().leastSignificantBits and Long.MAX_VALUE) % 900000).toString()
    private var row: Uri? = null
    private var oldPrefs: NotificationPrefs? = null
    private var oldSending: SendingPrefs? = null
    @Before fun prepare() {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assumeTrue(SystemPlatform(context).isHeld())
        c = (context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
        oldPrefs = c.notifications.value.value; oldSending = c.sending.value.value
    }
    @After fun cleanup() = runBlocking {
        if (!::c.isInitialized) return@runBlocking
        c.sendQueue.operations().first().filter { it.body == replyText && it.state == SendState.PENDING_UNDO }.forEach { c.sendQueue.cancel(it.id) }
        row?.let { context.contentResolver.delete(it, null, null) }
        oldPrefs?.let { old -> c.notifications.update { old } }; oldSending?.let { old -> c.sending.update { old } }
        c.conversations.refresh()
    }
    private suspend fun sendReply(pending: android.app.PendingIntent, fill: Intent) {
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            pending.send(context, 0, fill, android.app.PendingIntent.OnFinished { _, _, _, _, _ ->
                if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }
    private suspend fun waitUntil(test: () -> Boolean) { withTimeout(15000) { while (!test()) delay(25) } }
    private suspend fun incoming(): ConversationId {
        c.notifications.update { NotificationPrefs(previews = false) }
        val address = testAddress
        val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(address))
        row = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put("thread_id", thread); put("address", address); put("body", "PRIVATE QA BODY")
            put("date", System.currentTimeMillis()); put("read", 0); put("seen", 0); put("type", 1)
        })!!
        c.conversations.refresh()
        return withTimeout(15000) { c.conversations.inbox().first { list -> list.any { summary -> summary.participants.any { it.address.raw == address } } }.first { summary -> summary.participants.any { it.address.raw == address } }.id }
    }
    @Test fun publicAndHiddenPreviewsNeverContainBodyAndTimedMutePreservesUnreadCount() = runBlocking {
        val id = incoming(); val tag = "conversation:${id.value}"
        waitUntil { manager.activeNotifications.any { it.tag == tag } }
        val notification = manager.activeNotifications.single { it.tag == tag }.notification
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("PRIVATE"))
        assertFalse(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("PRIVATE"))
        c.notifications.update { it.copy(mutedUntil = mapOf(id.value to System.currentTimeMillis() + 2000)) }
        waitUntil { manager.activeNotifications.none { it.tag == tag } }
        assertTrue(c.conversations.conversation(id).first()!!.isUnread)
        waitUntil { manager.activeNotifications.any { it.tag == tag } }
        assertTrue(c.conversations.conversation(id).first()!!.isUnread)
    }
    @Test fun explicitSimQuickReplyUsesSharedQueueAndDuplicateActionCannotSendTwice() = runBlocking {
        val id = incoming(); val tag = "conversation:${id.value}"
        c.sending.update { it.copy(undoSeconds = 10) }
        waitUntil { manager.activeNotifications.any { it.tag == tag && !it.notification.actions.isNullOrEmpty() } }
        val notice = manager.activeNotifications.single { it.tag == tag }.notification
        val action = notice.actions.first { !it.remoteInputs.isNullOrEmpty() }
        assertTrue(action.isAuthenticationRequired)
        assertTrue(action.title.toString().contains("·"))
        val fill = Intent()
        RemoteInput.addResultsToIntent(arrayOf(RemoteInput.Builder("reply_text").build()), fill, android.os.Bundle().apply { putCharSequence("reply_text", replyText) })
        sendReply(action.actionIntent, fill)
        val operation = withTimeout(15000) { c.sendQueue.operations().first { rows -> rows.any { it.body == replyText } }.first { it.body == replyText } }
        assertEquals(SendState.PENDING_UNDO, operation.state)
        assertNotNull(operation.subscriptionId)
        assertTrue(c.sendQueue.cancel(operation.id))
        sendReply(action.actionIntent, fill)
        waitUntil { c.smsRole.isHeld() }
        assertEquals(1, c.sendQueue.operations().first().count { it.body == replyText })
    }
    @Test fun deniedNotificationsKeepHistoryAndSafeShareKeepsTextWithoutSending() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val id = incoming()
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            c.platform!!.refresh()
            waitUntil { manager.activeNotifications.none { it.tag == "conversation:${id.value}" } }
            assertTrue(c.conversations.conversation(id).first()!!.isUnread)
        }
        val platform = SystemPlatform(context)
        platform.pendingComposeId()?.let(platform::acknowledgeCompose)
        val before = c.sendQueue.operations().first().size
        platform.acceptIntent(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_TEXT, "Shared draft QA").putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///not-accessible")))
        assertNull(platform.incomingDraftId.value)
        platform.acceptIntent(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Shared draft QA"))
        val pending = withTimeout(5000) { platform.incomingDraftId.first { it != null } }!!
        val shared = platform.externalDraft(pending)!!
        assertEquals("Shared draft QA", shared.text); assertTrue(shared.recipients.isEmpty())
        assertEquals(before, c.sendQueue.operations().first().size)
        platform.acknowledgeCompose(pending)
    }
}
