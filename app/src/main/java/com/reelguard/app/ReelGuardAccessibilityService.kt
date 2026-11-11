package com.reelguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device screen detector for the dedicated Instagram Reels and YouTube Shorts viewers.
 * No screenshots, message text, input content or network traffic are used.
 */
class ReelGuardAccessibilityService : AccessibilityService() {
    companion object {
        const val PREFS_NAME = "reelguard_prefs"
        const val KEY_GUARD_ENABLED = "guard_enabled"
        const val KEY_BLOCK_INSTAGRAM = "block_instagram_reels"
        const val KEY_BLOCK_YOUTUBE = "block_youtube_shorts"
        const val KEY_BLOCKED_TODAY = "blocked_today"
        const val KEY_INSTAGRAM_TODAY = "blocked_instagram_today"
        const val KEY_YOUTUBE_TODAY = "blocked_youtube_today"
        const val KEY_COUNTER_DATE = "counter_date"

        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        private const val MAX_TREE_DEPTH = 42
        private const val MAX_TREE_NODES = 900
        private const val MIN_SURFACE_AREA_RATIO = 0.18f

        private val instagramViewerMarkers = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_viewer_root",
            "clips_video_container",
            "clips_recycler_view",
            "reel_viewer_fragment",
            "reel_viewer_container",
            "reel_viewer_root",
            "reel_viewer_pager"
        )

        private val youtubeShortsMarkers = listOf(
            "shorts_container",
            "shorts_player",
            "shorts_video_container",
            "shorts_recycler_view",
            "reel_watch_page",
            "reel_watch_player",
            "reel_player_page",
            "reel_recycler_view",
            "reel_watch_container",
            "shorts_view_pager"
        )

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${ReelGuardAccessibilityService::class.java.name}"
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun refreshDailyCounter(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            if (prefs.getString(KEY_COUNTER_DATE, null) != today) {
                prefs.edit()
                    .putString(KEY_COUNTER_DATE, today)
                    .putInt(KEY_BLOCKED_TODAY, 0)
                    .putInt(KEY_INSTAGRAM_TODAY, 0)
                    .putInt(KEY_YOUTUBE_TODAY, 0)
                    .apply()
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlay: ReelBlockOverlay
    private var blockedPackage: String? = null
    private var scanPending = false
    private var nodeCount = 0

    private val scanRunnable = Runnable {
        scanPending = false
        inspectActiveWindow()
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (blockedPackage == null) return
            inspectActiveWindow()
            if (blockedPackage != null) mainHandler.postDelayed(this, 800L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = ReelBlockOverlay(this)
        refreshDailyCounter(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::overlay.isInitialized) return
        val packageName = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName != INSTAGRAM_PACKAGE && packageName != YOUTUBE_PACKAGE) {
            mainHandler.removeCallbacks(scanRunnable)
            mainHandler.post { clearBlock() }
            return
        }
        if (packageName != INSTAGRAM_PACKAGE && packageName != YOUTUBE_PACKAGE) return

        // Coalesce rapid accessibility updates. Window-state events get a shorter delay
        // so the destination is ready before the first scan.
        mainHandler.removeCallbacks(scanRunnable)
        scanPending = true
        mainHandler.postDelayed(
            scanRunnable,
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 100L else 170L
        )
    }

    private fun inspectActiveWindow() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_GUARD_ENABLED, true)) {
            clearBlock()
            return
        }

        val root = activeTargetRoot()
        val packageName = root?.packageName?.toString()
        if (root == null || (packageName != INSTAGRAM_PACKAGE && packageName != YOUTUBE_PACKAGE)) {
            clearBlock()
            return
        }

        val platform = when (packageName) {
            INSTAGRAM_PACKAGE -> if (prefs.getBoolean(KEY_BLOCK_INSTAGRAM, true)) "instagram" else null
            YOUTUBE_PACKAGE -> if (prefs.getBoolean(KEY_BLOCK_YOUTUBE, true)) "youtube" else null
            else -> null
        }
        if (platform == null) {
            clearBlock()
            return
        }

        val detected = when (platform) {
            "instagram" -> hasLargeViewerNode(root, instagramViewerMarkers)
            else -> hasLargeViewerNode(root, youtubeShortsMarkers)
        }

        if (detected) {
            if (blockedPackage != packageName) {
                incrementCounter(platform)
                blockedPackage = packageName
                overlay.show(platform) { leaveShortFormViewer() }
                mainHandler.removeCallbacks(watchdog)
                mainHandler.postDelayed(watchdog, 800L)
            }
        } else {
            clearBlock()
        }
    }

    /** Prefer the target app's application window even while our accessibility overlay is visible. */
    private fun activeTargetRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        val activePackage = active?.packageName?.toString()
        if (active != null && (activePackage == INSTAGRAM_PACKAGE || activePackage == YOUTUBE_PACKAGE)) {
            return active
        }
        return runCatching {
            windows.asSequence()
                .mapNotNull { window -> window.root }
                .firstOrNull { root ->
                    val packageName = root.packageName?.toString()
                    packageName == INSTAGRAM_PACKAGE || packageName == YOUTUBE_PACKAGE
                }
        }.getOrNull()
    }

    /**
     * A match must be visible and live inside a substantial on-screen container. This
     * avoids treating small navigation labels or inline thumbnails as full-screen feeds.
     */
    private fun hasLargeViewerNode(root: AccessibilityNodeInfo, markers: List<String>): Boolean {
        nodeCount = 0
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        return walkTree(root, markers, 0, false, screenWidth, screenHeight)
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        markers: List<String>,
        depth: Int,
        hasLargeAncestor: Boolean,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (depth > MAX_TREE_DEPTH || nodeCount++ > MAX_TREE_NODES) return false

        val bounds = Rect()
        val area = runCatching {
            node.getBoundsInScreen(bounds)
            bounds.width().coerceAtLeast(0).toLong() * bounds.height().coerceAtLeast(0).toLong()
        }.getOrDefault(0L)
        val screenArea = screenWidth.toLong() * screenHeight.toLong()
        val isLarge = area >= (screenArea * MIN_SURFACE_AREA_RATIO).toLong()
        val largeContext = hasLargeAncestor || isLarge

        val id = runCatching { node.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty() }.getOrDefault("")
        val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
        if (visible && largeContext && id.isNotEmpty() && markers.any { id.contains(it) }) {
            return true
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            if (walkTree(child, markers, depth + 1, largeContext, screenWidth, screenHeight)) return true
        }
        return false
    }

    private fun incrementCounter(platform: String) {
        refreshDailyCounter(this)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit().putInt(KEY_BLOCKED_TODAY, prefs.getInt(KEY_BLOCKED_TODAY, 0) + 1)
        if (platform == "instagram") {
            editor.putInt(KEY_INSTAGRAM_TODAY, prefs.getInt(KEY_INSTAGRAM_TODAY, 0) + 1)
        } else {
            editor.putInt(KEY_YOUTUBE_TODAY, prefs.getInt(KEY_YOUTUBE_TODAY, 0) + 1)
        }
        editor.apply()
    }

    private fun leaveShortFormViewer() {
        // The overlay stays until the target viewer disappears, so scrolling cannot
        // resume behind it. Android's global Back action is the least intrusive exit.
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({ inspectActiveWindow() }, 350L)
    }

    private fun clearBlock() {
        mainHandler.removeCallbacks(watchdog)
        blockedPackage = null
        if (::overlay.isInitialized) overlay.hide()
    }

    override fun onInterrupt() {
        clearBlock()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(scanRunnable)
        mainHandler.removeCallbacks(watchdog)
        clearBlock()
        super.onDestroy()
    }
}
