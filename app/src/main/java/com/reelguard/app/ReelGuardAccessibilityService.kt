package com.reelguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelGuardAccessibilityService : AccessibilityService() {
    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val PREFS_NAME = "reelguard_prefs"
        const val BLOCKED_COUNT_KEY = "blocked_count"
        private const val LAST_BLOCK_KEY = "last_block_ms"

        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + ReelGuardAccessibilityService::class.java.name
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val root = rootInActiveWindow ?: return
        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels
        if (containsReelsBottomTab(root, screenHeight)) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong(LAST_BLOCK_KEY, 0L)
            if (now - last < 2500L) return
            prefs.edit().putLong(LAST_BLOCK_KEY, now)
                .putInt(BLOCKED_COUNT_KEY, prefs.getInt(BLOCKED_COUNT_KEY, 0) + 1).apply()
            val intent = Intent(this, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    private fun containsReelsBottomTab(node: AccessibilityNodeInfo, screenHeight: Int): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val label = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
        val isReelsLabel = label.equals("Reels", ignoreCase = true)
        val inBottomNavigation = bounds.top >= (screenHeight * 0.72f) && bounds.bottom <= screenHeight
        if (isReelsLabel && inBottomNavigation && node.isClickable) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = containsReelsBottomTab(child, screenHeight)
            child.recycle()
            if (found) return true
        }
        return false
    }

    override fun onInterrupt() = Unit
}