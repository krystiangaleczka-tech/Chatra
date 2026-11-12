package app.chatra.platform

import android.content.Intent
import app.chatra.core.model.ConversationId
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

/** Adapter platformy do cyklu życia, implementowany wyłącznie przez wariant full. */
interface ActivityPlatform : PlatformAccess {
    fun bindActivity(activity: ComponentActivity)
    fun acceptIntent(intent: Intent): String?
    fun pendingComposeId(): String?
    val incomingDraftId: StateFlow<String?>
    /** Wątek z wewnętrznego PendingIntent powiadomienia, bez numeru i treści SMS. */
    val incomingScheduled: StateFlow<Boolean>
    fun acknowledgeScheduled()
    val incomingConversationId: StateFlow<ConversationId?>
    fun acknowledgeConversation(id: ConversationId)
    fun acknowledgeCompose(id: String)
}
