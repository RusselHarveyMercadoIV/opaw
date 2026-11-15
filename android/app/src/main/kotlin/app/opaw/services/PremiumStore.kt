package app.opaw.services

import android.app.Activity
import android.content.Context
import app.opaw.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Opaw Premium with Google Play Billing: loads the plans, buys and restores them, and keeps track of
 * whether Premium is on. Mirrors PremiumStore.swift.
 *
 * Play Console setup: subscriptions `app.opaw.premium.yearly` and `app.opaw.premium.monthly`, each
 * with one base plan and a 7-day free-trial offer, and `app.opaw.premium.yearly.offer` with a 50%
 * first-year discount offer.
 */
class PremiumStore(private val context: Context) {
    enum class Plan(val productId: String, val title: String) {
        YEARLY("app.opaw.premium.yearly", "Yearly"),
        MONTHLY("app.opaw.premium.monthly", "Monthly"),
    }

    /** What the person has right now. Play doesn't tell apps the expiry date, so it's worked out for trials. */
    data class Status(val productId: String, val isTrial: Boolean, val purchaseDate: Instant, val expirationDate: Instant?, val willRenew: Boolean) {
        val plan: Plan get() = if (productId == Plan.MONTHLY.productId) Plan.MONTHLY else Plan.YEARLY
    }

    var products by mutableStateOf<Map<String, ProductDetails>>(emptyMap())
        private set
    var status by mutableStateOf<Status?>(null)
        private set
    var isPurchasing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)

    /**
     * Test builds only: Premium switched on by hand in Settings, because a sideloaded APK can't buy
     * through Google Play. Always off in release builds.
     */
    var testPremium by mutableStateOf(BuildConfig.DEBUG && prefs.getBoolean("testPremium", false))
        private set

    val isPremium: Boolean get() = status != null || testPremium

    fun setTestPremium(on: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean("testPremium", on).apply()
        testPremium = on
        Premium.isPremium = isPremium
    }

    init { Premium.isPremium = isPremium }
    private var pendingPurchase: CompletableDeferred<Boolean>? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> scope.launch { onPurchases(result, purchases.orEmpty()) } }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init { connect() }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { load() }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun product(plan: Plan): ProductDetails? = products[plan.productId]
    val offerProduct: ProductDetails? get() = products[OFFER_ID]

    suspend fun load() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            (Plan.entries.map { it.productId } + OFFER_ID).map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            },
        ).build()
        val result = client.queryProductDetails(params)
        products = result.productDetailsList.orEmpty().associateBy { it.productId }
        refreshStatus()
    }

    /** The offer with a free trial, when Play still offers it to this person. */
    private fun trialOffer(details: ProductDetails?): ProductDetails.SubscriptionOfferDetails? =
        details?.subscriptionOfferDetails?.firstOrNull { offer -> offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }

    private fun baseOffer(details: ProductDetails?): ProductDetails.SubscriptionOfferDetails? =
        details?.subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: details?.subscriptionOfferDetails?.firstOrNull()

    /** True when the person can still get the free trial (Play only returns offers they're eligible for). */
    fun isEligibleForTrial(plan: Plan): Boolean = trialOffer(product(plan)) != null

    /** "$29.99", the plan's regular price. */
    fun displayPrice(details: ProductDetails?): String? =
        baseOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    /** The price for one month of a yearly plan, such as "$2.50". */
    fun monthlyEquivalent(details: ProductDetails?): String? {
        val phase = baseOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
        if (phase.billingPeriod != "P1Y") return null
        val monthly = phase.priceAmountMicros / 12 / 1_000_000.0
        return java.text.NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(phase.priceCurrencyCode) }.format(monthly)
    }

    /** The first-year price of the one-time offer, such as "$14.99". */
    fun introPrice(details: ProductDetails?): String? =
        details?.subscriptionOfferDetails?.firstOrNull { it.offerId != null }?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice

    fun price(details: ProductDetails?): Double? =
        baseOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.priceAmountMicros?.div(1_000_000.0)

    /** Buys a plan. Returns true when Premium is on afterwards. */
    suspend fun purchase(activity: Activity, details: ProductDetails, useTrial: Boolean = true): Boolean {
        val offer = (if (useTrial) trialOffer(details) else null) ?: details.subscriptionOfferDetails?.firstOrNull { it.offerId != null } ?: baseOffer(details)
            ?: return false
        isPurchasing = true
        val done = CompletableDeferred<Boolean>()
        pendingPurchase = done
        prefs.edit().putBoolean("trial.${details.productId}", offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }).apply()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()),
        ).build()
        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            isPurchasing = false
            errorMessage = launch.debugMessage.ifEmpty { "Google Play couldn't start the purchase." }
            return false
        }
        val result = done.await()
        isPurchasing = false
        return result
    }

    private suspend fun onPurchases(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.forEach { acknowledge(it) }
                refreshStatus()
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) errorMessage = "Your purchase is waiting for approval."
                status?.takeIf { it.isTrial }?.let { scheduleTrialReminder(it) }
                pendingPurchase?.complete(isPremium)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> pendingPurchase?.complete(false)
            else -> {
                errorMessage = result.debugMessage.ifEmpty { "Google Play couldn't confirm that purchase." }
                pendingPurchase?.complete(false)
            }
        }
        pendingPurchase = null
    }

    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        }
    }

    suspend fun restore() {
        isPurchasing = true
        refreshStatus()
        isPurchasing = false
        if (!isPremium && errorMessage == null) errorMessage = "There's no Opaw Premium purchase on this Google account."
    }

    suspend fun refreshStatus() {
        if (!client.isReady) { Premium.isPremium = isPremium; return }
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        val owned = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        owned.forEach { acknowledge(it) }
        status = owned.mapNotNull { purchase ->
            val id = purchase.products.firstOrNull() ?: return@mapNotNull null
            val start = Instant.ofEpochMilli(purchase.purchaseTime)
            val trial = prefs.getBoolean("trial.$id", false) && start.plusSeconds(7 * 86_400) > Instant.now()
            Status(id, trial, start, if (trial) start.plusSeconds(7 * 86_400) else null, purchase.isAutoRenewing)
        }.maxByOrNull { it.purchaseDate }
        Premium.isPremium = isPremium
    }

    /** Two days before the trial ends, a notification says billing is about to start. */
    val trialReminderDate: Instant? get() = status?.takeIf { it.isTrial }?.expirationDate?.minusSeconds(2 * 86_400)

    private fun scheduleTrialReminder(status: Status) {
        val at = status.expirationDate?.minusSeconds(2 * 86_400) ?: return
        if (at <= Instant.now()) return
        NotificationService.scheduleOnce(context, "premium.trial.reminder", at, "Your Opaw Premium trial ends in 2 days",
            "Cancel in Google Play before then if you don't want to be charged.")
    }

    companion object {
        /** The yearly plan with 50% off the first year, shown once after closing the paywall. */
        const val OFFER_ID = "app.opaw.premium.yearly.offer"
    }
}
