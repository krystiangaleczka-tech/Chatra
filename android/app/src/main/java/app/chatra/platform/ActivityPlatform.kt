package app.chatra.platform

import android.content.Intent
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

/** Adapter platformy do cyklu życia, implementowany wyłącznie przez wariant full. */
interface ActivityPlatform : PlatformAccess {
    fun bindActivity(activity: ComponentActivity)
    fun acceptIntent(intent: Intent): String?
    fun pendingComposeId(): String?
    val incomingDraftId: StateFlow<String?>
    fun acknowledgeCompose(id: String)
}
