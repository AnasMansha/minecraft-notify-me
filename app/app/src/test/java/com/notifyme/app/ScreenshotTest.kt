package com.notifyme.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.notifyme.app.ui.App
import com.notifyme.app.ui.BlockDialog
import com.notifyme.app.ui.ManualAddDialog
import com.notifyme.app.ui.NotifyMeTheme
import com.notifyme.app.ui.previewFoundWorlds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen with sample data to PNGs in app/screenshots (no emulator needed). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi", application = Application::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun block(name: String, msg: String, tier: Int, active: Boolean, strength: Int, threshold: Int, loaded: Boolean = true, ago: Long = 120_000) =
        BlockInfo("s1", "uuid-$name", name, msg, tier, "rising", threshold, 5, 60, active, strength, now - ago, loaded, 0, 120, 64, -340, "Steve")

    @Before
    fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        Store.initForTest(ctx, "Odd Deer 68")
        Store.updateSettings { Settings() }
        Link.init(ctx)
        Store.servers.value.forEach { Store.removeServer(it.id) }
        Store.addServer(ServerConfig(id = "s1", name = "GTNH Base", host = "192.168.18.12", port = 25570))
        Store.addServer(ServerConfig(id = "s2", name = "Friends GTNH", host = "100.71.4.22", port = 25570))
        Store.setStatus("s1", ConnStatus(LinkState.CONNECTED, "as Odd Deer 68"))
        Store.setStatus("s2", ConnStatus(LinkState.PENDING, "Approve this phone in-game"))
        Store.replaceBlocks(
            "s1",
            listOf(
                block("EBF Overheat", "Coolant running low!", 4, true, 12, 8, ago = 40_000),
                block("Smeltery Fuel", "Out of lava", 2, false, 3, 5, ago = 3_600_000),
                block("Boiler Water", "", 3, true, 15, 1, loaded = false, ago = 900_000),
                block("AE2 Storage Full", "ME system at 95%", 1, false, 0, 1, ago = 86_400_000),
            ),
        )
        Store.clearAlerts()
        listOf(
            AlertItem("a4", "s1", "GTNH Base", "uuid-1", "AE2 Storage Full", 1, "ME system at 95%", true, now - 7_200_000, false, false),
            AlertItem("a3", "s1", "GTNH Base", "uuid-2", "Smeltery Fuel", 2, "Out of lava", true, now - 3_600_000, true, false),
            AlertItem("a2", "s1", "GTNH Base", "uuid-3", "Boiler Water", 3, "", true, now - 900_000, false, false, acked = true, ackedBy = "Odd Deer 68"),
            AlertItem("a1", "s1", "GTNH Base", "uuid-4", "EBF Overheat", 4, "Coolant running low!", true, now - 40_000, false, false),
        ).forEach { Store.addAlert(it) }
    }

    private fun shot(name: String) = captureScreenRoboImage("screenshots/$name.png")

    @Test
    fun blocks() {
        Store.offerInvite(Invite("Friend's Factory", "192.168.18.77", 25570, "zz"))
        compose.setContent { NotifyMeTheme { App(0, initialTab = 0) } }
        shot("1_blocks")
    }

    @Test
    fun alerts() {
        compose.setContent { NotifyMeTheme { App(0, initialTab = 1) } }
        shot("2_alerts")
    }

    @Test
    fun worlds() {
        previewFoundWorlds = listOf(
            FoundWorld("GTNH Base", "192.168.18.12", 25570, "x"),
            FoundWorld("Creative test world", "192.168.18.40", 25570, "y"),
        )
        compose.setContent { NotifyMeTheme { App(0, initialTab = 2) } }
        shot("3_worlds")
        previewFoundWorlds = null
    }

    @Test
    fun alarmRed() {
        val a = AlertItem("al", "s1", "GTNH Base", "uuid-4", "EBF Overheat", 4, "Coolant running low!", true, now, false, false)
        compose.setContent { NotifyMeTheme { AlarmContent(listOf(a), Settings(), {}, {}, animate = false) } }
        shot("8_alarm_red")
    }

    @Test
    fun alarmEnderTwoLines() {
        val a = AlertItem("al", "s1", "GTNH Base", "uuid-4", "Electric Blast Furnace Overheat", 4, "Coolant running low in the main reactor loop!", true, now, false, false, color = 5)
        compose.setContent { NotifyMeTheme { AlarmContent(listOf(a), Settings(flashScreen = false), {}, {}, animate = false) } }
        shot("9_alarm_ender")
    }

    @Test
    @Config(qualifiers = "w400dp-h1700dp-xxhdpi")
    fun settings() {
        compose.setContent { NotifyMeTheme { App(0, initialTab = 3) } }
        shot("4_settings")
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xxhdpi")
    fun blockDialog() {
        compose.setContent { NotifyMeTheme { BlockDialog(block("EBF Overheat", "Coolant running low!", 4, true, 12, 8), inline = true) {} } }
        shot("5_block_dialog")
    }

    @Test
    fun blocksCompactFiltered() {
        Store.updateSettings {
            it.copy(compact = true, blockSort = 1, pinned = setOf(Store.key("s1", "uuid-Smeltery Fuel")), showCallsignCard = false)
        }
        compose.setContent { NotifyMeTheme { App(0, initialTab = 0) } }
        shot("7_blocks_compact")
        Store.updateSettings { Settings() }
    }

    @Test
    fun manualAdd() {
        compose.setContent { NotifyMeTheme { ManualAddDialog(inline = true) {} } }
        shot("6_add_address")
    }
}
