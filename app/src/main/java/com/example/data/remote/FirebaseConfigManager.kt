package com.example.data.remote

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Firebase Remote Config and Analytics settings for PDFCraft.
 */
object FirebaseConfigManager {
    private const val TAG = "FirebaseConfigManager"

    private val _isAdMobEnabled = MutableStateFlow(false)
    val isAdMobEnabled: StateFlow<Boolean> = _isAdMobEnabled.asStateFlow()

    private val _bannerAdUnitId = MutableStateFlow("ca-app-pub-3940256099942544/6300978111")
    val bannerAdUnitId: StateFlow<String> = _bannerAdUnitId.asStateFlow()

    private val _interstitialAdUnitId = MutableStateFlow("ca-app-pub-3940256099942544/1033173712")
    val interstitialAdUnitId: StateFlow<String> = _interstitialAdUnitId.asStateFlow()

    private val _ratePromptThreshold = MutableStateFlow(3)
    val ratePromptThreshold: StateFlow<Int> = _ratePromptThreshold.asStateFlow()

    private val _sharePromptThreshold = MutableStateFlow(6)
    val sharePromptThreshold: StateFlow<Int> = _sharePromptThreshold.asStateFlow()

    private val _appShareUrl = MutableStateFlow("https://play.google.com/store/apps/details?id=com.soloprono.pdfcraft")
    val appShareUrl: StateFlow<String> = _appShareUrl.asStateFlow()

    private val _latestVersionCode = MutableStateFlow(19)
    val latestVersionCode: StateFlow<Int> = _latestVersionCode.asStateFlow()

    private val _latestVersionName = MutableStateFlow("1.6.6")
    val latestVersionName: StateFlow<String> = _latestVersionName.asStateFlow()

    private val _updateReleaseNotes = MutableStateFlow("Performance optimizations, bug fixes, and improved offline rendering.")
    val updateReleaseNotes: StateFlow<String> = _updateReleaseNotes.asStateFlow()

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseApp initialization skipped or failed: ${t.message}")
        }

        if (FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            } catch (t: Throwable) {
                Log.w(TAG, "FirebaseAnalytics init failed: ${t.message}")
            }

            try {
                val remoteConfig = FirebaseRemoteConfig.getInstance()
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
                remoteConfig.setConfigSettingsAsync(configSettings)

                val defaults = mapOf(
                    "is_admob_enabled" to false,
                    "rate_prompt_operation_threshold" to 3,
                    "share_prompt_operation_threshold" to 6,
                    "app_share_url" to "https://play.google.com/store/apps/details?id=com.soloprono.pdfcraft",
                    "banner_ad_unit_id" to "ca-app-pub-3940256099942544/6300978111",
                    "interstitial_ad_unit_id" to "ca-app-pub-3940256099942544/1033173712",
                    "latest_version_code" to 19,
                    "latest_version_name" to "1.6.6",
                    "update_release_notes" to "Performance optimizations, bug fixes, and improved offline rendering."
                )
                remoteConfig.setDefaultsAsync(defaults)

                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val adMobEnabled = remoteConfig.getBoolean("is_admob_enabled")
                        _isAdMobEnabled.value = adMobEnabled
                        
                        val rateThreshold = remoteConfig.getLong("rate_prompt_operation_threshold").toInt()
                        if (rateThreshold > 0) _ratePromptThreshold.value = rateThreshold

                        val shareThreshold = remoteConfig.getLong("share_prompt_operation_threshold").toInt()
                        if (shareThreshold > 0) _sharePromptThreshold.value = shareThreshold

                        val shareUrl = remoteConfig.getString("app_share_url")
                        if (shareUrl.isNotBlank()) _appShareUrl.value = shareUrl

                        val bannerId = remoteConfig.getString("banner_ad_unit_id")
                        if (bannerId.isNotBlank()) {
                            _bannerAdUnitId.value = bannerId
                        }
                        val interstitialId = remoteConfig.getString("interstitial_ad_unit_id")
                        if (interstitialId.isNotBlank()) {
                            _interstitialAdUnitId.value = interstitialId
                        }

                        val vCode = remoteConfig.getLong("latest_version_code").toInt()
                        if (vCode > 0) _latestVersionCode.value = vCode

                        val vName = remoteConfig.getString("latest_version_name")
                        if (vName.isNotBlank()) _latestVersionName.value = vName

                        val notes = remoteConfig.getString("update_release_notes")
                        if (notes.isNotBlank()) _updateReleaseNotes.value = notes

                        Log.d(TAG, "Remote Config fetched successfully. AdMob enabled = $adMobEnabled, vCode = $vCode")
                    } else {
                        Log.w(TAG, "Remote Config fetch failed", task.exception)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "FirebaseRemoteConfig init failed: ${t.message}")
            }
        }
    }

    /**
     * Toggles AdMob state dynamically (for remote config updates or local preview testing).
     */
    fun setAdMobEnabled(enabled: Boolean) {
        _isAdMobEnabled.value = enabled
        Log.d(TAG, "Remote Config updated isAdMobEnabled = $enabled")
    }

    fun setUserProperty(name: String, value: String) {
        try {
            firebaseAnalytics?.setUserProperty(name, value)
            Log.d(TAG, "Analytics user property set: $name = $value")
        } catch (t: Throwable) {
            Log.w(TAG, "Analytics setUserProperty error: ${t.message}")
        }
    }

    fun logAnalyticsEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        try {
            val bundle = Bundle().apply {
                for ((key, value) in params) {
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent(eventName, bundle)
            Log.d(TAG, "Analytics event logged: $eventName | params: $params")
        } catch (t: Throwable) {
            Log.w(TAG, "Analytics log error: ${t.message}")
        }
    }

    fun logScreenView(screenName: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Screen view logged: $screenName")
        } catch (t: Throwable) {
            Log.w(TAG, "Screen view log error: ${t.message}")
        }
    }

    fun recordException(throwable: Throwable) {
        Log.w(TAG, "Recorded exception: ${throwable.message}")
        try {
            val bundle = Bundle().apply {
                putString("exception_message", throwable.message ?: "Unknown error")
                putString("exception_class", throwable.javaClass.name)
            }
            firebaseAnalytics?.logEvent("app_exception", bundle)
        } catch (_: Throwable) {}
    }
}

