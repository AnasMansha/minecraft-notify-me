package com.notifyme.app

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class FoundWorld(val name: String, val host: String, val port: Int, val serverId: String)

/**
 * Finds worlds on the LAN. Sends a probe the mod answers right away, and also hears the mod's 2 s beacon.
 * Stops ~1 s after the last new world (to catch other PCs), or after [maxMs] if nothing answers.
 */
object Discovery {

    private val PROBE = """{"t":"notifyme-probe","v":1}""".toByteArray()

    suspend fun scan(
        context: Context,
        maxMs: Long = 3_000,
        port: Int = 25571,
        onFound: (List<FoundWorld>) -> Unit = {},
    ): List<FoundWorld> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi.createMulticastLock("NotifyMe:discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = linkedMapOf<String, FoundWorld>()
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(port))
                socket.soTimeout = 150
                val targets = broadcastTargets()
                fun probe() = targets.forEach { a ->
                    try {
                        socket.send(DatagramPacket(PROBE, PROBE.size, a, port))
                    } catch (_: Exception) {
                    }
                }
                val start = System.currentTimeMillis()
                var end = start + maxMs
                var reprobed = false
                probe()
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    if (!reprobed && System.currentTimeMillis() - start > 600) {
                        probe() // a dropped UDP packet should not cost a whole scan
                        reprobed = true
                    }
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket.receive(p)
                        val o = JSONObject(String(p.data, 0, p.length, Charsets.UTF_8))
                        if (o.optString("t") != "notifyme") continue
                        val host = p.address.hostAddress ?: continue
                        val w = FoundWorld(o.optString("name", "Minecraft"), host, o.optInt("port", 25570), o.optString("id"))
                        if (found.put("$host:${w.port}", w) == null) {
                            end = minOf(start + maxMs, System.currentTimeMillis() + 1_000)
                            val snapshot = found.values.toList()
                            withContext(Dispatchers.Main) { onFound(snapshot) }
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            lock.release()
        }
        found.values.toList()
    }

    private fun broadcastTargets(): Set<InetAddress> {
        val out = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
                if (nif.isUp && !nif.isLoopback) nif.interfaceAddresses.mapNotNullTo(out) { it.broadcast }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
