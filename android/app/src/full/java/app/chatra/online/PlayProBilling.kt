package app.chatra.online

import android.app.Activity
import android.content.Context
import app.chatra.BuildConfig
import app.chatra.core.model.ProOffer
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

internal class PlayProBilling(context: Context, private val scope: CoroutineScope) : ProBilling {
    private val product = BuildConfig.PLAY_PRODUCT
    private val online = OnlineApi(context)
    private val observable = MutableStateFlow(ProState())
    override val state = observable.asStateFlow()
    private val lock = Mutex()
    private var details: ProductDetails? = null
    private var offerToken: String? = null
    private var offer: ProOffer = ProOffer.Unavailable
    private var buying = false
    private var connectedOnce = false

    private val client = BillingClient.newBuilder(context)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases -> scope.launch { onPurchase(result, purchases.orEmpty()) } }
        .build()

    override fun refresh() { scope.launch { synchronize() } }

    private suspend fun onPurchase(result: BillingResult, purchases: List<Purchase>) {
        buying = false
        when (result.responseCode) {
            BillingResponseCode.OK -> synchronize(purchases)
            BillingResponseCode.ITEM_ALREADY_OWNED -> synchronize()
            BillingResponseCode.USER_CANCELED -> observable.value = ProState(offer, ProStatus.Cancelled)
            else -> observable.value = ProState(offer, ProStatus.VerificationFailed)
        }
    }

    private suspend fun synchronize(callbackPurchases: List<Purchase> = emptyList()) = lock.withLock {
        if (buying) return@withLock
        observable.value = ProState(offer, ProStatus.Checking, observable.value.entitled)
        val result = withTimeoutOrNull(40_000) {
            if (!connectedOnce && !connect()) return@withTimeoutOrNull ProStatus.Unavailable
            val storePurchases = purchases()
            // The purchase callback can arrive before the inventory query reflects the change.
            val owned = (callbackPurchases + storePurchases.orEmpty()).filter { product in it.products }
                .distinctBy { it.purchaseToken }.sortedByDescending { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            val ownedStatus = if (owned.isEmpty() && storePurchases == null) ProStatus.VerificationFailed else verify(owned)
            updateOffer()
            ownedStatus ?: if (details != null) ProStatus.Available else ProStatus.Unavailable
        }
        observable.value = ProState(offer, result ?: ProStatus.VerificationFailed)
    }

    private suspend fun verify(purchases: List<Purchase>): ProStatus? {
        if (purchases.isEmpty()) return null
        var pending = false
        var failed = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                pending = true
                continue
            }
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val state = try {
                online.request("POST", "/v1/billing/google-play/verify",
                    JSONObject().put("productId", product).put("token", purchase.purchaseToken).bytes()).getString("state")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
                continue
            }
            if (state == "active") return ProStatus.Active
            if (state == "pending") pending = true
        }
        return when {
            pending -> ProStatus.Pending
            failed -> ProStatus.VerificationFailed
            else -> ProStatus.Invalid
        }
    }

    override fun buy(activity: Activity) {
        if (buying || state.value.status !in setOf(ProStatus.Available, ProStatus.Cancelled)) return
        val selected = details ?: return
        val token = offerToken ?: return
        buying = true
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(selected).setOfferToken(token).build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if (result.responseCode != BillingResponseCode.OK) scope.launch { onPurchase(result, emptyList()) }
    }

    private suspend fun connect(): Boolean = suspendCancellableCoroutine { continuation ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ready = result.responseCode == BillingResponseCode.OK
                connectedOnce = ready
                if (continuation.isActive) continuation.resume(ready)
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private suspend fun purchases(): List<Purchase>? = suspendCancellableCoroutine { continuation ->
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, data ->
            if (continuation.isActive) continuation.resume(data.takeIf { result.responseCode == BillingResponseCode.OK })
        }
    }

    private suspend fun updateOffer() {
        val available = suspendCancellableCoroutine { continuation ->
            val request = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product).setProductType(BillingClient.ProductType.INAPP).build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(request)).build()) { result, data ->
                if (continuation.isActive) continuation.resume(
                    data.productDetailsList.singleOrNull { it.productId == product }.takeIf { result.responseCode == BillingResponseCode.OK },
                )
            }
        }
        val purchaseOption = available?.oneTimePurchaseOfferDetailsList?.singleOrNull { it.offerId == null && it.rentalDetails == null }
        details = available.takeIf { purchaseOption != null }
        offerToken = purchaseOption?.offerToken
        offer = purchaseOption?.formattedPrice?.takeIf { it.isNotBlank() && "[CENA]" !in it }?.let(ProOffer::Available) ?: ProOffer.Unavailable
    }
}
