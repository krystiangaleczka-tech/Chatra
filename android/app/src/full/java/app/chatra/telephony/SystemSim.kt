package app.chatra.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import app.chatra.core.model.*
import app.chatra.platform.AccessFeature
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class SystemSim(context: Context, platform: SystemPlatform, scope: CoroutineScope) : SimRepository {
    private val manager = context.getSystemService(SubscriptionManager::class.java)
    private val changes = callbackFlow {
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() { override fun onSubscriptionsChanged() { trySend(Unit) } }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { trySend(Unit) } }
        val registered = runCatching { manager.addOnSubscriptionsChangedListener(context.mainExecutor, listener) }.isSuccess
        // Telefonia może nadawać z uprzywilejowanego UID innego niż system. Extras są ignorowane.
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED"), ContextCompat.RECEIVER_EXPORTED)
        trySend(Unit)
        awaitClose { if (registered) manager.removeOnSubscriptionsChangedListener(listener); context.unregisterReceiver(receiver) }
    }
    private val snapshot: StateFlow<Pair<List<SimCard>, SubscriptionId?>> = combine(changes, platform.changes) { _, _ ->
        if (!platform.allowed(AccessFeature.Sims) || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) Pair<List<SimCard>, SubscriptionId?>(emptyList(), null) else try {
            val cards = manager.activeSubscriptionInfoList.orEmpty().map { SimCard(SubscriptionId(it.subscriptionId), it.simSlotIndex, it.displayName?.toString().orEmpty()) }
            cards to SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { wanted -> cards.any { it.id.value == wanted } }?.let(::SubscriptionId)
        } catch (_: SecurityException) { emptyList<SimCard>() to null }
        catch (_: Exception) { emptyList<SimCard>() to null }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList<SimCard>() to null)
    override fun cards() = snapshot.map { it.first }.distinctUntilChanged()
    override fun defaultSubscription() = snapshot.map { it.second }.distinctUntilChanged()
}

class PlatformSmsText(private val context: Context) : SmsTextAnalyzer {
    fun manager(id: SubscriptionId?): SmsManager = context.getSystemService(SmsManager::class.java).let { if (id != null) it.createForSubscriptionId(id.value) else it }
    fun divide(text: String, id: SubscriptionId) = manager(id).divideMessage(text)
    override fun count(text: String, subscriptionId: SubscriptionId?): SmsCounter.Count {
        val values = SmsMessage.calculateLength(text, false)
        val parts = if (text.isEmpty()) emptyList() else manager(subscriptionId).divideMessage(text)
        return SmsCounter.Count(if (values[3] == SmsMessage.ENCODING_7BIT) SmsCounter.Encoding.Gsm7 else SmsCounter.Encoding.Ucs2,
            values[1], parts.size, if (values[0] > 0) (values[1] + values[2]) / values[0] else 160, text.codePointCount(0, text.length),
            parts.lastOrNull()?.let { SmsMessage.calculateLength(it, false)[1] }, parts.joinToString("") == text)
    }
}
