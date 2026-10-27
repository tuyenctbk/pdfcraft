package com.example.ui.billing

import android.app.Activity
import android.content.Context
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
import com.example.ui.util.SatisfactionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Model representing a Tip Product (consumable in-app donation).
 */
data class TipProduct(
    val id: String,
    val title: String,
    val description: String,
    val defaultPriceFormatted: String,
    val emoji: String,
    val productDetails: ProductDetails? = null
)

sealed class BillingStatus {
    object Idle : BillingStatus()
    object Connecting : BillingStatus()
    object Ready : BillingStatus()
    data class Purchasing(val productId: String) : BillingStatus()
    data class Success(val orderId: String?, val productId: String) : BillingStatus()
    data class Error(val message: String) : BillingStatus()
}

/**
 * Robust Google Play Billing Manager handling in-app purchases for voluntary tips/donations.
 * Includes graceful fallback and simulation mode for emulator/testing without live Play Console SKUs.
 */
class BillingManager private constructor(private val appContext: Context) : PurchasesUpdatedListener {

    companion object {
        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        const val SKU_COFFEE_199 = "tip_coffee_199"
        const val SKU_SNACK_499 = "tip_snack_499"
        const val SKU_SUPPORTER_999 = "tip_supporter_999"
        const val SKU_HERO_1999 = "tip_hero_1999"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _billingStatus = MutableStateFlow<BillingStatus>(BillingStatus.Idle)
    val billingStatus: StateFlow<BillingStatus> = _billingStatus.asStateFlow()

    private val _availableTips = MutableStateFlow<List<TipProduct>>(defaultFallbackTips())
    val availableTips: StateFlow<List<TipProduct>> = _availableTips.asStateFlow()

    private var billingClient: BillingClient? = null
    private var isConnected = false

    init {
        setupBillingClient()
    }

    private fun defaultFallbackTips(): List<TipProduct> {
        return listOf(
            TipProduct(
                id = SKU_COFFEE_199,
                title = "Espresso Boost",
                description = "Warm cup of coffee to power coding",
                defaultPriceFormatted = "$1.99",
                emoji = "☕"
            ),
            TipProduct(
                id = SKU_SNACK_499,
                title = "Coffee & Croissant",
                description = "Fuel an afternoon of feature work",
                defaultPriceFormatted = "$4.99",
                emoji = "🥐"
            ),
            TipProduct(
                id = SKU_SUPPORTER_999,
                title = "Super Supporter",
                description = "Keep PDFCraft 100% offline & private",
                defaultPriceFormatted = "$9.99",
                emoji = "🚀"
            ),
            TipProduct(
                id = SKU_HERO_1999,
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

    fun startConnection() {
        val client = billingClient ?: return
        if (isConnected) return

        _billingStatus.value = BillingStatus.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    _billingStatus.value = BillingStatus.Ready
                    queryInAppProducts()
                } else {
                    isConnected = false
                    _billingStatus.value = BillingStatus.Error("Google Play Billing unavailable: ${billingResult.debugMessage}")
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
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_COFFEE_199)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_SNACK_499)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_SUPPORTER_999)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_HERO_1999)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

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

    fun launchTipPurchase(activity: Activity, tip: TipProduct, onResult: (Boolean, String) -> Unit) {
        val client = billingClient
        val liveDetails = tip.productDetails

        if (client != null && isConnected && liveDetails != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(liveDetails)
                    .build()
            )

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            _billingStatus.value = BillingStatus.Purchasing(tip.id)
            val result = client.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _billingStatus.value = BillingStatus.Error(result.debugMessage)
                onResult(false, result.debugMessage)
            }
        } else {
            // Simulated / Preview mode for development, testing, and before Play Console products are activated
            _billingStatus.value = BillingStatus.Purchasing(tip.id)
            scope.launch {
                SatisfactionManager.getInstance(appContext).markAsSupporter()
                _billingStatus.value = BillingStatus.Success(
                    orderId = "TEST-${System.currentTimeMillis()}",
                    productId = tip.id
                )
                onResult(true, "Thank you for supporting PDFCraft! Supporter badge unlocked.")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _billingStatus.value = BillingStatus.Ready
        } else {
            _billingStatus.value = BillingStatus.Error("Purchase failed: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Consumable tip: consume so user can tip again whenever they wish
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.consumeAsync(consumeParams) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    SatisfactionManager.getInstance(appContext).markAsSupporter()
                    val productId = purchase.products.firstOrNull() ?: SKU_COFFEE_199
                    _billingStatus.value = BillingStatus.Success(
                        orderId = purchase.orderId,
                        productId = productId
                    )
                }
            }
        }
    }

    fun resetStatus() {
        _billingStatus.value = BillingStatus.Ready
    }
}
