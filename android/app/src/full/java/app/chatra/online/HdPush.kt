package app.chatra.online

import android.content.Context
import androidx.work.*
import app.chatra.BuildConfig
import app.chatra.ChatraApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.TimeUnit

internal object HdPush {
    suspend fun token(context: Context): String? {
        if (BuildConfig.FIREBASE_APIKEY.isEmpty()) return null
        synchronized(this) {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context,
                FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_APIKEY)
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATIONID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDERID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECTID).build())
        }
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
    fun signal(context: Context) {
        if (!BuildConfig.HD_PILOT) return
        WorkManager.getInstance(context).enqueueUniqueWork("hd-mailbox-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<HdSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}

/** Payload is only a wake-up hint. It never contains a number, URI or receipt. */
class HdPushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) { HdPush.signal(this) }
    override fun onNewToken(token: String) { HdPush.signal(this) }
}

class HdSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val hd = (applicationContext as ChatraApplication).container(false).hd ?: return Result.success()
        return try { hd.sync(); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
