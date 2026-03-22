package com.ghostech.blehound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class BleMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(1001, buildNotification())
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
