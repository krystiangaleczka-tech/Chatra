package app.chatra.mms

import android.content.*
import androidx.work.*
import app.chatra.ChatraApplication
import app.chatra.di.FullTelephonyContainer
import kotlinx.coroutines.*

class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.chatra.MMS_RESULT") return
        val result = resultCode; val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ((context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer).mms.result(intent, result) }
            catch (_: Exception) { /* Durable journal is retained for recovery. */ }
            finally { pending.finish() }
        }
    }
}
class MmsDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString("key") ?: return Result.failure()
        return try {
            ((applicationContext as ChatraApplication).container(false) as FullTelephonyContainer).mms.processSpool(key)
            Result.success()
        } catch (_: Exception) { Result.failure() }
    }
    companion object {
        fun schedule(context: Context, key: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("mms-download:$key", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MmsDownloadWorker>().setInputData(workDataOf("key" to key)).build())
        }
    }
}
