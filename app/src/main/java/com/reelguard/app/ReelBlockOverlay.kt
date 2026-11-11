package com.reelguard.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Full-window, touch-consuming blocker rendered by the AccessibilityService. */
class ReelBlockOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null

    fun show(platform: String, onBack: () -> Unit) {
        if (rootView != null) return
        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun shape(color: Int, radius: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

        val platformName = if (platform == "instagram") "Instagram Reels" else "YouTube Shorts"
        val root = FrameLayout(service).apply {
            setBackgroundColor(Color.rgb(13, 13, 13))
            isClickable = true
            isFocusable = true
            contentDescription = "ReelGuard blocking screen"
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(30), dp(26), dp(26))
            background = shape(Color.rgb(25, 25, 27), 30)
            elevation = dp(14).toFloat()
        }

        card.addView(TextView(service).apply {
            text = "✳"
            setTextColor(Color.rgb(167, 139, 250))
            textSize = 38f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(TextView(service).apply {
            text = "Not this time."
            setTextColor(Color.rgb(246, 246, 246))
            textSize = 29f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(TextView(service).apply {
            text = "$platformName is paused. You opened it for a moment — don't let that moment become an endless scroll."
            setTextColor(Color.rgb(168, 168, 176))
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(12), 0, dp(22))
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(Button(service).apply {
            text = "Back to my day"
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = shape(Color.rgb(124, 58, 237), 18)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(-1, dp(54)))

        card.addView(TextView(service).apply {
            text = "YOUR FOCUS IS WORTH PROTECTING"
            setTextColor(Color.rgb(125, 125, 135))
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = 0.11f
            setPadding(0, dp(18), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching { windowManager.addView(root, params) }
            .onSuccess { rootView = root }
    }

    fun hide() {
        val view = rootView ?: return
        runCatching { windowManager.removeView(view) }
        rootView = null
    }
}
