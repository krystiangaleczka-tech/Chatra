package app.chatra.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.chatra.MainActivity
import app.chatra.core.database.ChatraDatabase
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MessageNotifications(private val context: Context, db: ChatraDatabase, platform: SystemPlatform, scope: CoroutineScope) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel("sms", "Wiadomości SMS", NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch { combine(db.dao().indices(), platform.changes) { rows, _ -> if (context.smsRead(platform)) rows.count { !it.meta.trashed && (it.unreadCount > 0 || it.meta.manualUnread) } else 0 }.collect { update(it) } }
    }
    @SuppressLint("MissingPermission")
    private fun update(count: Int) {
        if (count == 0 || android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { manager.cancel(1); return }
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try { manager.notify(1, NotificationCompat.Builder(context, "sms").setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Chatra").setContentText("Nieprzeczytane rozmowy: $count").setNumber(count)
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()) }
        catch (_: SecurityException) { manager.cancel(1) }
    }
}
