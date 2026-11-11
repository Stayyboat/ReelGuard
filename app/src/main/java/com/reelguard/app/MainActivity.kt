package com.reelguard.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusDot: View
    private lateinit var enableButton: Button
    private lateinit var blockedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 247, 243)
        window.navigationBarColor = Color.rgb(246, 247, 243)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(R.layout.activity_main)

        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        statusDot = findViewById(R.id.statusDot)
        enableButton = findViewById(R.id.enableButton)
        blockedCount = findViewById(R.id.blockedCount)

        enableButton.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.openInstagramButton).setOnClickListener { openInstagram() }
        findViewById<Button>(R.id.followingButton).setOnClickListener { showFollowingGuide() }
        findViewById<Button>(R.id.previewButton).setOnClickListener { showPreview() }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusTitle.isInitialized) refreshStatus()
    }

    private fun refreshStatus() {
        val active = ReelGuardAccessibilityService.isEnabled(this)
        statusTitle.text = if (active) "Protection is on" else "Protection is off"
        statusDetail.text = if (active) "ReelGuard is watching for the Instagram Reels tab."
            else "Turn on ReelGuard in Android Accessibility settings to block the Reels tab."
        statusDot.setBackgroundResource(if (active) R.drawable.bg_status_dot_on else R.drawable.bg_status_dot_off)
        enableButton.text = if (active) "Accessibility settings" else "Enable protection"
        val count = getSharedPreferences(ReelGuardAccessibilityService.PREFS_NAME, MODE_PRIVATE)
            .getInt(ReelGuardAccessibilityService.BLOCKED_COUNT_KEY, 0)
        blockedCount.text = count.toString()
    }

    private fun openInstagram() {
        val launchIntent = packageManager.getLaunchIntentForPackage(ReelGuardAccessibilityService.INSTAGRAM_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(this, "Instagram isn't installed on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launchIntent)
    }

    private fun showFollowingGuide() {
        AlertDialog.Builder(this).setTitle("Use the Following feed")
            .setMessage("1. Open Instagram and go to Home.\n\n2. Tap the Instagram name or logo at the top of the feed (some versions show “For you”).\n\n3. Choose Following.\n\nThat view is for posts and Reels from accounts you follow, in chronological order. Instagram may return to its regular Home feed later, so you may need to choose Following again.")
            .setPositiveButton("Open Instagram") { _, _ -> openInstagram() }
            .setNegativeButton("Close", null).show()
    }

    private fun showPreview() {
        AlertDialog.Builder(this).setTitle("Not this time.")
            .setMessage("The Reels tab is blocked. Your messages and shared links stay available.\n\nThis is a preview of the block screen.")
            .setPositiveButton("Got it", null).show()
    }
}