package com.ghostech.blehound

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import java.util.LinkedHashMap

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

data class BleSeenDevice(
    var name: String,
    var address: String,
    var rssi: Int,
    var packetCount: Int,
    var lastSeenMs: Long,
    var manufacturerText: String,
    var manufacturerDataText: String,
    var serviceUuidsText: String,
    var rawAdvText: String,
    var isWifi: Boolean = false,
    var droneLat: Double? = null,
    var droneLon: Double? = null
 )

// ---------------------------------------------------------------------------
// Global store
// ---------------------------------------------------------------------------

object BleStore {
    val devices = LinkedHashMap<String, BleSeenDevice>()
    var shouldScan = false
    var isListFrozen = false
    var vibrateOnTracker = false
    var soundOnTracker = false
    var trackerSeenThisSession = false
    var lastNotifyMs = 0L
    var lastPacketSeenMs = 0L
    var lastScanRestartMs = 0L
    var lastDroneBeepMs = 0L
    var dronePresent = false
}

// ---------------------------------------------------------------------------
// Classification
// ---------------------------------------------------------------------------

fun classifyDevice(d: BleSeenDevice): String {
    val name = d.name.lowercase()
    val mfg = d.manufacturerText.uppercase()
    val raw = d.rawAdvText.uppercase()
    val svc = d.serviceUuidsText.uppercase()
    val mfgData = d.manufacturerDataText.uppercase()

    // --- WiFi-detected devices (from WiFi scan) ---
    if (d.isWifi) {
        if (name.contains("pwnagotchi") || d.address.lowercase().startsWith("de:ad:be")) return "Pwnagotchi"
        if (name.contains("flipper")) return "Flipper Zero"
        if (name.contains("dji") || name.contains("parrot") || name.contains("skydio") || name.contains("autel")) return "Drone"
        // WiFi Pineapple OUI: xx:13:37:xx:xx:xx
        val bssid = d.address.lowercase()
        if (bssid.length >= 8 && bssid.substring(3, 5) == "13" && bssid.substring(6, 8) == "37") return "WiFi Pineapple"
        return "WiFi Device"
    }

    // --- Apple devices ---
    if (mfg == "APPLE") {
        if ("AIRTAG" in name) return "AirTag"
        if ("FINDMY" in name || "FIND MY" in name) return "Find My"
        if ("AIRPODS" in name || "AIRPOD" in name) return "AirPods"
        if ("IBEACON" in name) return "iBeacon"
        if ("004C" in mfgData && ("12 19" in raw || "004C 12" in raw || "004C 19" in raw)) return "AirTag"
        if ("004C" in mfgData && "0215" in raw.replace(" ", "")) return "iBeacon"
        if ("BEATS" in name) return "Beats"
        return "Apple BLE"
    }

    // --- General BLE ---
    if ("META" in name || "RAYBAN" in name || "RAY-BAN" in name) return "Meta Glasses"
    if ("TILE" in name) return "Tile"
    if ("SMARTTAG" in name || "GALAXY TAG" in name || (mfg == "SAMSNG" && "TAG" in name)) return "Galaxy Tag"
    if ("BEACON" in name) return "Beacon"
    if ("EARBUD" in name || "HEADSET" in name) return "Audio BLE"
    if ("WATCH" in name) return "Watch"
    if ("PHONE" in name) return "Phone BLE"
    if ("ESP32" in name || "ARDUINO" in name) return "Dev Board"
    if ("NORDIC" in mfg) return "Nordic BLE"
    if ("META" in mfg) return "Meta BLE"
    if ("GOOGL" in mfg || "GOOGLE" in mfg) return "Google BLE"
    if ("MSFT" in mfg) return "Microsoft BLE"
    if ("SAMSNG" in mfg) return "Samsung BLE"

    // --- nyanBOX Ported BLE Identifiers ---
    val macPrefix = d.address.take(8).lowercase()

    // Flipper Zero
    if (macPrefix == "80:e1:26" || macPrefix == "80:e1:27" || macPrefix == "0c:fa:22" ||
        "3081" in svc || "3082" in svc || "3083" in svc || "3080" in svc || "FLIPPER" in name
    ) {
        return "Flipper Zero"
    }

    // Pwnagotchi
    if ("PWNAGOTCHI" in name || macPrefix == "de:ad:be") {
        return "Pwnagotchi"
    }

    // Card Skimmer
    if ("HC-03" in name || "HC-05" in name || "HC-06" in name) {
        return "Card Skimmer"
    }

    // Drone (Remote ID / known manufacturers)
    if ("DJI" in name || "PARROT" in name || "SKYDIO" in name || "AUTEL" in name || "FFFA" in svc) {
        return "Drone"
    }

    // Axon (law enforcement body cameras)
    if (macPrefix == "00:25:df" || "AXON" in name) {
        return "Axon"
    }

    // Flock / ALPR systems
    val flockPrefixes = listOf(
        "58:8e:81", "cc:cc:cc", "ec:1b:bd", "90:35:ea", "04:0d:84",
        "f0:82:c0", "1c:34:f1", "38:5b:44", "94:34:69", "b4:e3:f9",
        "70:c9:4e", "3c:91:80", "d8:f3:bc", "80:30:49", "14:5a:fc",
        "74:4c:a1", "08:3a:88", "9c:2f:9d", "94:08:53", "e4:aa:ea"
    )
    if ("FS EXT BATTERY" in name || "PENGUIN" in name || "FLOCK" in name || "PIGVISION" in name || flockPrefixes.contains(macPrefix)) {
        return "Flock"
    }
    // --- End nyanBOX Ported Logic ---

    if (svc != "-" && svc.isNotBlank()) return "BLE Device"

    return "-"
}

// ---------------------------------------------------------------------------
// Category helpers
// ---------------------------------------------------------------------------

fun isCyberGadgetClass(c: String) = c == "Flipper Zero" || c == "Pwnagotchi" || c == "Card Skimmer" || c == "Dev Board" || c == "WiFi Pineapple"
fun isDroneClass(c: String) = c == "Drone"
fun isPoliceClass(c: String) = c == "Axon" || c == "Flock"
fun isTrackerClass(c: String) = c == "AirTag" || c == "Tile" || c == "Galaxy Tag" || c == "Find My"
fun isAlertCategory(c: String) = isTrackerClass(c) || isCyberGadgetClass(c) || isDroneClass(c) || isPoliceClass(c)

// ===================================================================
// MainActivity
// ===================================================================

class MainActivity : Activity() {

    fun getAnimationTick(): Int = animationTick

    private lateinit var statusView: TextView
    private lateinit var headerPanel: LinearLayout
    private lateinit var flameTitleView: TextView
    private lateinit var counterView: TextView
    private lateinit var trackerChip: TextView
    private lateinit var gadgetChip: TextView
    private lateinit var droneChip: TextView
    private lateinit var federalChip: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var vibrateButton: Button
    private lateinit var soundButton: Button
    private lateinit var listView: ListView
    private lateinit var adapter: DeviceListAdapter

    private var scanner: BluetoothLeScanner? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var wifiManager: WifiManager? = null
    private var isScannerActive = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private var uiDirty = false
    private var animationTick = 0

    // ---------------------------------------------------------------
    // WiFi scan receiver
    // ---------------------------------------------------------------

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                processWifiResults()
            }
        }
    }

    @Suppress("MissingPermission")
    private fun processWifiResults() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val results = wifiManager?.scanResults ?: return
        val now = System.currentTimeMillis()
        for (r in results) {
            val bssid = r.BSSID ?: continue
            val ssid = r.SSID ?: ""
            val rssi = r.level

            val existing = BleStore.devices[bssid]
            if (existing == null) {
                BleStore.devices[bssid] = BleSeenDevice(
                    name = ssid.ifEmpty { "Hidden Network" },
                    address = bssid,
                    rssi = rssi,
                    packetCount = 1,
                    lastSeenMs = now,
                    manufacturerText = "WIFI",
                    manufacturerDataText = "-",
                    serviceUuidsText = "-",
                    rawAdvText = "-",
                    isWifi = true
                )
            } else {
                if (ssid.isNotEmpty()) existing.name = ssid
                existing.rssi = rssi
                existing.packetCount += 1
                existing.lastSeenMs = now
            }

            val cls = classifyDevice(BleStore.devices[bssid]!!)
            if (isAlertCategory(cls)) notifyDeviceDetected()
        }
        if (!BleStore.isListFrozen) uiDirty = true
        updateHeaderCounts()
    }

    // ---------------------------------------------------------------
    // UI refresh (450 ms tick)
    // ---------------------------------------------------------------

    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            if (uiDirty && !BleStore.isListFrozen) {
                updateHeaderCounts()
        renderDeviceList()
                uiDirty = false
            }
            animationTick = (animationTick + 1) % 4
            updateFlameHeader()
            if (!BleStore.isListFrozen) adapter.notifyDataSetChanged()

            // Continuous drone beep
            if (BleStore.dronePresent && BleStore.soundOnTracker) {
                val now = System.currentTimeMillis()
                if (now - BleStore.lastDroneBeepMs > 1000) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                    BleStore.lastDroneBeepMs = now
                }
            }

            uiHandler.postDelayed(this, 450)
        }
    }

    // ---------------------------------------------------------------
    // Watchdog (4 s tick) - prunes stale devices & triggers WiFi
    // ---------------------------------------------------------------

    private val scanWatchdogRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            // 7-second retention: remove devices not seen for > 7 s
            if (!BleStore.isListFrozen) {
                val iter = BleStore.devices.entries.iterator()
                var removed = false
                while (iter.hasNext()) {
                    if (now - iter.next().value.lastSeenMs > 7000) {
                        iter.remove()
                        removed = true
                    }
                }
                if (removed) uiDirty = true
            }

            // BLE watchdog restart
            if (isScannerActive && BleStore.shouldScan) {
                val staleFor = now - BleStore.lastPacketSeenMs
                val restartedAgo = now - BleStore.lastScanRestartMs
                if (BleStore.lastPacketSeenMs > 0L && staleFor > 12000L && restartedAgo > 5000L) {
                    restartScanSession("watchdog")
                }
            }

            // Trigger a WiFi scan each cycle
            @Suppress("MissingPermission")
            if (BleStore.shouldScan) {
                try { wifiManager?.startScan() } catch (_: Exception) {}
            }

            uiHandler.postDelayed(this, 4000)
        }
    }

    // ---------------------------------------------------------------
    // onCreate
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        headerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(24), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A0000.toInt(), 0xFF140000.toInt(), 0xFF000000.toInt())
            ).apply { setStroke(dp(1), 0xFFFF2200.toInt()) }
        }

        flameTitleView = TextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "BLE HOUND"
            gravity = Gravity.CENTER
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(0xFFFF5A1F.toInt())
            setShadowLayer(22f, 0f, 0f, 0xFFFF1E00.toInt())
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(6))
        }

        statusView = TextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFF4444.toInt())
            setPadding(0, 0, 0, dp(4))
        }

        counterView = TextView(this).apply {
            visibility = View.GONE
        }

        trackerChip = buildCounterChip("TRACKERS: 0", 0xFFFFFF66.toInt(), 0x55B8A800)
        gadgetChip = buildCounterChip("GADGETS: 0", 0xFFFFB366.toInt(), 0x55A84A00)
        droneChip = buildCounterChip("DRONES: 0", 0xFFD6B3FF.toInt(), 0x554B1E88)
        federalChip = buildCounterChip("FEDS: 0", 0xFF8DB8FF.toInt(), 0x55163E8A)

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dp(10))
        }

        chipRow.addView(trackerChip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        chipRow.addView(gadgetChip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(1); marginEnd = dp(3) })
        chipRow.addView(droneChip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        chipRow.addView(federalChip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3) })

        vibrator = getSystemService(Vibrator::class.java)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        val headerShell = FrameLayout(this)

        val headerTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val titleHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val settingsButton = TextView(this).apply {
            text = "⚙"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFF5533.toInt())
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dp(4))
        }

        startButton = buildHellButton("START")
        startButton.setOnClickListener { startScan() }

        stopButton = buildHellButton("STOP")
        stopButton.setOnClickListener { lockList() }

        vibrateButton = buildHellButton("VIBRATE")
        vibrateButton.setOnClickListener {
            BleStore.vibrateOnTracker = !BleStore.vibrateOnTracker
            updateButtonStates()
        }

        soundButton = buildHellButton("SOUND")
        soundButton.setOnClickListener {
            BleStore.soundOnTracker = !BleStore.soundOnTracker
            updateButtonStates()
        }

        buttonBar.addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        buttonBar.addView(stopButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        buttonBar.addView(vibrateButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        buttonBar.addView(soundButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })

        headerPanel.addView(flameTitleView)
        headerPanel.addView(statusView)
        headerPanel.addView(chipRow)
        headerPanel.addView(buttonBar)

        headerShell.addView(
            headerPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        headerShell.addView(
            settingsButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = -dp(2)
                marginEnd = dp(4)
            }
        )

        adapter = DeviceListAdapter(this) { device -> openDetail(device.address) }

        listView = ListView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            divider = null
            dividerHeight = 0
        }
        listView.adapter = adapter

        root.addView(headerShell)
        root.addView(buildTableHeader())
        root.addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        updateFlameHeader()
        ensureBackgroundMonitorState()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                ),
                1
            )
        } else {
            initBle()
        }

        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        uiHandler.post(uiRefreshRunnable)
        uiHandler.post(scanWatchdogRunnable)
        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        renderDeviceList()
    }

    override fun onDestroy() {
        BleStore.shouldScan = isScannerActive
        super.onDestroy()
        try { unregisterReceiver(wifiScanReceiver) } catch (_: Exception) {}
        uiHandler.removeCallbacks(uiRefreshRunnable)
        uiHandler.removeCallbacks(scanWatchdogRunnable)
        hardStopScanner()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initBle()
        } else {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            updateButtonStates()
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateFlameHeader() {
        val tick = animationTick % 4

        val titleColor = when (tick) {
            0 -> 0xFFFF5A1F.toInt()
            1 -> 0xFFFF7A1F.toInt()
            2 -> 0xFFFFB347.toInt()
            else -> 0xFFFF3B1F.toInt()
        }

        val glowColor = when (tick) {
            0 -> 0xAAFF2200.toInt()
            1 -> 0xAAFF6600.toInt()
            2 -> 0xAAFFAA00.toInt()
            else -> 0xAAFF3300.toInt()
        }

        val topColor = when (tick) {
            0 -> 0xFF220000.toInt()
            1 -> 0xFF2A0000.toInt()
            2 -> 0xFF300800.toInt()
            else -> 0xFF1A0000.toInt()
        }

        val midColor = when (tick) {
            0 -> 0xFF110000.toInt()
            1 -> 0xFF160000.toInt()
            2 -> 0xFF1F0500.toInt()
            else -> 0xFF100000.toInt()
        }

        flameTitleView.setTextColor(titleColor)
        flameTitleView.setShadowLayer(18f, 0f, 0f, glowColor)

        headerPanel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, midColor, 0xFF000000.toInt())
        ).apply {
            setStroke(dp(1), 0xFFFF2200.toInt())
        }
    }


    private fun updateHeaderCounts() {
        var trackerCount = 0
        var gadgetCount = 0
        var droneCount = 0
        var federalCount = 0

        for (d in BleStore.devices.values) {
            val c = classifyDevice(d)
            when {
                isTrackerClass(c) -> trackerCount++
                isCyberGadgetClass(c) -> gadgetCount++
                isDroneClass(c) -> droneCount++
                isPoliceClass(c) -> federalCount++
            }
        }

        if (::trackerChip.isInitialized) trackerChip.text = "TRACKERS: $trackerCount"
        if (::gadgetChip.isInitialized) gadgetChip.text = "GADGETS: $gadgetCount"
        if (::droneChip.isInitialized) droneChip.text = "DRONES: $droneCount"
        if (::federalChip.isInitialized) federalChip.text = "FEDS: $federalCount"
    }


    private fun buildCounterChip(text: String, textColor: Int, fillColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(textColor)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(fillColor)
            }
        }
    }


    private fun ensureBackgroundMonitorState() {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("background_enabled", false)) return

        val intent = Intent(this, BleMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun buildHellButton(label: String): Button {
        return Button(this).apply {
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
    }

    private fun buildCounterText(tracker: Int, gadget: Int, drone: Int, federal: Int, other: Int): android.text.SpannableStringBuilder {
        val sb = android.text.SpannableStringBuilder()

        fun addPill(text: String, fg: Int, bg: Int) {
            val start = sb.length
            sb.append(" ")
            val textStart = sb.length
            sb.append(text)
            val textEnd = sb.length
            sb.append("    ")

            sb.setSpan(android.text.style.ForegroundColorSpan(fg), textStart, textEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.BackgroundColorSpan(bg), textStart, textEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), textStart, textEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        addPill("TRACKERS: $tracker", 0xFFFFFF66.toInt(), 0x55B8A800)
        addPill("GADGETS: $gadget", 0xFFFFB366.toInt(), 0x55A84A00)
        addPill("DRONES: $drone", 0xFFD6B3FF.toInt(), 0x554B1E88)
        addPill("FEDS: $federal", 0xFF8DB8FF.toInt(), 0x55163E8A)

        return sb
    }

    private fun buildHeaderCell(text: String, weight: Float): TextView {
        return TextView(this).apply {
            this.text = text
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 11f
            setTextColor(0xFF80FF80.toInt())
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun setHellButtonState(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.38f
    }

    // ---------------------------------------------------------------
    // Notifications - fires for ANY alert category
    // ---------------------------------------------------------------

    private fun notifyDeviceDetected() {
        val now = System.currentTimeMillis()
        if (now - BleStore.lastNotifyMs < 3000) return
        BleStore.lastNotifyMs = now

        if (BleStore.vibrateOnTracker) {
            vibrator?.let { v ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(180)
                }
            }
        }

        if (BleStore.soundOnTracker) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            uiHandler.postDelayed({
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            }, 220)
        }
    }

    private fun updateButtonStates() {
        val startEnabled = !isScannerActive || BleStore.isListFrozen
        val stopEnabled = isScannerActive && !BleStore.isListFrozen
        setHellButtonState(startButton, startEnabled)
        setHellButtonState(stopButton, stopEnabled)
        vibrateButton.text = "VIBRATE"
        vibrateButton.alpha = if (BleStore.vibrateOnTracker) 1.0f else 0.55f
        soundButton.text = "SOUND"
        soundButton.alpha = if (BleStore.soundOnTracker) 1.0f else 0.55f
    }

    private fun buildTableHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(buildHeaderCell("RSSI", 0.9f))
            addView(buildHeaderCell("MAC", 3.0f))
            addView(buildHeaderCell("MFG", 1.3f))
            addView(buildHeaderCell("CLASS", 1.9f))
        }
    }

    // ---------------------------------------------------------------
    // BLE scan management
    // ---------------------------------------------------------------

    private fun restartScanSession(reason: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner?.startScan(null, settings, scanCallback)
            isScannerActive = true
            BleStore.shouldScan = true
            BleStore.lastScanRestartMs = System.currentTimeMillis()
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        } catch (_: Exception) {}
        updateButtonStates()
    }

    private fun initBle() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
            updateButtonStates()
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (BleStore.shouldScan) {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                scanner?.startScan(null, settings, scanCallback)
                isScannerActive = true
                BleStore.lastScanRestartMs = System.currentTimeMillis()
                BleStore.lastPacketSeenMs = System.currentTimeMillis()
            }
        }
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun startScan() {
        BleStore.isListFrozen = false
        if (isScannerActive) {
            BleStore.shouldScan = true
            renderDeviceList()
            updateButtonStates()
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(null, settings, scanCallback)
        isScannerActive = true
        BleStore.shouldScan = true
        BleStore.lastScanRestartMs = System.currentTimeMillis()
        BleStore.lastPacketSeenMs = System.currentTimeMillis()
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun lockList() {
        if (!isScannerActive) {
            updateButtonStates()
            return
        }
        BleStore.isListFrozen = true
        BleStore.shouldScan = true
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun hardStopScanner() {
        if (!isScannerActive) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanner?.stopScan(scanCallback)
        }
        isScannerActive = false
        BleStore.lastPacketSeenMs = 0L
        updateButtonStates()
    }

    private fun openDetail(address: String) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra("address", address))
    }

    // ---------------------------------------------------------------
    // BLE scan callback
    // ---------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = result.device.name ?: record?.deviceName ?: "Unknown"
            val addr = result.device.address ?: "Unknown"
            val rssi = result.rssi
            val now = System.currentTimeMillis()
            BleStore.lastPacketSeenMs = now

            val manufacturerText = detectManufacturer(record)
            val manufacturerDataText = formatManufacturerData(record)
            val serviceUuidsText = formatServiceUuids(record)
            val rawAdvText = formatRawAdv(record)

            val existing = BleStore.devices[addr]
            if (existing == null) {
                BleStore.devices[addr] = BleSeenDevice(
                    name = name, address = addr, rssi = rssi,
                    packetCount = 1, lastSeenMs = now,
                    manufacturerText = manufacturerText,
                    manufacturerDataText = manufacturerDataText,
                    serviceUuidsText = serviceUuidsText,
                    rawAdvText = rawAdvText
                )
            } else {
                existing.name = name
                existing.rssi = rssi
                existing.packetCount += 1
                existing.lastSeenMs = now
                existing.manufacturerText = manufacturerText
                existing.manufacturerDataText = manufacturerDataText
                existing.serviceUuidsText = serviceUuidsText
                existing.rawAdvText = rawAdvText
            }

            // Extract Drone Remote ID coordinates (ODID via UUID 0xFFFA)
            if (record != null) {
                val serviceData = record.serviceData
                if (serviceData != null) {
                    for ((uuid, data) in serviceData) {
                        if (uuid.toString().uppercase().contains("FFFA") && data.isNotEmpty()) {
                            val msgType = (data[0].toInt() and 0xF0) shr 4
                            if (msgType == 1 && data.size >= 13) {
                                val latInt = (data[5].toInt() and 0xFF) or
                                        ((data[6].toInt() and 0xFF) shl 8) or
                                        ((data[7].toInt() and 0xFF) shl 16) or
                                        ((data[8].toInt() and 0xFF) shl 24)
                                val lonInt = (data[9].toInt() and 0xFF) or
                                        ((data[10].toInt() and 0xFF) shl 8) or
                                        ((data[11].toInt() and 0xFF) shl 16) or
                                        ((data[12].toInt() and 0xFF) shl 24)
                                val lat = latInt / 10000000.0
                                val lon = lonInt / 10000000.0
                                if (lat != 0.0 && lon != 0.0) {
                                    BleStore.devices[addr]?.droneLat = lat
                                    BleStore.devices[addr]?.droneLon = lon
                                }
                            }
                        }
                    }
                }
            }

            val classText = classifyDevice(BleStore.devices[addr]!!)
            if (isAlertCategory(classText)) {
                BleStore.trackerSeenThisSession = true
                notifyDeviceDetected()
            }

            if (!BleStore.isListFrozen) uiDirty = true
            updateHeaderCounts()
        }
    }

    // ---------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------

    private fun renderDeviceList() {
        val sorted = BleStore.devices.values.sortedWith(
            compareByDescending<BleSeenDevice> { it.rssi }.thenByDescending { it.lastSeenMs }
        )

        var trackerCount = 0; var gadgetCount = 0; var droneCount = 0; var federalCount = 0; var otherCount = 0
        for (d in sorted) {
            val c = classifyDevice(d)
            when {
                isTrackerClass(c) -> trackerCount++
                isCyberGadgetClass(c) -> gadgetCount++
                isDroneClass(c) -> droneCount++
                isPoliceClass(c) -> federalCount++
                else -> otherCount++
            }
        }
        BleStore.dronePresent = droneCount > 0

        updateHeaderCounts()
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        adapter.replaceData(sorted.take(250))
        updateButtonStates()
    }

    // ---------------------------------------------------------------
    // BLE record helpers
    // ---------------------------------------------------------------

    private fun detectManufacturer(record: ScanRecord?): String {
        if (record == null) return "-"
        val data = record.manufacturerSpecificData ?: return "-"
        if (data.size() == 0) return "-"
        val ids = mutableListOf<Int>()
        for (i in 0 until data.size()) ids.add(data.keyAt(i))
        return when {
            ids.contains(0x004C) -> "APPLE"
            ids.contains(0x0006) -> "MSFT"
            ids.contains(0x0075) -> "SAMSNG"
            ids.contains(0x00E0) -> "GOOGL"
            ids.contains(0x00D2) -> "NRDIC"
            else -> String.format("%04X", ids.first())
        }
    }

    private fun formatManufacturerData(record: ScanRecord?): String {
        if (record == null) return "-"
        val data = record.manufacturerSpecificData ?: return "-"
        if (data.size() == 0) return "-"
        val parts = mutableListOf<String>()
        for (i in 0 until data.size()) {
            val id = data.keyAt(i)
            val bytes = data.valueAt(i)
            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            parts.add(String.format("0x%04X=[%s]", id, hex))
        }
        return parts.joinToString(" | ")
    }

    private fun formatServiceUuids(record: ScanRecord?): String {
        val uuids = record?.serviceUuids ?: return "-"
        if (uuids.isEmpty()) return "-"
        return uuids.joinToString(" | ") { it.uuid.toString() }
    }

    private fun formatRawAdv(record: ScanRecord?): String {
        val bytes = record?.bytes ?: return "-"
        if (bytes.isEmpty()) return "-"
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}

// ===================================================================
// OutlinedTextView
// ===================================================================

class OutlinedTextView(context: android.content.Context) : AppCompatTextView(context) {
    var strokeColor: Int = 0xFF000000.toInt()
    var strokeWidthPx: Float = 4f

    override fun onDraw(canvas: Canvas) {
        val orig = currentTextColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidthPx
        setTextColor(strokeColor)
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        setTextColor(orig)
        super.onDraw(canvas)
    }
}

// ===================================================================
// DeviceListAdapter
// ===================================================================

class DeviceListAdapter(
    private val activity: Activity,
    private val onDetailsClick: (BleSeenDevice) -> Unit
) : BaseAdapter() {

    private val items = mutableListOf<BleSeenDevice>()

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun nameCharLimit(): Int {
        val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (landscape) 32 else 22
    }

    fun replaceData(newItems: List<BleSeenDevice>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(pos: Int) = items[pos]
    override fun getItemId(pos: Int) = pos.toLong()

    private fun buildCell(text: String, weight: Float): TextView {
        return TextView(activity).apply {
            this.text = text
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val rowBg = if (position % 2 == 0) 0xFF8F0A0A.toInt() else 0xFF050505.toInt()
        val classText = classifyDevice(item)

        val isTracker = isTrackerClass(classText)
        val isCyber = isCyberGadgetClass(classText)
        val isDrone = isDroneClass(classText)
        val isPolice = isPoliceClass(classText)
        val tick = (activity as? MainActivity)?.getAnimationTick() ?: 0

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(rowBg)
                when {
                    isTracker -> setStroke(dp(2), 0xFFFFFF00.toInt())
                    isCyber   -> setStroke(dp(2), 0xFFFF8800.toInt())
                    isDrone   -> setStroke(dp(2), 0xFF8A2BE2.toInt())          // solid violet
                    isPolice  -> if (tick == 0) setStroke(dp(2), 0xFFFF0000.toInt()) else setStroke(dp(2), 0xFF0000FF.toInt())
                }
            }
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(rowBg)
        }

        val bottomRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(rowBg)
        }

        val clippedMfg = item.manufacturerText.take(6)
        val clippedName = item.name.replace("\n", " ").take(nameCharLimit())

        topRow.addView(buildCell(item.rssi.toString(), 0.9f))
        topRow.addView(buildCell(item.address, 3.0f))
        topRow.addView(buildCell(clippedMfg, 1.3f))

        val classColor = when (classText) {
            "AirTag", "Tile", "Galaxy Tag", "Find My" -> 0xFFFFFF00.toInt()
            "Flipper Zero", "Pwnagotchi", "Card Skimmer", "Dev Board", "WiFi Pineapple" -> 0xFFFF8800.toInt()
            "Drone" -> 0xFF8A2BE2.toInt()
            "Axon", "Flock" -> if (tick == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

        val classView = TextView(activity).apply {
            text = classText
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(classColor)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.9f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        topRow.addView(classView)

        val nameBox = TextView(activity).apply {
            text = android.text.SpannableString("NAME: $clippedName").apply {
                setSpan(android.text.style.ForegroundColorSpan(0xFF80FF80.toInt()), 0, 5, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 11f
            setTextColor(0xFFF2F2F2.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF303030.toInt())
                setStroke(dp(1), 0xFF555555.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
                bottomMargin = dp(6)
            }
        }

        bottomRow.addView(nameBox)
        container.addView(topRow)
        container.addView(bottomRow)
        container.setOnClickListener { onDetailsClick(item) }
        return container
    }
}
