package app.chatra.telephony

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.work.*
import app.chatra.ChatraApplication
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.database.InboundReceipt
import app.chatra.di.FullTelephonyContainer
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

class IncomingSms(private val context: Context, private val db: ChatraDatabase, private val platform: SystemPlatform, private val history: SystemHistory,
    private val directory: File = File(context.filesDir, "pending-deliveries")) {
    private val mutex = Mutex()
    private val resolver = context.contentResolver
    private fun allowed() = context.smsRead(platform) && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun process(key: String): Boolean = mutex.withLock {
        if (!key.matches(Regex("[a-f0-9]{64}")) || !allowed()) return@withLock false
        val file = File(directory, "$key.json")
        if (!file.exists()) return@withLock true
        val json = JSONObject(file.readText())
        if (json.getString("kind") != "sms") return@withLock true // MMS zachowane do transportu CH032.
        val dao = db.dao()
        var receipt = dao.receipt(key)
        if (receipt?.state == "done") { file.delete(); return@withLock true }
        if (receipt?.state == "ambiguous") return@withLock false
        val pdus = json.getJSONArray("pdus")
        val intent = Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION).putExtra("format", json.optString("format", "3gpp"))
            .putExtra("pdus", (0 until pdus.length()).map { Base64.decode(pdus.getString(it), Base64.NO_WRAP) }.toTypedArray())
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList().orEmpty()
        require(messages.isNotEmpty()) { "Brak części SMS." }
        val sender = messages.first().originatingAddress ?: error("Brak nadawcy SMS.")
        require(messages.all { it.originatingAddress == sender }) { "Różni nadawcy części SMS." }
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val sentAt = messages.first().timestampMillis
        val subscription = json.optInt("subscription", -1)
        if (receipt == null) {
            val baseline = resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), null, null, "_id DESC")?.use { c -> if (c.moveToFirst()) c.long("_id") else 0 } ?: error("Provider niedostępny.")
            receipt = InboundReceipt(key, "writing", baseline)
            dao.receipt(receipt)
        }
        // Po awarii pomiędzy providerem a Room szukamy wyłącznie nowych, dokładnie pasujących rekordów.
        val candidates = mutableListOf<Long>()
        resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id"), "_id>? AND address=? AND body=? AND date_sent=? AND sub_id=?",
            arrayOf(receipt.baselineId.toString(), sender, body, sentAt.toString(), subscription.toString()), null)?.use { c -> while (c.moveToNext()) candidates += c.long("_id") } ?: error("Provider niedostępny.")
        if (candidates.size > 1) { dao.receipt(receipt.copy(state = "ambiguous")); return@withLock false }
        val providerId = candidates.singleOrNull() ?: run {
            check(allowed())
            val thread = Telephony.Threads.getOrCreateThreadId(context, sender)
            val values = ContentValues().apply {
                put("address", sender); put("body", body); put("date", json.optLong("receivedAt", System.currentTimeMillis()))
                put("date_sent", sentAt); put("sub_id", subscription); put("thread_id", thread)
                put("read", 0); put("seen", 0); put("service_center", messages.first().serviceCenterAddress)
            }
            check(allowed())
            resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: error("Nie udało się zapisać SMS.")
        }
        dao.receipt(receipt.copy(state = "done", providerId = providerId))
        file.delete()
        history.refresh()
        true
    }
    fun recover() { directory.listFiles().orEmpty().filter { it.extension == "json" }.forEach { schedule(context, it.nameWithoutExtension) } }
    companion object {
        fun schedule(context: Context, key: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("sms-inbound:$key", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IncomingSmsWorker>().setInputData(workDataOf("key" to key)).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
        }
    }
}

class IncomingSmsWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val container = (applicationContext as ChatraApplication).container(false) as FullTelephonyContainer
        if (container.incomingSms.process(inputData.getString("key") ?: return Result.failure())) Result.success() else Result.failure()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: SecurityException) { Result.failure() }
    catch (_: IllegalArgumentException) { Result.failure() }
    catch (_: org.json.JSONException) { Result.failure() }
    catch (_: Exception) { Result.retry() }
}
