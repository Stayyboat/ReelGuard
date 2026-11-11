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

        // Only react to a real tap. This avoids blocking a Reel that was opened from a DM
        // merely because the word "Reels" appears somewhere in Instagram's interface.
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val screenHeight = resources.displayMetrics.heightPixels
        val clickedNode = event.source
        val isReelsTabTap = clickedNode?.let { isReelsNavigationNode(it, screenHeight) } == true
        clickedNode?.recycle()

        if (!isReelsTabTap) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(LAST_BLOCK_KEY, 0L)
        if (now - last < 1800L) return

        prefs.edit()
            .putLong(LAST_BLOCK_KEY, now)
            .putInt(BLOCKED_COUNT_KEY, prefs.getInt(BLOCKED_COUNT_KEY, 0) + 1)
            .apply()

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun isReelsNavigationNode(start: AccessibilityNodeInfo, screenHeight: Int): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(start)
        var depth = 0
        var foundReelsLabel = false
        var foundClickableAncestor = false
        var bottomNavigationBounds = false

        try {
            while (current != null && depth < 8) {
                val node = current
                val label = listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString(),
                    node.viewIdResourceName
                ).joinToString(" ").lowercase()

                if (label == "reels" || label.contains("reels_tab") ||
                    label.contains("reels tab") || label == "reel") {
                    foundReelsLabel = true
                }
                if (node.isClickable) foundClickableAncestor = true

                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty && bounds.top >= (screenHeight * 0.62f) &&
                    bounds.bottom <= screenHeight + 8) {
                    bottomNavigationBounds = true
                }

                val parent = node.parent
                node.recycle()
                current = parent
                depth++
            }
        } finally {
            current?.recycle()
        }

        return foundReelsLabel && foundClickableAncestor && bottomNavigationBounds
    }

    override fun onInterrupt() = Unit
}
