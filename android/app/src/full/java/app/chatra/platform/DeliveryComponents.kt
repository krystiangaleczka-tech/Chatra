package app.chatra.platform

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Telephony
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.ContextCompat
import app.chatra.ChatraApplication
import app.chatra.core.model.ObservableSmsRoleGate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import app.chatra.telephony.IncomingSms
import android.telephony.SubscriptionManager

/** CH021: trwała skrzynka wejściowych PDU. Składanie i projekcja do providera w CH023/CH032. */
private object DeliverySpool {
    val executor = Executors.newSingleThreadExecutor()
    fun allowed(context: Context, permission: String): Boolean = SystemPlatform(context).isHeld() && ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun save(context: Context, intent: Intent, mms: Boolean) {
        val permission = if (mms) Manifest.permission.RECEIVE_MMS else Manifest.permission.RECEIVE_SMS
        if (!allowed(context, permission)) return
        val pdus: List<ByteArray> = if (mms) listOfNotNull(intent.getByteArrayExtra("data")) else {
            @Suppress("DEPRECATION")
            (intent.extras?.get("pdus") as? Array<*>)?.mapNotNull { it as? ByteArray }.orEmpty()
        }
        if (pdus.isEmpty() || pdus.sumOf { it.size } > 1_048_576) return
        val json = JSONObject().put("kind", if (mms) "mms-notification" else "sms")
            .put("format", intent.getStringExtra("format") ?: "3gpp").put("subscription", intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", -1)))
            .put("pdus", JSONArray(pdus.map { Base64.encodeToString(it, Base64.NO_WRAP) }))
        val payload = json.toString().toByteArray()
        val key = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "pending-deliveries").apply { mkdirs() }
        val target = File(directory, "$key.json")
        if (!target.exists()) {
            val file = AtomicFile(target); val stream = file.startWrite()
            try { stream.write(json.put("receivedAt", System.currentTimeMillis()).toString().toByteArray()); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
        }
        if (!mms) IncomingSms.schedule(context, key)
    }
}

abstract class DeliveryReceiver(private val action: String, private val mms: Boolean) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != action || (mms && intent.type != "application/vnd.wap.mms-message")) return
        if (!DeliverySpool.allowed(context, if (mms) Manifest.permission.RECEIVE_MMS else Manifest.permission.RECEIVE_SMS)) return
        val pending = goAsync()
        DeliverySpool.executor.execute {
            try { DeliverySpool.save(context.applicationContext, intent, mms) }
            catch (_: Exception) { /* Odbiornik nie kończy procesu; nie potwierdzamy zapisu w UI. */ }
            finally { pending.finish() }
        }
    }
}
class DeliverReceiver : DeliveryReceiver(Telephony.Sms.Intents.SMS_DELIVER_ACTION, false)
class MmsDeliverReceiver : DeliveryReceiver(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION, true)

class RoleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED) {
            ((context.applicationContext as ChatraApplication).container(false).smsRole as? ObservableSmsRoleGate)?.refresh()
        }
    }
}

/** Brak transportu przed CH026: zachowuje odpowiedź jako szkic zamiast udawać wysyłkę. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && SystemPlatform(this).isHeld()) parseComposeIntent(intent)?.let { ExternalComposeStore(this).save(it) }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
