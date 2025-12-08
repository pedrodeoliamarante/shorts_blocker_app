package com.example.shortsblockernative

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
class ShortBlockAccessibilityService : AccessibilityService() {
    private var lastInstagramReelClickTime: Long = 0L
    companion object {
        private const val TAG = "ShortBlocker"
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

    // ---------------- YOUTUBE LOGIC ----------------

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
            Log.d(TAG, "Detected Shorts screen — backing out")
            performGlobalAction(GLOBAL_ACTION_BACK)
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

    // ---------------- INSTAGRAM LOGIC ----------------


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
                "ShortBlocker",
                "IG CLICK: class=$className viewId=$viewId text=$text desc=$desc"
            )

            // 🔹 Mark clicks that likely open reels
            val clickedReelsTab =
                viewId == "com.instagram.android:id/clips_tab" ||
                        desc?.contains("Reels", ignoreCase = true) == true

            val clickedReelCard =
                text?.contains("Reel by", ignoreCase = true) == true ||
                        desc?.contains("Reel by", ignoreCase = true) == true

            if (clickedReelsTab || clickedReelCard) {
                lastInstagramReelClickTime = SystemClock.uptimeMillis()
                Log.d("ShortBlocker", "IG: marked reel click at $lastInstagramReelClickTime")
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
            "ShortBlocker",
            "IG SCREEN: type=$type class=$className sample=${texts.take(12)}"
        )

        if (isInstagramReelsScreen(texts)) {
            Log.d("ShortBlocker", "Detected Instagram Reel — backing out")
            performGlobalAction(GLOBAL_ACTION_BACK)
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
     */


    private fun isInstagramReelsScreen(texts: List<String>): Boolean {
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

        // “Context” markers for tray / home feed
        val hasTray = joinedLower.contains("reels tray container")
        val hasHome = joinedLower.contains("instagram home feed")

        // Block if:
        //  - It looks like a reel viewer AND
        //      - either it’s right after a reel-related click (Reels tab / reel tap), OR
        //      - there is no tray/home text (pure viewer, like in your jamesgunn logs)
        val shouldBlock =
            looksLikeReelViewer && (recentReelClick || (!hasTray && !hasHome))

        Log.d(
            "ShortBlocker",
            "isInstagramReelsScreen: reelBy=$reelBy doubleTap=$doubleTap " +
                    "likeInfo=$likeInfo commentInfo=$commentInfo " +
                    "hasTray=$hasTray hasHome=$hasHome " +
                    "recentReelClick=$recentReelClick (sinceClick=$sinceClick) " +
                    "=> $shouldBlock"
        )

        return shouldBlock
    }

    // ---------------- SHARED HELPERS ----------------

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
