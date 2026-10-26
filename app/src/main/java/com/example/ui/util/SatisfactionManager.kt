package com.example.ui.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intelligent manager tracking user satisfaction, operations, and engagement.
 * Ensures donation requests, banners, and review prompts are NEVER shown to casual/new users,
 * but only to users who have experienced real value and high satisfaction.
 */
class SatisfactionManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SatisfactionManager? = null

        fun getInstance(context: Context): SatisfactionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SatisfactionManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val PREFS_NAME = "pdfcraft_satisfaction_prefs"
        private const val KEY_COMPLETED_OPS = "pref_completed_operations_count"
        private const val KEY_FIRST_INSTALL = "pref_first_install_time"
        private const val KEY_APP_LAUNCH_COUNT = "pref_app_launch_count"
        private const val KEY_IS_SUPPORTER = "pref_is_supporter"
        private const val KEY_BANNER_DISMISSED_TIME = "pref_donation_banner_dismissed_time"
        private const val KEY_LAST_AUTO_DONATION_PROMPT = "pref_last_auto_donation_prompt_time"

        // Thresholds
        private const val MIN_OPERATIONS_FOR_BANNER = 3
        private const val MIN_LAUNCHES_FOR_BANNER = 3
        private const val MIN_DAYS_FOR_BANNER = 2
        private const val BANNER_DISMISS_COOLDOWN_MS = 14L * 24L * 3600L * 1000L // 14 days
        private const val MIN_OPERATIONS_FOR_AUTO_PROMPT = 7
        private const val AUTO_PROMPT_COOLDOWN_MS = 30L * 24L * 3600L * 1000L // 30 days
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSupporter = MutableStateFlow(prefs.getBoolean(KEY_IS_SUPPORTER, false))
    val isSupporter: StateFlow<Boolean> = _isSupporter.asStateFlow()

    private val _completedOperations = MutableStateFlow(prefs.getInt(KEY_COMPLETED_OPS, 0))
    val completedOperations: StateFlow<Int> = _completedOperations.asStateFlow()

    private val _showHomeDonationBanner = MutableStateFlow(computeShouldShowBanner())
    val showHomeDonationBanner: StateFlow<Boolean> = _showHomeDonationBanner.asStateFlow()

    init {
        // Record first install time if not already recorded
        if (!prefs.contains(KEY_FIRST_INSTALL)) {
            prefs.edit().putLong(KEY_FIRST_INSTALL, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Called on each app launch/session start.
     */
    fun onAppLaunch() {
        val launches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_APP_LAUNCH_COUNT, launches).apply()
        updateBannerVisibility()
    }

    /**
     * Called whenever a PDF operation (merge, compress, split, convert, etc.) completes successfully.
     */
    fun onOperationCompleted() {
        val current = prefs.getInt(KEY_COMPLETED_OPS, 0) + 1
        prefs.edit().putInt(KEY_COMPLETED_OPS, current).apply()
        _completedOperations.value = current
        updateBannerVisibility()
    }

    /**
     * Evaluates if user has demonstrated sufficient satisfaction to see the home screen donation banner.
     */
    private fun computeShouldShowBanner(): Boolean {
        if (_isSupporter.value) {
            return false // Already supported, never nag
        }

        val ops = prefs.getInt(KEY_COMPLETED_OPS, 0)
        if (ops < MIN_OPERATIONS_FOR_BANNER) {
            return false // Not enough successful operations yet
        }

        val installTime = prefs.getLong(KEY_FIRST_INSTALL, System.currentTimeMillis())
        val daysSinceInstall = (System.currentTimeMillis() - installTime) / (24L * 3600L * 1000L)
        val launches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0)

        if (daysSinceInstall < MIN_DAYS_FOR_BANNER && launches < MIN_LAUNCHES_FOR_BANNER) {
            return false // Too new
        }

        val dismissedTime = prefs.getLong(KEY_BANNER_DISMISSED_TIME, 0L)
        if (dismissedTime > 0L) {
            val elapsed = System.currentTimeMillis() - dismissedTime
            if (elapsed < BANNER_DISMISS_COOLDOWN_MS) {
                return false // Respect user dismissal cooldown
            }
        }

        return true
    }

    fun updateBannerVisibility() {
        _showHomeDonationBanner.value = computeShouldShowBanner()
    }

    /**
     * User dismissed the home donation banner via 'X'.
     * Silences it for at least 14 days.
     */
    fun dismissHomeBanner() {
        prefs.edit().putLong(KEY_BANNER_DISMISSED_TIME, System.currentTimeMillis()).apply()
        _showHomeDonationBanner.value = false
    }

    /**
     * Checks if the app should auto-show a gentle donation suggestion dialog.
     * Only true for power users who have completed 7+ operations and have not been prompted recently.
     */
    fun shouldAutoShowDonationDialog(): Boolean {
        if (_isSupporter.value) return false

        val ops = prefs.getInt(KEY_COMPLETED_OPS, 0)
        if (ops < MIN_OPERATIONS_FOR_AUTO_PROMPT) return false

        val lastPrompt = prefs.getLong(KEY_LAST_AUTO_DONATION_PROMPT, 0L)
        val elapsed = System.currentTimeMillis() - lastPrompt
        if (elapsed < AUTO_PROMPT_COOLDOWN_MS) return false

        return true
    }

    fun markAutoDonationPromptShown() {
        prefs.edit().putLong(KEY_LAST_AUTO_DONATION_PROMPT, System.currentTimeMillis()).apply()
    }

    /**
     * User completed a donation / tip via Google Play IAP, PayPal, or crypto.
     * Sets permanent supporter flag and hides banners.
     */
    fun markAsSupporter() {
        prefs.edit().putBoolean(KEY_IS_SUPPORTER, true).apply()
        _isSupporter.value = true
        _showHomeDonationBanner.value = false
    }
}
