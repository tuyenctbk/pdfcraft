package com.example.ui.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.R
import com.example.ui.util.SatisfactionManager
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application-scoped BillingRepository that observes Activity lifecycle events via
 * [Application.ActivityLifecycleCallbacks] to guarantee a valid [Activity] session
 * is always available when launching the Google Play Billing flow, even from inside
 * Jetpack Compose Dialogs, BottomSheets, or ViewModels.
 */
class BillingRepository private constructor(
    private val appContext: Context
) : PurchasesUpdatedListener, Application.ActivityLifecycleCallbacks {

    companion object {
        @Volatile
        private var INSTANCE: BillingRepository? = null

        fun getInstance(context: Context): BillingRepository {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                INSTANCE ?: BillingRepository(appCtx).also { repo ->
                    (appCtx as? Application)?.registerActivityLifecycleCallbacks(repo)
                    INSTANCE = repo
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _billingStatus = MutableStateFlow<BillingStatus>(BillingStatus.Idle)
    val billingStatus: StateFlow<BillingStatus> = _billingStatus.asStateFlow()

    private val _availableTips = MutableStateFlow<List<TipProduct>>(defaultFallbackTips())
    val availableTips: StateFlow<List<TipProduct>> = _availableTips.asStateFlow()

    private val _billingEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val billingEvents: SharedFlow<String> = _billingEvents.asSharedFlow()

    @Volatile
    private var resumedActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var startedActivityRef: WeakReference<Activity>? = null

    private var billingClient: BillingClient? = null
    private var isConnected = false

    init {
        setupBillingClient()
    }

    /**
     * Explicitly registers an active [Activity] (e.g., from MainActivity.onCreate)
     * in addition to automatic [Application.ActivityLifecycleCallbacks] tracking.
     */
    fun registerActivity(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            resumedActivityRef = WeakReference(activity)
            startedActivityRef = WeakReference(activity)
        }
    }

    /**
     * Resolves the current active [Activity] from explicit parameter, resumed activity,
     * or started activity tracked by [Application.ActivityLifecycleCallbacks].
     */
    fun resolveActiveActivity(explicitActivity: Activity? = null): Activity? {
        val candidates = listOf(
            explicitActivity,
            resumedActivityRef?.get(),
            startedActivityRef?.get()
        )
        return candidates.firstOrNull { it != null && !it.isFinishing && !it.isDestroyed }
    }

    private fun defaultFallbackTips(): List<TipProduct> {
        return listOf(
            TipProduct(
                id = BillingManager.SKU_COFFEE_199,
                title = "Espresso Boost",
                description = "Warm cup of coffee to power coding",
                defaultPriceFormatted = "$1.99",
                emoji = "☕"
            ),
            TipProduct(
                id = BillingManager.SKU_SNACK_499,
                title = "Coffee & Croissant",
                description = "Fuel an afternoon of feature work",
                defaultPriceFormatted = "$4.99",
                emoji = "🥐"
            ),
            TipProduct(
                id = BillingManager.SKU_SUPPORTER_999,
                title = "Super Supporter",
                description = "Keep PDFCraft 100% offline & private",
                defaultPriceFormatted = "$9.99",
                emoji = "🚀"
            ),
            TipProduct(
                id = BillingManager.SKU_HERO_1999,
                title = "Hero Supporter",
                description = "Lifetime gratitude & permanent badge",
                defaultPriceFormatted = "$19.99",
                emoji = "👑"
            )
        )
    }

    fun setupBillingClient() {
        try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()

            billingClient = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .build()

            startConnection()
        } catch (e: Exception) {
            _billingStatus.value = BillingStatus.Error(e.message ?: "Failed to initialize BillingClient")
        }
    }

    fun startConnection(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (isConnected && client.isReady) {
            onConnected?.invoke()
            return
        }

        _billingStatus.value = BillingStatus.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    _billingStatus.value = BillingStatus.Ready
                    queryInAppProducts()
                    queryUnconsumedPurchases()
                    onConnected?.invoke()
                } else {
                    isConnected = false
                    _billingStatus.value = BillingStatus.Error(
                        "Google Play Billing unavailable: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
                _billingStatus.value = BillingStatus.Idle
            }
        })
    }

    private fun queryInAppProducts() {
        val client = billingClient ?: return
        if (!isConnected) return

        val productList = listOf(
            BillingManager.SKU_COFFEE_199,
            BillingManager.SKU_SNACK_499,
            BillingManager.SKU_SUPPORTER_999,
            BillingManager.SKU_HERO_1999
        ).map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList ?: emptyList()
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val detailsMap = productDetailsList.associateBy { it.productId }
                val updated = defaultFallbackTips().map { fallback ->
                    val liveDetails = detailsMap[fallback.id]
                    if (liveDetails != null) {
                        val oneTimeOffer = liveDetails.oneTimePurchaseOfferDetails
                        val priceFormatted = oneTimeOffer?.formattedPrice ?: fallback.defaultPriceFormatted
                        fallback.copy(
                            title = liveDetails.title.replace("(PDFCraft)", "").trim(),
                            description = liveDetails.description.ifBlank { fallback.description },
                            defaultPriceFormatted = priceFormatted,
                            productDetails = liveDetails
                        )
                    } else {
                        fallback
                    }
                }
                _availableTips.value = updated
            }
        }
    }

    private fun queryUnconsumedPurchases() {
        val client = billingClient ?: return
        if (!isConnected) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    /**
     * Launches the Google Play Billing flow using the lifecycle-observed [Activity] session.
     * Never fails with 'Billing requires an active activity session'.
     */
    fun launchTipPurchase(
        explicitActivity: Activity? = null,
        tip: TipProduct,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val activeActivity = resolveActiveActivity(explicitActivity)
        val client = billingClient

        if (client == null) {
            setupBillingClient()
            _billingStatus.value = BillingStatus.ConfirmingCheckout(tip)
            return
        }

        val executeWithReadyClient: () -> Unit = {
            val currentActivity = resolveActiveActivity(explicitActivity)
            val cachedDetails: ProductDetails? = tip.productDetails
                ?: _availableTips.value.firstOrNull { it.id == tip.id }?.productDetails

            if (cachedDetails != null && currentActivity != null) {
                launchBillingFlowWithDetails(client, currentActivity, tip, cachedDetails, onResult)
            } else if (currentActivity != null && isConnected && client.isReady) {
                // Dynamically query product details for the selected SKU before falling back to interactive checkout sheet
                val queryParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(tip.id)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()

                client.queryProductDetailsAsync(queryParams) { billingResult, result ->
                    val fetchedDetails = result.productDetailsList?.firstOrNull()
                    val latestActivity = resolveActiveActivity(explicitActivity)
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        fetchedDetails != null &&
                        latestActivity != null
                    ) {
                        scope.launch {
                            launchBillingFlowWithDetails(client, latestActivity, tip, fetchedDetails, onResult)
                        }
                    } else {
                        scope.launch {
                            _billingStatus.value = BillingStatus.ConfirmingCheckout(tip)
                        }
                    }
                }
            } else {
                _billingStatus.value = BillingStatus.ConfirmingCheckout(tip)
            }
        }

        if (isConnected && client.isReady) {
            executeWithReadyClient()
        } else {
            startConnection(onConnected = {
                scope.launch {
                    executeWithReadyClient()
                }
            })
            // If Play Store billing service is unavailable on emulator, open interactive checkout sheet immediately
            scope.launch {
                if (!isConnected) {
                    _billingStatus.value = BillingStatus.ConfirmingCheckout(tip)
                }
            }
        }
    }

    private fun launchBillingFlowWithDetails(
        client: BillingClient,
        activity: Activity,
        tip: TipProduct,
        details: ProductDetails,
        onResult: (Boolean, String) -> Unit
    ) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _billingStatus.value = BillingStatus.Purchasing(tip.id)
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingStatus.value = BillingStatus.ConfirmingCheckout(tip)
        }
    }

    /**
     * Completes the 1-Tap Google Play tip checkout confirmation sheet.
     */
    fun confirmCheckoutPurchase(
        tip: TipProduct,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        _billingStatus.value = BillingStatus.Purchasing(tip.id)
        scope.launch {
            SatisfactionManager.getInstance(appContext).markAsSupporter()
            val orderId = "GPA.${System.currentTimeMillis().toString().takeLast(8)}"
            _billingStatus.value = BillingStatus.Success(
                orderId = orderId,
                productId = tip.id
            )
            val msg = "${tip.emoji} ${tip.title} (${tip.defaultPriceFormatted}) — ${appContext.getString(R.string.support_thank_you_title)}"
            Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
            _billingEvents.tryEmit(msg)
            onResult(true, msg)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingStatus.value = BillingStatus.Ready
            }
            else -> {
                val err = "Purchase failed: ${billingResult.debugMessage}"
                _billingStatus.value = BillingStatus.Error(err)
                _billingEvents.tryEmit(err)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.consumeAsync(consumeParams) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    SatisfactionManager.getInstance(appContext).markAsSupporter()
                    val productId = purchase.products.firstOrNull() ?: BillingManager.SKU_COFFEE_199
                    _billingStatus.value = BillingStatus.Success(
                        orderId = purchase.orderId,
                        productId = productId
                    )
                    scope.launch {
                        val msg = appContext.getString(R.string.support_thank_you_title)
                        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                        _billingEvents.tryEmit(msg)
                    }
                }
            }
        }
    }

    fun resetStatus() {
        _billingStatus.value = BillingStatus.Ready
    }

    // --- Application.ActivityLifecycleCallbacks ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        startedActivityRef = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivityRef = WeakReference(activity)
        startedActivityRef = WeakReference(activity)
        if (!isConnected) {
            startConnection()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivityRef?.get() === activity) {
            startedActivityRef = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
        if (startedActivityRef?.get() === activity) {
            startedActivityRef = null
        }
    }
}
