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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.chatra.MainActivity
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.di.PreferenceStore
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

internal fun conversationIntent(context: Context, id: String): PendingIntent = PendingIntent.getActivity(context, 0,
    Intent(context, MainActivity::class.java).setAction(SystemPlatform.ACTION_OPEN_CONVERSATION)
        .setData(Uri.Builder().scheme("chatra").authority("conversation").appendPath(id).build())
        .putExtra(SystemPlatform.EXTRA_CONVERSATION_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Visibility changes, unread counts and timed mutes are reconciled even after a cold start. */
class MessageNotifications(private val context: Context, db: ChatraDatabase, private val platform: SystemPlatform,
    scope: CoroutineScope, private val prefs: PreferenceStore<NotificationPrefs>, private val sims: SimRepository) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val dao = db.dao()
    private var previous: Map<String, Pair<Int, Long?>>? = null
    private var operationStates: Map<String, String>? = null
    private val tickets = context.getSharedPreferences("reply-tickets", Context.MODE_PRIVATE)
    init {
        manager.createNotificationChannels(listOf(
            NotificationChannel("sms", "Wiadomości", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("send_errors", "Problemy z wysyłaniem", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("scheduled", "Zaplanowane wiadomości", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("hd", "Chatra HD", NotificationManager.IMPORTANCE_DEFAULT),
        ).onEach { it.lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val ticks = flow { while (true) { emit(System.currentTimeMillis()); delay(1000) } }
        scope.launch(Dispatchers.IO) {
            combine(dao.indices(), platform.changes, prefs.value, sims.cards(), ticks) { rows, _, settings, cards, now ->
                Snapshot(rows, settings, cards, now)
            }.distinctUntilChangedBy { s -> listOf(s.rows, s.prefs, s.cards, s.rows.map { s.prefs.muted(it.meta.id, s.now) }, permitted()) }
                .collect { update(it) }
        }
        scope.launch(Dispatchers.IO) {
            combine(dao.operations(), platform.changes, prefs.value) { operations, _, p -> operations to p }.collect { (ops, p) -> status(ops, p) }
        }
    }
    private data class Snapshot(val rows: List<ConversationIndex>, val prefs: NotificationPrefs, val cards: List<SimCard>, val now: Long)
    private fun permitted() = context.smsRead(platform) && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    private fun clean(prefix: String, active: Set<String>, id: Int) {
        manager.activeNotifications.mapNotNull { it.tag }.filter { it.startsWith(prefix) && it !in active }.forEach { manager.cancel(it, id) }
    }
    private fun publicNotice() = NotificationCompat.Builder(context, "sms").setSmallIcon(android.R.drawable.sym_action_chat)
        .setContentTitle("Chatra").setContentText("Nowe wiadomości").build()
    private fun replyAction(id: String, card: SimCard): NotificationCompat.Action {
        val key = "$id:${card.id.value}"
        val token = tickets.getString(key, null) ?: UUID.randomUUID().toString().also { tickets.edit().putString(key, it).commit() }
        val intent = Intent(context, QuickReplyReceiver::class.java).setAction(QuickReplyReceiver.ACTION)
            .setData(Uri.Builder().scheme("chatra-reply").authority("conversation").appendPath(id).appendPath(token).appendPath(card.id.value.toString()).build())
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return NotificationCompat.Action.Builder(0, "Odpowiedz · ${card.label}", pending)
            .addRemoteInput(RemoteInput.Builder(QuickReplyReceiver.TEXT).setLabel("Odpowiedź przez ${card.label}").build())
            .setAllowGeneratedReplies(false).setAuthenticationRequired(true).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build()
    }
    @SuppressLint("MissingPermission")
    private suspend fun update(s: Snapshot) {
        val visible = if (permitted()) s.rows.filter { !it.meta.trashed && (it.unreadCount > 0 || it.meta.manualUnread) } else emptyList()
        val audible = visible.filterNot { it.meta.muted || s.prefs.muted(it.meta.id, s.now) }
        val current = s.rows.associate { it.meta.id to (it.unreadCount to it.lastId) }
        val old = previous; previous = if (context.smsRead(platform)) current else null
        clean("conversation:", audible.map { "conversation:${it.meta.id}" }.toSet(), 10)
        if (visible.isEmpty()) { manager.cancel(1); ShortcutManagerCompat.removeAllDynamicShortcuts(context); return }
        try {
            val shortcuts = audible.take(3).map { item ->
                ShortcutInfoCompat.Builder(context, "conversation:${item.meta.id}").setShortLabel("Rozmowa")
                    .setLongLived(true).setIcon(IconCompat.createWithResource(context, android.R.drawable.sym_action_chat))
                    .setIntent(Intent(context, MainActivity::class.java).setAction(SystemPlatform.ACTION_OPEN_CONVERSATION)
                        .putExtra(SystemPlatform.EXTRA_CONVERSATION_ID, item.meta.id)).build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            for (item in audible) {
                val before = old?.get(item.meta.id)
                val fresh = old != null && item.unreadCount > (before?.first ?: 0) && item.lastId != before?.second
                val preview = if (s.prefs.previews && item.lastKind == "SMS" && item.lastId != null) runCatching {
                    context.contentResolver.query(Uri.withAppendedPath(android.provider.Telephony.Sms.CONTENT_URI, item.lastId.toString()), arrayOf("body"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0)?.take(200) else null }
                }.getOrNull() else null
                val notice = NotificationCompat.Builder(context, "sms").setSmallIcon(android.R.drawable.sym_action_chat)
                    .setContentTitle("Nowa wiadomość").setContentText(preview ?: "Otwórz rozmowę")
                    .setContentIntent(conversationIntent(context, item.meta.id)).setAutoCancel(true).setOnlyAlertOnce(!fresh).setSilent(!fresh)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicNotice()).setGroup("chatra.messages")
                if (shortcuts.any { it.id == "conversation:${item.meta.id}" }) notice.setShortcutId("conversation:${item.meta.id}")
                if (addresses(item.meta.participants).size == 1 && addresses(item.meta.participants).all { isOutgoingNumber(it.raw) }) s.cards.take(2).forEach { notice.addAction(replyAction(item.meta.id, it)) }
                manager.notify("conversation:${item.meta.id}", 10, notice.build())
            }
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(1, NotificationCompat.Builder(context, "sms").setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Chatra")
                .setContentText("Nieprzeczytane rozmowy: ${visible.size}").setNumber(visible.size).setContentIntent(open)
                .setGroup("chatra.messages").setGroupSummary(true).setSilent(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicNotice()).build())
        } catch (_: SecurityException) { clean("conversation:", emptySet(), 10); manager.cancel(1) }
    }
    @SuppressLint("MissingPermission")
    private fun status(rows: List<OutgoingOperation>, p: NotificationPrefs) {
        val old = operationStates; operationStates = rows.associate { it.id to it.state }
        val errors = if (permitted()) rows.filter { it.state in setOf("FAILED", "PARTIALLY_SENT", "DISPATCH_UNCERTAIN", "MISSED_NEEDS_DECISION") && !p.muted(it.conversationId.orEmpty(), System.currentTimeMillis()) } else emptyList()
        clean("operation:", errors.map { "operation:${it.id}" }.toSet(), 11)
        for (item in errors) {
            val channel = if (item.state == "MISSED_NEEDS_DECISION") "scheduled" else "send_errors"
            try { manager.notify("operation:${item.id}", 11, NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("Wiadomość wymaga sprawdzenia")
                .setContentText("Otwórz rozmowę i sprawdź status wysyłania.")
                .setContentIntent(item.conversationId?.let { conversationIntent(context, it) } ?: PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .setOnlyAlertOnce(true).setSilent(old == null || old[item.id] == item.state).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicNotice()).build())
            } catch (_: SecurityException) { }
        }
    }
}
