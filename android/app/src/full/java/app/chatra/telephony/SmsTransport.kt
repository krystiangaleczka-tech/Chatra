package app.chatra.telephony

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.work.*
import app.chatra.core.model.SubscriptionId
import java.util.concurrent.TimeUnit

fun interface SmsTransport {
    fun send(subscription: SubscriptionId, address: String, parts: ArrayList<String>, sent: ArrayList<PendingIntent>, delivered: ArrayList<PendingIntent>)
}
class NativeSmsTransport(private val text: PlatformSmsText) : SmsTransport {
    /** Kolejka sprawdza rolę, SEND_SMS i aktualną subskrypcję bezpośrednio przed wywołaniem. */
    @SuppressLint("MissingPermission")
    override fun send(subscription: SubscriptionId, address: String, parts: ArrayList<String>, sent: ArrayList<PendingIntent>, delivered: ArrayList<PendingIntent>) {
        if (parts.size == 1) text.manager(subscription).sendTextMessage(address, null, parts.single(), sent.single(), delivered.single())
        else text.manager(subscription).sendMultipartTextMessage(address, null, parts, sent, delivered)
    }
}
interface SmsWork {
    fun schedule(id: String, deadline: Long)
    fun cancel(id: String)
}
class PlatformSmsWork(private val context: Context) : SmsWork {
    override fun schedule(id: String, deadline: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork("sms-send:$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SmsDispatchWorker>().setInputData(workDataOf("operation" to id))
                .setInitialDelay((deadline - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
    }
    override fun cancel(id: String) { WorkManager.getInstance(context).cancelUniqueWork("sms-send:$id") }
}
