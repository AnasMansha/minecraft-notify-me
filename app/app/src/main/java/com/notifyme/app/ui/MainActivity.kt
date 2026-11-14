package com.notifyme.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyme.app.AppVisibility
import com.notifyme.app.Link
import com.notifyme.app.LinkService
import com.notifyme.app.LinkState
import com.notifyme.app.Store

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so permission checks refresh after visiting system settings. */
    private var resumeTick by mutableIntStateOf(0)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xFF141518.toInt()),
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        Link.init(this)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        LinkService.start(this)
        setContent { NotifyMeTheme { App(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.foreground = true
        resumeTick++
        LinkService.start(this, LinkService.ACTION_RETRY)
    }

    override fun onPause() {
        AppVisibility.foreground = false
        super.onPause()
    }
}

private val TABS = listOf(
    "Blocks" to Icons.Filled.Sensors,
    "Alerts" to Icons.Filled.Notifications,
    "Worlds" to Icons.Filled.Public,
    "Settings" to Icons.Filled.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun App(resumeTick: Int, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val snackbar = remember { SnackbarHostState() }
    val error by Store.lastError.collectAsStateWithLifecycle()
    val alerts by Store.alerts.collectAsStateWithLifecycle()
    val status by Store.status.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            Store.setError(null)
        }
    }
    val unacked = alerts.count { !it.acked && it.tier >= 3 }
    val online = status.values.count { it.state == LinkState.CONNECTED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Deepslate),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RedstoneLamp(on = online > 0, size = 16.dp, onColor = Redstone)
                        Spacer(Modifier.width(10.dp))
                        Text(if (tab == 0) "Redstone Pager" else TABS[tab].first, style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (tab == 1) {
                        if (unacked > 0) TextButton(onClick = { Link.engine.acknowledgeAll() }) {
                            Icon(Icons.Filled.DoneAll, null)
                            Spacer(Modifier.width(4.dp))
                            Text("ACK ALL")
                        }
                        IconButton(onClick = { confirmClear = true }, enabled = alerts.isNotEmpty()) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear alerts")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Deepslate) {
                TABS.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, selectedTextColor = Redstone,
                            indicatorColor = RedstoneDim, unselectedIconColor = Muted, unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> BlocksScreen(m, resumeTick, onGoWorlds = { tab = 2 })
            1 -> AlertsScreen(m)
            2 -> WorldsScreen(m)
            else -> SettingsScreen(m, resumeTick)
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear alert history?") },
        text = { Text("Removes every alert from this list. Open alarms keep ringing until acknowledged.") },
        confirmButton = { TextButton(onClick = { Store.clearAlerts(); confirmClear = false }) { Text("CLEAR") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("CANCEL") } },
    )
}
