package com.notifyme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifyme.app.R

// ---------------------------------------------------------------- palette (deepslate + redstone)

val Deepslate = Color(0xFF141518)
val Stone = Color(0xFF1E2025)
val StoneLight = Color(0xFF2A2D33)
val StoneEdge = Color(0xFF3A3F47)
val Redstone = Color(0xFFFF3B30)
val RedstoneDim = Color(0xFF4A1512)
val Emerald = Color(0xFF2FD36B)
val Gold = Color(0xFFFFC233)
val Lapis = Color(0xFF4F8DFF)
val Muted = Color(0xFF9AA1A9)

val SignalOn = Redstone
val SignalOff = Color(0xFF5A3532)
val TierColors = listOf(Color(0xFF9E9E9E), Lapis, Emerald, Gold, Redstone)

val Pixel = FontFamily(Font(R.font.pixelify_sans, FontWeight.Normal))

private val BlockShape = RoundedCornerShape(3.dp)

@Composable
fun NotifyMeTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Redstone, onPrimary = Color.White,
        primaryContainer = RedstoneDim, onPrimaryContainer = Color(0xFFFFDAD6),
        secondary = Emerald, onSecondary = Color.Black,
        tertiary = Gold, onTertiary = Color.Black,
        background = Deepslate, onBackground = Color(0xFFE8EAED),
        surface = Stone, onSurface = Color(0xFFE8EAED),
        surfaceVariant = StoneLight, onSurfaceVariant = Muted,
        surfaceContainer = Stone, surfaceContainerHigh = StoneLight, surfaceContainerHighest = StoneLight,
        surfaceContainerLow = Stone, surfaceContainerLowest = Deepslate,
        outline = StoneEdge, outlineVariant = StoneEdge,
        error = Color(0xFFFF6B5E),
    )
    val base = Typography()
    val typography = base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = Pixel, fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontFamily = Pixel, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Pixel, fontWeight = FontWeight.Normal, fontSize = 18.sp),
        titleSmall = base.titleSmall.copy(fontFamily = Pixel, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Pixel, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp), small = BlockShape, medium = BlockShape,
        large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(6.dp),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}

/** Minecraft-slot style frame: dark outline, light top/left bevel, dark bottom/right bevel. */
fun Modifier.blockFrame(fill: Color = Stone, shape: Shape = BlockShape, accent: Color? = null): Modifier =
    this
        .border(1.dp, accent ?: Color.Black, shape)
        .background(fill, shape)
        .drawBehind {
            val w = size.width
            val h = size.height
            val px = 2.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.07f), Offset(px, px), Offset(w - px, px), px)
            drawLine(Color.White.copy(alpha = 0.07f), Offset(px, px), Offset(px, h - px), px)
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(px, h - px), Offset(w - px, h - px), px)
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(w - px, px), Offset(w - px, h - px), px)
        }

/** Square "redstone lamp" indicator. */
@Composable
fun RedstoneLamp(on: Boolean, size: Dp = 14.dp, onColor: Color = SignalOn, offColor: Color = SignalOff) {
    val glow = if (on) Modifier.shadow(6.dp, RoundedCornerShape(2.dp), ambientColor = onColor, spotColor = onColor) else Modifier
    Box(
        glow.size(size).border(1.dp, Color.Black, RoundedCornerShape(2.dp))
            .background(if (on) onColor else offColor, RoundedCornerShape(2.dp))
            .drawBehind {
                if (on) drawRect(
                    Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(this.size.width * 0.22f, this.size.height * 0.22f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.28f, this.size.height * 0.28f),
                )
            },
    )
}

/**
 * The 15-step redstone signal meter, same as in-game. Lit cells = current signal strength,
 * gold underline = strengths that count as ON (>= threshold). Tap / drag to set the threshold when [onThreshold] != null.
 */
@Composable
fun SignalMeter(
    strength: Int,
    threshold: Int,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 14.dp,
    showZone: Boolean = true,
    onThreshold: ((Int) -> Unit)? = null,
) {
    fun cellAt(x: Float, width: Int) = ((x / width) * 15).toInt().coerceIn(0, 14) + 1
    val input = if (onThreshold == null) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures { onThreshold(cellAt(it.x, size.width)) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> onThreshold(cellAt(change.position.x, size.width)) } }
    Row(modifier.fillMaxWidth().then(input), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..15) {
            val lit = i <= strength
            val onZone = i >= threshold
            val fill = when {
                lit -> lerp(Color(0xFF6A0E08), Redstone, i / 15f)
                onZone -> Color(0xFF3A2020)
                else -> StoneLight
            }
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth().height(cellHeight)
                        .border(if (showZone && i == threshold) 2.dp else 1.dp, if (showZone && i == threshold) Gold else Color.Black, RoundedCornerShape(1.dp))
                        .background(fill, RoundedCornerShape(1.dp)),
                )
                if (showZone) Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(2.dp).background(if (onZone) Gold else Color.Transparent))
            }
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * AlertDialog in the app's style. [inline] draws the same layout without a dialog window
 * (used by the JVM screenshot tests, where dialog windows never go idle).
 */
@Composable
fun ThemedDialog(
    onDismiss: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    inline: Boolean = false,
) {
    if (!inline) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss, containerColor = Stone, title = title, text = text,
            confirmButton = confirmButton, dismissButton = dismissButton, icon = icon,
        )
        return
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(24.dp)) {
        Column(Modifier.fillMaxWidth().blockFrame(fill = Stone, shape = RoundedCornerShape(6.dp)).padding(24.dp)) {
            if (icon != null) Box(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(bottom = 12.dp)) { icon() }
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            Box(Modifier.padding(top = 16.dp, bottom = 20.dp)) {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
            }
            Row(Modifier.align(androidx.compose.ui.Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
    }
}

/** Small uppercase pixel-font section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(
        text.uppercase(), modifier,
        style = TextStyle(fontFamily = Pixel, fontSize = 12.sp, letterSpacing = 1.sp, color = color),
    )
}

/** Coloured pill, e.g. tier or connection state. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, color = color, style = TextStyle(fontFamily = Pixel, fontSize = 12.sp))
    }
}
