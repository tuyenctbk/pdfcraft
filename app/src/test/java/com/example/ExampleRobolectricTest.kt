package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.util.SatisfactionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDFCraft", appName)
  }

  @Test
  fun `satisfaction manager suppresses popups for new users and respects tenure and caps`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("pdfcraft_satisfaction_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    val manager = SatisfactionManager.getInstance(context)
    // Fresh user: even after 10 operations on Day 1, neither banner nor auto-donation should trigger
    repeat(10) { manager.onOperationCompleted() }
    repeat(6) { manager.onAppLaunch() }
    assertFalse("Banner should not show on day 1", manager.showHomeDonationBanner.value)
    assertFalse("Auto donation should not show on day 1", manager.shouldAutoShowDonationDialog())
    assertFalse("Minimum 3-day tenure must fail on day 1", manager.hasMinimumTenure(3L, 3))

    // Simulate 15 days since install + 20 total operations -> eligible once
    val fifteenDaysAgo = System.currentTimeMillis() - (15L * 24L * 3600L * 1000L)
    prefs.edit().putLong("pref_first_install_time", fifteenDaysAgo).commit()
    repeat(10) { manager.onOperationCompleted() }
    manager.updateBannerVisibility()
    assertTrue("Minimum tenure satisfied after 15 days & 6 launches", manager.hasMinimumTenure(3L, 3))
    assertTrue("Home banner eligible for satisfied returning user", manager.showHomeDonationBanner.value)
    assertTrue("Auto donation eligible for 20+ ops & 14+ days", manager.shouldAutoShowDonationDialog())

    // Dismissing home banner silences it immediately
    manager.dismissHomeBanner()
    assertFalse("Dismissing home banner hides it", manager.showHomeDonationBanner.value)

    // Showing auto-donation enforces 60-day cooldown and lifetime cap of 2
    manager.markAutoDonationPromptShown()
    assertFalse("Cooldown blocks immediate re-prompt", manager.shouldAutoShowDonationDialog())

    val sixtyOneDaysAgo = System.currentTimeMillis() - (61L * 24L * 3600L * 1000L)
    prefs.edit().putLong("pref_last_auto_donation_prompt_time", sixtyOneDaysAgo).commit()
    assertTrue("Second prompt allowed after 60-day cooldown", manager.shouldAutoShowDonationDialog())

    manager.markAutoDonationPromptShown()
    prefs.edit().putLong("pref_last_auto_donation_prompt_time", sixtyOneDaysAgo).commit()
    assertFalse("Lifetime cap of 2 prevents a third auto-donation prompt", manager.shouldAutoShowDonationDialog())

    // Supporter status permanently silences prompts
    manager.markAsSupporter()
    assertTrue(manager.isSupporter.value)
    assertFalse(manager.showHomeDonationBanner.value)
    assertFalse(manager.shouldAutoShowDonationDialog())
  }
}
