package com.ghostech.blehound

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var bgButton: Button
    private lateinit var filteredModeButton: Button
    private var lastThemeHex: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A0000.toInt(), 0xFF140000.toInt(), 0xFF000000.toInt())
            ).apply { setStroke(dp(1), themeColor(this@SettingsActivity)) }
        }

        val title = TextView(this).apply {
            text = "SETTINGS"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@SettingsActivity))
            setShadowLayer(12f, 0f, 0f, 0xFFFF9900.toInt())
        }
        header.addView(title)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        bgButton = buildHellButton("")
        bgButton.setOnClickListener {
            startActivity(Intent(this, BackgroundMonitoringActivity::class.java))
        }
        content.addView(bgButton)

        val bwButton = buildHellButton("BLACKLIST / WHITELIST")
        bwButton.setOnClickListener {
            startActivity(Intent(this, BlacklistWhitelistActivity::class.java))
        }
        content.addView(bwButton)

        filteredModeButton = buildHellButton("")
        filteredModeButton.setOnClickListener {
            startActivity(Intent(this, FilterModeActivity::class.java))
        }
        content.addView(filteredModeButton)

        val notificationsButton = buildHellButton("NOTIFICATIONS")
        notificationsButton.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        content.addView(notificationsButton)

        val themeButton = buildHellButton("THEME")
        themeButton.setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }
        content.addView(themeButton)

        val offensiveButton = buildHellButton("OFFENSIVE TOOLS")
        offensiveButton.setOnClickListener {
            startActivity(Intent(this, OffensiveToolsActivity::class.java))
        }
        content.addView(offensiveButton)

        val aboutButton = buildHellButton("ABOUT")
        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        content.addView(aboutButton)

        val backButton = buildHellButton("BACK")
        backButton.setOnClickListener { finish() }

        val backContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(20))
        }
        backContainer.addView(backButton)

        root.addView(header)
        root.addView(content)
        root.addView(backContainer)
        setContentView(root)

        lastThemeHex = themeHex(this)
        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        val current = themeHex(this)
        if (lastThemeHex.isNotEmpty() && current != lastThemeHex) {
            recreate()
            return
        }
        lastThemeHex = current
        refreshButtons()
    }

    private fun refreshButtons() {
        bgButton.text = "BACKGROUND MONITORING"
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        filteredModeButton.text = "FILTER MODE"
    }

    private fun toggleFilteredMode() {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("filtered_mode", true)
        if (!enabled) {
            prefs.edit().putBoolean("filtered_mode", true).apply()
            refreshButtons()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Disable Filtered Mode?")
            .setMessage("Unfiltered traffic in BLE-dense environments may cause the app to freeze and crash.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Acknowledge") { _, _ ->
                prefs.edit().putBoolean("filtered_mode", false).apply()
                refreshButtons()
            }
            .show()
    }

    private fun buildHellButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = true
        textSize = 13f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(0xFFFFF1E0.toInt())
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF6A0000.toInt(), 0xFF260000.toInt())
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), themeColor(this@SettingsActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
