package com.ghostech.blehound

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AboutActivity : Activity() {
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
            ).apply { setStroke(dp(1), themeColor(this@AboutActivity)) }
        }

        val title = TextView(this).apply {
            text = "ABOUT"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@AboutActivity))
        }

        header.addView(title)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        content.addView(sectionTitle("CATEGORIES"))
        content.addView(bodyText("TRACKERS: AirTag, Tile, Galaxy Tag, Find My"))
        content.addView(bodyText("GADGETS: Flipper Zero, Pwnagotchi, Card Skimmer, Dev Board, WiFi Pineapple, Meta Glasses"))
        content.addView(bodyText("DRONES: DJI / Parrot / Skydio / Autel / BLE Remote ID"))
        content.addView(bodyText("FEDS: Axon and Flock detections"))
        content.addView(spannableNote("NOTE:", " Pwnagotchi and WiFi Pineapple detections are limited on Android. Unlike dedicated hardware tools such as HaleHound or ESP Marauder that use raw 802.11 frame analysis and promiscuous mode, this app relies on standard WiFi scan results. As a result, many spoofed or low-visibility devices may not appear or may be inconsistently detected."))

        content.addView(sectionTitle("RSSI"))
        content.addView(bodyText("RSSI stands for Received Signal Strength Indicator. It reflects signal strength at the time of observation and helps compare relative proximity and movement trends."))

        content.addView(sectionTitle("CREATOR"))
        content.addView(bodyText("Created by GH0ST3CH"))

        val ghButton = buildButton("OPEN GH0ST3CH GITHUB")
        ghButton.setOnClickListener { openUrl("https://github.com/GH0ST3CH") }
        content.addView(ghButton)

        content.addView(sectionTitle("OFFENSIVE TOOLS"))
        content.addView(bodyText("BLE offensive tools (Sour Apple, BLE Flood, Swift Pair) contributed by CK42X / nyanBEE"))

        val ck42xButton = buildButton("OPEN CK42X")
        ck42xButton.setOnClickListener { openUrl("https://ck42x.com") }
        content.addView(ck42xButton)

        val nyanbeeButton = buildButton("OPEN NYANBEE")
        nyanbeeButton.setOnClickListener { openUrl("https://ck42x.com/nyanbee") }
        content.addView(nyanbeeButton)

        content.addView(sectionTitle("CREDITS"))
        content.addView(bodyText("Inspired by HaleHound firmware and ESP Marauder firmware"))

        val halehoundButton = buildButton("OPEN HALEHOUND GITHUB")
        halehoundButton.setOnClickListener { openUrl("https://github.com/JesseCHale/HaleHound-CYD") }
        content.addView(halehoundButton)

        val marauderButton = buildButton("OPEN ESP MARAUDER GITHUB")
        marauderButton.setOnClickListener { openUrl("https://github.com/justcallmekoko/ESP32Marauder") }
        content.addView(marauderButton)

        scroll.addView(content)
        root.addView(header)
        root.addView(scroll)
        setContentView(root)
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

    private fun buildButton(label: String) = Button(this).apply {
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
            setStroke(dp(1), themeColor(this@AboutActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    
    private fun spannableNote(prefix: String, body: String) = TextView(this).apply {
        val full = prefix + body
        val spannable = android.text.SpannableString(full)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(0xFFFF4444.toInt()),
            0,
            prefix.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text = spannable
        textSize = 13f
        typeface = Typeface.MONOSPACE
        setTextColor(0xFFFFE0C0.toInt())
        setPadding(0, 0, 0, dp(10))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
