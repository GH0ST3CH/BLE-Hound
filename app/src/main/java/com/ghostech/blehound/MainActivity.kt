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
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    var serviceDataText: String,
    var flagsText: String,
    var txPowerText: String,
    var appearanceText: String,
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
    val latchedTrackerClassByAddress = LinkedHashMap<String, String>()
    val latchedTrackerSeenMsByAddress = LinkedHashMap<String, Long>()
    var shouldScan = false
    var isListFrozen = false
    var vibrateOnTracker = false
    var soundOnTracker = false
    var trackerSeenThisSession = false
    var lastNotifyMs = 0L
    var lastPacketSeenMs = 0L
    var lastDiscoveryChangeMs = 0L
    var lastScanRestartMs = 0L
    var lastDroneBeepMs = 0L
    var dronePresent = false
}

// ---------------------------------------------------------------------------
// Classification
// ---------------------------------------------------------------------------

fun isExactPwnagotchiWifi(bssid: String): Boolean {
    return bssid == "de:ad:be:ef:de:ad"
}

fun isLikelyPwnagotchiWifi(ssid: String, bssid: String): Boolean {
    return bssid == "de:ad:be:ef:de:ad" ||
            bssid.startsWith("de:ad:be") ||
            ssid.contains("pwnagotchi") ||
            ssid.contains("bettercap") ||
            ssid.contains("pwngrid")
}

fun isLikelyWifiPineapple(ssid: String, bssid: String): Boolean {
    return ssid.contains("pineapple") ||
            ssid.contains("pineap") ||
            ssid.contains("openap") ||
            ssid.contains("evilportal") ||
            ssid.contains("hak5") ||
            (bssid.length >= 8 && bssid.substring(3, 5) == "13" && bssid.substring(6, 8) == "37")
}

fun isExactBleDrone(d: BleSeenDevice): Boolean {
    val name = d.name.uppercase()
    val raw = d.rawAdvText.uppercase()
    val svc = d.serviceUuidsText.uppercase()
    return (d.droneLat != null && d.droneLon != null) ||
            raw.contains("16 FA FF 0D") ||
            raw.replace(" ", "").contains("16FAFF0D") ||
            svc.contains("FFFA") ||
            name.contains("DJI") ||
            name.contains("PARROT") ||
            name.contains("SKYDIO") ||
            name.contains("AUTEL") ||
            name.contains("ANAFI")
}

fun isLikelyWifiDrone(ssid: String): Boolean {
    return ssid.contains("dji") ||
            ssid.contains("parrot") ||
            ssid.contains("skydio") ||
            ssid.contains("autel") ||
            ssid.contains("anafi") ||
            ssid.contains("drone") ||
            ssid.contains("uav") ||
            ssid.contains("uas") ||
            ssid.contains("remoteid") ||
            ssid.contains("remote-id") ||
            ssid.contains("opendroneid") ||
            ssid.contains("rid") ||
            ssid.contains("uas-") ||
            ssid.contains("uas_")
}

fun hasNyanboxBleRemoteIdSignature(d: BleSeenDevice): Boolean {
    val raw = d.rawAdvText.uppercase().replace(" ", "")
    return raw.contains("16FAFF0D")
}

fun hasRawPattern(rawText: String, hexNoSpace: String): Boolean {
    return rawText.uppercase().replace(" ", "").contains(hexNoSpace.uppercase().replace(" ", ""))
}

fun hasServiceUuidHint(serviceText: String, shortUuid: String): Boolean {
    val s = serviceText.uppercase()
    val u = shortUuid.uppercase()
    return s.contains(u) || s.contains("0000${u}-0000-1000-8000-00805F9B34FB")
}

fun isAirTagSignature(d: BleSeenDevice): Boolean {
    return hasRawPattern(d.rawAdvText, "1EFF4C00") ||
            hasRawPattern(d.rawAdvText, "4C001219")
}

fun isTileSignature(d: BleSeenDevice): Boolean {
    return hasServiceUuidHint(d.serviceUuidsText, "FEED") ||
            hasServiceUuidHint(d.serviceUuidsText, "FEEC")
}

fun isGalaxyTagSignature(d: BleSeenDevice): Boolean {
    return hasServiceUuidHint(d.serviceUuidsText, "FD5A")
}

fun isRayBanMetaSignature(d: BleSeenDevice): Boolean {
    return hasServiceUuidHint(d.serviceUuidsText, "FD5F")
}

fun classifyDevice(d: BleSeenDevice): String {
    getLatchedTrackerClass(d.address)?.let { return it }
    val name = d.name.lowercase()
    val mfg = d.manufacturerText.uppercase()
    val raw = d.rawAdvText.uppercase()
    val svc = d.serviceUuidsText.uppercase()
    val mfgData = d.manufacturerDataText.uppercase()

    // --- WiFi-detected devices (from WiFi scan) ---
    if (d.isWifi) {
        val bssid = d.address.lowercase()
        if (isExactPwnagotchiWifi(bssid)) return "Pwnagotchi"
        if (isLikelyPwnagotchiWifi(name, bssid)) return "Pwnagotchi"
        if (name.contains("flipper")) return "Flipper Zero"
        if (isLikelyWifiDrone(name)) return "Drone"
        if (isLikelyWifiPineapple(name, bssid)) return "WiFi Pineapple"
        return "WiFi Device"
    }

    // --- Apple devices ---
    if (mfg == "APPLE") {
        if (isAirTagSignature(d)) return "AirTag"
        if ("FINDMY" in name || "FIND MY" in name) return "Find My"
        if ("AIRPODS" in name || "AIRPOD" in name) return "AirPods"
        if ("IBEACON" in name) return "iBeacon"
        if ("004C" in mfgData && "0215" in raw.replace(" ", "")) return "iBeacon"
        if ("BEATS" in name) return "Beats"
        return "Apple BLE"
    }

    // --- General BLE ---
    if (isRayBanMetaSignature(d)) return "Meta Glasses"
    if (isTileSignature(d)) return "Tile"
    if (isGalaxyTagSignature(d)) return "Galaxy Tag"
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

    // --- BLE Identifiers ---
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
    if (assessSkimmer(d).isSkimmerCandidate) {
        return "Card Skimmer"
    }

    // Drone detection
    val droneNameMatch =
        "DJI" in name || "PARROT" in name || "SKYDIO" in name ||
        "AUTEL" in name || "ANAFI" in name || mfg == "DJI"

    val droneBleRemoteIdMatch =
        hasNyanboxBleRemoteIdSignature(d)

    val droneCoordsPresent =
        d.droneLat != null && d.droneLon != null

    if (droneCoordsPresent || droneBleRemoteIdMatch || droneNameMatch) {
        return "Drone"
    }

    // Axon detection
    val axonMacMatch = macPrefix == "00:25:df"
    val axonMfgMatch = mfg == "AXON"
    val axonNameMatch = "AXON" in name || "TASER" in name

    if (axonMacMatch || axonMfgMatch || axonNameMatch) {
        return when {
            "TASER" in name -> "Axon Taser"
            "CAM" in name || "BODY" in name || "BODYCAM" in name || "CAMERA" in name -> "Axon Cam"
            else -> "Axon Device"
        }
    }

    // Flock / ALPR systems
    val flockDirectPrefixes = listOf(
        "04:0d:84", "58:8e:81", "90:35:ea", "cc:cc:cc", "ec:1b:bd",
        "1c:34:f1", "38:5b:44", "94:34:69", "b4:e3:f9", "f0:82:c0",
        "14:5a:fc", "3c:91:80", "70:c9:4e", "80:30:49", "d8:f3:bc",
        "08:3a:88", "74:4c:a1", "94:08:53", "9c:2f:9d", "e4:aa:ea",
        "b4:1e:52"
    )

    val flockMfrPrefixes = listOf(
        "00:f4:8d", "d0:39:57", "e0:0a:f6",
        "f4:6a:dd", "f8:a2:d6", "e8:d0:fc"
    )

    val flockNameMatch =
        "FS EXT BATTERY" in name || "PENGUIN" in name || "FLOCK" in name || "PIGVISION" in name

    val flockDirectMatch = flockDirectPrefixes.contains(macPrefix)
    val flockMfrMatch = flockMfrPrefixes.contains(macPrefix)
    val flockXuntongMatch = mfg == "XUNTONG" || "09C8" in mfgData

    if (assessFlock(d).isFlock) {
        return "Flock"
    }
    // --- End BLE Identifier Logic ---

    if (svc != "-" && svc.isNotBlank()) return "BLE Device"

    return "-"
}

fun extractManufacturerCompanyIds(mfgDataText: String): Set<String> {
    val regex = Regex("""0x([0-9A-Fa-f]{4})=""")
    return regex.findAll(mfgDataText)
        .map { it.groupValues[1].uppercase() }
        .toSet()
}

data class FlockAssessment(
    val isFlock: Boolean,
    val confidence: String,
    val reasonCode: String,
    val reasonText: String
)

fun assessFlock(d: BleSeenDevice): FlockAssessment {
    val name = d.name.uppercase()
    val macPrefix = d.address.take(8).lowercase()
    val mfg = d.manufacturerText.uppercase()
    val companyIds = extractManufacturerCompanyIds(d.manufacturerDataText)

    val flockDirectPrefixes = setOf(
        "04:0d:84", "58:8e:81", "90:35:ea", "cc:cc:cc", "ec:1b:bd",
        "1c:34:f1", "38:5b:44", "94:34:69", "b4:e3:f9", "f0:82:c0",
        "14:5a:fc", "3c:91:80", "70:c9:4e", "80:30:49", "d8:f3:bc",
        "08:3a:88", "74:4c:a1", "94:08:53", "9c:2f:9d", "e4:aa:ea",
        "b4:1e:52"
    )

    val flockMfrPrefixes = setOf(
        "00:f4:8d", "d0:39:57", "e0:0a:f6",
        "f4:6a:dd", "f8:a2:d6", "e8:d0:fc"
    )

    val flockNameMatch =
        "FS EXT BATTERY" in name || "PENGUIN" in name || "FLOCK" in name || "PIGVISION" in name

    val flockDirectMatch = macPrefix in flockDirectPrefixes
    val flockMfrMatch = macPrefix in flockMfrPrefixes
    val flockXuntongMatch = mfg == "XUNTONG" || "09C8" in companyIds

    return when {
        flockDirectMatch -> FlockAssessment(
            true,
            "HIGH",
            "FLOCK_DIRECT_PREFIX",
            "Matched known Flock MAC prefix list"
        )
        flockXuntongMatch && flockNameMatch -> FlockAssessment(
            true,
            "HIGH",
            "FLOCK_XUNTONG+NAME",
            "Matched XUNTONG manufacturer company ID 0x09C8 and Flock-related device name"
        )
        flockMfrMatch && flockNameMatch -> FlockAssessment(
            true,
            "MEDIUM",
            "FLOCK_MFR_PREFIX+NAME",
            "Matched known contract-manufacturer MAC prefix and Flock-related device name"
        )
        flockXuntongMatch -> FlockAssessment(
            true,
            "MEDIUM",
            "FLOCK_XUNTONG",
            "Matched XUNTONG manufacturer company ID 0x09C8"
        )
        flockMfrMatch -> FlockAssessment(
            true,
            "LOW",
            "FLOCK_MFR_PREFIX",
            "Matched known contract-manufacturer MAC prefix"
        )
        flockNameMatch -> FlockAssessment(
            true,
            "LOW",
            "FLOCK_NAME",
            "Matched Flock-related BLE device name only"
        )
        else -> FlockAssessment(false, "-", "-", "Not identified as Flock")
    }
}

data class SkimmerAssessment(
    val isSkimmerCandidate: Boolean,
    val confidence: String,
    val reasonCode: String,
    val reasonText: String
)

fun assessSkimmer(d: BleSeenDevice): SkimmerAssessment {
    val name = d.name.uppercase()
    val hcNameMatch =
        "HC-03" in name || "HC-05" in name || "HC-06" in name

    return if (hcNameMatch) {
        SkimmerAssessment(
            true,
            "LOW",
            "SKIMMER_HC_NAME",
            "Matched HC-03/HC-05/HC-06 Bluetooth module naming pattern"
        )
    } else {
        SkimmerAssessment(false, "-", "-", "Not identified as a skimmer candidate")
    }
}

const val RULE_WHITELIST = "whitelist_rules"
const val RULE_BLACKLIST = "blacklist_rules"

fun rulePrefs(context: Context) =
    context.getSharedPreferences("blehound_rules", Context.MODE_PRIVATE)

fun ruleSet(context: Context, key: String): MutableSet<String> =
    rulePrefs(context).getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()



private fun cleanSigPart(s: String, max: Int = 80): String =
    s.uppercase().replace("\n", " ").replace("\r", " ").trim().take(max)

private fun buildHighValueSignature(d: BleSeenDevice): String {
    val cls = classifyDevice(d)
    return listOf(
        if (d.isWifi) "WIFI" else "BLE",
        cleanSigPart(cls, 40),
        cleanSigPart(d.manufacturerText, 40),
        cleanSigPart(d.manufacturerDataText, 80),
        cleanSigPart(d.serviceUuidsText, 80),
        cleanSigPart(d.serviceDataText, 80),
        cleanSigPart(d.appearanceText, 40),
        cleanSigPart(d.rawAdvText, 120)
    ).joinToString("||")
}



private fun ruleEntry(sig: String, mac: String): String =
    "SIG::$sig##MAC::" + mac.uppercase()

private fun ruleSig(entry: String): String =
    entry.substringAfter("SIG::", "").substringBefore("##MAC::")

private fun ruleMac(entry: String): String =
    entry.substringAfter("##MAC::", "").uppercase()



private fun findRuleMatch(context: Context, key: String, d: BleSeenDevice?, mac: String): String? {
    val rules = ruleSet(context, key)
    val macNorm = mac.uppercase()
    val sig = d?.let { buildHighValueSignature(it) }

    if (sig != null) {
        val bySig = rules.firstOrNull { ruleSig(it) == sig }
        if (bySig != null) return bySig
    }

    return rules.firstOrNull { ruleMac(it) == macNorm || it.uppercase() == macNorm }
}

fun saveRuleSet(context: Context, key: String, rules: Set<String>) {
    rulePrefs(context).edit().putStringSet(key, rules).apply()
}

fun isDeviceWhitelisted(context: Context, mac: String): Boolean =
    findRuleMatch(context, RULE_WHITELIST, BleStore.devices[mac], mac) != null

fun isDeviceBlacklisted(context: Context, mac: String): Boolean =
    findRuleMatch(context, RULE_BLACKLIST, BleStore.devices[mac], mac) != null

fun toggleWhitelist(context: Context, mac: String) {
    val wl = ruleSet(context, RULE_WHITELIST)
    val bl = ruleSet(context, RULE_BLACKLIST)
    val d = BleStore.devices[mac]
    val existing = findRuleMatch(context, RULE_WHITELIST, d, mac)

    if (existing != null) {
        wl.remove(existing)
    } else {
        val sig = d?.let { buildHighValueSignature(it) } ?: ""
        wl.add(ruleEntry(sig, mac))
        findRuleMatch(context, RULE_BLACKLIST, d, mac)?.let { bl.remove(it) }
    }

    saveRuleSet(context, RULE_WHITELIST, wl)
    saveRuleSet(context, RULE_BLACKLIST, bl)
}

fun toggleBlacklist(context: Context, mac: String) {
    val wl = ruleSet(context, RULE_WHITELIST)
    val bl = ruleSet(context, RULE_BLACKLIST)
    val d = BleStore.devices[mac]
    val existing = findRuleMatch(context, RULE_BLACKLIST, d, mac)

    if (existing != null) {
        bl.remove(existing)
    } else {
        val sig = d?.let { buildHighValueSignature(it) } ?: ""
        bl.add(ruleEntry(sig, mac))
        findRuleMatch(context, RULE_WHITELIST, d, mac)?.let { wl.remove(it) }
    }

    saveRuleSet(context, RULE_BLACKLIST, bl)
    saveRuleSet(context, RULE_WHITELIST, wl)
}

// ---------------------------------------------------------------------------
// Category helpers
// ---------------------------------------------------------------------------

fun isCyberGadgetClass(c: String) =
    c == "Flipper Zero" ||
    c == "Meta Glasses"
 || c == "Pwnagotchi" || c == "Card Skimmer" || c == "Dev Board" || c == "WiFi Pineapple"
fun isDroneClass(c: String) = c == "Drone"
fun isPoliceClass(c: String) =
    c == "Axon Device" || c == "Axon Cam" || c == "Axon Taser" || c == "Flock"
fun isTrackerClass(c: String) = c == "AirTag" || c == "Tile" || c == "Galaxy Tag" || c == "Find My"
fun isAlertCategory(c: String) = isTrackerClass(c) || isCyberGadgetClass(c) || isDroneClass(c) || isPoliceClass(c)

fun latchTrackerClass(address: String, classText: String) {
    if (!isTrackerClass(classText)) return
    BleStore.latchedTrackerClassByAddress[address] = classText
    BleStore.latchedTrackerSeenMsByAddress[address] = System.currentTimeMillis()
}

fun getLatchedTrackerClass(address: String): String? {
    val seen = BleStore.latchedTrackerSeenMsByAddress[address] ?: return null
    if (System.currentTimeMillis() - seen > 30000L) {
        BleStore.latchedTrackerSeenMsByAddress.remove(address)
        BleStore.latchedTrackerClassByAddress.remove(address)
        return null
    }
    return BleStore.latchedTrackerClassByAddress[address]
}

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
    private lateinit var settingsButtonView: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: DeviceListAdapter

    private var scanner: BluetoothLeScanner? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var wifiManager: WifiManager? = null
    private var wifiReceiverRegistered = false
    private var isScannerActive = false
    private var sessionRunning = false
    private var filteredModeEnabled = true

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
        if (!BleStore.shouldScan || !sessionRunning) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val results = wifiManager?.scanResults ?: return
        val now = System.currentTimeMillis()
        for (r in results) {
            val bssid = r.BSSID ?: continue
            val ssid = r.SSID ?: ""
            val rssi = r.level

            val existing = BleStore.devices[bssid]
            val candidate = (existing?.copy() ?: BleSeenDevice(
                name = ssid.ifEmpty { "Hidden Network" },
                address = bssid,
                rssi = rssi,
                packetCount = 0,
                lastSeenMs = now,
                manufacturerText = "WIFI",
                manufacturerDataText = "-",
                serviceUuidsText = "-",
                serviceDataText = "-",
                flagsText = "-",
                txPowerText = "-",
                appearanceText = "-",
                rawAdvText = "-",
                isWifi = true
            )).apply {
                if (ssid.isNotEmpty()) name = ssid else if (name.isBlank()) name = "Hidden Network"
                this.rssi = rssi
                packetCount += 1
                lastSeenMs = now
                manufacturerText = "WIFI"
                manufacturerDataText = if (r.capabilities.isNullOrBlank()) "-" else r.capabilities
                serviceUuidsText = "FREQ=${r.frequency}"
                serviceDataText = "-"
                flagsText = "-"
                txPowerText = "-"
                appearanceText = "-"
                rawAdvText = "SSID=${if (ssid.isEmpty()) "<hidden>" else ssid}"
                isWifi = true
            }

            val cls = classifyDevice(candidate)
            if (isTrackerClass(cls)) latchTrackerClass(bssid, cls)
            if (filteredModeEnabled && !isAlertCategory(cls)) {
                BleStore.devices.remove(bssid)
                continue
            }

            BleStore.devices[bssid] = candidate
            if (isAlertCategory(cls)) notifyDeviceDetected(cls)
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
            if (!BleStore.isListFrozen && BleStore.dronePresent && BleStore.soundOnTracker) {
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
                val packetStaleFor = now - BleStore.lastPacketSeenMs
                val discoveryStaleFor = now - BleStore.lastDiscoveryChangeMs
                val restartedAgo = now - BleStore.lastScanRestartMs

                if (
                    restartedAgo > 5000L &&
                    (
                        (BleStore.lastPacketSeenMs > 0L && packetStaleFor > 12000L) ||
                        (BleStore.lastDiscoveryChangeMs > 0L && discoveryStaleFor > 15000L)
                    )
                ) {
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
            ).apply { setStroke(dp(1), themeColor(this@MainActivity)) }
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
            setTextColor(themeColor(this@MainActivity))
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

        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
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

        settingsButtonView = TextView(this).apply {
            text = "⚙"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(themeColor(this@MainActivity))
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
        startButton.setOnClickListener {
            if (!sessionRunning) {
                startScan()
                updateButtonStates()
            } else {
                resetToLaunchState()
                updateButtonStates()
            }
        }

        stopButton = buildHellButton("PAUSE")
        stopButton.setOnClickListener {
            if (!sessionRunning) return@setOnClickListener
            if (BleStore.isListFrozen) {
                resumeScan()
            } else {
                lockList()
            }
        }

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
            settingsButtonView,
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
        syncSessionState()
        loadFilterModePreference()
        applyCurrentFilter()
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

        registerWifiReceiverIfNeeded()
        uiHandler.post(uiRefreshRunnable)
        uiHandler.post(scanWatchdogRunnable)
        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        syncSessionState()
        loadFilterModePreference()
        applyCurrentFilter()
        flameTitleView.setTextColor(themeColor(this))
        if (::settingsButtonView.isInitialized) settingsButtonView.setTextColor(themeColor(this))
        refreshHellButtonOutline(startButton)
        refreshHellButtonOutline(stopButton)
        refreshHellButtonOutline(vibrateButton)
        refreshHellButtonOutline(soundButton)
        updateFlameHeader()
        listView.invalidateViews()
        renderDeviceList()
    }

    override fun onDestroy() {
        BleStore.shouldScan = isScannerActive
        super.onDestroy()
        unregisterWifiReceiverIfNeeded()
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

    private fun registerWifiReceiverIfNeeded() {
        if (wifiReceiverRegistered) return
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        wifiReceiverRegistered = true
    }

    private fun unregisterWifiReceiverIfNeeded() {
        if (!wifiReceiverRegistered) return
        try { unregisterReceiver(wifiScanReceiver) } catch (_: Exception) {}
        wifiReceiverRegistered = false
    }

    private fun loadFilterModePreference() {
        filteredModeEnabled = getSharedPreferences("blehound_prefs", MODE_PRIVATE).getBoolean("filtered_mode", true)
    }

    private fun syncSessionState() {
        sessionRunning = isScannerActive || BleStore.shouldScan
    }

    private fun applyCurrentFilter() {
        if (!filteredModeEnabled) return
        val iter = BleStore.devices.entries.iterator()
        var removed = false
        while (iter.hasNext()) {
            if (!isAlertCategory(classifyDevice(iter.next().value))) {
                iter.remove()
                removed = true
            }
        }
        if (removed) uiDirty = true
    }

    private fun updateFlameHeader() {
        val tick = animationTick % 4

        val titleColor = if (isDefaultTheme(this)) {
            when (tick) {
                0 -> 0xFFFF5A1F.toInt()
                1 -> 0xFFFF6A2A.toInt()
                2 -> 0xFFFF7A35.toInt()
                else -> 0xFFFF6A2A.toInt()
            }
        } else {
            themeColor(this)
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
            setStroke(dp(1), themeColor(this@MainActivity))
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
                setStroke(dp(1), themeColor(this@MainActivity))
            }
            setPadding(dp(10), dp(14), dp(10), dp(14))
        }
    }

    private fun refreshHellButtonOutline(button: Button) {
        val bg = button.background
        if (bg is GradientDrawable) {
            bg.setStroke(dp(1), themeColor(this))
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

    private fun stopActiveAlerts() {
        try { vibrator?.cancel() } catch (_: Exception) {}
        try { toneGenerator?.stopTone() } catch (_: Exception) {}
        BleStore.dronePresent = false
    }

    private fun resetToLaunchState() {
        stopActiveAlerts()
        hardStopScanner()
        sessionRunning = false
        BleStore.shouldScan = false
        syncSessionState()
        BleStore.isListFrozen = false
        BleStore.trackerSeenThisSession = false
        BleStore.lastNotifyMs = 0L
        BleStore.lastPacketSeenMs = 0L
        BleStore.lastDiscoveryChangeMs = 0L
        BleStore.lastScanRestartMs = 0L
        BleStore.lastDroneBeepMs = 0L
        BleStore.devices.clear()
        BleStore.latchedTrackerClassByAddress.clear()
        BleStore.latchedTrackerSeenMsByAddress.clear()
        uiDirty = true
        renderDeviceList()
        updateButtonStates()
    }

    // ---------------------------------------------------------------
    // Notifications - fires for ANY alert category
    // ---------------------------------------------------------------


    private fun playCategoryTone(category: String) {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        val key = when {
            isTrackerClass(category) -> "sound_trackers"
            isCyberGadgetClass(category) -> "sound_gadgets"
            isDroneClass(category) -> "sound_drones"
            isPoliceClass(category) -> "sound_feds"
            else -> ""
        }
        if (key.isEmpty()) return

        val raw = prefs.getString(key, "__DEFAULT__") ?: "__DEFAULT__"

        if (raw == "__SILENT__" || raw == "__DISABLED__") return

        if (raw == "__DEFAULT__") {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            uiHandler.postDelayed({
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            }, 220)
            return
        }

        try {
            RingtoneManager.getRingtone(this, Uri.parse(raw))?.play()
        } catch (_: Exception) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            uiHandler.postDelayed({
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
            }, 220)
        }
    }

    private fun notifyDeviceDetected(category: String) {
        if (BleStore.isListFrozen) return
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)

        val enabled = when {
            isTrackerClass(category) -> prefs.getBoolean("notif_trackers", true)
            isCyberGadgetClass(category) -> prefs.getBoolean("notif_gadgets", true)
            isDroneClass(category) -> prefs.getBoolean("notif_drones", true)
            isPoliceClass(category) -> prefs.getBoolean("notif_feds", true)
            else -> true
        }

        val soundKey = when {
            isTrackerClass(category) -> "sound_trackers"
            isCyberGadgetClass(category) -> "sound_gadgets"
            isDroneClass(category) -> "sound_drones"
            isPoliceClass(category) -> "sound_feds"
            else -> ""
        }

        if (!enabled) return
        if (soundKey.isNotEmpty() && prefs.getString(soundKey, "__DEFAULT__") == "__DISABLED__") return

        val now = System.currentTimeMillis()
        if (now - BleStore.lastNotifyMs < 3000) return
        BleStore.lastNotifyMs = now

        if (BleStore.vibrateOnTracker) {
            vibrator?.let { v ->
                if (!v.hasVibrator()) return@let
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(180)
                }
            }
        }

        if (BleStore.soundOnTracker) {
            playCategoryTone(category)
        }
    }

    private fun setHellButtonState(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.38f
    }

    private fun updateButtonStates() {
        startButton.text = if (sessionRunning) "STOP" else "START"
        stopButton.text = if (!sessionRunning) "PAUSE" else if (BleStore.isListFrozen) "RESUME" else "PAUSE"

        startButton.isEnabled = true
        startButton.alpha = 1.0f

        stopButton.isEnabled = sessionRunning
        stopButton.alpha = if (sessionRunning) 1.0f else 0.38f

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
            syncSessionState()
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
        syncSessionState()
        if (BleStore.shouldScan) {
            registerWifiReceiverIfNeeded()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                scanner?.startScan(null, settings, scanCallback)
                isScannerActive = true
                syncSessionState()
                BleStore.lastScanRestartMs = System.currentTimeMillis()
                BleStore.lastPacketSeenMs = System.currentTimeMillis()
                BleStore.lastDiscoveryChangeMs = System.currentTimeMillis()
            }
        }
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun startScan() {
        sessionRunning = true
        BleStore.shouldScan = true
        BleStore.isListFrozen = false
        registerWifiReceiverIfNeeded()
        if (isScannerActive) {
            BleStore.shouldScan = true
            syncSessionState()
            renderDeviceList()
            updateButtonStates()
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(null, settings, scanCallback)
        isScannerActive = true
        BleStore.shouldScan = true
        syncSessionState()
        BleStore.lastScanRestartMs = System.currentTimeMillis()
        BleStore.lastPacketSeenMs = System.currentTimeMillis()
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun lockList() {
        if (!sessionRunning) {
            updateButtonStates()
            return
        }
        BleStore.isListFrozen = true
        BleStore.shouldScan = true
        stopActiveAlerts()
        listView.isEnabled = true
        listView.isClickable = true
        listView.isFocusable = true
        statusView.text = "PAUSED"
        updateButtonStates()
    }

    private fun resumeScan() {
        if (!sessionRunning) {
            updateButtonStates()
            return
        }
        BleStore.isListFrozen = false
        BleStore.shouldScan = true
        listView.isEnabled = true
        listView.isClickable = true
        listView.isFocusable = true
        statusView.text = "TAP ANY DEVICE ROW TO VIEW DETAILS"
        updateButtonStates()
    }

    private fun hardStopScanner() {
        unregisterWifiReceiverIfNeeded()
        if (isScannerActive && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanner?.stopScan(scanCallback)
        }
        isScannerActive = false
        syncSessionState()
        BleStore.lastPacketSeenMs = 0L
        BleStore.lastDiscoveryChangeMs = 0L
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
            if (!BleStore.shouldScan || !sessionRunning) return
            val record = result.scanRecord
            val name = result.device.name ?: record?.deviceName ?: "Unknown"
            val addr = result.device.address ?: "Unknown"
            val rssi = result.rssi
            val now = System.currentTimeMillis()
            BleStore.lastPacketSeenMs = now

            val manufacturerText = detectManufacturer(record)
            val manufacturerDataText = formatManufacturerData(record)
            val serviceUuidsText = formatServiceUuids(record)
            val serviceDataText = formatServiceData(record)
            val flagsText = formatFlags(record)
            val txPowerText = formatTxPower(record)
            val appearanceText = formatAppearance(record)
            val rawAdvText = formatRawAdv(record)

            val existing = BleStore.devices[addr]
            val candidate = if (existing == null) {
                BleStore.lastDiscoveryChangeMs = now
                BleSeenDevice(
                    name = name, address = addr, rssi = rssi,
                    packetCount = 1, lastSeenMs = now,
                    manufacturerText = manufacturerText,
                    manufacturerDataText = manufacturerDataText,
                    serviceUuidsText = serviceUuidsText,
                    serviceDataText = serviceDataText,
                    flagsText = flagsText,
                    txPowerText = txPowerText,
                    appearanceText = appearanceText,
                    rawAdvText = rawAdvText
                )
            } else {
                val changed = existing.name != name ||
                    kotlin.math.abs(existing.rssi - rssi) >= 6 ||
                    existing.manufacturerText != manufacturerText ||
                    existing.manufacturerDataText != manufacturerDataText ||
                    existing.serviceUuidsText != serviceUuidsText ||
                    existing.serviceDataText != serviceDataText ||
                    existing.flagsText != flagsText ||
                    existing.txPowerText != txPowerText ||
                    existing.appearanceText != appearanceText ||
                    existing.rawAdvText != rawAdvText

                if (changed) {
                    BleStore.lastDiscoveryChangeMs = now
                }

                existing.name = name
                existing.rssi = rssi
                existing.packetCount += 1
                existing.lastSeenMs = now
                existing.manufacturerText = manufacturerText
                existing.manufacturerDataText = manufacturerDataText
                existing.serviceUuidsText = serviceUuidsText
                existing.serviceDataText = serviceDataText
                existing.flagsText = flagsText
                existing.txPowerText = txPowerText
                existing.appearanceText = appearanceText
                existing.rawAdvText = rawAdvText
                existing
            }

            val preliminaryClass = classifyDevice(candidate)
            if (isTrackerClass(preliminaryClass)) latchTrackerClass(addr, preliminaryClass)
            if (filteredModeEnabled && !isAlertCategory(preliminaryClass)) {
                BleStore.devices.remove(addr)
                if (!BleStore.isListFrozen) uiDirty = true
                updateHeaderCounts()
                return
            }

            BleStore.devices[addr] = candidate

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
                notifyDeviceDetected(classText)
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
            ids.contains(0x08AA) -> "DJI"
            ids.contains(0x09C8) -> "XUNTONG"
            ids.contains(0xFC81) -> "AXON"
            else -> String.format("%04X", ids.first())
        }
    }

    private fun formatServiceData(record: ScanRecord?): String {
        if (record == null) return "-"
        val out = mutableListOf<String>()
        val sd = record.serviceData ?: return "-"
        for ((uuid, data) in sd) {
            val hex = data.joinToString(" ") { "%02X".format(it) }
            out.add("${uuid}: [$hex]")
        }
        return if (out.isEmpty()) "-" else out.joinToString(" | ")
    }

    private fun formatFlags(record: ScanRecord?): String {
        val bytes = record?.bytes ?: return "-"
        if (bytes.size < 3) return "-"
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0 || i + len >= bytes.size + 1) break
            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x01 && len >= 2) return "0x%02X".format(bytes[i + 2].toInt() and 0xFF)
            i += len + 1
        }
        return "-"
    }

    private fun formatTxPower(record: ScanRecord?): String {
        val v = record?.txPowerLevel ?: Int.MIN_VALUE
        return if (v == Int.MIN_VALUE) "-" else "${v} dBm"
    }

    private fun formatAppearance(record: ScanRecord?): String {
        val bytes = record?.bytes ?: return "-"
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0 || i + len >= bytes.size + 1) break
            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x19 && len >= 3) {
                val lo = bytes[i + 2].toInt() and 0xFF
                val hi = bytes[i + 3].toInt() and 0xFF
                return "0x%04X".format((hi shl 8) or lo)
            }
            i += len + 1
        }
        return "-"
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
        val rowBg = if (position % 2 == 0) themedListRowColor(activity) else 0xFF050505.toInt()
        val classText = classifyDevice(item)
        val isWhitelisted = isDeviceWhitelisted(activity, item.address)

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
                if (!isWhitelisted) {
                    when {
                        isTracker -> setStroke(dp(2), 0xFFFFFF00.toInt())
                        isCyber   -> setStroke(dp(2), 0xFFFF8800.toInt())
                        isDrone   -> setStroke(dp(2), 0xFF8A2BE2.toInt())
                        isPolice  -> if (tick == 0) setStroke(dp(2), 0xFFFF0000.toInt()) else setStroke(dp(2), 0xFF0000FF.toInt())
                    }
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

        val topTextColor = if (!isDefaultTheme(activity) && rowBg != 0xFF050505.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

        fun buildTopCell(text: String, weight: Float): TextView {
            return buildCell(text, weight).apply {
                setTextColor(topTextColor)
            }
        }

        topRow.addView(buildTopCell(item.rssi.toString(), 0.9f))
        topRow.addView(buildTopCell(item.address, 3.0f))
        topRow.addView(buildTopCell(clippedMfg, 1.3f))

        val classColor = when (classText) {
            "AirTag", "Tile", "Galaxy Tag", "Find My" -> 0xFFFFFF00.toInt()
            "Flipper Zero", "Pwnagotchi", "Card Skimmer", "Dev Board", "WiFi Pineapple" -> 0xFFFF8800.toInt()
            "Drone" -> 0xFF8A2BE2.toInt()
            "Axon Device", "Axon Cam", "Axon Taser", "Flock" ->
                if (tick == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

        val classView = OutlinedTextView(activity).apply {
            text = classText
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(classColor)
            strokeColor = 0xFF000000.toInt()
            strokeWidthPx = if (!isDefaultTheme(activity) && rowBg != 0xFF050505.toInt()) dp(2).toFloat() else 0f
            setPadding(dp(8), dp(8), dp(4), dp(8))
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
            setTextColor(0xFFFFFFFF.toInt())
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
