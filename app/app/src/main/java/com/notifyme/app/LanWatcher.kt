package com.notifyme.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Listens for the mod's LAN beacon in the background. When a world's beacon lists this phone's callsign in
 * "invite" (a block owner linked it in-game) and the world is not added yet, offers it as an invite:
 * one tap on Accept adds the world, and the mod approves the phone automatically.
 */
class LanWatcher(private val context: Context, private val port: Int = 25571) {

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "NotifyMe-LanWatcher").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        socket?.close()
        thread = null
    }

    private fun loop() {
        while (running) {
            try {
                DatagramSocket(null).use { s ->
                    socket = s
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(port))
                    s.soTimeout = 2_000
                    val buf = ByteArray(4096)
                    while (running) {
                        try {
                            val p = DatagramPacket(buf, buf.size)
                            s.receive(p)
                            handle(String(p.data, 0, p.length, Charsets.UTF_8), p.address.hostAddress ?: continue)
                        } catch (_: SocketTimeoutException) {
                        }
                    }
                }
            } catch (_: Exception) {
                if (running) Thread.sleep(5_000) // port busy / network change: try again
            }
        }
    }

    private fun handle(text: String, host: String) {
        val o = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        if (o.optString("t") != "notifyme") return
        val invites = o.optJSONArray("invite") ?: return
        val me = Store.callsign.value
        if ((0 until invites.length()).none { invites.optString(it) == me }) return
        val invite = Invite(o.optString("name", "Minecraft"), host, o.optInt("port", 25570), o.optString("id"))
        main.post { if (Store.offerInvite(invite)) Link.engine.onInvite(invite) }
    }
}

data class Invite(val name: String, val host: String, val port: Int, val serverId: String) {
    val key get() = "$host:$port"
}
