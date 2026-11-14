package com.notifyme.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * One direct WebSocket link to a Minecraft world running the Notify Me mod.
 * Reconnects forever with backoff. All callbacks are posted to the main thread.
 */
class ServerConnection(
    @Volatile var config: ServerConfig,
    private val client: OkHttpClient,
    private val alerts: AlertEngine,
) {
    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var generation = 0
    private var backoffMs = 2_000L
    private var closedByUs = false

    /** True once a session got "welcome"; used by the watchdog. */
    var everConnected = false
        private set
    var cleanStop = false
        private set
    var disconnectedSince = 0L
        private set
    var watchdogFired = false

    val connected get() = status().state == LinkState.CONNECTED

    fun status(): ConnStatus = Store.status.value[config.id] ?: ConnStatus(LinkState.DISCONNECTED)

    private fun setStatus(state: LinkState, detail: String = "") {
        Store.setStatus(config.id, ConnStatus(state, detail))
    }

    fun start() {
        closedByUs = false
        connect()
    }

    fun stop() {
        closedByUs = true
        generation++
        main.removeCallbacksAndMessages(null)
        socket?.close(1000, "bye")
        socket = null
        setStatus(LinkState.DISABLED)
    }

    /** Reconnect now (e.g. network came back or user pressed retry). */
    fun kick() {
        if (closedByUs) return
        if (status().state == LinkState.CONNECTED || status().state == LinkState.PENDING) return
        main.removeCallbacksAndMessages(null)
        backoffMs = 2_000L
        connect()
    }

    private fun connect() {
        val gen = ++generation
        socket?.cancel()
        if (!cleanStop && status().state != LinkState.REJECTED) setStatus(LinkState.CONNECTING, "${config.host}:${config.port}")
        val req = Request.Builder().url("ws://${config.host}:${config.port}/ws").build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = post(gen) {
                val hello = JSONObject()
                    .put("t", "hello").put("v", 1)
                    .put("callsign", Store.identity.callsign)
                    .put("key", Store.identity.publicKeyB64)
                    .put("device", Identity.deviceName())
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = post(gen) { handle(webSocket, text) }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = post(gen) { onDisconnected(null) }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                post(gen) { onDisconnected(t.message ?: t.javaClass.simpleName) }
        })
    }

    private fun post(gen: Int, block: () -> Unit) {
        main.post { if (gen == generation) block() }
    }

    private fun onDisconnected(error: String?) {
        if (closedByUs) return
        val st = status().state
        if (st == LinkState.CONNECTED || st == LinkState.PENDING) disconnectedSince = System.currentTimeMillis()
        if (disconnectedSince == 0L) disconnectedSince = System.currentTimeMillis()
        socket = null
        when {
            st == LinkState.REJECTED -> backoffMs = 60_000L
            cleanStop -> {
                setStatus(LinkState.STOPPED, "World closed or server stopped - will reconnect when it is back")
                backoffMs = backoffMs.coerceIn(15_000L, 30_000L)
            }
            else -> setStatus(LinkState.DISCONNECTED, error ?: "Connection closed")
        }
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        main.postDelayed({ if (!closedByUs) connect() }, delay)
    }

    fun send(o: JSONObject): Boolean = socket?.send(o.toString()) ?: false

    private fun handle(ws: WebSocket, text: String) {
        val m = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        when (m.optString("t")) {
            "challenge" -> {
                val nonce = m.getString("nonce")
                val serverId = m.getString("serverId")
                val sig = Store.identity.sign(Identity.authPayload(nonce, serverId))
                ws.send(JSONObject().put("t", "auth").put("sig", sig).toString())
                updateConfig(m.optString("server"), serverId)
            }
            "pending" -> setStatus(LinkState.PENDING, m.optString("message"))
            "welcome" -> {
                backoffMs = 2_000L
                everConnected = true
                cleanStop = false
                watchdogFired = false
                disconnectedSince = 0L
                updateConfig(m.optString("server"), m.optString("serverId"))
                setStatus(LinkState.CONNECTED, "as ${m.optString("callsign")}")
                alerts.onReconnected(config)
            }
            "rejected" -> {
                val reason = when (val r = m.optString("reason")) {
                    "callsign_taken" -> "Another phone already uses this callsign here. Generate a new callsign in Settings."
                    "denied" -> "This phone was denied in-game. Run /notifyme approve after reconnecting."
                    "bad_signature" -> "Key check failed."
                    "callsign_mismatch" -> "Callsign does not match key (update the app)."
                    else -> r
                }
                setStatus(LinkState.REJECTED, reason)
            }
            "snapshot" -> {
                val arr = m.getJSONArray("blocks")
                Store.replaceBlocks(config.id, (0 until arr.length()).map { BlockInfo.fromJson(config.id, arr.getJSONObject(it)) })
            }
            "block" -> Store.upsertBlock(BlockInfo.fromJson(config.id, m.getJSONObject("block")))
            "removed" -> Store.removeBlock(config.id, m.getString("uuid"))
            "alert" -> alerts.onAlert(config, m)
            "acked" -> alerts.onRemoteAck(m.getString("id"), m.optString("by"))
            "bye" -> {
                cleanStop = true
                setStatus(LinkState.STOPPED, "World closed or server stopped")
                alerts.onServerStopped(config)
            }
            "error" -> Store.setError(m.optString("message"))
            "pong" -> {}
            else -> Log.d("NotifyMe", "Unknown message $text")
        }
    }

    private fun updateConfig(name: String, serverId: String) {
        if (name.isEmpty() && serverId.isEmpty()) return
        if (config.name == name && config.serverId == serverId) return
        Store.updateServer(config.id) { it.copy(name = name.ifEmpty { it.name }, serverId = serverId.ifEmpty { it.serverId }) }
        config = Store.servers.value.firstOrNull { it.id == config.id } ?: config
    }
}
