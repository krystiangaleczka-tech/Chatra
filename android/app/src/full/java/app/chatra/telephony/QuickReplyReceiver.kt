package app.chatra.telephony

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.chatra.ChatraApplication
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Only explicit, non-exported notification actions can enqueue; each action names its SIM. */
class QuickReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || context.getSystemService(KeyguardManager::class.java).isDeviceLocked) return
        val path = intent.data?.takeIf { it.scheme == "chatra-reply" && it.host == "conversation" }?.pathSegments ?: return
        if (path.size != 3 || runCatching { java.util.UUID.fromString(path[0]) }.isFailure) return
        val token = path[1]; val sim = path[2].toIntOrNull()?.let(::SubscriptionId) ?: return
        val saved = context.getSharedPreferences("reply-tickets", Context.MODE_PRIVATE)
        if (saved.getString("${path[0]}:${sim.value}", null) != token) return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(TEXT)?.toString() ?: return
        if (text.isBlank() || text.length > 16384 || '\u0000' in text) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
                val id = ConversationId(path[0])
                val summary = container.conversations.conversation(id).first() ?: return@launch
                if (summary.isGroupMms) return@launch // group replies are composed in the app with explicit transport.
                val recipients = summary.participants.map { it.address }
                if (recipients.isEmpty() || recipients.any { !isOutgoingNumber(it.raw) }) return@launch
                val card = container.sims.cards().first().firstOrNull { it.id == sim }
                val draft = Draft(DraftId("reply:$token"), id, recipients, text, RecipientMode.Single, emptyList(), sim, Instant.now(), subscriptionLabel = card?.label ?: "Wybrana SIM")
                // Save before enqueue: an unavailable role/card leaves a recoverable draft, never loses the reply.
                if (!container.drafts.isClosed(draft.id)) container.drafts.save(draft)
                container.sendQueue.enqueue(draft, container.sending.value.value.undoSeconds)
                container.conversations.markRead(id)
                saved.edit().remove("${path[0]}:${sim.value}").commit()
                context.getSystemService(NotificationManager::class.java).cancel("conversation:${id.value}", 10)
            } catch (_: Exception) {
                // Failed enqueues retain the durable draft. A neutral notification opens the composer.
                ReplyFailure.show(context, path[0])
            } finally { pending.finish() }
        }
    }
    companion object { const val ACTION = "app.chatra.REPLY"; const val TEXT = "reply_text" }
}

internal object ReplyFailure {
    fun show(context: Context, id: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        try { manager.notify("reply:$id", 12, androidx.core.app.NotificationCompat.Builder(context, "send_errors")
            .setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Odpowiedź pozostała w szkicu")
            .setContentText("Otwórz rozmowę i sprawdź SIM oraz dostęp do SMS.")
            .setContentIntent(conversationIntent(context, id)).setAutoCancel(true)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET).build())
        } catch (_: SecurityException) { }
    }
}
