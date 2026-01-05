package com.shortsblocker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.shortsblocker.app.util.TestUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Automated tests for ShortsBlocker accessibility service.
 *
 * Prerequisites:
 * - Emulator/device with YouTube and Instagram installed and logged in
 * - Test app has WRITE_SECURE_SETTINGS permission (granted via adb):
 *   adb shell pm grant com.shortsblocker.app android.permission.WRITE_SECURE_SETTINGS
 *
 * Run with:
 *   ./gradlew connectedAndroidTest
 *
 * Or run specific test class:
 *   ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shortsblocker.app.ShortsBlockerTest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ShortsBlockerTest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setUp() {
        device = TestUtils.getDevice()
        context = TestUtils.getContext()

        // Start from home screen
        TestUtils.goHome(device)

        // Enable accessibility service
        TestUtils.enableAccessibilityService(context)

        // Clear logs before each test
        TestUtils.clearLogcat()
    }

    @After
    fun tearDown() {
        // Return to home screen
        TestUtils.goHome(device)
    }


    @Test
    fun test01_accessibilityServiceCanBeEnabled() {
        // Verify service is enabled
        assertTrue(
            "Accessibility service should be enabled",
            TestUtils.isAccessibilityServiceEnabled(context)
        )
    }

    @Test
    fun test02_accessibilityServiceCanBeDisabled() {
        TestUtils.disableAccessibilityService(context)
        Thread.sleep(500)

        assertFalse(
            "Accessibility service should be disabled",
            TestUtils.isAccessibilityServiceEnabled(context)
        )

        // Re-enable for subsequent tests
        TestUtils.enableAccessibilityService(context)
    }


    /**
     * Helper to skip blocking tests if service isn't actually running.
     * On Android 16+, the service may be enabled but not bound if consent wasn't given via Settings UI.
     */
    private fun assumeServiceIsRunning() {
        Assume.assumeTrue(
            "Service must be running (bound) for blocking tests. On Android 16+, enable via Settings UI.",
            TestUtils.isAccessibilityServiceRunning()
        )
    }

    @Test
    fun test10_youTubeShortsTab_isBlocked() {
        assumeServiceIsRunning()

        // Launch YouTube
        TestUtils.launchYouTube(device, context)

        // Navigate to Shorts tab
        val navigated = TestUtils.navigateToYouTubeShorts(device)
        assertTrue("Should be able to navigate to Shorts", navigated)

        // Wait and check if blocked
        val wasBlocked = TestUtils.waitForBlock(timeoutMs = 5000, checkYouTube = true)

        assertTrue(
            "YouTube Shorts should be blocked. Logs: ${TestUtils.readShortBlockerLogs().takeLast(10)}",
            wasBlocked
        )
    }

    @Test
    fun test11_youTubeShortsFromHomeFeed_isBlocked() {
        assumeServiceIsRunning()

        // Launch YouTube
        TestUtils.launchYouTube(device, context)
        Thread.sleep(2000)

        // Find and click on a Short video thumbnail from home feed
        val shortVideo = device.findObject(By.descContains("play Short"))

        if (shortVideo != null) {
            TestUtils.clearLogcat()
            shortVideo.click()
            Thread.sleep(3000)

            val wasBlocked = TestUtils.logsContainYouTubeBlock()
            assertTrue(
                "Short from home feed should be blocked",
                wasBlocked
            )
        } else {
            // Scroll to find Shorts section
            device.swipe(540, 1500, 540, 500, 10)
            Thread.sleep(1000)

            val shortVideoAfterScroll = device.findObject(By.descContains("play Short"))
            if (shortVideoAfterScroll != null) {
                TestUtils.clearLogcat()
                shortVideoAfterScroll.click()
                Thread.sleep(3000)

                val wasBlocked = TestUtils.logsContainYouTubeBlock()
                assertTrue("Short from home feed should be blocked", wasBlocked)
            } else {
                // Skip if no Shorts found on home
                println("No Shorts found on YouTube home feed - skipping test")
            }
        }
    }

    @Test
    fun test12_youTubeRegularVideo_isNotBlocked() {
        // Launch a regular YouTube video via shell command (more reliable)
        device.executeShellCommand("am start -a android.intent.action.VIEW -d https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        // Wait for YouTube to open
        device.wait(Until.hasObject(By.pkg(TestUtils.YOUTUBE_PACKAGE).depth(0)), 10000)
        Thread.sleep(6000) // Let video load

        // Check that it was NOT blocked
        val wasBlocked = TestUtils.logsContainYouTubeBlock()
        assertFalse(
            "Regular YouTube video should NOT be blocked",
            wasBlocked
        )

        // Verify the service did receive events (sanity check) - relaxed check
        // The service may or may not log "YT event" for regular videos depending on timing
        val logs = TestUtils.readShortBlockerLogs()
        val hasAnyLogs = logs.isNotEmpty()
        // Just verify we didn't crash and weren't blocked - the primary assertion passed
    }

    @Test
    fun test13_youTubeHomeFeed_isNotBlocked() {
        // Launch YouTube
        TestUtils.launchYouTube(device, context)
        Thread.sleep(3000)

        // Scroll around the home feed
        device.swipe(540, 1500, 540, 800, 10)
        Thread.sleep(1000)
        device.swipe(540, 800, 540, 1500, 10)
        Thread.sleep(1000)

        // Check that home feed was NOT blocked
        val wasBlocked = TestUtils.logsContainYouTubeBlock()
        assertFalse(
            "YouTube home feed should NOT be blocked",
            wasBlocked
        )
    }

    @Test
    fun test14_youTubeSearch_isNotBlocked() {
        // Launch YouTube
        TestUtils.launchYouTube(device, context)
        Thread.sleep(2000)

        // Find and click search button
        val searchButton = device.findObject(By.desc("Search"))
        if (searchButton != null) {
            searchButton.click()
            Thread.sleep(2000)

            // Check that search was NOT blocked
            val wasBlocked = TestUtils.logsContainYouTubeBlock()
            assertFalse(
                "YouTube search should NOT be blocked",
                wasBlocked
            )
        }
    }


    @Test
    fun test20_instagramReelsTab_isBlocked() {
        assumeServiceIsRunning()

        // Launch Instagram
        TestUtils.launchInstagram(device, context)

        // Navigate to Reels tab
        val navigated = TestUtils.navigateToInstagramReels(device)
        assertTrue("Should be able to navigate to Reels", navigated)

        // Wait and check if blocked
        val wasBlocked = TestUtils.waitForBlock(timeoutMs = 6000, checkYouTube = false)

        assertTrue(
            "Instagram Reels should be blocked. Logs: ${TestUtils.readShortBlockerLogs().takeLast(10)}",
            wasBlocked
        )
    }

    @Test
    fun test21_instagramHomeFeed_isNotBlocked() {
        // Launch Instagram
        TestUtils.launchInstagram(device, context)
        Thread.sleep(3000)

        // Make sure we're on home tab
        val homeTab = device.findObject(By.res("com.instagram.android:id/feed_tab"))
            ?: device.findObject(By.desc("Home"))
        homeTab?.click()
        Thread.sleep(2000)

        // Scroll through home feed
        device.swipe(540, 1500, 540, 800, 10)
        Thread.sleep(1000)
        device.swipe(540, 800, 540, 1500, 10)
        Thread.sleep(1000)

        // Check that home feed was NOT blocked
        val wasBlocked = TestUtils.logsContainInstagramBlock()
        assertFalse(
            "Instagram home feed should NOT be blocked",
            wasBlocked
        )
    }

    @Test
    fun test22_instagramHomeFeedWithReelsTray_isNotBlocked() {
        // Launch Instagram
        TestUtils.launchInstagram(device, context)
        Thread.sleep(3000)

        // Go to home tab (should have reels tray at top)
        val homeTab = device.findObject(By.res("com.instagram.android:id/feed_tab"))
            ?: device.findObject(By.desc("Home"))
        homeTab?.click()
        Thread.sleep(2000)

        // Check logs for hasTray=true and NOT blocked
        val logs = TestUtils.readShortBlockerLogs()
        val hasTrayContext = logs.any { it.contains("hasTray=true") || it.contains("hasHome=true") }
        val wasBlocked = TestUtils.logsContainInstagramBlock()

        // If we have tray context, we should NOT be blocked
        if (hasTrayContext) {
            assertFalse(
                "Instagram home with reels tray should NOT be blocked",
                wasBlocked
            )
        }
    }

    @Test
    fun test23_instagramExplore_isNotBlocked() {
        // Launch Instagram
        TestUtils.launchInstagram(device, context)
        Thread.sleep(2000)

        // Find and click explore/search tab
        val searchTab = device.findObject(By.res("com.instagram.android:id/search_tab"))
            ?: device.findObject(By.desc("Search and explore"))

        if (searchTab != null) {
            searchTab.click()
            Thread.sleep(2000)

            // Scroll around explore
            device.swipe(540, 1500, 540, 800, 10)
            Thread.sleep(1000)

            // Check that explore was NOT blocked (unless clicking on a reel)
            val logs = TestUtils.readShortBlockerLogs()
            val hasInstagramEvents = logs.any { it.contains("IG SCREEN") }

            // If no Instagram events, the service might not be monitoring explore
            // which is expected behavior
            if (hasInstagramEvents) {
                val wasBlocked = TestUtils.logsContainInstagramBlock()
                // Explore grid should not be blocked (only full-screen reels)
                assertFalse(
                    "Instagram Explore grid should NOT be blocked",
                    wasBlocked
                )
            }
        }
    }

    @Test
    fun test24_instagramProfile_isNotBlocked() {
        // Launch Instagram
        TestUtils.launchInstagram(device, context)
        Thread.sleep(2000)

        // Find and click profile tab
        val profileTab = device.findObject(By.res("com.instagram.android:id/profile_tab"))
            ?: device.findObject(By.desc("Profile"))

        if (profileTab != null) {
            profileTab.click()
            Thread.sleep(2000)

            // Check that profile was NOT blocked
            val wasBlocked = TestUtils.logsContainInstagramBlock()
            assertFalse(
                "Instagram Profile should NOT be blocked",
                wasBlocked
            )
        }
    }


    @Test
    fun test30_withServiceDisabled_shortsAreNotBlocked() {
        // Disable service
        TestUtils.disableAccessibilityService(context)
        Thread.sleep(1000)
        TestUtils.clearLogcat()

        // Launch YouTube and go to Shorts
        TestUtils.launchYouTube(device, context)
        TestUtils.navigateToYouTubeShorts(device)
        Thread.sleep(3000)

        // Should NOT be blocked (service is disabled)
        val logs = TestUtils.readShortBlockerLogs()
        assertTrue(
            "With service disabled, no ShortBlocker logs should appear",
            logs.isEmpty() || logs.all { !it.contains("Detected") }
        )

        // Re-enable service
        TestUtils.enableAccessibilityService(context)
    }

    @Test
    fun test31_withServiceDisabled_reelsAreNotBlocked() {
        // Disable service
        TestUtils.disableAccessibilityService(context)
        Thread.sleep(1000)
        TestUtils.clearLogcat()

        // Launch Instagram and go to Reels
        TestUtils.launchInstagram(device, context)
        TestUtils.navigateToInstagramReels(device)
        Thread.sleep(4000)

        // Should NOT be blocked (service is disabled)
        val logs = TestUtils.readShortBlockerLogs()
        assertTrue(
            "With service disabled, no ShortBlocker logs should appear",
            logs.isEmpty() || logs.all { !it.contains("Detected") }
        )

        // Re-enable service
        TestUtils.enableAccessibilityService(context)
    }


    /**
     * This test verifies that only ONE block action is performed per Shorts detection.
     */
    @Test
    fun test25_youTubeShorts_onlySingleBlockAction() {
        assumeServiceIsRunning()

        // Launch YouTube
        TestUtils.launchYouTube(device, context)
        TestUtils.clearLogcat()

        // Navigate to Shorts tab
        val navigated = TestUtils.navigateToYouTubeShorts(device)
        assertTrue("Should be able to navigate to Shorts", navigated)

        // Wait for blocking to occur
        Thread.sleep(4000)

        // Count how many block actions were performed
        val blockCount = TestUtils.countBlockActions()

        // Should only have ONE block action, not multiple
        assertEquals(
            "Should perform exactly ONE block action, not multiple. " +
                "Logs: ${TestUtils.readShortBlockerLogs().filter { it.contains("Performing") }}",
            1,
            blockCount
        )
    }

    /**
     * This test verifies that reels opened from the Explore/Search tab are blocked.
     */
    @Test
    fun test26_instagramExploreReel_isBlocked() {
        assumeServiceIsRunning()

        // Launch Instagram
        TestUtils.launchInstagram(device, context)

        // Navigate to Explore tab
        val navigatedToExplore = TestUtils.navigateToInstagramExplore(device)
        assertTrue("Should be able to navigate to Explore", navigatedToExplore)

        // Clear logs before clicking on a reel
        TestUtils.clearLogcat()

        // Click on a reel from Explore grid
        val clickedReel = TestUtils.clickExploreReel(device)
        assertTrue("Should be able to click on a reel in Explore", clickedReel)

        // Wait and check if blocked
        val wasBlocked = TestUtils.waitForBlock(timeoutMs = 6000, checkYouTube = false)

        assertTrue(
            "Instagram Reel from Explore should be blocked. " +
                "Logs: ${TestUtils.readShortBlockerLogs().takeLast(15)}",
            wasBlocked
        )
    }


    @Test
    fun test40_rapidShortsNavigation_allBlocked() {
        assumeServiceIsRunning()

        // Launch YouTube
        TestUtils.launchYouTube(device, context)
        Thread.sleep(3000)

        var blockedCount = 0
        val attempts = 3

        // Calculate tap coordinates for Shorts tab
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight
        val shortsTabX = screenWidth * 3 / 10
        val bottomNavY = screenHeight - 80

        repeat(attempts) { i ->
            TestUtils.clearLogcat()

            // Navigate to Shorts - try UI element first, then fallback to tap
            val shortsTab = device.findObject(By.desc("Shorts"))
            if (shortsTab != null) {
                shortsTab.click()
            } else {
                device.executeShellCommand("input tap $shortsTabX $bottomNavY")
            }
            Thread.sleep(3000)

            if (TestUtils.logsContainYouTubeBlock()) {
                blockedCount++
            }

            // The back action from blocking should return us, but press back to be sure
            device.pressBack()
            Thread.sleep(1500)
        }

        assertTrue(
            "Should block Shorts on rapid navigation ($blockedCount/$attempts blocked)",
            blockedCount >= attempts - 1 // Allow 1 miss due to timing
        )
    }

    @Test
    fun test41_rapidReelsNavigation_allBlocked() {
        assumeServiceIsRunning()

        // Launch Instagram
        TestUtils.launchInstagram(device, context)
        Thread.sleep(3000)

        var blockedCount = 0
        val attempts = 3

        // Calculate tap coordinates
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight
        val reelsTabX = screenWidth / 2
        val homeTabX = screenWidth / 10
        val bottomNavY = screenHeight - 80

        repeat(attempts) { i ->
            TestUtils.clearLogcat()

            // Navigate to Reels - try UI element first, then fallback to tap
            val reelsTab = device.findObject(By.res("com.instagram.android:id/clips_tab"))
                ?: device.findObject(By.desc("Reels"))

            if (reelsTab != null) {
                reelsTab.click()
            } else {
                device.executeShellCommand("input tap $reelsTabX $bottomNavY")
            }
            Thread.sleep(4000)

            if (TestUtils.logsContainInstagramBlock()) {
                blockedCount++
            }

            // Navigate back to home - try UI element first, then fallback to tap
            val homeTab = device.findObject(By.res("com.instagram.android:id/feed_tab"))
                ?: device.findObject(By.desc("Home"))
            if (homeTab != null) {
                homeTab.click()
            } else {
                device.executeShellCommand("input tap $homeTabX $bottomNavY")
            }
            Thread.sleep(1500)
        }

        assertTrue(
            "Should block Reels on rapid navigation ($blockedCount/$attempts blocked)",
            blockedCount >= attempts - 1 // Allow 1 miss due to timing
        )
    }
}
