package com.ghostech.blehound

import android.Manifest
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
    private val prefs by lazy { getSharedPreferences("blehound_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A0000.toInt(), 0xFF140000.toInt(), 0xFF000000.toInt())
            ).apply { setStroke(dp(1), 0xFFFF2200.toInt()) }
        }

        val title = TextView(this).apply {
            text = "SETTINGS / ABOUT"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(0xFFFF5522.toInt())
            setShadowLayer(12f, 0f, 0f, 0xFFFF9900.toInt())
        }

        header.addView(title)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        bgButton = buildHellButton("")

        bgButton.setOnClickListener { toggleBackground() }

        content.addView(bgButton)

        content.addView(sectionTitle("ABOUT CATEGORIES"))
        content.addView(bodyText("TRACKERS: AirTag, Tile, Galaxy Tag, Find My"))
        content.addView(bodyText("GADGETS: Flipper Zero, Pwnagotchi, Card Skimmer, Dev Board, WiFi Pineapple"))
        content.addView(bodyText("DRONES: DJI / Parrot / Skydio / Autel / BLE Remote ID"))
        content.addView(bodyText("FEDS: Axon and Flock detections"))

        content.addView(sectionTitle("RSSI"))
        content.addView(bodyText("RSSI stands for Received Signal Strength Indicator. Live RSSI helps estimate relative signal strength while moving around an area, which can help with heat mapping and locating the strongest signal area."))

        content.addView(sectionTitle("CREATOR"))
        content.addView(bodyText("Created by GH0ST3CH"))

        val ghButton = buildHellButton("OPEN GH0ST3CH GITHUB")
        ghButton.setOnClickListener { openUrl("https://github.com/GH0ST3CH") }
        content.addView(ghButton)

        val supportButton = buildHellButton("SUPPORT ON BUY ME A COFFEE")
        supportButton.setOnClickListener { openUrl("https://www.buymeacoffee.com/ghostechrepair") }
        content.addView(supportButton)

        content.addView(sectionTitle("INSPIRED BY"))
        content.addView(bodyText("HaleHound firmware and ESP Marauder firmware"))

        val halehoundButton = buildHellButton("OPEN HALEHOUND GITHUB")
        halehoundButton.setOnClickListener { openUrl("https://github.com/JesseCHale/HaleHound-CYD") }
        content.addView(halehoundButton)

        val marauderButton = buildHellButton("OPEN ESP MARAUDER GITHUB")
        marauderButton.setOnClickListener { openUrl("https://github.com/justcallmekoko/ESP32Marauder") }
        content.addView(marauderButton)

        val backButton = buildHellButton("BACK")
        backButton.setOnClickListener { finish() }
        content.addView(backButton)

        scroll.addView(content)
        root.addView(header)
        root.addView(scroll)
        setContentView(root)

        refreshButtons()
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
        bgButton.text = if (prefs.getBoolean("background_enabled", false)) "BACKGROUND MONITORING: ON" else "BACKGROUND MONITORING: OFF"
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
            setStroke(dp(1), 0xFFFF4400.toInt())
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
