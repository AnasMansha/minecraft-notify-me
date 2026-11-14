package com.notifyme.app

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.notifyme.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the direct WebSocket links open while the app is in the background.
 * Uses the "specialUse" type, which (unlike dataSync) is not limited to 6 h/day on Android 15+.
 */
class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private var wifiLock: WifiManager.WifiLock? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private val lanWatcher by lazy { LanWatcher(this) }

    private val watchdog = object : Runnable {
        override fun run() {
            Link.watchdogCheck()
            main.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Link.init(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification("Starting..."),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        acquireWifiLock()
        registerNetworkCallback()
        scope.launch { Store.servers.collect { Link.sync() } }
        scope.launch {
            combine(Store.status, Store.servers, Store.blocks) { st, servers, blocks -> Triple(st, servers, blocks) }
                .collect { (st, servers, blocks) ->
                    val enabled = servers.filter { it.enabled }
                    val online = enabled.count { st[it.id]?.state == LinkState.CONNECTED }
                    val on = blocks.values.count { it.active }
                    val text = when {
                        enabled.isEmpty() -> "No worlds added yet"
                        else -> "Connected to $online/${enabled.size} world(s) - ${blocks.size} block(s), $on ON"
                    }
                    getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
                }
        }
        main.postDelayed(watchdog, 30_000L)
        lanWatcher.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RETRY) Link.kickAll()
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        lanWatcher.stop()
        scope.cancel()
        Link.stopAll()
        wifiLock?.let { if (it.isHeld) it.release() }
        netCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, AlertEngine.CH_SERVICE)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setContentTitle("Redstone Pager")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        try {
            val wm = applicationContext.getSystemService(WifiManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "NotifyMe:link").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.postDelayed({ Link.kickAll() }, 1_500L)
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        netCallback = cb
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_RETRY = "com.notifyme.app.RETRY"

        fun start(context: Context, action: String? = null) {
            val i = Intent(context, LinkService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
