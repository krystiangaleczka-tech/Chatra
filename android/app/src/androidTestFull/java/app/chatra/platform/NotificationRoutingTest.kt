package app.chatra.platform

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.MainActivity
import app.chatra.core.model.ConversationId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRoutingTest {
    @Test fun notificationOpensOnlyValidatedLocalConversationId() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val platform = SystemPlatform(app)
        val id = ConversationId("b9e503fe-6644-49d0-a342-993431a9e2f6")
        val valid = Intent(app, MainActivity::class.java).apply {
            action = SystemPlatform.ACTION_OPEN_CONVERSATION
            putExtra(SystemPlatform.EXTRA_CONVERSATION_ID, id.value)
        }
        assertNull(platform.acceptIntent(valid))
        assertEquals(id, platform.incomingConversationId.value)
        platform.acknowledgeConversation(id)
        assertNull(platform.incomingConversationId.value)

        val malicious = Intent(app, MainActivity::class.java).apply {
            action = SystemPlatform.ACTION_OPEN_CONVERSATION
            putExtra(SystemPlatform.EXTRA_CONVERSATION_ID, "../not-a-conversation")
        }
        platform.acceptIntent(malicious)
        assertNull(platform.incomingConversationId.value)

        val external = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("smsto:+48600100200")
            putExtra("sms_body", "Nie wysyłaj automatycznie")
        }
        assertNotNull(platform.acceptIntent(external))
        assertNull(platform.incomingConversationId.value)
    }
}
