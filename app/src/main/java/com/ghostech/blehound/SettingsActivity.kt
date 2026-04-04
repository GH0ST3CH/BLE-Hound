package com.ghostech.blehound

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : Activity() {

    private lateinit var bgButton: Button
    private lateinit var filteredModeButton: Button
    private var lastThemeHex: String = ""
    private val prefs by lazy { getSharedPreferences("blehound_prefs", MODE_PRIVATE) }

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
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        bgButton = buildHellButton("")
        bgButton.setOnClickListener {
            startActivity(Intent(this, BackgroundMonitoringActivity::class.java))
        }
        content.addView(bgButton)

        filteredModeButton = buildHellButton("")
        filteredModeButton.setOnClickListener { toggleFilteredMode() }
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

        val aboutButton = buildHellButton("ABOUT")
        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        content.addView(aboutButton)

        val backButton = buildHellButton("BACK")
        backButton.setOnClickListener { finish() }

        root.addView(header)
        root.addView(content)

        val backContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(20))
        }
        backContainer.addView(backButton)
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
    }

    private fun toggleBackground() {
        val enabled = !prefs.getBoolean("background_enabled", false)
        prefs.edit().putBoolean("background_enabled", enabled).apply()

        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    2001
                )
                refreshButtons()
                return
            }

            val intent = Intent(this, BleMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(Intent(this, BleMonitorService::class.java))
        }

        refreshButtons()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 2001) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean("background_enabled", granted).apply()

            if (granted) {
                val intent = Intent(this, BleMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }

            refreshButtons()
        }
    }


    private fun ensureBackgroundMonitorState() {
        if (!prefs.getBoolean("background_enabled", false)) return

        val intent = Intent(this, BleMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun refreshButtons() {
        bgButton.text = "BACKGROUND MONITORING"
        filteredModeButton.text = if (prefs.getBoolean("filtered_mode", true)) "FILTERED MODE: ON" else "FILTERED MODE: OFF"
    }

    private fun toggleFilteredMode() {
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

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(0xFFFFAA55.toInt())
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.MONOSPACE
        setTextColor(0xFFFFE0C0.toInt())
        setPadding(0, 0, 0, dp(10))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
