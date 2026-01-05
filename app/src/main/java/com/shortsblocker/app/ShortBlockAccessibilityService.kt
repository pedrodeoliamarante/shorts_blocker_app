package com.shortsblocker.app

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock

class ShortBlockAccessibilityService : AccessibilityService() {
    private var lastInstagramReelClickTime: Long = 0L
    private var lastInstagramExploreClickTime: Long = 0L
    private var lastBlockTime: Long = 0L

    companion object {
        private const val TAG = "ShortBlocker"
        // Cooldown period to prevent double-blocking
        private const val BLOCK_COOLDOWN_MS = 1000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString() ?: return

        when (pkg) {
            "com.google.android.youtube" -> handleYoutube(event)
            "com.instagram.android"      -> handleInstagram(event)
            else                         -> Unit
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service INTERRUPTED")
    }



    /**
     * Perform the configured block action based on user preferences.
     * Returns true if action was performed, false if still in cooldown.
     */
    private fun performBlockAction(): Boolean {
        val now = SystemClock.uptimeMillis()
        val timeSinceLastBlock = now - lastBlockTime

        // Prevent double-blocking by enforcing cooldown period
        if (timeSinceLastBlock < BLOCK_COOLDOWN_MS) {
            Log.d(TAG, "Block action skipped - cooldown active (${timeSinceLastBlock}ms since last block)")
            return false
        }

        val action = AppPreferences.getBlockAction(this)
        val globalAction = when (action) {
            AppPreferences.BlockAction.BACK -> GLOBAL_ACTION_BACK
            AppPreferences.BlockAction.HOME -> GLOBAL_ACTION_HOME
            AppPreferences.BlockAction.RECENTS -> GLOBAL_ACTION_RECENTS
        }
        Log.d(TAG, "Performing block action: $action")
        lastBlockTime = now
        performGlobalAction(globalAction)
        return true
    }



    private fun handleYoutube(event: AccessibilityEvent) {
        val type = event.eventType
        val className = event.className?.toString()

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        val texts = collectAllTexts(root)

        Log.d(
            TAG,
            "YT event type=$type class=$className sample=${texts.take(10)}"
        )

        if (isYoutubeShortsScreen(texts)) {
            Log.d(TAG, "Detected Shorts screen — blocking")
            performBlockAction()
        }
    }

    private fun isYoutubeShortsScreen(texts: List<String>): Boolean {
        val lower = texts.map { it.lowercase() }

        val hasGoToChannel = lower.any { it.contains("go to channel") }
        val hasSubscribeTo = lower.any { it.contains("subscribe to") }
        val hasHandle      = texts.any { it.contains("@") }

        val looksLikeFullVideo = lower.any {
            it.contains("video player") && it.contains("play video")
        }

        Log.d(
            TAG,
            "isShortsScreen: goToChannel=$hasGoToChannel " +
                    "subscribeTo=$hasSubscribeTo handle=$hasHandle fullVideo=$looksLikeFullVideo"
        )

        return !looksLikeFullVideo &&
                hasGoToChannel &&
                hasSubscribeTo &&
                hasHandle
    }



    private fun handleInstagram(event: AccessibilityEvent) {
        val type = event.eventType
        val className = event.className?.toString()

        // Keep click logs around for future tuning.
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val src = event.source
            val text = src?.text?.toString()
            val desc = src?.contentDescription?.toString()
            val viewId = src?.viewIdResourceName

            Log.d(
                TAG,
                "IG CLICK: class=$className viewId=$viewId text=$text desc=$desc"
            )

            // Mark clicks that likely open reels
            val clickedReelsTab =
                viewId == "com.instagram.android:id/clips_tab" ||
                        desc?.contains("Reels", ignoreCase = true) == true

            val clickedReelCard =
                text?.contains("Reel by", ignoreCase = true) == true ||
                        desc?.contains("Reel by", ignoreCase = true) == true

            if (clickedReelsTab || clickedReelCard) {
                lastInstagramReelClickTime = SystemClock.uptimeMillis()
                Log.d(TAG, "IG: marked reel click at $lastInstagramReelClickTime")
            }

            // Track Explore tab and Explore item clicks
            val clickedExploreTab =
                viewId == "com.instagram.android:id/search_tab" ||
                        desc?.contains("Search and explore", ignoreCase = true) == true

            // Explore grid items are often ImageViews without specific IDs
            // Track any click that happens after recently navigating to Explore
            val now = SystemClock.uptimeMillis()
            val recentlyOnExplore = (now - lastInstagramExploreClickTime) < 30000 // 30 second window

            if (clickedExploreTab) {
                lastInstagramExploreClickTime = now
                Log.d(TAG, "IG: marked explore tab click at $lastInstagramExploreClickTime")
            } else if (recentlyOnExplore && className?.contains("ImageView") == true) {
                // Clicking an image while on Explore likely opens a reel/post
                lastInstagramReelClickTime = now
                Log.d(TAG, "IG: marked explore item click (ImageView) at $now")
            }
            // don't return here; the same event might also cause content change
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val root = rootInActiveWindow ?: return
        val texts = collectAllTexts(root)

        Log.d(
            TAG,
            "IG SCREEN: type=$type class=$className sample=${texts.take(12)}"
        )

        // Pass event type and className for additional context in detection
        // TYPE_WINDOW_STATE_CHANGED (32) indicates a new window/screen
        val isWindowStateChange = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (isInstagramReelsScreen(texts, className, isWindowStateChange)) {
            Log.d(TAG, "Detected Instagram Reel — blocking")
            performBlockAction()
        }
    }

    /**
     * Heuristic for Reels:
     * - Full-screen Reels viewer has text like:
     *     "Reel by <user>. Double tap to play or pause."
     *     "Like number is..."
     *     "Comment number is..."
     * - Home feed / stories tray has:
     *     "reels tray container"
     *     "<user> posted a photo / carousel / video"
     *
     * So we:
     * - Require the strong reel phrases, AND
     * - Explicitly *exclude* feed/tray context.
     *
     * @param texts Collected texts from the accessibility tree
     * @param eventClass The class name of the view that triggered the event
     * @param isWindowStateChange True if this is a TYPE_WINDOW_STATE_CHANGED event
     */
    private fun isInstagramReelsScreen(
        texts: List<String>,
        eventClass: String?,
        isWindowStateChange: Boolean
    ): Boolean {
        if (texts.isEmpty()) return false

        val now = SystemClock.uptimeMillis()
        val sinceClick = now - lastInstagramReelClickTime
        val recentReelClick = sinceClick in 0..1500  // ~1.5s window

        val joinedLower = texts.joinToString(" ").lowercase()

        // Reel viewer markers
        val reelBy = joinedLower.contains("reel by ")
        val doubleTap = joinedLower.contains("double tap to play or pause")
        val likeInfo =
            joinedLower.contains("like number is") ||
                    joinedLower.contains("view likes")
        val commentInfo =
            joinedLower.contains("comment number is") ||
                    joinedLower.contains("view comments")

        val looksLikeReelViewer = reelBy && (doubleTap || likeInfo || commentInfo)

        // Strong reel viewer: has multiple engagement indicators (more confident detection)
        val strongReelViewer = reelBy && doubleTap && (likeInfo || commentInfo)

        // "Context" markers for tray / home feed
        val hasTray = joinedLower.contains("reels tray container")
        val hasHome = joinedLower.contains("instagram home feed")

        // Video playback UI signal - SeekBar indicates active video player
        val isVideoPlaybackEvent = eventClass?.contains("SeekBar") == true

        // Block if:
        //  - It looks like a reel viewer AND one of:
        //      - it's right after a reel-related click (Reels tab / reel tap), OR
        //      - there is no tray/home text (pure viewer), OR
        //      - we have strong reel viewer signals (reelBy + doubleTap + engagement)
        //        This combination ONLY appears in full-screen reel viewer, never on home feed.
        //        The home feed shows reelBy=false on fresh launch, even with reels tray.
        //        This handles all cases: Reels tab, Explore → Reel, direct reel links.
        val shouldBlock = looksLikeReelViewer && (
            recentReelClick ||
            (!hasTray && !hasHome) ||
            strongReelViewer
        )

        Log.d(
            TAG,
            "isInstagramReelsScreen: reelBy=$reelBy doubleTap=$doubleTap " +
                    "likeInfo=$likeInfo commentInfo=$commentInfo " +
                    "hasTray=$hasTray hasHome=$hasHome " +
                    "windowChange=$isWindowStateChange seekBar=$isVideoPlaybackEvent " +
                    "strongViewer=$strongReelViewer recentClick=$recentReelClick => $shouldBlock"
        )

        return shouldBlock
    }



    private fun collectAllTexts(root: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            node.text?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { out += it }

            node.contentDescription?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { out += it }

            for (i in 0 until node.childCount) {
                dfs(node.getChild(i))
            }
        }

        dfs(root)
        return out
    }
}
