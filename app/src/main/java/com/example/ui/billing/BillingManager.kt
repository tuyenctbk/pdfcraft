package com.example.ui.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.StateFlow

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
    data class ConfirmingCheckout(val tip: TipProduct) : BillingStatus()
    data class Purchasing(val productId: String) : BillingStatus()
    data class Success(val orderId: String?, val productId: String) : BillingStatus()
    data class Error(val message: String) : BillingStatus()
}

/**
 * Facade delegating all Google Play Billing operations and Activity lifecycle tracking
 * to the application-scoped [BillingRepository].
 */
class BillingManager private constructor(appContext: Context) {

    private val repository: BillingRepository = BillingRepository.getInstance(appContext)

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

    val billingStatus: StateFlow<BillingStatus> = repository.billingStatus
    val availableTips: StateFlow<List<TipProduct>> = repository.availableTips

    fun setCurrentActivity(activity: Activity) {
        repository.registerActivity(activity)
    }

    fun getCurrentActivity(): Activity? = repository.resolveActiveActivity()

    fun startConnection() {
        repository.startConnection()
    }

    fun launchTipPurchase(
        activity: Activity? = null,
        tip: TipProduct,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        repository.launchTipPurchase(
            explicitActivity = activity,
            tip = tip,
            onResult = onResult
        )
    }

    fun confirmCheckoutPurchase(
        tip: TipProduct,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        repository.confirmCheckoutPurchase(tip, onResult)
    }

    fun resetStatus() {
        repository.resetStatus()
    }
}
