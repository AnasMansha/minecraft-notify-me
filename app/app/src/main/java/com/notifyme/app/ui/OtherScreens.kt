package com.notifyme.app.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyme.app.Discovery
import com.notifyme.app.FoundWorld
import com.notifyme.app.Link
import com.notifyme.app.LinkService
import com.notifyme.app.LinkState
import com.notifyme.app.ServerConfig
import com.notifyme.app.Store
import com.notifyme.app.TIER_NAMES
import com.notifyme.app.title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===================================================================== Alerts

@Composable
fun AlertsScreen(modifier: Modifier) {
    val all by Store.alerts.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    val dayAgo = System.currentTimeMillis() - 86_400_000L
    val needAck = all.count { !it.acked && it.tier >= 3 }
    val alerts = when (settings.alertFilter) {
        1 -> all.filter { !it.acked && it.tier >= 3 }
        2 -> all.filter { it.ts >= dayAgo }
        else -> all
    }
    Column(modifier.fillMaxWidth()) {
        if (all.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All ${all.size}", "Needs ACK $needAck", "Last 24h").forEachIndexed { i, label ->
                FilterChip(
                    settings.alertFilter == i, { Store.updateSettings { it.copy(alertFilter = i) } }, label = { Text(label) },
                    colors = tierChipColors(if (i == 1) Gold else Muted),
                )
            }
        }
        AlertList(Modifier.weight(1f), alerts, filtered = all.isNotEmpty())
    }
}

@Composable
private fun AlertList(modifier: Modifier, alerts: List<com.notifyme.app.AlertItem>, filtered: Boolean) {
    if (alerts.isEmpty()) {
        Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RedstoneLamp(false, 28.dp)
            Spacer(Modifier.height(12.dp))
            Text(if (filtered) "Nothing here" else "All quiet", style = MaterialTheme.typography.titleMedium)
            Text(
                if (filtered) "No alerts match this filter." else "Alerts show up here. Use \"Save + Test\" in a notifier's GUI to try one.",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(alerts, key = { it.id }) { a ->
            val tier = a.tier.coerceIn(0, 4)
            val color = TierColors[tier]
            val open = !a.acked && tier >= 3
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).blockFrame(accent = if (open) color.copy(alpha = 0.7f) else null),
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(color))
                Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (a.blockUuid.isNotEmpty()) {
                            RedstoneLamp(a.active, 10.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            (if (a.test) "[TEST] " else "") + a.title(),
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (a.message.isNotBlank()) Text(a.message, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(TIER_NAMES[tier], color)
                        Text(
                            "${a.serverName} · ${DateUtils.getRelativeTimeSpanString(a.ts)}" +
                                (if (a.missed) " · late" else ""),
                            style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (a.acked && tier >= 3) Text(
                        "acknowledged" + (if (a.ackedBy.isNotEmpty()) " by ${a.ackedBy}" else ""),
                        style = MaterialTheme.typography.labelSmall, color = Emerald,
                    )
                }
                if (open) TextButton(onClick = { Link.engine.acknowledge(a.id) }, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Text("ACK", color = color)
                }
            }
        }
    }
}

// ===================================================================== Invites

/** "GTNH Base wants to link this phone" with one-tap Accept. Shown on Blocks and Worlds. */
@Composable
internal fun InviteCards() {
    val invites by Store.invites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    invites.forEach { inv ->
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).blockFrame(fill = Color(0x2217DD62), accent = Emerald.copy(alpha = 0.7f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedstoneLamp(true, 12.dp, onColor = Emerald)
                Spacer(Modifier.width(10.dp))
                Text("${inv.name} wants to link this phone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(
                "A Redstone Notifier at ${inv.host} was linked to you. Accept to add the world - no approval needed.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = {
                        Store.acceptInvite(inv)
                        Link.engine.clearInvite(inv.key)
                        LinkService.start(context)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.Black),
                ) { Text("ACCEPT") }
                OutlinedButton(onClick = {
                    Store.ignoreInvite(inv.key)
                    Link.engine.clearInvite(inv.key)
                }) { Text("IGNORE", color = Muted) }
            }
        }
    }
}

// ===================================================================== Worlds

/** Screenshot tests only: pre-filled LAN scan result. */
internal var previewFoundWorlds: List<FoundWorld>? = null

@Composable
fun WorldsScreen(modifier: Modifier) {
    val servers by Store.servers.collectAsStateWithLifecycle()
    val status by Store.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var found by remember { mutableStateOf(previewFoundWorlds) }
    var manual by remember { mutableStateOf(false) }
    var tipOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ServerConfig?>(null) }
    val scanMs = 3_000L

    fun startScan() {
        scanning = true
        found = null
        scope.launch {
            found = try {
                Discovery.scan(context, scanMs) { found = it }
            } catch (e: Exception) {
                Store.setError("Scan failed: ${e.message}")
                emptyList()
            }
            scanning = false
        }
    }
    LaunchedEffect(scanning) {
        progress = 0f
        while (scanning && progress < 1f) {
            delay(100)
            progress = (progress + 100f / scanMs).coerceAtMost(1f)
        }
    }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InviteCards() }
        item { SectionLabel("Add a world") }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(Icons.Filled.Radar, "Scan LAN", "Find open worlds on this Wi-Fi", Modifier.weight(1f), enabled = !scanning) { startScan() }
                ActionTile(Icons.Filled.Keyboard, "Enter address", "IP, hostname or Tailscale", Modifier.weight(1f)) { manual = true }
            }
        }
        item {
            AnimatedVisibility(scanning || found != null) {
                Column(Modifier.fillMaxWidth().blockFrame().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val list = found.orEmpty()
                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RedstoneLamp(true, 10.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (list.isEmpty()) "Searching..." else "Found ${list.size}, still looking...",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Redstone, trackColor = StoneEdge,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (list.isEmpty()) "No worlds found" else "Found ${list.size} world${if (list.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { startScan() }) { Text("SCAN AGAIN") }
                            IconButton(onClick = { found = null }) { Icon(Icons.Filled.ExpandLess, "Hide") }
                        }
                    }
                    run {
                        if (!scanning && list.isEmpty()) Text(
                            "• The world must be open (singleplayer counts) with the mod installed.\n" +
                                "• Phone and PC on the same Wi-Fi.\n" +
                                "• Windows Firewall: allow Java on private networks.",
                            style = MaterialTheme.typography.bodySmall, color = Muted,
                        )
                        list.forEach { w ->
                            val already = servers.any { it.host == w.host && it.port == w.port }
                            HorizontalDivider(color = StoneEdge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RedstoneLamp(true, 10.dp, onColor = Emerald)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${w.host}:${w.port}", style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                                if (already) SectionLabel("added", color = Emerald)
                                else OutlinedButton(onClick = {
                                    Store.addServer(ServerConfig(name = w.name, host = w.host, port = w.port, serverId = w.serverId))
                                    LinkService.start(context)
                                }) { Text("ADD") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clickable { tipOpen = !tipOpen }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Playing away from home?", style = MaterialTheme.typography.bodyMedium, color = Gold, modifier = Modifier.weight(1f))
                    Icon(if (tipOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Gold)
                }
                AnimatedVisibility(tipOpen) {
                    Text(
                        "There's no cloud in between, so the phone must reach your PC directly. Install Tailscale (free) on the PC and the phone, " +
                            "then choose Enter address and use the PC's Tailscale IP with port 25570. Port forwarding works too.",
                        style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (servers.isNotEmpty()) item { SectionLabel("Your worlds", Modifier.padding(top = 8.dp)) }
        items(servers, key = { it.id }) { s -> WorldCard(s, status[s.id]?.state, status[s.id]?.detail.orEmpty(), onDelete = { deleting = s }) }
    }

    if (manual) ManualAddDialog(onDismiss = { manual = false })

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Stone,
            title = { Text("Remove ${s.name.ifEmpty { s.host }}?") },
            text = { Text("Its blocks disappear from this phone. The blocks in-game stay linked until you unlink them there.") },
            confirmButton = { TextButton(onClick = { Store.removeServer(s.id); deleting = null }) { Text("REMOVE", color = Redstone) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun ActionTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().blockFrame(fill = StoneLight).clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(36.dp).blockFrame(fill = RedstoneDim, accent = Color.Black), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (enabled) Redstone else Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.White else Muted)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
    }
}

@Composable
private fun WorldCard(s: ServerConfig, state: LinkState?, detail: String, onDelete: () -> Unit) {
    val context = LocalContext.current
    val color = when (state) {
        LinkState.CONNECTED -> Emerald
        LinkState.PENDING -> Gold
        LinkState.REJECTED -> Redstone
        else -> Muted
    }
    Column(Modifier.fillMaxWidth().blockFrame().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedstoneLamp(state == LinkState.CONNECTED, 12.dp, onColor = Emerald, offColor = Color(0xFF3A3F47))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name.ifEmpty { "Minecraft" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.host}:${s.port}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Switch(
                s.enabled, { on -> Store.updateServer(s.id) { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Redstone, checkedThumbColor = Color.White),
            )
            IconButton(onClick = { LinkService.start(context, LinkService.ACTION_RETRY) }) { Icon(Icons.Filled.Refresh, "Retry", tint = Muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove", tint = Muted) }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(stateLabel(state), color)
            if (detail.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(end = 10.dp))
            }
        }
    }
}

@Composable
internal fun ManualAddDialog(inline: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("25570") }
    val portOk = (port.toIntOrNull() ?: 0) in 1..65535
    ThemedDialog(
        inline = inline,
        onDismiss = onDismiss,
        icon = { Icon(Icons.Filled.Keyboard, null, tint = Redstone) },
        title = { Text("Enter address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    host, { host = it.trim() }, label = { Text("Host / IP") }, singleLine = true,
                    placeholder = { Text("192.168.1.20") },
                    supportingText = { Text("Shown in the notifier GUI, or run /notifyme in-game") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                        isError = !portOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.4f),
                    )
                    OutlinedTextField(
                        name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true,
                        placeholder = { Text("optional") }, modifier = Modifier.weight(0.6f),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                enabled = host.isNotBlank() && portOk,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Redstone),
                onClick = {
                Store.addServer(ServerConfig(name = name, host = host, port = port.toInt()))
                LinkService.start(context)
                onDismiss()
            }) { Text("ADD WORLD") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("CANCEL", color = Muted) } },
    )
}

// ===================================================================== Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(modifier: Modifier, resumeTick: Int) {
    val s by Store.settings.collectAsStateWithLifecycle()
    var confirmReroll by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IdentityCard()
        TextButton(onClick = { confirmReroll = true }) { Text("GENERATE A NEW CALLSIGN", color = Muted) }

        SettingsSection("Try each tier") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { t ->
                    OutlinedButton(onClick = { Link.engine.preview(t) }) {
                        RedstoneLamp(true, 8.dp, onColor = TierColors[t])
                        Spacer(Modifier.width(6.dp))
                        Text(TIER_NAMES[t])
                    }
                }
            }
        }

        SettingsSection("Alarm (tier 4)") {
            ToggleRow("Speak the block name", s.tts) { v -> Store.updateSettings { it.copy(tts = v) } }
            ToggleRow("Flashlight strobe", s.torch) { v -> Store.updateSettings { it.copy(torch = v) } }
            ToggleRow("Flashing screen", s.flashScreen) { v -> Store.updateSettings { it.copy(flashScreen = v) } }
            Text(
                "Alarm colour and \"is ON\" text are set per block (in-game GUI or tap a block).",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }

        SettingsSection("Quiet hours") {
            ToggleRow("Notify and Urgent become Quiet at night. Alarm still rings.", s.quietEnabled) { v ->
                Store.updateSettings { it.copy(quietEnabled = v) }
            }
            if (s.quietEnabled) {
                HourStepper("From", s.quietStart) { v -> Store.updateSettings { it.copy(quietStart = v) } }
                HourStepper("Until", s.quietEnd) { v -> Store.updateSettings { it.copy(quietEnd = v) } }
            }
        }

        SettingsSection("Connection watchdog") {
            Text(
                "Alert me when a world disappears without closing cleanly (crash, PC asleep, network down).",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 2, 5, 10, 30).forEach { m ->
                    FilterChip(
                        s.watchdogMinutes == m, { Store.updateSettings { it.copy(watchdogMinutes = m) } },
                        label = { Text(if (m == 0) "Off" else "$m min", fontFamily = FontFamily.Default) }, colors = tierChipColors(Redstone),
                    )
                }
            }
            if (s.watchdogMinutes > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { t ->
                    FilterChip(
                        s.watchdogTier == t, { Store.updateSettings { it.copy(watchdogTier = t) } },
                        label = { Text(TIER_NAMES[t], fontFamily = FontFamily.Default) }, colors = tierChipColors(TierColors[t]),
                    )
                }
            }
        }

        SettingsSection("Permissions") {
            PermissionList(resumeTick, showAll = true)
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) { Text("SOUNDS PER TIER") }
        }
        Text(
            "Redstone Pager · works with the Notify Me mod",
            style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(8.dp),
        )
    }

    if (confirmReroll) AlertDialog(
        onDismissRequest = { confirmReroll = false },
        containerColor = Stone,
        title = { Text("New callsign?") },
        text = { Text("Your blocks are linked to the current callsign. After changing it you must type the new one into each block and approve the phone again in every world.") },
        confirmButton = {
            TextButton(onClick = {
                Store.rerollIdentity()
                Link.restartAll()
                confirmReroll = false
            }) { Text("GENERATE", color = Redstone) }
        },
        dismissButton = { TextButton(onClick = { confirmReroll = false }) { Text("CANCEL") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.notifyme.app.ALARM_COLORS.forEachIndexed { i, (name, dark, bright) ->
            val on = i == selected
            Column(
                Modifier.clickable { onSelect(i) }.padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(34.dp)
                        .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .padding(if (on) 3.dp else 1.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(dark), Color(bright))),
                            androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                        ),
                )
                Text(name, style = MaterialTheme.typography.labelSmall, color = if (on) Color.White else Muted)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().blockFrame().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Redstone, checkedThumbColor = Color.White))
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((hour + 23) % 24) }) { Text("-") }
        Text("%02d:00".format(hour), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
    }
}

// ===================================================================== Permissions

private data class Perm(val title: String, val why: String, val ok: Boolean, val fix: Intent)

private fun permissions(context: Context): List<Perm> {
    val pkg = Uri.parse("package:${context.packageName}")
    val nm = context.getSystemService(NotificationManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    val list = mutableListOf(
        Perm(
            "Notifications", "Needed for every alert tier.",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        ),
        Perm(
            "Unrestricted battery", "Stops Android cutting the link while the phone sleeps.",
            pm.isIgnoringBatteryOptimizations(context.packageName),
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
        ),
    )
    if (Build.VERSION.SDK_INT >= 34) list += Perm(
        "Full-screen alarms", "Lets tier 4 alarms take over the lock screen.",
        nm.canUseFullScreenIntent(),
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
    )
    list += Perm(
        "Do Not Disturb access", "Optional: Urgent alerts ring during Do Not Disturb.",
        nm.isNotificationPolicyAccessGranted,
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
    )
    return list
}

@Composable
fun PermissionWarnings(resumeTick: Int) {
    val context = LocalContext.current
    val missing = remember(resumeTick) { permissions(context).filter { !it.ok && !it.title.startsWith("Do Not") } }
    if (missing.isEmpty()) return
    Column(Modifier.fillMaxWidth().blockFrame(fill = Color(0x26FFC233), accent = Gold.copy(alpha = 0.6f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Gold)
            Spacer(Modifier.width(8.dp))
            Text("Alerts may not reach you", style = MaterialTheme.typography.titleSmall, color = Gold)
        }
        PermissionList(resumeTick, showAll = false)
    }
}

@Composable
private fun PermissionList(resumeTick: Int, showAll: Boolean) {
    val context = LocalContext.current
    val perms = remember(resumeTick) { permissions(context) }
    perms.filter { showAll || (!it.ok && !it.title.startsWith("Do Not")) }.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RedstoneLamp(p.ok, 10.dp, onColor = Emerald, offColor = Gold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.bodyMedium)
                Text(p.why, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (!p.ok) TextButton(onClick = {
                try {
                    context.startActivity(p.fix)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }) { Text("FIX") }
        }
    }
}
