package com.shortsblocker.app.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Test utilities for ShortsBlocker automated testing.
 */
object TestUtils {

    private const val SERVICE_COMPONENT =
        "com.shortsblocker.app/com.shortsblocker.app.ShortBlockAccessibilityService"

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    private const val TIMEOUT_LONG = 10_000L
    private const val TIMEOUT_SHORT = 3_000L

    /**
     * Enable the ShortsBlocker accessibility service via shell command.
     * Uses UiDevice.executeShellCommand() which runs as shell user with proper permissions.
     * On Android 16+, we need to toggle the service to ensure it binds properly after APK reinstall.
     */
    fun enableAccessibilityService(context: Context) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // First disable completely to ensure clean state
        device.executeShellCommand("settings put secure enabled_accessibility_services null")
        device.executeShellCommand("settings put secure accessibility_enabled 0")
        Thread.sleep(500)
        // Now enable
        device.executeShellCommand("settings put secure enabled_accessibility_services $SERVICE_COMPONENT")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        // Give service time to start - longer wait for Android 16
        Thread.sleep(2000)
        // Verify service is running by checking logs
        val logs = device.executeShellCommand("logcat -d -s ShortBlocker:* | tail -5")
        if (!logs.contains("CONNECTED")) {
            // Try toggling again
            device.executeShellCommand("settings put secure enabled_accessibility_services null")
            Thread.sleep(500)
            device.executeShellCommand("settings put secure enabled_accessibility_services $SERVICE_COMPONENT")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            Thread.sleep(2000)
        }
    }

    /**
     * Disable the ShortsBlocker accessibility service via shell command.
     */
    fun disableAccessibilityService(context: Context) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("settings put secure enabled_accessibility_services \"\"")
    }

    /**
     * Check if the accessibility service is currently enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(SERVICE_COMPONENT)
    }

    /**
     * Check if the accessibility service is actually running (bound).
     * On Android 16+, a service can be enabled but not bound if consent wasn't given via Settings UI.
     */
    fun isAccessibilityServiceRunning(): Boolean {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Pipe doesn't work in executeShellCommand, so get full output and parse
        val output = device.executeShellCommand("dumpsys accessibility")
        // Look for our service in the Bound services section
        val boundServicesMatch = Regex("Bound services:\\{([^}]*)\\}").find(output)
        val boundServices = boundServicesMatch?.groupValues?.get(1) ?: ""
        return boundServices.contains("shortsblocker")
    }

    /**
     * Clear logcat buffer via shell command.
     */
    fun clearLogcat() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("logcat -c")
        Thread.sleep(100)
    }

    /**
     * Read recent logcat entries filtered by ShortBlocker tag via shell command.
     */
    fun readShortBlockerLogs(): List<String> {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val output = device.executeShellCommand("logcat -d -s ShortBlocker:*")
        return output.lines().filter { it.isNotBlank() }
    }

    /**
     * Check if logcat contains a blocking action for YouTube.
     */
    fun logsContainYouTubeBlock(): Boolean {
        val logs = readShortBlockerLogs()
        return logs.any {
            it.contains("Detected Shorts screen — blocking") ||
            it.contains("Detected Shorts screen — backing out")
        }
    }

    /**
     * Check if logcat contains a blocking action for Instagram.
     */
    fun logsContainInstagramBlock(): Boolean {
        val logs = readShortBlockerLogs()
        return logs.any {
            it.contains("Detected Instagram Reel — blocking") ||
            it.contains("Detected Instagram Reel — backing out")
        }
    }

    /**
     * Count how many times a block action was performed.
     */
    fun countBlockActions(): Int {
        val logs = readShortBlockerLogs()
        return logs.count { it.contains("Performing block action:") }
    }

    /**
     * Navigate to Instagram Explore tab.
     * Returns true if navigation was successful.
     */
    fun navigateToInstagramExplore(device: UiDevice): Boolean {
        // Wait for home to load
        Thread.sleep(2000)

        // Try to find Search/Explore tab by resource-id
        val searchTab = device.findObject(By.res("com.instagram.android:id/search_tab"))
            ?: device.findObject(By.desc("Search and explore"))

        if (searchTab != null) {
            searchTab.click()
            Thread.sleep(3000)
            return true
        }

        // Fallback: use shell command with approximate coordinates
        // Search tab is typically the 4th icon from left
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight
        device.executeShellCommand("input tap ${screenWidth * 7 / 10} ${screenHeight - 126}")
        Thread.sleep(3000)
        return true
    }

    /**
     * Click on a reel video from Instagram Explore grid.
     * Returns true if a reel was found and clicked.
     */
    fun clickExploreReel(device: UiDevice): Boolean {
        // Look for video indicators in explore grid
        // Videos typically have play icons or are marked as reels
        val reelItem = device.findObject(By.descContains("Reel"))
            ?: device.findObject(By.descContains("Video"))

        if (reelItem != null) {
            reelItem.click()
            Thread.sleep(4000)
            return true
        }

        // Fallback: tap on a grid item that's likely a video
        // Explore grid starts around y=100 (below search bar)
        // First row of items is at approximately y=200-400
        // Tap on first item in grid (left side, first row)
        val screenWidth = device.displayWidth
        device.executeShellCommand("input tap ${screenWidth / 6} 350")
        Thread.sleep(4000)
        return true
    }

    /**
     * Check if YouTube Shorts detection was negative (not blocked).
     */
    fun logsShowYouTubeNotBlocked(): Boolean {
        val logs = readShortBlockerLogs()
        val hasYouTubeEvent = logs.any { it.contains("YT event") }
        val wasBlocked = logsContainYouTubeBlock()
        return hasYouTubeEvent && !wasBlocked
    }

    /**
     * Check if Instagram Reels detection was negative (not blocked).
     */
    fun logsShowInstagramNotBlocked(): Boolean {
        val logs = readShortBlockerLogs()
        val hasInstagramEvent = logs.any { it.contains("IG SCREEN") }
        val wasBlocked = logsContainInstagramBlock()
        return hasInstagramEvent && !wasBlocked
    }

    /**
     * Launch YouTube app via shell command for reliability.
     */
    fun launchYouTube(device: UiDevice, context: Context) {
        // Force stop first to get clean state
        device.executeShellCommand("am force-stop $YOUTUBE_PACKAGE")
        Thread.sleep(500)
        // Launch via shell command
        device.executeShellCommand("am start -n $YOUTUBE_PACKAGE/.HomeActivity")
        device.wait(Until.hasObject(By.pkg(YOUTUBE_PACKAGE).depth(0)), TIMEOUT_LONG)
    }

    /**
     * Launch Instagram app via shell command for reliability.
     */
    fun launchInstagram(device: UiDevice, context: Context) {
        // Force stop first to get clean state
        device.executeShellCommand("am force-stop $INSTAGRAM_PACKAGE")
        Thread.sleep(500)
        // Launch via shell command
        device.executeShellCommand("am start -n $INSTAGRAM_PACKAGE/com.instagram.mainactivity.InstagramMainActivity")
        device.wait(Until.hasObject(By.pkg(INSTAGRAM_PACKAGE).depth(0)), TIMEOUT_LONG)
    }

    /**
     * Navigate to YouTube Shorts tab.
     * Returns true if navigation was successful.
     */
    fun navigateToYouTubeShorts(device: UiDevice): Boolean {
        // Wait for home to load
        Thread.sleep(3000)

        // Try multiple times to find and click Shorts tab
        repeat(3) { attempt ->
            // Try to find Shorts button by content-desc
            val shortsTab = device.findObject(By.desc("Shorts").clazz("android.widget.Button"))
                ?: device.findObject(By.desc("Shorts"))

            if (shortsTab != null && shortsTab.isClickable) {
                shortsTab.click()
                Thread.sleep(3000)
                return true
            }

            // Try to find a Short video from home feed
            val shortVideo = device.findObject(By.descContains("play Short"))
            if (shortVideo != null) {
                shortVideo.click()
                Thread.sleep(3000)
                return true
            }

            Thread.sleep(1000)
        }

        // Fallback: use shell command to tap on approximate Shorts tab location
        // YouTube pivot bar is at Y ~2274 (middle of 2211-2337 range)
        // Shorts button is at X ~324 (middle of 216-432 range)
        device.executeShellCommand("input tap 324 2274")
        Thread.sleep(3000)
        return true
    }

    /**
     * Navigate to Instagram Reels tab.
     * Returns true if navigation was successful.
     */
    fun navigateToInstagramReels(device: UiDevice): Boolean {
        // Wait for home to load
        Thread.sleep(3000)

        // Try multiple times to find and click Reels tab
        repeat(3) { attempt ->
            // Try to find Reels tab by resource-id
            val reelsTab = device.findObject(By.res("com.instagram.android:id/clips_tab"))
                ?: device.findObject(By.desc("Reels"))

            if (reelsTab != null) {
                reelsTab.click()
                Thread.sleep(4000) // Reels take time to load
                return true
            }

            Thread.sleep(1000)
        }

        // Fallback: use shell command with approximate coordinates
        // Instagram Reels is in the center of bottom nav
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight
        device.executeShellCommand("input tap ${screenWidth / 2} ${screenHeight - 126}")
        Thread.sleep(4000)
        return true
    }

    /**
     * Go back to home screen.
     */
    fun goHome(device: UiDevice) {
        device.pressHome()
        Thread.sleep(1000)
    }

    /**
     * Press back button.
     */
    fun pressBack(device: UiDevice) {
        device.pressBack()
        Thread.sleep(500)
    }

    /**
     * Wait for blocking to occur (checks logs periodically).
     */
    fun waitForBlock(timeoutMs: Long = 5000, checkYouTube: Boolean = true): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (checkYouTube && logsContainYouTubeBlock()) return true
            if (!checkYouTube && logsContainInstagramBlock()) return true
            Thread.sleep(500)
        }
        return false
    }

    /**
     * Get UiDevice instance.
     */
    fun getDevice(): UiDevice {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * Get test context.
     */
    fun getContext(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }
}
