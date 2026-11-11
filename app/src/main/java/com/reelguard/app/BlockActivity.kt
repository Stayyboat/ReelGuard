package com.reelguard.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 247, 243)
        window.navigationBarColor = Color.rgb(246, 247, 243)
        window.decorView.systemUiVisibility = 0x00002000 or 0x00000010
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(246, 247, 243))
        }
        root.addView(TextView(this).apply {
            text = "Not this time."
            textSize = 30f
            setTextColor(Color.rgb(32, 33, 31))
            gravity = Gravity.CENTER
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "You opened the Reels tab. Take a breath and head back to your Instagram Home feed."
            textSize = 17f
            setTextColor(Color.rgb(90, 92, 85))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 28)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "Return to Instagram Home"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(ReelGuardAccessibilityService.INSTAGRAM_PACKAGE)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launch)
                }
                finish()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}