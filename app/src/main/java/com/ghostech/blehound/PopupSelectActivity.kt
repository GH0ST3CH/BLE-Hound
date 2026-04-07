package com.ghostech.blehound

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PopupSelectActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("blehound_prefs", MODE_PRIVATE) }
    private val popupButtons = linkedMapOf<String, Button>()

    private val popupItems = listOf(
        "popup_blacklist" to "BLACKLIST",
        "popup_drone" to "DRONE",
        "popup_airtag" to "AIRTAG",
        "popup_tile" to "TILE",
        "popup_galaxytag" to "GALAXY TAG",
        "popup_findmy" to "FIND MY",
        "popup_flipper" to "FLIPPER ZERO",
        "popup_pwnagotchi" to "PWNAGOTCHI",
        "popup_skimmer" to "CARD SKIMMER",
        "popup_pineapple" to "WIFI PINEAPPLE",
        "popup_metaglasses" to "META GLASSES",
        "popup_axondevice" to "AXON DEVICE",
        "popup_axoncam" to "AXON CAM",
        "popup_axontaser" to "AXON TASER",
        "popup_flock" to "FLOCK"
    )

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
            ).apply { setStroke(dp(1), themeColor(this@PopupSelectActivity)) }
        }

        val title = TextView(this).apply {
            text = "POPUP SELECT"
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@PopupSelectActivity))
            gravity = android.view.Gravity.CENTER
        }

        header.addView(title)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        for ((key, label) in popupItems) {
            val b = buildHellButton("")
            b.setOnClickListener {
                prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply()
                refreshButtons()
            }
            popupButtons[key] = b
            content.addView(b)
        }

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

    private fun refreshButtons() {
        for ((key, button) in popupButtons) {
            val base = popupItems.first { it.first == key }.second
            button.text = "$base: " + if (prefs.getBoolean(key, false)) "ON" else "OFF"
        }
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
            setStroke(dp(1), themeColor(this@PopupSelectActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
