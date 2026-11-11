package com.reelguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects Instagram's actual full-screen Reels viewer from its visible accessibility
 * resource IDs, then covers that screen with a touch-blocking accessibility overlay.
 */
class ReelGuardAccessibilityService : AccessibilityService() {
    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val PREFS_NAME = "reelguard_prefs"
        const val BLOCKED_COUNT_KEY = "blocked_count"

        // Instagram's internal IDs can change between releases. Keep the list centralized.
        private val REEL_VIEW_ID_MARKERS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_viewer_root",
            "clips_video_container",
            "clips_recycler_view",
            "reel_viewer_fragment",
            "reel_viewer_container",
            "reel_viewer_root"
        )

        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + ReelGuardAccessibilityService::class.java.name
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlay: ReelBlockOverlay
    private var isReelBlocked = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (!isReelBlocked || !::overlay.isInitialized) return
            val root = rootInActiveWindow
            val stillOnReel = root != null &&
                root.packageName?.toString() == INSTAGRAM_PACKAGE &&
                containsVisibleReelViewer(root, 0)

            if (stillOnReel) {
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            } else {
                clearBlock()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = ReelBlockOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::overlay.isInitialized) return

        val eventPackage = event.packageName?.toString()
        if (eventPackage != INSTAGRAM_PACKAGE) {
            // The service listens for window changes outside Instagram too, so the panel
            // is removed immediately when the user leaves Instagram.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                clearBlock()
            }
            return
        }

        val root = rootInActiveWindow ?: return
        val onReel = containsVisibleReelViewer(root, 0)

        if (onReel) {
            if (!isReelBlocked) {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit()
                    .putInt(BLOCKED_COUNT_KEY, prefs.getInt(BLOCKED_COUNT_KEY, 0) + 1)
                    .apply()
            }

            isReelBlocked = true
            overlay.show {
                // Keep the panel in place until the viewer actually disappears.
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.removeCallbacks(watchdog)
                mainHandler.postDelayed(watchdog, 250L)
            }
            mainHandler.removeCallbacks(watchdog)
            mainHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        } else {
            clearBlock()
        }
    }

    private fun containsVisibleReelViewer(node: AccessibilityNodeInfo, depth: Int): Boolean {
        val resourceId = node.viewIdResourceName
            ?.substringAfter('/')
            ?.lowercase()

        if (node.isVisibleToUser && resourceId != null &&
            REEL_VIEW_ID_MARKERS.any { marker -> resourceId.contains(marker) }) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) return true
        }

        if (depth >= MAX_TREE_DEPTH) return false

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (containsVisibleReelViewer(child, depth + 1)) return true
        }
        return false
    }

    private fun clearBlock() {
        mainHandler.removeCallbacks(watchdog)
        if (::overlay.isInitialized) overlay.hide()
        isReelBlocked = false
    }

    override fun onInterrupt() {
        clearBlock()
    }

    override fun onDestroy() {
        clearBlock()
        super.onDestroy()
    }

    private companion object {
        const val MAX_TREE_DEPTH = 70
        const val WATCHDOG_INTERVAL_MS = 650L
    }
}
