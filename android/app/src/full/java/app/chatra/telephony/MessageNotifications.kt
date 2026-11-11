package app.chatra.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.chatra.MainActivity
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.database.ConversationIndex
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * CH030: widoczne liczniki obejmują także wyciszone rozmowy; alerty tworzymy wyłącznie dla
 * niewyciszonych. Zablokowany ekran widzi jedynie neutralną wersję powiadomienia.
 */
class MessageNotifications(private val context: Context, db: ChatraDatabase, platform: SystemPlatform, scope: CoroutineScope) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val group = "chatra.messages"
    private val threadNotificationId = 10
    private var activeIds = emptySet<String>()
    private var lastState: Map<String, Pair<Int, Long?>>? = null

    init {
        manager.createNotificationChannels(listOf(
            NotificationChannel("sms", "Wiadomości SMS", NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel("send_errors", "Problemy z wysyłaniem", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("scheduled", "Zaplanowane wiadomości", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("hd", "Chatra HD", NotificationManager.IMPORTANCE_DEFAULT),
        ))
        scope.launch {
            combine(db.dao().indices(), platform.changes) { rows, _ -> rows to context.smsRead(platform) }
                .collect { (rows, permitted) -> update(rows, permitted) }
        }
    }

    private fun threadIntent(id: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = SystemPlatform.ACTION_OPEN_CONVERSATION
            data = Uri.Builder().scheme("chatra").authority("conversation").appendPath(id).build()
            putExtra(SystemPlatform.EXTRA_CONVERSATION_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Odrębny URI sprawia, że PendingIntent nie otworzy ostatnio zapisanej *innej* rozmowy.
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun update(rows: List<ConversationIndex>, permitted: Boolean) {
        val allowed = permitted && (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val visible = if (allowed) rows.filter { !it.meta.trashed && (it.unreadCount > 0 || it.meta.manualUnread) } else emptyList()
        val unmuted = visible.filterNot { it.meta.muted }
        val current = rows.associate { it.meta.id to (it.unreadCount to it.lastId) }
        val old = lastState
        lastState = if (permitted) current else null

        (activeIds - unmuted.map { it.meta.id }.toSet()).forEach { manager.cancel("conversation:$it", threadNotificationId) }
        activeIds = unmuted.map { it.meta.id }.toSet()

        if (visible.isEmpty()) {
            manager.cancel(1)
            return
        }
        val privacy = NotificationCompat.Builder(context, "sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Chatra")
            .setContentText("Nowe wiadomości")
            .build()
        try {
            for (item in unmuted) {
                val previous = old?.get(item.meta.id)
                val fresh = previous != null && item.unreadCount > previous.first && item.lastId != previous.second
                val notice = NotificationCompat.Builder(context, "sms")
                    .setSmallIcon(android.R.drawable.sym_action_chat)
                    .setContentTitle("Nowa wiadomość")
                    .setContentText("Otwórz rozmowę")
                    .setContentIntent(threadIntent(item.meta.id))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(!fresh)
                    .setSilent(!fresh)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(privacy)
                    .setGroup(group)
                    .build()
                manager.notify("conversation:${item.meta.id}", threadNotificationId, notice)
            }
            val open = PendingIntent.getActivity(context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(1, NotificationCompat.Builder(context, "sms")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle("Chatra")
                .setContentText("Nieprzeczytane rozmowy: ${visible.size}")
                .setNumber(visible.size)
                .setContentIntent(open)
                .setGroup(group)
                .setGroupSummary(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(privacy)
                .build())
        } catch (_: SecurityException) {
            activeIds.forEach { manager.cancel("conversation:$it", threadNotificationId) }
            activeIds = emptySet()
            manager.cancel(1)
        }
    }
}
