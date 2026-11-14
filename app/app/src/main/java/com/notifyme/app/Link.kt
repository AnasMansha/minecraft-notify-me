package com.notifyme.app

import android.content.Context
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Owns the alert engine and one [ServerConnection] per enabled world. Main thread only. */
object Link {

    lateinit var engine: AlertEngine
        private set

    private val connections = mutableMapOf<String, ServerConnection>()
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun init(context: Context) {
        if (::engine.isInitialized) return
        engine = AlertEngine(context.applicationContext) { serverLocal, alertId ->
            send(serverLocal, JSONObject().put("t", "ack").put("id", alertId))
        }
    }

    /** Starts / stops connections so they match the enabled servers in [Store]. */
    fun sync() {
        val wanted = Store.servers.value.filter { it.enabled }.associateBy { it.id }
        (connections.keys - wanted.keys).forEach { id ->
            connections.remove(id)?.stop()
            Store.servers.value.firstOrNull { it.id == id }?.let { Store.setStatus(id, ConnStatus(LinkState.DISABLED)) }
        }
        wanted.forEach { (id, cfg) ->
            val existing = connections[id]
            if (existing == null) {
                connections[id] = ServerConnection(cfg, client, engine).also { it.start() }
            } else if (existing.config.host != cfg.host || existing.config.port != cfg.port) {
                existing.stop()
                connections[id] = ServerConnection(cfg, client, engine).also { it.start() }
            } else {
                existing.config = cfg
            }
        }
    }

    fun stopAll() {
        connections.values.forEach { it.stop() }
        connections.clear()
    }

    /** Called when the network changes or the user taps retry. */
    fun kickAll() = connections.values.forEach { it.kick() }

    /** Identity changed: every connection must handshake again with the new key. */
    fun restartAll() {
        stopAll()
        sync()
    }

    fun send(serverLocal: String, o: JSONObject): Boolean = connections[serverLocal]?.send(o) ?: false

    fun connectedCount() = connections.values.count { it.connected }

    fun watchdogCheck() {
        val minutes = Store.settings.value.watchdogMinutes
        if (minutes <= 0) return
        val now = System.currentTimeMillis()
        connections.values.forEach { c ->
            if (c.everConnected && !c.connected && !c.cleanStop && !c.watchdogFired &&
                c.disconnectedSince > 0 && now - c.disconnectedSince >= minutes * 60_000L
            ) {
                c.watchdogFired = true
                engine.onWatchdog(c.config, minutes)
            }
        }
    }
}
