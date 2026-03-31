package com.ghostech.blehound

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.bluetooth.*
import android.bluetooth.le.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


class DetailActivity : Activity() {
    private val SPP_UUID =
        java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    private lateinit var rssiView: TextView
    private lateinit var summaryView: TextView
    private lateinit var detailsView: TextView

    private lateinit var gattView: TextView
    private lateinit var skimmerScanButton: Button
    private var gattData: String = ""

    private var bluetoothGatt: BluetoothGatt? = null
    private var skimmerValidationConfidence: String? = null
    private var skimmerValidationCode: String? = null
    private var skimmerValidationText: String? = null


    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { gattView.text = "GATT CONNECTED - DISCOVERING SERVICES..." }
                
gatt.requestMtu(517)
gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread { gattData += "\n[STATUS] GATT DISCONNECTED\n"
                runOnUiThread { gattView.text = gattData } }
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gattData += "\nMTU NEGOTIATED: $mtu\n"
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val sb = StringBuilder()
            sb.append("\n================ GATT PROFILE\n[STATUS] GATT DISCONNECTED ================\n\n")

            for (service in gatt.services) {
                val sUuid = service.uuid.toString()
                val sName = uuidName(sUuid)
                sb.append("\n------------------------------\n[SERVICE]\n")
                sb.append("UUID        : ").append(sUuid).append("\n")
                if (sName.isNotEmpty()) sb.append(" [").append(sName).append("]")
                sb.append("\n")

                for (ch in service.characteristics) {
                    val cUuid = ch.uuid.toString()
                    val cName = uuidName(cUuid)
                    sb.append("  ├─ CHARACTERISTIC\n")
                    sb.append("     UUID        : ").append(cUuid).append("\n")
                    if (cName.isNotEmpty()) sb.append(" [").append(cName).append("]")

                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        gatt.readCharacteristic(ch)
                    }

                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        gatt.setCharacteristicNotification(ch, true)
                        for (desc in ch.descriptors) {
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                        }
                        sb.append(" [NOTIFY ENABLED]")
                    }

                    for (desc in ch.descriptors) {
                        gatt.readDescriptor(desc)
                    }

                    sb.append("\n")
                }

                sb.append("\n")
            }

            gattData = if (sb.isNotEmpty()) sb.toString() else gattData

            runOnUiThread {
                gattView.text = gattData
            }
        }

        
        
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val pretty = bytesToPretty(descriptor.value)
            val dUuid = descriptor.uuid.toString()
            val dName = uuidName(dUuid)
            gattData += "    DESC: " + dUuid +
                (if (dName.isNotEmpty()) " [" + dName + "]" else "") +
                " = " + pretty + "\n"
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val pretty = bytesToPretty(characteristic.value)
            gattData += "    NOTIFY: ${characteristic.uuid} = $pretty\n"

            runOnUiThread {
                gattView.text = gattData
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = characteristic.value
            val pretty = bytesToPretty(value)

            gattData += "     VALUE\n"
            gattData += "       UUID  : ${characteristic.uuid}\n"
            gattData += "       DATA  : $pretty\n"

            runOnUiThread {
                gattView.text = gattData
            }
        }
    }


    private var pendingSaveText: String = ""
    private var pendingSaveName: String = "SIGNAL-LOG-0001.txt"

    private val handler = Handler(Looper.getMainLooper())
    private var address: String? = null

    private var cachedDevice: BleSeenDevice? = null


    private val refresher = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = intent.getStringExtra("address")
        val initial = BleStore.devices[address]
        if (initial != null) {
            cachedDevice = initial.copy()
        }


        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val headerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF2A0000.toInt(),
                    0xFF140000.toInt(),
                    0xFF000000.toInt()
                )
            ).apply {
                setStroke(dp(1), themeColor(this@DetailActivity))
            }
        }

        val titleView = TextView(this).apply {
            text = "BLE HOUND DETAIL"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)
            setTextColor(themeColor(this@DetailActivity))
            setShadowLayer(12f, 0f, 0f, 0xFFFF7700.toInt())
            letterSpacing = 0.08f
            setPadding(0, 0, 0, dp(8))
        }

        rssiView = TextView(this).apply {
            text = "-- dBm"
            gravity = Gravity.CENTER
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFF0D0.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        summaryView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFFFD2A0.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        val saveButton = Button(this).apply {
            text = "SAVE LOG"
            setOnClickListener { saveCurrentDeviceLog() }
        }
        skimmerScanButton = Button(this).apply {
            text = "SKIMMER TEST"
            visibility = android.view.View.GONE
            setOnClickListener { beginSkimmerValidation() }
        }


        headerPanel.addView(titleView)
        headerPanel.addView(rssiView)
        headerPanel.addView(summaryView)
        headerPanel.addView(saveButton)
        headerPanel.addView(skimmerScanButton)
        val gattButton = Button(this).apply {
            text = "READ GATT"
            setOnClickListener { readGatt() }
        }

        gattView = TextView(this).apply {
            setBackgroundColor(0xFF050505.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setLineSpacing(2f, 1.1f)

            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF66CCFF.toInt())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            text = "GATT DATA: (not loaded)"
        }

        headerPanel.addView(gattButton)
        


        detailsView = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF9CFF9C.toInt())
            setBackgroundColor(0xFF000000.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val scrollContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(detailsView)
            addView(gattView)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(scrollContainer)
        }

        root.addView(headerPanel)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    

    private fun nowStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private fun nextLogFileName(deviceClass: String): String {
        val prefs = getSharedPreferences("blehound_logs", MODE_PRIVATE)
        val key = "log_counter_" + deviceClass.uppercase().replace(" ", "_")
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return deviceClass.uppercase().replace(" ", "-") + "-LOG-" + next.toString().padStart(4, '0') + ".txt"
    }

    private fun buildClassificationReason(d: BleSeenDevice): String {
        val n = d.name.uppercase()
        val m = d.manufacturerText.uppercase()
        val md = d.manufacturerDataText.uppercase()
        val raw = d.rawAdvText.uppercase()
        val svc = d.serviceUuidsText.uppercase()
        val mac = d.address.lowercase()
        if (d.isWifi) {
            if (mac == "de:ad:be:ef:de:ad") return "Exact Wi-Fi Pwnagotchi BSSID match"
            if (mac.startsWith("de:ad:be")) return "Wi-Fi Pwnagotchi-style MAC prefix match"
            if (d.name.lowercase().contains("pwnagotchi")) return "Wi-Fi name contains pwnagotchi"
            if (isLikelyWifiDrone(d.name.lowercase())) return "Wi-Fi name matched drone keyword"
            if (isLikelyWifiPineapple(d.name.lowercase(), mac)) return "Wi-Fi SSID/BSSID matched Pineapple heuristic"
            return "Wi-Fi classification based on SSID/BSSID heuristics"
        }
        if (m == "APPLE" && ("AIRTAG" in n || ("004C" in md && ("12 19" in raw || "004C 12" in raw || "004C 19" in raw))) ) {
            return "Apple manufacturer data / BLE advertisement matched AirTag pattern"
        }
        if (mac == "80:e1:26" || mac == "80:e1:27" || mac == "0c:fa:22" || "3081" in svc || "3082" in svc || "3083" in svc || "3080" in svc || "FLIPPER" in n) {
            return "Flipper Zero signature matched MAC, service UUID, or name"
        }
        if ("PWNAGOTCHI" in n || mac.startsWith("de:ad:be")) return "Pwnagotchi signature matched name or MAC prefix"
        if ("HC-03" in n || "HC-05" in n || "HC-06" in n) return "Card skimmer signature matched HC-03/05/06 naming"
        if (d.droneLat != null && d.droneLon != null) return "Drone coordinates parsed from BLE payload"
        if (raw.contains("16 FA FF 0D") || raw.replace(" ", "").contains("16FAFF0D")) return "BLE raw advertisement contains 16 FA FF 0D drone signature"
        if (svc.contains("FFFA")) return "BLE service UUID contains FFFA"
        if ("DJI" in n || "PARROT" in n || "SKYDIO" in n || "AUTEL" in n || "ANAFI" in n) return "BLE name matched drone vendor keyword"
        if (mac.startsWith("00:25:df") || "AXON" in n) return "Axon signature matched MAC prefix or name"
        if (classifyDevice(d) == "Flock") {
            val flockAssessment = assessFlock(d)
            if (flockAssessment.isFlock) return flockAssessment.reasonText
        }
        return "Classification based on current BLE/Wi-Fi signature rules"
    }

    private fun buildResearchLog(d: BleSeenDevice): String {
        val deviceClass = classifyDevice(d)
        val flockAssessment = if (deviceClass == "Flock") assessFlock(d) else null
        val skimmerAssessment = if (deviceClass == "Card Skimmer") assessSkimmer(d) else null
        val reason = flockAssessment?.reasonText
            ?: skimmerValidationText
            ?: skimmerAssessment?.reasonText
            ?: buildClassificationReason(d)
        val ageMs = System.currentTimeMillis() - d.lastSeenMs
        val ageText = if (ageMs < 1000) "${ageMs}ms" else String.format(Locale.US, "%.1fs", ageMs / 1000.0)

        return buildString {
            append("BLE HOUND SIGNAL LOG\n")
            append("====================\n\n")

            append("LOGGED AT           : ${nowStamp()}\n")
            append("CLASS               : ${deviceClass}\n")
            if (flockAssessment != null) {
                append("CONFIDENCE          : ${flockAssessment.confidence}\n")
                append("REASON CODE         : ${flockAssessment.reasonCode}\n")
            }
            append("CLASS BASIS         : ${reason}\n")
            append("NAME                : ${d.name}\n")
            append("MAC ADDRESS         : ${d.address}\n")
            append("MANUFACTURER        : ${d.manufacturerText}\n")
            append("RSSI AT SAVE        : ${d.rssi} dBm\n")
            append("PACKETS OBSERVED    : ${d.packetCount}\n")
            append("AGE AT SAVE         : ${ageText}\n")

            if (d.droneLat != null && d.droneLon != null) {
                append("DRONE LATITUDE      : ${d.droneLat}\n")
                append("DRONE LONGITUDE     : ${d.droneLon}\n")
            }

            append("\nMANUFACTURER DATA\n")
            append("-------\n")
            append("${d.manufacturerDataText}\n")

            append("\nSERVICE UUIDS\n")
            append("---\n")
            append("${d.serviceUuidsText}\n")

            append("\nRAW ADVERTISEMENT\n")
            append("-------\n")
            append("${d.rawAdvText}\n")

            append("\nGATT DATA\n")
            append("---------\n")
            append(if (gattData.isBlank()) "Not collected" else gattData)


            append("\nGATT DATA\n")
            append("---------\n")
            append(if (gattData.isBlank()) "Not collected" else gattData)

        }
    }

    private fun saveCurrentDeviceLog() {
        val addr = address ?: return
        val d = BleStore.devices[addr] ?: return
        val deviceClass = classifyDevice(d)
        pendingSaveName = nextLogFileName(deviceClass)
        pendingSaveText = buildResearchLog(d)

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, pendingSaveName)
        }
        startActivityForResult(intent, SAVE_LOG_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAVE_LOG_REQUEST && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.openOutputStream(uri)?.use {
                it.write(pendingSaveText.toByteArray())
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getDeviceDescription(classText: String): String {
        return when (classText) {
            "Flipper Zero" -> "Portable multi-tool for pentesters and geeks. Can emulate RFID, NFC, sub-GHz remotes, and more. Commonly used for security research and sometimes malicious replay attacks."
            "Pwnagotchi" -> "AI-powered WiFi auditing tool built on Raspberry Pi. Passively captures WPA handshakes to audit wireless network security. Often carried by security researchers."
            "WiFi Pineapple" -> "Rogue access point and WiFi auditing platform by Hak5. Used for man-in-the-middle attacks, credential harvesting, and wireless network reconnaissance. Identified by OUI pattern xx:13:37."
            "Card Skimmer" -> "WARNING: Potential card skimmer detected. HC-05/HC-06 Bluetooth modules are commonly found in illegal skimming devices attached to ATMs, gas pumps, and POS terminals. Exercise caution."
            "Drone" -> "Unmanned Aerial Vehicle (UAV) detected. BLE detection follows nyanBOX-style Open Drone ID / Remote ID signature logic where feasible on Android. Coordinates are shown below if available."
            "Axon Device" -> "Axon law enforcement equipment detected. This label is used when BLE evidence indicates an Axon device but the advertisement name does not identify the exact product type."
            "Axon Cam" -> "Axon camera detected. This label is used only when the BLE advertisement name suggests a camera or body camera."
            "Axon Taser" -> "Axon TASER device detected. This label is used only when the BLE advertisement name suggests a TASER device."
            "Flock" -> "Flock Safety Automated License Plate Recognition (ALPR) system detected. These cameras are used by law enforcement and private communities to log vehicle plate data."
            "AirTag" -> "Apple AirTag Bluetooth tracker. Can be used legitimately for item tracking but also potentially for unwanted surveillance."
            "Tile" -> "Tile Bluetooth tracker. Used for finding personal items. Be aware of potential unwanted tracking."
            "Galaxy Tag" -> "Samsung Galaxy SmartTag Bluetooth tracker."
            "Find My" -> "Apple Find My network compatible device."
            "Dev Board" -> "Development board (ESP32/Arduino). General purpose microcontroller often used in DIY projects, IoT devices, and security research tools."
            else -> ""
        }
    }

    
    
    
    private fun uuidName(uuid: String): String {
        val u = uuid.lowercase()
        return when {
            u.contains("180f") -> "Battery Service"
            u.contains("2a19") -> "Battery Level"
            u.contains("180d") -> "Heart Rate Service"
            u.contains("2a37") -> "Heart Rate Measurement"
            u.contains("180a") -> "Device Information"
            u.contains("2a29") -> "Manufacturer Name"
            u.contains("2a24") -> "Model Number"
            u.contains("2a25") -> "Serial Number"
            u.contains("2a27") -> "Hardware Revision"
            u.contains("2a26") -> "Firmware Revision"
            u.contains("2a28") -> "Software Revision"
            u.contains("2902") -> "Client Characteristic Config"
            else -> ""
        }
    }

    private fun bytesToPretty(value: ByteArray?): String {
        if (value == null) return "null"

        val hex = value.joinToString(" ") { "%02X".format(it) }
        val ascii = value.map {
            if (it in 32..126) it.toInt().toChar() else '.'
        }.joinToString("")

        return "HEX[$hex] ASCII[$ascii]"
    }

    private fun readGatt() {
        val addr = address ?: return

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val device = adapter.getRemoteDevice(addr) ?: return

        gattData = "CONNECTING TO " + addr
        gattView.text = gattData

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun render() {
        val addr = address ?: return
        val d = BleStore.devices[addr]

        if (d == null) {
            rssiView.text = "-- dBm"
            
            
            return
        }

        val ageMs = System.currentTimeMillis() - d.lastSeenMs
        val ageText = when {
            ageMs < 1000 -> "${ageMs}ms"
            else -> String.format("%.1fs", ageMs / 1000.0)
        }

        rssiView.text = if (d.rssi != 0) "${d.rssi} dBm" else rssiView.text
        val deviceClass = classifyDevice(d)
        val description = getDeviceDescription(deviceClass)
        val skimmerAssessment = if (deviceClass == "Card Skimmer") assessSkimmer(d) else null

        skimmerScanButton.visibility =
            if (deviceClass == "Card Skimmer") android.view.View.VISIBLE else android.view.View.GONE
        val flockAssessment = if (deviceClass == "Flock") assessFlock(d) else null

        summaryView.text =
            "CLASS:${deviceClass}   MFG:${d.manufacturerText}   AGE:$ageText"

        detailsView.text = buildString {
            append("NAME           : ${d.name}\n")
            append("ADDRESS        : ${d.address}\n")
            append("CLASS          : ${deviceClass}\n")
            if (flockAssessment != null) {
                append("CONFIDENCE     : ${flockAssessment.confidence}\n")
                append("REASON CODE    : ${flockAssessment.reasonCode}\n")
                append("CLASS BASIS    : ${flockAssessment.reasonText}\n")
            }
            if (skimmerAssessment != null) {
                append("CONFIDENCE     : ${skimmerValidationConfidence ?: skimmerAssessment.confidence}\n")
                append("REASON CODE    : ${skimmerValidationCode ?: skimmerAssessment.reasonCode}\n")
                append("CLASS BASIS    : ${skimmerValidationText ?: skimmerAssessment.reasonText}\n")
            }
            if (description.isNotEmpty()) {
                append("DESCRIPTION    : ${description}\n")
            }
            if (d.droneLat != null && d.droneLon != null) {
                append("DRONE LAT      : ${d.droneLat}\n")
                append("DRONE LON      : ${d.droneLon}\n")
            }
            append("RSSI      : ${d.rssi}\n")
            append("PACKETS SEEN   : ${d.packetCount}\n")
            append("AGE            : $ageText\n")
            append("MANUFACTURER   : ${d.manufacturerText}\n\n")
            append("MANUFACTURER DATA\n")
            append("${d.manufacturerDataText}\n\n")
            append("SERVICE UUIDS\n")
            append("${d.serviceUuidsText}\n\n")
            append("RAW ADVERTISEMENT\n")
            append("${d.rawAdvText}\n")
        }
    }

    private fun beginSkimmerValidation() {
        val d = cachedDevice ?: return
        val mac = d.address ?: return

        skimmerValidationConfidence = "TESTING"
        skimmerValidationCode = "CONNECTING"
        skimmerValidationText = "Connecting..."
        render()

        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device = adapter.getRemoteDevice(mac)
                adapter.cancelDiscovery()


                val socket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e: Exception) {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                }

                socket.connect()

                val out = socket.outputStream
                val input = socket.inputStream

                out.write("P".toByteArray())
                out.flush()

                val buffer = ByteArray(32)
                var response = ""
                val start = System.currentTimeMillis()

                while (System.currentTimeMillis() - start < 2000) {
                    if (input.available() > 0) {
                        val len = input.read(buffer)
                        response += String(buffer, 0, len)
                        break
                    }
                }

                socket.close()


                runOnUiThread {
                    if (response.contains("M")) {
                        skimmerValidationConfidence = "HIGH"
                        skimmerValidationCode = "PROTOCOL_MATCH"
                        skimmerValidationText = "Confirmed (M response)"
                    } else {
                        skimmerValidationConfidence = "MEDIUM"
                        skimmerValidationCode = "NO_MATCH"
                        skimmerValidationText = "No skimmer response"
                    }
                    render()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    skimmerValidationConfidence = "LOW"
                    skimmerValidationCode = "ERROR"
                    skimmerValidationText = "Failed: ${e.message}"
                    render()
                }
            }
        }.start()
    }

}

private const val SAVE_LOG_REQUEST = 2002
