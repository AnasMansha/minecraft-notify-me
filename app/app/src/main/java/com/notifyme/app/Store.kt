package com.notifyme.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val serverId: String = "",
    val enabled: Boolean = true,
)

enum class LinkState { DISABLED, CONNECTING, CONNECTED, PENDING, REJECTED, DISCONNECTED, STOPPED }

data class ConnStatus(val state: LinkState, val detail: String = "", val since: Long = System.currentTimeMillis())

data class BlockInfo(
    val serverId: String, // local ServerConfig.id
    val uuid: String,
    val name: String,
    val message: String,
    val tier: Int,
    val trigger: String,
    val threshold: Int,
    val cooldown: Int,
    val repeat: Int,
    val active: Boolean,
    val strength: Int,
    val lastChange: Long,
    val loaded: Boolean,
    val dim: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val owner: String,
    val alarmColor: Int = 0,
    val showState: Boolean = true,
    val dimName: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("serverLocal", serverId).put("uuid", uuid).put("name", name).put("message", message)
        .put("tier", tier).put("trigger", trigger).put("threshold", threshold).put("cooldown", cooldown)
        .put("repeat", repeat).put("active", active).put("strength", strength).put("lastChange", lastChange)
        .put("loaded", loaded).put("dim", dim).put("x", x).put("y", y).put("z", z).put("owner", owner)
        .put("alarmColor", alarmColor).put("showState", showState).put("dimName", dimName)

    companion object {
        fun fromJson(serverLocalId: String, o: JSONObject) = BlockInfo(
            serverId = serverLocalId,
            uuid = o.getString("uuid"),
            name = o.optString("name"),
            message = o.optString("message"),
            tier = o.optInt("tier", 2),
            trigger = o.optString("trigger", "rising"),
            threshold = o.optInt("threshold", 1),
            cooldown = o.optInt("cooldown", 5),
            repeat = o.optInt("repeat", 60),
            active = o.optBoolean("active"),
            strength = o.optInt("strength"),
            lastChange = o.optLong("lastChange"),
            loaded = o.optBoolean("loaded"),
            dim = o.optInt("dim"),
            x = o.optInt("x"),
            y = o.optInt("y"),
            z = o.optInt("z"),
            owner = o.optString("owner"),
            alarmColor = o.optInt("alarmColor"),
            showState = o.optBoolean("showState", true),
            dimName = o.optString("dimName"),
        )
    }
}

data class AlertItem(
    val id: String,
    val serverLocalId: String,
    val serverName: String,
    val blockUuid: String,
    val blockName: String,
    val tier: Int,
    val message: String,
    val active: Boolean,
    val ts: Long,
    val missed: Boolean,
    val test: Boolean,
    val acked: Boolean = false,
    val ackedBy: String = "",
    val color: Int = 0,
    val showState: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("serverLocalId", serverLocalId).put("serverName", serverName)
        .put("blockUuid", blockUuid).put("blockName", blockName).put("tier", tier).put("message", message)
        .put("active", active).put("ts", ts).put("missed", missed).put("test", test)
        .put("acked", acked).put("ackedBy", ackedBy)
        .put("color", color).put("showState", showState)

    companion object {
        fun fromJson(o: JSONObject) = AlertItem(
            o.getString("id"), o.optString("serverLocalId"), o.optString("serverName"), o.optString("blockUuid"),
            o.optString("blockName"), o.optInt("tier"), o.optString("message"), o.optBoolean("active"),
            o.optLong("ts"), o.optBoolean("missed"), o.optBoolean("test"), o.optBoolean("acked"),
            o.optString("ackedBy"), o.optInt("color"), o.optBoolean("showState", true),
        )
    }
}

data class Settings(
    val quietEnabled: Boolean = false,
    val quietStart: Int = 23,
    val quietEnd: Int = 7,
    val watchdogMinutes: Int = 5,
    val watchdogTier: Int = 3,
    val tts: Boolean = true,
    val torch: Boolean = true,
    val flashScreen: Boolean = true,
    // ---- display (this phone only) ----
    val blockFilter: Int = 0, // 0 all, 1 ON, 2 OFF
    val blockSort: Int = 0, // 0 name, 1 recently changed, 2 tier
    val compact: Boolean = false,
    val showMeter: Boolean = true,
    val showMessage: Boolean = true,
    val showDetails: Boolean = false,
    val showCallsignCard: Boolean = true,
    val showHidden: Boolean = false,
    val pinned: Set<String> = emptySet(), // Store.key(server, uuid)
    val hidden: Set<String> = emptySet(),
    val alertFilter: Int = 0, // 0 all, 1 needs ACK, 2 today
) {
    fun toJson(): JSONObject = JSONObject()
        .put("quietEnabled", quietEnabled).put("quietStart", quietStart).put("quietEnd", quietEnd)
        .put("watchdogMinutes", watchdogMinutes).put("watchdogTier", watchdogTier).put("tts", tts)
        .put("torch", torch).put("flashScreen", flashScreen)
        .put("blockFilter", blockFilter).put("blockSort", blockSort).put("compact", compact).put("showMeter", showMeter)
        .put("showMessage", showMessage).put("showDetails", showDetails).put("showCallsignCard", showCallsignCard)
        .put("showHidden", showHidden).put("pinned", JSONArray(pinned.toList())).put("hidden", JSONArray(hidden.toList()))
        .put("alertFilter", alertFilter)

    companion object {
        fun fromJson(o: JSONObject) = Settings(
            o.optBoolean("quietEnabled", false), o.optInt("quietStart", 23), o.optInt("quietEnd", 7),
            o.optInt("watchdogMinutes", 5), o.optInt("watchdogTier", 3), o.optBoolean("tts", true),
            o.optBoolean("torch", true), o.optBoolean("flashScreen", true),
            blockFilter = o.optInt("blockFilter"), blockSort = o.optInt("blockSort"), compact = o.optBoolean("compact"),
            showMeter = o.optBoolean("showMeter", true), showMessage = o.optBoolean("showMessage", true),
            showDetails = o.optBoolean("showDetails"), showCallsignCard = o.optBoolean("showCallsignCard", true),
            showHidden = o.optBoolean("showHidden"), pinned = strings(o.optJSONArray("pinned")),
            hidden = strings(o.optJSONArray("hidden")), alertFilter = o.optInt("alertFilter"),
        )

        private fun strings(a: JSONArray?): Set<String> =
            if (a == null) emptySet() else (0 until a.length()).map { a.getString(it) }.toSet()
    }
}

val TIER_NAMES = listOf("Status only", "Quiet", "Notify", "Urgent", "Alarm")
val TIER_HINTS = listOf(
    "Only shows ON/OFF in the app",
    "Silent notification",
    "Notification with sound and vibration",
    "Repeats every 30 s until acknowledged, can bypass Do Not Disturb",
    "Full-screen alarm, loud even on silent, flashing screen and flashlight",
)
val TRIGGERS = listOf("rising" to "Turns ON", "falling" to "Turns OFF", "both" to "ON and OFF", "while_on" to "While ON (repeat)")

/** App-wide state. Persisted to SharedPreferences as JSON; observed by the UI through StateFlows. */
object Store {

    private lateinit var prefs: SharedPreferences
    lateinit var identity: Identity
        private set

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers

    private val _blocks = MutableStateFlow<Map<String, BlockInfo>>(emptyMap()) // key serverLocal|uuid
    val blocks: StateFlow<Map<String, BlockInfo>> = _blocks

    private val _alerts = MutableStateFlow<List<AlertItem>>(emptyList())
    val alerts: StateFlow<List<AlertItem>> = _alerts

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _status = MutableStateFlow<Map<String, ConnStatus>>(emptyMap())
    val status: StateFlow<Map<String, ConnStatus>> = _status

    private val _callsign = MutableStateFlow("")
    val callsign: StateFlow<String> = _callsign

    private val _invites = MutableStateFlow<List<Invite>>(emptyList())
    val invites: StateFlow<List<Invite>> = _invites
    private val ignoredInvites = mutableMapOf<String, Long>() // key -> ignored until

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** JVM screenshot tests only: Robolectric has no Android Keystore, so skip [Identity]. */
    fun initForTest(context: Context, callsign: String) {
        prefs = context.getSharedPreferences("notifyme-test", Context.MODE_PRIVATE)
        _callsign.value = callsign
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("notifyme", Context.MODE_PRIVATE)
        identity = Identity(context)
        _callsign.value = identity.callsign
        _servers.value = readArray("servers").map {
            ServerConfig(
                it.getString("id"), it.optString("name"), it.getString("host"), it.optInt("port", 25570),
                it.optString("serverId"), it.optBoolean("enabled", true),
            )
        }
        _blocks.value = readArray("blocks").map { BlockInfo.fromJson(it.getString("serverLocal"), it) }
            .associateBy { key(it.serverId, it.uuid) }
        _alerts.value = readArray("alerts").map { AlertItem.fromJson(it) }
        prefs.getString("settings", null)?.let { _settings.value = Settings.fromJson(JSONObject(it)) }
    }

    private fun readArray(name: String): List<JSONObject> {
        val raw = prefs.getString(name, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeArray(name: String, items: List<JSONObject>) {
        val a = JSONArray()
        items.forEach { a.put(it) }
        prefs.edit().putString(name, a.toString()).apply()
    }

    fun key(serverLocal: String, uuid: String) = "$serverLocal|$uuid"

    // ---------------------------------------------------------------- servers

    fun addServer(s: ServerConfig) {
        _servers.update { list -> list.filterNot { it.host == s.host && it.port == s.port } + s }
        saveServers()
    }

    fun updateServer(id: String, f: (ServerConfig) -> ServerConfig) {
        _servers.update { list -> list.map { if (it.id == id) f(it) else it } }
        saveServers()
    }

    fun removeServer(id: String) {
        _servers.update { list -> list.filterNot { it.id == id } }
        _blocks.update { m -> m.filterValues { it.serverId != id } }
        _status.update { it - id }
        saveServers()
        saveBlocks()
    }

    private fun saveServers() = writeArray("servers", _servers.value.map {
        JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port)
            .put("serverId", it.serverId).put("enabled", it.enabled)
    })

    fun setStatus(serverLocal: String, st: ConnStatus) {
        _status.update { it + (serverLocal to st) }
    }

    // ---------------------------------------------------------------- blocks

    fun replaceBlocks(serverLocal: String, list: List<BlockInfo>) {
        _blocks.update { m -> m.filterValues { it.serverId != serverLocal } + list.associateBy { key(serverLocal, it.uuid) } }
        saveBlocks()
    }

    fun upsertBlock(b: BlockInfo) {
        _blocks.update { it + (key(b.serverId, b.uuid) to b) }
        saveBlocks()
    }

    fun removeBlock(serverLocal: String, uuid: String) {
        _blocks.update { it - key(serverLocal, uuid) }
        saveBlocks()
    }

    private fun saveBlocks() = writeArray("blocks", _blocks.value.values.map { it.toJson() })

    // ---------------------------------------------------------------- alerts

    fun addAlert(a: AlertItem) {
        _alerts.update { list -> (listOf(a) + list.filterNot { it.id == a.id }).take(300) }
        saveAlerts()
    }

    fun markAcked(id: String, by: String) {
        _alerts.update { list -> list.map { if (it.id == id) it.copy(acked = true, ackedBy = by) else it } }
        saveAlerts()
    }

    fun clearAlerts() {
        _alerts.value = emptyList()
        saveAlerts()
    }

    private fun saveAlerts() = writeArray("alerts", _alerts.value.map { it.toJson() })

    // ---------------------------------------------------------------- settings / identity

    fun updateSettings(f: (Settings) -> Settings) {
        _settings.update(f)
        prefs.edit().putString("settings", _settings.value.toJson().toString()).apply()
    }

    fun rerollIdentity() {
        identity.reroll()
        _callsign.value = identity.callsign
    }

    // ---------------------------------------------------------------- invites (world linked this phone)

    /** True if this is a new invite worth notifying about. */
    fun offerInvite(inv: Invite): Boolean {
        val known = _servers.value.any { (it.host == inv.host && it.port == inv.port) || (inv.serverId.isNotEmpty() && it.serverId == inv.serverId) }
        if (known || (ignoredInvites[inv.key] ?: 0L) > System.currentTimeMillis()) return false
        if (_invites.value.any { it.key == inv.key }) return false
        _invites.update { it + inv }
        return true
    }

    fun acceptInvite(inv: Invite) {
        _invites.update { list -> list.filterNot { it.key == inv.key } }
        if (_servers.value.none { it.host == inv.host && it.port == inv.port })
            addServer(ServerConfig(name = inv.name, host = inv.host, port = inv.port, serverId = inv.serverId))
    }

    fun ignoreInvite(key: String) {
        ignoredInvites[key] = System.currentTimeMillis() + 60 * 60_000L // ask again in an hour
        _invites.update { list -> list.filterNot { it.key == key } }
    }

    fun setError(msg: String?) {
        _lastError.value = msg
    }
}

/** Alarm screen background: name, dark, bright (ARGB). Index 0 is the default. */
val ALARM_COLORS = listOf(
    Triple("Redstone", 0xFF8B0000, 0xFFFF2D1A),
    Triple("Lava", 0xFF7A2E00, 0xFFFF8A00),
    Triple("Gold", 0xFF6B4A00, 0xFFFFC233),
    Triple("Emerald", 0xFF00451D, 0xFF17DD62),
    Triple("Diamond", 0xFF004452, 0xFF1EE3FF),
    Triple("Ender", 0xFF2A0845, 0xFFA23BFF),
)

/** "Boiler Water is ON" or just "Boiler Water", depending on the block's "is ON" option. */
fun alertTitle(name: String, active: Boolean, isBlock: Boolean = true, showState: Boolean = true): String =
    if (isBlock && showState) "$name is ${if (active) "ON" else "OFF"}" else name

fun AlertItem.title() = alertTitle(blockName, active, blockUuid.isNotEmpty(), showState)
