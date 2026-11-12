package app.chatra.scheduling

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.chatra.ChatraApplication
import app.chatra.MainActivity
import app.chatra.core.database.ScheduledRow
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class PlatformScheduleAlarms(private val context: Context) : ScheduleAlarms {
    private val manager = context.getSystemService(AlarmManager::class.java)
    override fun boot(): Int = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    override fun available() = manager.canScheduleExactAlarms()
    private fun pending(row: ScheduledRow, create: Boolean): PendingIntent? {
        val intent = Intent(context, ScheduleDueReceiver::class.java).setAction("app.chatra.SCHEDULE_DUE")
            .setData(Uri.Builder().scheme("chatra-schedule").authority("due").appendPath(row.id).appendPath(row.version.toString()).appendPath(row.token).build())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
    }
    override fun register(row: ScheduledRow) {
        check(manager.canScheduleExactAlarms()) { "Brak dostępu do dokładnych alarmów." }
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, row.instant, requireNotNull(pending(row, true)))
    }
    override fun cancel(row: ScheduledRow) { pending(row, false)?.let { manager.cancel(it); it.cancel() } }
}
class PlatformScheduleConditions(private val context: Context, private val role: SmsRoleGate) : ScheduleConditions {
    override fun problem(sim: SubscriptionId): String? {
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return "Odblokuj telefon, aby sprawdzić zaplanowaną wiadomość."
        if (!role.isHeld()) return "Chatra nie ma roli domyślnej aplikacji SMS."
        if (listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE).any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) return "Sprawdź dostęp do SMS i kart SIM."
        return try {
            // Check explicitly at the access point as permissions can be revoked while the app runs.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return "Brak dostępu do SIM."
            if (context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().any { it.subscriptionId == sim.value }) null else "Zapisana karta SIM jest niedostępna. Wybierz ją świadomie ponownie."
        } catch (_: SecurityException) { "Cofnięto dostęp do SIM." }
    }
}
class PlatformScheduleNotice(private val context: Context) : ScheduleNotice {
    override fun cancel(id: String) { context.getSystemService(NotificationManager::class.java).cancel("schedule:$id", 20) }
    override fun show(row: ScheduledRow, message: String) {
        val intent = Intent(context, MainActivity::class.java).setAction(SystemPlatform.ACTION_OPEN_SCHEDULED)
            .setData(Uri.parse("chatra-schedule://list/${row.id}"))
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            context.getSystemService(NotificationManager::class.java).notify("schedule:${row.id}", 20,
                NotificationCompat.Builder(context, "scheduled").setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .setContentTitle("Zaplanowana wiadomość wymaga decyzji").setContentText("Otwórz Zaplanowane i sprawdź warunki wysyłania.")
                    .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_SECRET).build())
        } catch (_: SecurityException) { /* The in-app decision survives denied POST_NOTIFICATIONS. */ }
    }
}

class ScheduleDueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        if (intent.action != "app.chatra.SCHEDULE_DUE" || uri.scheme != "chatra-schedule" || uri.host != "due") return
        val path = uri.pathSegments
        if (path.size != 3 || runCatching { java.util.UUID.fromString(path[0]); java.util.UUID.fromString(path[2]) }.isFailure) return
        val version = path[1].toIntOrNull()?.takeIf { it >= 0 } ?: return
        WorkManager.getInstance(context).enqueueUniqueWork("schedule-due:${path[0]}:$version", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScheduleDueWorker>().setInputData(workDataOf("id" to path[0], "version" to version, "token" to path[2]))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
    }
}
class ScheduleDueWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        val container = (applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
        return try { container.persistentSchedules.fire(ScheduleId(id), inputData.getInt("version", -1), token); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.failure() }
    }
}
class ScheduleRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return Result.retry()
        return try {
            val container = (applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
            container.persistentSchedules.reconcile(inputData.getString("reason") ?: "recovery")
            container.localOrganization.expire()
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun request(context: Context, reason: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("schedule-recovery", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ScheduleRecoveryWorker>().setInputData(workDataOf("reason" to reason)).build())
        }
        fun periodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("schedule-repair", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScheduleRecoveryWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
/** Credential-encrypted message data is never opened before the first user unlock. */
class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || !context.getSystemService(UserManager::class.java).isUserUnlocked) {
            context.createDeviceProtectedStorageContext().getSharedPreferences("schedule-recovery", Context.MODE_PRIVATE).edit().putBoolean("unlock-needed", true).apply()
            return
        }
        context.createDeviceProtectedStorageContext().getSharedPreferences("schedule-recovery", Context.MODE_PRIVATE).edit().remove("unlock-needed").apply()
        ScheduleRecoveryWorker.request(context, intent.action.orEmpty())
    }
}
