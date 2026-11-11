package com.reelguard.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * ReelGuard's dashboard. The visual language borrows Streak's quiet dark surfaces,
 * rounded expressive cards, large headlines and violet accent without copying its code.
 */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(ReelGuardAccessibilityService.PREFS_NAME, MODE_PRIVATE) }

    private lateinit var serviceStatus: TextView
    private lateinit var serviceDetail: TextView
    private lateinit var serviceDot: View
    private lateinit var serviceCaption: TextView
    private lateinit var primaryAction: Button
    private lateinit var blockedToday: TextView
    private lateinit var reelsToday: TextView
    private lateinit var shortsToday: TextView
    private lateinit var masterSwitch: Switch
    private lateinit var instagramSwitch: Switch
    private lateinit var youtubeSwitch: Switch

    private val bg = Color.rgb(13, 13, 13)
    private val card = Color.rgb(25, 25, 27)
    private val raised = Color.rgb(34, 34, 37)
    private val white = Color.rgb(246, 246, 246)
    private val muted = Color.rgb(151, 151, 158)
    private val violet = Color.rgb(124, 58, 237)
    private val green = Color.rgb(52, 211, 153)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildDashboard()
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::serviceStatus.isInitialized) refreshDashboard()
    }

    private fun buildDashboard() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(36))
        }
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))

        content.addView(label("YOUR DIGITAL WELLBEING", 11, muted, true).apply {
            letterSpacing = 1.4f
        })

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val title = label("ReelGuard", 34, white, true).apply {
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(label("✳", 30, violet, true))
        content.addView(titleRow)
        content.addView(label("Make room for what matters.", 15, muted, false).apply {
            setPadding(0, dp(2), 0, dp(24))
        })

        val hero = columnCard(28, card).apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceDot = View(this).apply { background = rounded(muted, 50) }
        statusRow.addView(serviceDot, LinearLayout.LayoutParams(dp(9), dp(9)))
        serviceCaption = label("NEEDS SETUP", 11, muted, true).apply {
            letterSpacing = 1.1f
            setPadding(dp(8), 0, 0, 0)
        }
        statusRow.addView(serviceCaption)
        hero.addView(statusRow)

        serviceStatus = label("One small step to start.", 25, white, true).apply {
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            setPadding(0, dp(17), 0, dp(7))
        }
        hero.addView(serviceStatus)
        serviceDetail = label(
            "Turn on the accessibility service to let ReelGuard recognise Reels and Shorts screens.",
            14, muted, false
        )
        hero.addView(serviceDetail)

        primaryAction = Button(this).apply {
            text = "Enable protection"
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(violet, 18)
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener { handlePrimaryAction() }
        }
        hero.addView(primaryAction, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(20)
        })
        content.addView(hero)

        content.addView(label("TODAY", 11, muted, true).apply {
            letterSpacing = 1.3f
            setPadding(dp(2), dp(27), 0, dp(10))
        })
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("✦", "0", "Blocked"), LinearLayout.LayoutParams(0, dp(112), 1f))
        stats.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        stats.addView(statCard("▣", "0", "Instagram"), LinearLayout.LayoutParams(0, dp(112), 1f))
        stats.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        stats.addView(statCard("▶", "0", "YouTube"), LinearLayout.LayoutParams(0, dp(112), 1f))
        content.addView(stats)

        content.addView(label("YOUR GUARDS", 11, muted, true).apply {
            letterSpacing = 1.3f
            setPadding(dp(2), dp(28), 0, dp(10))
        })

        val guardCard = columnCard(24, card).apply {
            setPadding(dp(18), dp(6), dp(18), dp(6))
        }
        masterSwitch = addSwitchRow(guardCard, "Protection", "Master switch for both guards", true) { checked ->
            prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_GUARD_ENABLED, checked).apply()
            refreshDashboard()
        }
        addThinDivider(guardCard)
        instagramSwitch = addSwitchRow(
            guardCard, "Instagram Reels", "Keep the normal feed and messages", true
        ) { checked ->
            prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_BLOCK_INSTAGRAM, checked).apply()
        }
        addThinDivider(guardCard)
        youtubeSwitch = addSwitchRow(
            guardCard, "YouTube Shorts", "Keep regular YouTube videos available", true
        ) { checked ->
            prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_BLOCK_YOUTUBE, checked).apply()
        }
        content.addView(guardCard)

        content.addView(label("HOW IT WORKS", 11, muted, true).apply {
            letterSpacing = 1.3f
            setPadding(dp(2), dp(28), 0, dp(10))
        })
        val infoCard = columnCard(24, card).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        infoCard.addView(label("Only the short-form screen is covered.", 17, white, true))
        infoCard.addView(label(
            "ReelGuard checks accessibility interface identifiers on your phone. It does not record your screen, read your messages, or send browsing activity to a server.",
            13, muted, false
        ).apply { setPadding(0, dp(8), 0, dp(14)) })
        infoCard.addView(label(
            "Instagram and YouTube can change their internal screen layouts. If that happens, detection may need an update.",
            12, muted, false
        ))
        content.addView(infoCard)

        content.addView(label("Made for fewer scrolls and more life.", 12, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(25), 0, 0)
        })
        setContentView(scroll)
    }

    private fun statCard(symbol: String, value: String, caption: String): LinearLayout {
        val box = columnCard(20, card).apply {
            setPadding(dp(13), dp(12), dp(10), dp(10))
        }
        box.addView(label(symbol, 17, violet, true))
        val number = label(value, 25, white, true).apply {
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        when (caption) {
            "Blocked" -> blockedToday = number
            "Instagram" -> reelsToday = number
            "YouTube" -> shortsToday = number
        }
        box.addView(number)
        box.addView(label(caption, 11, muted, false))
        return box
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        defaultValue: Boolean,
        onChanged: (Boolean) -> Unit
    ): Switch {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(11), 0, dp(11))
        }
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(label(title, 15, white, true))
        textColumn.addView(label(subtitle, 12, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        val toggle = Switch(this).apply {
            isChecked = prefs.getBoolean(keyFor(title), defaultValue)
            setOnCheckedChangeListener { _: CompoundButton, checked -> onChanged(checked) }
        }
        row.addView(toggle)
        parent.addView(row)
        return toggle
    }

    private fun keyFor(title: String): String = when (title) {
        "Protection" -> ReelGuardAccessibilityService.KEY_GUARD_ENABLED
        "Instagram Reels" -> ReelGuardAccessibilityService.KEY_BLOCK_INSTAGRAM
        else -> ReelGuardAccessibilityService.KEY_BLOCK_YOUTUBE
    }

    private fun addThinDivider(parent: LinearLayout) {
        parent.addView(
            View(this).apply { setBackgroundColor(raised) },
            LinearLayout.LayoutParams(-1, dp(1))
        )
    }

    private fun refreshDashboard() {
        ReelGuardAccessibilityService.refreshDailyCounter(this)
        val serviceEnabled = ReelGuardAccessibilityService.isEnabled(this)
        val masterEnabled = prefs.getBoolean(ReelGuardAccessibilityService.KEY_GUARD_ENABLED, true)
        val running = serviceEnabled && masterEnabled

        serviceCaption.text = when {
            !serviceEnabled -> "NEEDS SETUP"
            masterEnabled -> "PROTECTION ACTIVE"
            else -> "PROTECTION PAUSED"
        }
        serviceCaption.setTextColor(if (running) green else muted)
        serviceDot.background = rounded(if (running) green else muted, 50)

        serviceStatus.text = when {
            !serviceEnabled -> "One small step to start."
            masterEnabled -> "Your focus is protected."
            else -> "Protection is paused."
        }
        serviceDetail.text = when {
            !serviceEnabled -> "Enable ReelGuard in Android Accessibility settings. It only checks Instagram and YouTube screen layouts."
            masterEnabled -> "Reels and Shorts are covered when their dedicated viewer is detected."
            else -> "Turn the master switch back on whenever you're ready."
        }
        primaryAction.text = when {
            !serviceEnabled -> "Enable accessibility service"
            masterEnabled -> "Pause protection"
            else -> "Resume protection"
        }
        primaryAction.background = rounded(if (running) raised else violet, 18)

        masterSwitch.setOnCheckedChangeListener(null)
        masterSwitch.isChecked = masterEnabled
        masterSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_GUARD_ENABLED, checked).apply()
            refreshDashboard()
        }
        instagramSwitch.setOnCheckedChangeListener(null)
        instagramSwitch.isChecked = prefs.getBoolean(ReelGuardAccessibilityService.KEY_BLOCK_INSTAGRAM, true)
        instagramSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_BLOCK_INSTAGRAM, checked).apply()
        }
        youtubeSwitch.setOnCheckedChangeListener(null)
        youtubeSwitch.isChecked = prefs.getBoolean(ReelGuardAccessibilityService.KEY_BLOCK_YOUTUBE, true)
        youtubeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_BLOCK_YOUTUBE, checked).apply()
        }

        blockedToday.text = prefs.getInt(ReelGuardAccessibilityService.KEY_BLOCKED_TODAY, 0).toString()
        reelsToday.text = prefs.getInt(ReelGuardAccessibilityService.KEY_INSTAGRAM_TODAY, 0).toString()
        shortsToday.text = prefs.getInt(ReelGuardAccessibilityService.KEY_YOUTUBE_TODAY, 0).toString()
    }

    private fun handlePrimaryAction() {
        if (!ReelGuardAccessibilityService.isEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val next = !prefs.getBoolean(ReelGuardAccessibilityService.KEY_GUARD_ENABLED, true)
        prefs.edit().putBoolean(ReelGuardAccessibilityService.KEY_GUARD_ENABLED, next).apply()
        refreshDashboard()
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun columnCard(radius: Int, color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, radius)
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
