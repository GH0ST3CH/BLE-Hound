package com.ghostech.blehound

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BackgroundMonitoringActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("blehound_prefs", MODE_PRIVATE) }
    private lateinit var bgButton: Button
    private lateinit var popupSelectButton: Button
    private lateinit var popupVibrateButton: Button

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
            ).apply { setStroke(dp(1), themeColor(this@BackgroundMonitoringActivity)) }
        }

        val title = TextView(this).apply {
            text = "BACKGROUND MONITORING"
            gravity = android.view.Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@BackgroundMonitoringActivity))
            setShadowLayer(12f, 0f, 0f, 0xFFFF9900.toInt())
        }

        header.addView(title)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        bgButton = buildHellButton("")
        bgButton.setOnClickListener { toggleBackground() }
        content.addView(bgButton)

        popupSelectButton = buildHellButton("POPUP SELECT")
        popupSelectButton.setOnClickListener {
            startActivity(Intent(this, PopupSelectActivity::class.java))
        }
        content.addView(popupSelectButton)


        popupVibrateButton = buildHellButton("")
        popupVibrateButton.setOnClickListener {
            prefs.edit().putBoolean("popup_vibrate", !prefs.getBoolean("popup_vibrate", false)).apply()
            restartBackgroundServiceIfEnabled()
            refreshButtons()
        }
        content.addView(popupVibrateButton)

        content.addView(sectionTitle("POP-UP GUIDE"))
        content.addView(bodyText("Background Monitoring must be ON for pop-ups to work. Pop-ups can show the detected device type, MAC address, name, manufacturer, and live RSSI."))

        content.addView(sectionTitle("POP-UP SOUND"))
        content.addView(bodyText("Pop-up sound follows the sound selected for that category in Notifications. Blacklist pop-ups use the BLACKLIST sound in Notifications."))

        content.addView(sectionTitle("CATEGORY GUIDE"))
        content.addView(bodyText("Trackers: AirTag, Tile, Galaxy Tag, Find My"))
        content.addView(bodyText("Gadgets: Flipper Zero, Pwnagotchi, Card Skimmer, WiFi Pineapple, Meta Glasses, Dev Board"))
        content.addView(bodyText("Drones: Drone"))
        content.addView(bodyText("Feds: Axon Device, Axon Cam, Axon Taser, Flock"))
        content.addView(bodyText("Blacklist: Uses the BLACKLIST sound in Notifications"))

        val scroll = ScrollView(this).apply { addView(content) }

        val backContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(20))
        }

        val backButton = buildHellButton("BACK")
        backButton.setOnClickListener { finish() }
        backContainer.addView(backButton)

        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(backContainer)

        setContentView(root)
        refreshButtons()
    }

    private fun restartBackgroundServiceIfEnabled() {
        if (!prefs.getBoolean("background_enabled", false)) return
        stopService(Intent(this, BleMonitorService::class.java))
        val intent = Intent(this, BleMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun toggleBackground() {
        val enabled = !prefs.getBoolean("background_enabled", false)
        prefs.edit().putBoolean("background_enabled", enabled).apply()

        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2101)
                refreshButtons()
                return
            }
            val intent = Intent(this, BleMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else {
            stopService(Intent(this, BleMonitorService::class.java))
        }

        refreshButtons()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2101) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean("background_enabled", granted).apply()
            if (granted) {
                val intent = Intent(this, BleMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            }
            refreshButtons()
        }
    }

    private fun refreshButtons() {
        val bg = prefs.getBoolean("background_enabled", false)
        bgButton.text = "BACKGROUND MONITORING: " + if (bg) "ON" else "OFF"
        popupSelectButton.text = "POPUP SELECT"
        popupVibrateButton.text = "POPUP VIBRATE: " + if (prefs.getBoolean("popup_vibrate", false)) "ON" else "OFF"

        popupSelectButton.isEnabled = bg
        popupVibrateButton.isEnabled = bg

        popupSelectButton.alpha = if (bg) 1.0f else 0.45f
        popupVibrateButton.alpha = if (bg) 1.0f else 0.45f
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(0xFFFFAA55.toInt())
        setPadding(0, dp(10), 0, dp(6))
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.MONOSPACE
        setTextColor(0xFFFFE0C0.toInt())
        setLineSpacing(0f, 1.08f)
        setPadding(0, 0, 0, dp(8))
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
            setStroke(dp(1), themeColor(this@BackgroundMonitoringActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
