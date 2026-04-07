package com.ghostech.blehound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class BleMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val lastPopupAt = mutableMapOf<String, Long>()
    private val lastPopupRssi = mutableMapOf<String, Int>()

    private val updater = object : Runnable {
        override fun run() {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(1001, buildNotification())
            processPopups(nm)
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1001, buildNotification())
        handler.post(updater)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "blehound_bg_lockscreen",
                "BLE Hound Background Monitor",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Background monitoring status"
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setSound(null, null)
            channel.enableVibration(false)

            val popupChannel = NotificationChannel(
                currentPopupChannelId(),
                "BLE Hound Popup Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            popupChannel.description = "Background popup alerts"
            popupChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
            val popupVibrate = prefs.getBoolean("popup_vibrate", false)

            val nm = getSystemService(NotificationManager::class.java)

            popupChannel.setSound(null, null)
            popupChannel.enableVibration(popupVibrate)
            if (popupVibrate) {
                popupChannel.vibrationPattern = longArrayOf(0, 180, 120, 180)
            }

            nm.createNotificationChannel(channel)
            nm.createNotificationChannel(popupChannel)
        }
    }

    private fun soundPrefKeyForClass(c: String): String? = when {
        isTrackerClass(c) -> "sound_trackers"
        isCyberGadgetClass(c) -> "sound_gadgets"
        isDroneClass(c) -> "sound_drones"
        isPoliceClass(c) -> "sound_feds"
        else -> null
    }

    private fun popupPrefKeyForClass(c: String): String? = when (c) {
        "Drone" -> "popup_drone"
        "AirTag" -> "popup_airtag"
        "Tile" -> "popup_tile"
        "Galaxy Tag" -> "popup_galaxytag"
        "Find My" -> "popup_findmy"
        "Flipper Zero" -> "popup_flipper"
        "Pwnagotchi" -> "popup_pwnagotchi"
        "Card Skimmer" -> "popup_skimmer"
        "WiFi Pineapple" -> "popup_pineapple"
        "Meta Glasses" -> "popup_metaglasses"
        "Axon Device" -> "popup_axondevice"
        "Axon Cam" -> "popup_axoncam"
        "Axon Taser" -> "popup_axontaser"
        "Flock" -> "popup_flock"
        else -> null
    }

    private fun popupIdFor(key: String): Int = 2000 + abs(key.hashCode() % 200000)

    private fun currentPopupChannelId(): String {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        val sound = prefs.getString("popup_sound", "__DEFAULT__") ?: "__DEFAULT__"
        val vibrate = prefs.getBoolean("popup_vibrate", false)
        return "blehound_popup_alerts_" + abs((sound + "|" + vibrate).hashCode())
    }

    private fun vibratePopupIfEnabled() {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("popup_vibrate", false)) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 180, 120, 180), -1)
        }
    }

    private fun playPopupSoundForClass(cls: String, isBlacklisted: Boolean) {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        val key = if (isBlacklisted) "sound_blacklist" else (soundPrefKeyForClass(cls) ?: return)
        val raw = prefs.getString(key, "__DEFAULT__") ?: "__DEFAULT__"

        if (raw == "__DISABLED__" || raw == "__SILENT__") return

        try {
            if (raw == "__DEFAULT__") {
                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
                    } catch (_: Exception) {}
                }, 220)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        tone.release()
                    } catch (_: Exception) {}
                }, 900)
            } else {
                val uri = android.net.Uri.parse(raw)
                android.media.RingtoneManager.getRingtone(this, uri)?.play()
            }
        } catch (_: Exception) {}
    }

    private fun buildPopupNotification(cls: String, d: BleSeenDevice, isBlacklisted: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            popupIdFor("${cls}|${d.address}"),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nameText = if (d.name.isBlank()) "Unknown" else d.name
        val line1 = if (isBlacklisted) "BLACKLISTED • $cls nearby" else "$cls nearby"
        val line2 = "RSSI ${d.rssi} dBm • ${d.address}"
        val statusLine = if (isBlacklisted) "STATUS: BLACKLISTED\n" else ""
        val detail = statusLine + "Class: $cls\nName: $nameText\nMAC: ${d.address}\nRSSI: ${d.rssi} dBm\nMFG: ${d.manufacturerText}"

        return NotificationCompat.Builder(this, currentPopupChannelId())
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(line1)
            .setContentText(line2)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
    }

    private fun processPopups(nm: NotificationManager) {
        val prefs = getSharedPreferences("blehound_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("background_enabled", false)) return

        for (d in BleStore.devices.values.sortedByDescending { it.rssi }) {
            val cls = classifyDevice(d)

            val isBlacklisted = isDeviceBlacklisted(this, d.address)
            val allowBlacklist = prefs.getBoolean("popup_blacklist", false)

            if (isDeviceWhitelisted(this, d.address)) continue

            val prefKey = if (isBlacklisted) "popup_blacklist" else (popupPrefKeyForClass(cls) ?: continue)
            if (!prefs.getBoolean(prefKey, false)) continue

            val key = "${cls}|${d.address}"
            val now = System.currentTimeMillis()
            val prevAt = lastPopupAt[key] ?: 0L
            val prevRssi = lastPopupRssi[key]
            val shouldPopup = now - prevAt > 15000L
            val shouldUpdate = shouldPopup || prevRssi == null || abs(prevRssi - d.rssi) >= 3
            if (!shouldUpdate) continue

            playPopupSoundForClass(cls, isBlacklisted)
            vibratePopupIfEnabled()
            nm.notify(popupIdFor(key), buildPopupNotification(cls, d, isBlacklisted))
            lastPopupAt[key] = now
            lastPopupRssi[key] = d.rssi
        }
    }

    private fun buildNotification(): Notification {
        var trackers = 0
        var gadgets = 0
        var drones = 0
        var feds = 0

        for (d in BleStore.devices.values) {
            when {
                isTrackerClass(classifyDevice(d)) -> trackers++
                isCyberGadgetClass(classifyDevice(d)) -> gadgets++
                isDroneClass(classifyDevice(d)) -> drones++
                isPoliceClass(classifyDevice(d)) -> feds++
            }
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Trackers:$trackers  Gadgets:$gadgets  Drones:$drones  Feds:$feds"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "blehound_bg_lockscreen")
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("BLE Hound Background Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }
}
