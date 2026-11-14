package com.notifyme.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Full-screen alarm shown over the lock screen for tier 4 alerts. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Link.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val firstId = intent.getStringExtra("id")
        setContent { com.notifyme.app.ui.NotifyMeTheme { AlarmScreen(firstId, onDone = { finish() }) } }
    }

    @Composable
    private fun AlarmScreen(firstId: String?, onDone: () -> Unit) {
        val active by AlarmController.active.collectAsStateWithLifecycle()
        val alerts by Store.alerts.collectAsStateWithLifecycle()
        val settings by Store.settings.collectAsStateWithLifecycle()
        val ids = if (active.isEmpty() && firstId != null) listOf(firstId) else active
        val items = ids.mapNotNull { id -> alerts.firstOrNull { it.id == id && !it.acked } }

        LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onDone() }

        AlarmContent(
            items, settings,
            onAck = {
                items.forEach { Link.engine.acknowledge(it.id) }
                AlarmController.stopAll()
                onDone()
            },
            onSnooze = {
                items.forEach { Link.engine.snooze(it.id) }
                onDone()
            },
        )
    }

    companion object {
        fun intent(context: Context, id: String) = Intent(context, AlarmActivity::class.java)
            .putExtra("id", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/** The alarm screen itself. [animate] = false freezes the flash (screenshot tests). */
@Composable
internal fun AlarmContent(
    items: List<AlertItem>,
    settings: Settings,
    onAck: () -> Unit,
    onSnooze: () -> Unit,
    animate: Boolean = true,
) {
    // colour is chosen per block (first alarm wins when several ring at once)
    val (_, darkArgb, brightArgb) = ALARM_COLORS[(items.firstOrNull()?.color ?: 0).coerceIn(0, ALARM_COLORS.lastIndex)]
    val dark = Color(darkArgb)
    val bright = Color(brightArgb)
    val bg = if (animate && settings.flashScreen) {
        val transition = rememberInfiniteTransition(label = "flash")
        val c by transition.animateColor(
            initialValue = dark, targetValue = bright,
            animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "bg",
        )
        c
    } else if (settings.flashScreen) bright else dark
    // a soft shadow keeps white text readable on the bright colours (gold, diamond) mid-flash
    val shadow = Shadow(Color(0x99000000), Offset(0f, 3f), 8f)
    val big = TextStyle(
        color = Color.White, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center, shadow = shadow,
    )
    Column(
        Modifier.fillMaxSize().background(bg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("REDSTONE ALARM", color = Color.White, fontSize = 20.sp, fontFamily = com.notifyme.app.ui.Pixel, style = TextStyle(shadow = shadow))
        Spacer(Modifier.height(24.dp))
        items.take(3).forEach { a ->
            Text(a.title(), style = big)
            if (a.message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    a.message,
                    style = TextStyle(color = Color.White, fontSize = 20.sp, lineHeight = 27.sp, textAlign = TextAlign.Center, shadow = shadow),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(a.serverName, style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, shadow = shadow))
            Spacer(Modifier.height(18.dp))
        }
        if (items.size > 3) Text("+${items.size - 3} more", color = Color.White)
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onAck,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = dark),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) { Text("Acknowledge", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSnooze,
            border = BorderStroke(2.dp, Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Snooze 5 min", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) }
    }
}
