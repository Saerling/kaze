/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.theme

import android.graphics.Color as AColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState

/*
 * Estética "papel y tinta" (la de Z-Sync / Zen): fondos cálidos, texto casi negro,
 * superficies que se levantan apenas con un velo y un filo de 0.5 dp. Nada de los
 * colores por defecto de Material.
 */

val Forest = Color(0xFF264540)
val ForestDark = Color(0xFFA3CCC6)
val PaperLight = Color(0xFFF3ECE6)
val PaperDark = Color(0xFF1C1C1E)
val InkLight = Color(0xFF2E2E2E)
val InkDark = Color(0xFFF3ECE6)

@Immutable
data class KazeColors(
    val paper: Color,
    val ink: Color,
    /** Velo de las tarjetas (essentials, botones, barra de búsqueda). */
    val lift: Color,
    val accent: Color,
    val isDark: Boolean,
) {
    val ink70 get() = ink.copy(alpha = 0.7f)
    val ink55 get() = ink.copy(alpha = 0.55f)
    val ink35 get() = ink.copy(alpha = 0.35f)
    val ink12 get() = ink.copy(alpha = 0.12f)
    val rim get() = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)
}

fun kazeColors(dark: Boolean) = if (dark) {
    KazeColors(PaperDark, InkDark, Color.White.copy(alpha = 0.07f), ForestDark, true)
} else {
    KazeColors(PaperLight, InkLight, Color.Black.copy(alpha = 0.06f), Forest, false)
}

val LocalKaze = staticCompositionLocalOf { kazeColors(false) }

@Composable
fun KazeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val target = kazeColors(dark)
    val spec = tween<Color>(280, easing = FastOutSlowInEasing)
    val paper by animateColorAsState(target.paper, spec, label = "paper")
    val ink by animateColorAsState(target.ink, spec, label = "ink")
    val colors = target.copy(paper = paper, ink = ink)

    val scheme = if (dark) {
        darkColorScheme(
            primary = ForestDark, onPrimary = PaperDark, background = paper, onBackground = ink,
            surface = paper, onSurface = ink, surfaceVariant = Color(0xFF2A2A2C), onSurfaceVariant = ink.copy(alpha = 0.7f),
            surfaceContainer = Color(0xFF262628), surfaceContainerHigh = Color(0xFF303032),
            surfaceContainerHighest = Color(0xFF3A3A3C), surfaceContainerLow = Color(0xFF222224),
            outlineVariant = Color.White.copy(alpha = 0.12f),
        )
    } else {
        lightColorScheme(
            primary = Forest, onPrimary = Color.White, background = paper, onBackground = ink,
            surface = paper, onSurface = ink, surfaceVariant = Color(0xFFE4DED8), onSurfaceVariant = ink.copy(alpha = 0.7f),
            surfaceContainer = Color(0xFFECE5DF), surfaceContainerHigh = Color(0xFFE5DED7),
            surfaceContainerHighest = Color(0xFFDDD7D1), surfaceContainerLow = Color(0xFFF0E9E3),
            outlineVariant = Color.Black.copy(alpha = 0.1f),
        )
    }
    CompositionLocalProvider(LocalKaze provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object KazeType {
    private val f = FontFamily.Default
    val spaceTitle = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp)
    val tabTitle = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val body = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val small = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp)
    val sheetTitle = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp)
}

val CardShape = RoundedCornerShape(16.dp)
val PillShape = RoundedCornerShape(50)

/** Tarjeta "levantada": velo + filo fino. La misma para essentials, botones y barra. */
@Composable
fun Modifier.liftCard(shape: Shape = CardShape): Modifier {
    val k = LocalKaze.current
    return this.clip(shape).background(k.lift, shape).border(0.5.dp, k.rim, shape)
}

/** Se encoge y atenúa mientras el dedo está encima. */
@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource, scaleTo: Float = 0.94f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, tween(90), label = "pressScale")
    val alpha by animateFloatAsState(if (pressed) 0.6f else 1f, tween(90), label = "pressAlpha")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/** En modo oscuro, los colores del espacio se oscurecen un poco para no deslumbrar. */
fun spaceColor(argb: Int, dark: Boolean): Color {
    if (!dark) return Color(argb)
    val hsv = FloatArray(3)
    AColor.colorToHSV(argb, hsv)
    hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
    hsv[2] = (hsv[2] * 0.32f).coerceIn(0f, 1f)
    return Color(AColor.HSVToColor(hsv))
}

/** Color "base" de un espacio para teñir barras del navegador. */
fun spaceBase(colors: List<Int>, dark: Boolean, paper: Color): Color =
    colors.firstOrNull()?.let { spaceColor(it, dark) } ?: paper

fun Color.isLight(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) > 0.6

@Suppress("unused")
fun Color.argb(): Int = toArgb()
