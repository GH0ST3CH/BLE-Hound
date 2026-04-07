package com.ghostech.blehound

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class BlacklistWhitelistActivity : Activity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var clearButton: Button
    private lateinit var noteView: TextView
    private lateinit var activeListView: TextView
    private var currentRuleKey: String = RULE_BLACKLIST

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
            ).apply { setStroke(dp(1), themeColor(this@BlacklistWhitelistActivity)) }
        }

        val title = TextView(this).apply {
            text = "BLACKLIST / WHITELIST"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@BlacklistWhitelistActivity))
            setShadowLayer(12f, 0f, 0f, 0xFFFF9900.toInt())
        }
        header.addView(title)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        val viewBlacklist = buildHellButton("VIEW BLACKLIST")
        viewBlacklist.setOnClickListener { renderRuleList(RULE_BLACKLIST) }

        val viewWhitelist = buildHellButton("VIEW WHITELIST")
        viewWhitelist.setOnClickListener { renderRuleList(RULE_WHITELIST) }

        clearButton = buildHellButton("CLEAR BLACKLIST")
        clearButton.setOnClickListener {
            clearRuleList(currentRuleKey)
            labelPrefs().edit().clear().apply()
            renderRuleList(currentRuleKey)
        }

        noteView = TextView(this).apply {
            text = "Tap entry to edit name or remove it from the list."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFFFE0C0.toInt())
            setPadding(0, dp(10), 0, dp(4))
        }

        activeListView = TextView(this).apply {
            text = "CURRENT VIEW: BLACKLIST"
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFAA55.toInt())
            setPadding(0, 0, 0, dp(10))
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(viewBlacklist)
        content.addView(viewWhitelist)
        content.addView(clearButton)
        content.addView(noteView)
        content.addView(activeListView)
        content.addView(listContainer)

        val scroll = ScrollView(this).apply { addView(content) }

        val backWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(20))
        }

        val back = buildHellButton("BACK")
        back.setOnClickListener { finish() }
        backWrap.addView(back)

        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(backWrap)

        setContentView(root)
        renderRuleList(RULE_BLACKLIST)
    }

    private fun rules(key: String): MutableSet<String> =
        getSharedPreferences("blehound_rules", MODE_PRIVATE)
            .getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun saveRules(key: String, values: Set<String>) {
        getSharedPreferences("blehound_rules", MODE_PRIVATE)
            .edit().putStringSet(key, values).apply()
    }

    private fun clearRuleList(key: String) {
        saveRules(key, emptySet())
    }

    private fun labelPrefs() =
        getSharedPreferences("blehound_rule_labels", MODE_PRIVATE)

    private fun entryMac(entry: String): String =
        entry.substringAfter("##MAC::", entry).trim()

    private fun entrySig(entry: String): String =
        entry.substringAfter("SIG::", "").substringBefore("##MAC::").trim()

    private fun entryClass(entry: String): String =
        entrySig(entry).split("||").getOrNull(1)?.ifBlank { "-" } ?: "-"

    private fun entryMfg(entry: String): String =
        entrySig(entry).split("||").getOrNull(2)?.ifBlank { "-" } ?: "-"

    private fun entryName(entry: String): String {
        val custom = labelPrefs().getString(entry, null)
        if (!custom.isNullOrBlank()) return custom
        val mac = entryMac(entry)
        return BleStore.devices[mac]?.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    }

    private fun renderRuleList(key: String) {
        currentRuleKey = key
        clearButton.text = if (key == RULE_BLACKLIST) "CLEAR BLACKLIST" else "CLEAR WHITELIST"
        activeListView.text = if (key == RULE_BLACKLIST) "CURRENT VIEW: BLACKLIST" else "CURRENT VIEW: WHITELIST"
        listContainer.removeAllViews()

        val items = rules(key).sorted()
        if (items.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No devices saved."
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(0xFFFFE0C0.toInt())
            })
            return
        }

        for (entry in items) {
            listContainer.addView(buildRuleRow(entry))
        }
    }

    private fun showEntryMenu(entry: String) {
        val input = EditText(this).apply {
            setText(entryName(entry))
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle(entryMac(entry))
            .setItems(arrayOf("Edit Name", "Remove", "Cancel")) { d, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this)
                        .setTitle("Edit Name")
                        .setView(input)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save") { _, _ ->
                            labelPrefs().edit().putString(entry, input.text.toString().trim()).apply()
                            renderRuleList(currentRuleKey)
                        }
                        .show()
                    1 -> {
                        val set = rules(currentRuleKey)
                        set.remove(entry)
                        saveRules(currentRuleKey, set)
                        labelPrefs().edit().remove(entry).apply()
                        renderRuleList(currentRuleKey)
                    }
                    else -> d.dismiss()
                }
            }
            .show()
    }

    private fun buildRuleRow(entry: String): LinearLayout {
        val cls = entryClass(entry)
        val mfg = entryMfg(entry).take(6)
        val mac = entryMac(entry)
        val name = entryName(entry)

        val borderColor = when (cls) {
            "AirTag", "Tile", "Galaxy Tag", "Find My" -> 0xFFFFFF00.toInt()
            "Flipper Zero", "Pwnagotchi", "Card Skimmer", "Dev Board", "WiFi Pineapple", "Meta Glasses" -> 0xFFFF8800.toInt()
            "Drone" -> 0xFF8A2BE2.toInt()
            "Axon Device", "Axon Cam", "Axon Taser", "Flock" -> 0xFFFF0000.toInt()
            else -> themeColor(this)
        }

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFF121212.toInt())
                setStroke(dp(2), borderColor)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { showEntryMenu(entry) }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }

        val clsView = buildTopCell("CLASS: $cls", 1.7f)
        val mfgView = buildTopCell("MFG: $mfg", 1.0f)
        val macView = buildTopCell("MAC: $mac", 2.2f)

        top.addView(clsView)
        top.addView(mfgView)
        top.addView(macView)

        val nameBox = TextView(this).apply {
            text = "NAME: $name"
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF303030.toInt())
                setStroke(dp(1), 0xFF555555.toInt())
            }
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
            addView(nameBox, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }

        wrap.addView(top)
        wrap.addView(bottom)
        wrap.layoutParams = lp
        return wrap
    }

    private fun buildTopCell(text: String, weight: Float) = TextView(this).apply {
        this.text = text
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 11f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        isSingleLine = true
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
            setStroke(dp(1), themeColor(this@BlacklistWhitelistActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
