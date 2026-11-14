package com.notifyme.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.alpha
import com.notifyme.app.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyme.app.BlockInfo
import com.notifyme.app.Link
import com.notifyme.app.LinkState
import com.notifyme.app.Store
import com.notifyme.app.TIER_HINTS
import com.notifyme.app.TIER_NAMES
import com.notifyme.app.TRIGGERS
import org.json.JSONObject

val COOLDOWNS = listOf(0, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600)
val REPEATS = listOf(5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600)

@Composable
fun BlocksScreen(modifier: Modifier, resumeTick: Int, onGoWorlds: () -> Unit) {
    val blocks by Store.blocks.collectAsStateWithLifecycle()
    val servers by Store.servers.collectAsStateWithLifecycle()
    val status by Store.status.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BlockInfo?>(null) }

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { InviteCards() }
        if (settings.showCallsignCard) item { IdentityCard() }
        item { PermissionWarnings(resumeTick) }
        if (blocks.isNotEmpty()) item { DisplayBar(settings, blocks.values.filter { settings.showHidden || keyOf(it) !in settings.hidden }) }
        if (servers.isEmpty()) item {
            Column(Modifier.fillMaxWidth().blockFrame().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No worlds yet", style = MaterialTheme.typography.titleMedium)
                Text("1. Open your world with the Notify Me mod.\n2. Add it in the Worlds tab.\n3. Right-click a Redstone Notifier and type your callsign.")
                OutlinedButton(onClick = onGoWorlds) { Text("ADD A WORLD") }
            }
        }
        servers.forEach { s ->
            val st = status[s.id]
            val online = st?.state == LinkState.CONNECTED
            val all = blocks.values.filter { it.serverId == s.id }
            val list = displayBlocks(all, settings)
            val hiddenCount = if (settings.showHidden) 0 else all.count { keyOf(it) in settings.hidden }
            item(key = "h-${s.id}") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RedstoneLamp(online, 10.dp, onColor = Emerald, offColor = Color(0xFF3A3F47))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        s.name.ifEmpty { s.host }, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionLabel(stateLabel(st?.state), color = if (online) Emerald else Muted)
                }
            }
            if (all.isEmpty()) item(key = "e-${s.id}") {
                Text(
                    "No notifiers linked to this phone yet. Right-click a Redstone Notifier and type your callsign.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            } else if (list.isEmpty()) item(key = "f-${s.id}") {
                Text("No blocks match the current filter.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            items(list, key = { "b-${it.serverId}-${it.uuid}" }) { b -> BlockCard(b, online, settings) { editing = b } }
            if (hiddenCount > 0) item(key = "x-${s.id}") {
                Text(
                    "$hiddenCount hidden block${if (hiddenCount > 1) "s" else ""} · tap to show",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                    modifier = Modifier.clickable { Store.updateSettings { it.copy(showHidden = true) } }.padding(vertical = 4.dp),
                )
            }
        }
    }
    editing?.let { b -> BlockDialog(b) { editing = null } }
}

fun stateLabel(s: LinkState?) = when (s) {
    LinkState.CONNECTED -> "connected"
    LinkState.CONNECTING -> "connecting"
    LinkState.PENDING -> "awaiting approval"
    LinkState.REJECTED -> "rejected"
    LinkState.STOPPED -> "world stopped"
    LinkState.DISABLED -> "disabled"
    else -> "offline"
}

@Composable
fun IdentityCard() {
    val callsign by Store.callsign.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().blockFrame(fill = StoneLight, accent = RedstoneDim).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel("Your callsign")
            Text(
                callsign, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text("Type this into a Redstone Notifier to link it.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(callsign))
            Store.setError("Callsign copied")
        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy callsign", tint = Muted) }
    }
}

/** "dim 0 · Overworld", or just "dim 7" when the mod could not name it. */
fun dimLabel(b: BlockInfo) =
    if (b.dimName.isBlank() || b.dimName == "Dim ${b.dim}") "dim ${b.dim}" else "dim ${b.dim} · ${b.dimName}"

fun keyOf(b: BlockInfo) = Store.key(b.serverId, b.uuid)

/** Applies this phone's filter, sort, pin and hide choices. Pinned blocks always come first. */
fun displayBlocks(list: List<BlockInfo>, s: Settings): List<BlockInfo> {
    val byOrder: Comparator<BlockInfo> = when (s.blockSort) {
        1 -> compareByDescending { it.lastChange }
        2 -> compareByDescending<BlockInfo> { it.tier }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    return list
        .filter { s.showHidden || keyOf(it) !in s.hidden }
        .filter {
            when (s.blockFilter) {
                1 -> it.active
                2 -> !it.active
                else -> true
            }
        }
        .sortedWith(compareByDescending<BlockInfo> { keyOf(it) in s.pinned }.then(byOrder))
}

private val SORTS = listOf("Name A-Z", "Recently changed", "Alert tier")

/** Filter chips + sort menu + view options for the block list. */
@Composable
private fun DisplayBar(s: Settings, visible: List<BlockInfo>) {
    var sortOpen by remember { mutableStateOf(false) }
    var viewOpen by remember { mutableStateOf(false) }
    val on = visible.count { it.active }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All ${visible.size}", "ON $on", "OFF ${visible.size - on}").forEachIndexed { i, label ->
                FilterChip(
                    s.blockFilter == i, { Store.updateSettings { it.copy(blockFilter = i) } }, label = { Text(label) },
                    colors = tierChipColors(if (i == 1) Redstone else Muted),
                )
            }
        }
        Box {
            IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.SwapVert, "Sort", tint = Muted) }
            DropdownMenu(sortOpen, { sortOpen = false }, containerColor = StoneLight) {
                SectionLabel("Sort by", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                SORTS.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            Store.updateSettings { it.copy(blockSort = i) }
                            sortOpen = false
                        },
                        trailingIcon = { if (s.blockSort == i) Icon(Icons.Filled.Check, null, tint = Redstone) },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { viewOpen = true }) { Icon(Icons.Filled.Tune, "View options", tint = Muted) }
            DropdownMenu(viewOpen, { viewOpen = false }, containerColor = StoneLight) {
                SectionLabel("Show", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                ViewOption("Compact cards", s.compact) { v -> Store.updateSettings { it.copy(compact = v) } }
                ViewOption("Signal meter", s.showMeter) { v -> Store.updateSettings { it.copy(showMeter = v) } }
                ViewOption("Messages", s.showMessage) { v -> Store.updateSettings { it.copy(showMessage = v) } }
                ViewOption("Coordinates & settings", s.showDetails) { v -> Store.updateSettings { it.copy(showDetails = v) } }
                ViewOption("Callsign card", s.showCallsignCard) { v -> Store.updateSettings { it.copy(showCallsignCard = v) } }
                ViewOption("Hidden blocks", s.showHidden) { v -> Store.updateSettings { it.copy(showHidden = v) } }
            }
        }
    }
}

@Composable
private fun ViewOption(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onChange(!value) },
        trailingIcon = {
            Checkbox(value, onChange, colors = CheckboxDefaults.colors(checkedColor = Redstone, checkmarkColor = Color.White))
        },
    )
}

@Composable
private fun BlockCard(b: BlockInfo, serverOnline: Boolean, s: Settings, onClick: () -> Unit) {
    val tier = b.tier.coerceIn(0, 4)
    val pinned = keyOf(b) in s.pinned
    val hidden = keyOf(b) in s.hidden
    val frame = Modifier.fillMaxWidth().alpha(if (hidden) 0.55f else 1f)
        .blockFrame(accent = if (b.active) RedstoneDim else null).clickable(onClick = onClick)
    if (s.compact) {
        Column(frame.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedstoneLamp(b.active, 14.dp)
                Spacer(Modifier.width(10.dp))
                if (pinned) PinIcon()
                Text(
                    b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RedstoneLamp(true, 8.dp, onColor = TierColors[tier])
                Spacer(Modifier.width(10.dp))
                Text(if (b.active) "ON" else "OFF", style = MaterialTheme.typography.titleSmall, color = if (b.active) Redstone else Muted)
            }
            if (s.showMeter) SignalMeter(b.strength, b.threshold, cellHeight = 4.dp, showZone = false)
        }
        return
    }
    Column(frame.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RedstoneLamp(b.active, 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) PinIcon()
                    Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.showMessage && b.message.isNotBlank()) Text(
                    b.message, style = MaterialTheme.typography.bodyMedium, color = Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (b.active) "ON" else "OFF",
                style = MaterialTheme.typography.titleMedium,
                color = if (b.active) Redstone else Muted,
            )
        }
        if (s.showMeter) SignalMeter(b.strength, b.threshold, cellHeight = 8.dp, showZone = false)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(TIER_NAMES[tier], TierColors[tier])
            val since = if (b.lastChange > 0) DateUtils.getRelativeTimeSpanString(b.lastChange).toString() else "never"
            Text(
                "strength ${b.strength} · changed $since", style = MaterialTheme.typography.bodySmall, color = Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.showDetails) Text(
            "${b.x}, ${b.y}, ${b.z} · ${dimLabel(b)} · strength ≥${b.threshold} · " +
                (TRIGGERS.firstOrNull { it.first == b.trigger }?.second ?: b.trigger) +
                " · cooldown ${fmtSec(b.cooldown)}" + (if (b.trigger == "while_on") " · every ${fmtSec(b.repeat)}" else ""),
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        if (hidden) SectionLabel("hidden on this phone")
        val notes = buildList {
            if (!serverOnline) add("world offline")
            if (!b.loaded) add("chunk unloaded")
        }
        if (notes.isNotEmpty()) Text(
            notes.joinToString(" · ") + " - showing last known state",
            style = MaterialTheme.typography.labelSmall, color = Gold,
        )
    }
}

@Composable
private fun PinIcon() {
    Icon(Icons.Filled.PushPin, "Pinned", tint = Gold, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
}

@Composable
fun tierChipColors(color: Color) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = color.copy(alpha = 0.22f), selectedLabelColor = Color.White,
    labelColor = Muted,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockDialog(b: BlockInfo, inline: Boolean = false, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(b.name) }
    var message by remember { mutableStateOf(b.message) }
    var tier by remember { mutableIntStateOf(b.tier.coerceIn(0, 4)) }
    var trigger by remember { mutableStateOf(b.trigger) }
    var threshold by remember { mutableIntStateOf(b.threshold.coerceIn(1, 15)) }
    var cooldownIdx by remember { mutableIntStateOf(COOLDOWNS.indexOfLast { it <= b.cooldown }.coerceAtLeast(0)) }
    var alarmColor by remember { mutableIntStateOf(b.alarmColor) }
    var showState by remember { mutableStateOf(b.showState) }
    var repeatIdx by remember { mutableIntStateOf(REPEATS.indexOfLast { it <= b.repeat }.coerceAtLeast(0)) }
    var confirmUnlink by remember { mutableStateOf(false) }

    fun send(o: JSONObject): Boolean {
        val ok = Link.send(b.serverId, o.put("uuid", b.uuid))
        if (!ok) Store.setError("World is offline - try again when connected.")
        return ok
    }

    ThemedDialog(
        inline = inline,
        onDismiss = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedstoneLamp(b.active, 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${b.x}, ${b.y}, ${b.z} · ${dimLabel(b)} · owner ${b.owner}", style = MaterialTheme.typography.bodySmall, color = Muted)
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it.take(100) }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())

                SectionLabel("Alert tier")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIER_NAMES.forEachIndexed { i, n ->
                        FilterChip(tier == i, { tier = i }, label = { Text(n) }, colors = tierChipColors(TierColors[i]),
                            leadingIcon = { RedstoneLamp(true, 8.dp, onColor = TierColors[i]) })
                    }
                }
                Text(TIER_HINTS[tier], style = MaterialTheme.typography.bodySmall, color = TierColors[tier])
                if (tier == 4) {
                    SectionLabel("Alarm colour")
                    AlarmColorPicker(alarmColor) { alarmColor = it }
                }
                LocalToggle("Show \"is ON\" / \"is OFF\" after the name", showState) { showState = it }
                Text(
                    "Alerts read: " + com.notifyme.app.alertTitle(name.ifBlank { b.name }, b.active, true, showState),
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )

                SectionLabel("Alert when")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TRIGGERS.forEach { (id, label) -> FilterChip(trigger == id, { trigger = id }, label = { Text(label) }, colors = tierChipColors(Redstone)) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Strength", Modifier.weight(1f))
                    Text("≥ $threshold", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                SignalMeter(b.strength, threshold, onThreshold = { threshold = it })
                Text("Tap or drag. Lit = strength now (${b.strength}), gold = counts as ON.", style = MaterialTheme.typography.bodySmall, color = Muted)

                StepSliderRow("Cooldown", COOLDOWNS, cooldownIdx) { cooldownIdx = it }
                if (trigger == "while_on") StepSliderRow("Repeat every", REPEATS, repeatIdx) { repeatIdx = it }

                val key = keyOf(b)
                val st by Store.settings.collectAsStateWithLifecycle()
                SectionLabel("On this phone")
                LocalToggle("Pin to top", key in st.pinned) { v ->
                    Store.updateSettings { it.copy(pinned = if (v) it.pinned + key else it.pinned - key) }
                }
                LocalToggle("Hide from list", key in st.hidden) { v ->
                    Store.updateSettings { it.copy(hidden = if (v) it.hidden + key else it.hidden - key) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (send(JSONObject().put("t", "test"))) Store.setError("Test alert sent") }) { Text("TEST") }
                    OutlinedButton(onClick = { confirmUnlink = true }) { Text("UNLINK", color = Redstone) }
                }
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = Redstone), onClick = {
                if (send(
                        JSONObject().put("t", "config").put("name", name).put("message", message).put("tier", tier)
                            .put("trigger", trigger).put("threshold", threshold).put("cooldown", COOLDOWNS[cooldownIdx])
                            .put("repeat", REPEATS[repeatIdx]).put("alarmColor", alarmColor).put("showState", showState)
                    )
                ) onDismiss()
            }) { Text("SAVE") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("CLOSE", color = Muted) } },
    )
    if (confirmUnlink) AlertDialog(
        onDismissRequest = { confirmUnlink = false },
        containerColor = Stone,
        title = { Text("Unlink this block?") },
        text = { Text("This phone stops getting alerts from \"${b.name}\". You can link it again from the block's GUI.") },
        confirmButton = {
            TextButton(onClick = {
                send(JSONObject().put("t", "unlink"))
                confirmUnlink = false
                onDismiss()
            }) { Text("UNLINK", color = Redstone) }
        },
        dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("CANCEL") } },
    )
}

@Composable
private fun LocalToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Redstone, checkedThumbColor = Color.White))
    }
}

/** Slider that snaps to a list of second values, with a "Label  5m" header. */
@Composable
fun StepSliderRow(label: String, steps: List<Int>, index: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(label, Modifier.weight(1f))
            Text(fmtSec(steps[index]), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, steps.lastIndex)) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = Redstone, activeTrackColor = Redstone, inactiveTrackColor = StoneEdge,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.height(32.dp),
        )
    }
}

fun fmtSec(s: Int) = when {
    s == 0 -> "none"
    s < 60 -> "${s}s"
    s < 3600 -> "${s / 60}m"
    else -> "${s / 3600}h"
}
