/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.theme

import app.kaze.browser.data.AccentColor
import app.kaze.browser.data.UiStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import android.graphics.Color as AColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Estética de Safari (iOS): fondos agrupados gris claro / negro, tarjetas blancas,
 * barras de "vidrio" translúcidas y flotantes, azul del sistema para las acciones.
 * Los nombres (paper, ink, lift…) se mantienen para no tocar toda la app.
 */

val SystemBlue = Color(0xFF007AFF)
val SystemBlueDark = Color(0xFF0A84FF)
val SystemRed = Color(0xFFFF3B30)

@Immutable
data class KazeColors(
    /** Fondo de la app (systemGroupedBackground). */
    val paper: Color,
    /** Texto principal (label). */
    val ink: Color,
    /** Relleno de campos y botones (tertiarySystemFill). */
    val lift: Color,
    /** Azul de las acciones. */
    val accent: Color,
    val isDark: Boolean,
    /** Tarjetas y listas (secondarySystemGroupedBackground). */
    val card: Color,
    /** Barras flotantes de vidrio. */
    val glass: Color,
    val separator: Color,
    /** Estilo Material You: formas más redondas y contenedores tonales. */
    val material: Boolean = false,
    /** Contenedor tonal (secondaryContainer en Material You). */
    val tonal: Color = lift,
    val onTonal: Color = ink,
) {
    /** Radio de las tarjetas: más redondo en Material You (Material 3 Expressive). */
    val corner get() = if (material) 24 else 14
    val ink70 get() = ink.copy(alpha = 0.7f)
    val ink55 get() = ink.copy(alpha = 0.6f)
    val ink35 get() = ink.copy(alpha = 0.3f)
    val ink12 get() = ink.copy(alpha = 0.12f)
    val rim get() = (if (isDark) Color.White else Color.Black).copy(alpha = if (isDark) 0.10f else 0.06f)
}

fun kazeColors(dark: Boolean) = if (dark) {
    KazeColors(
        paper = Color(0xFF000000), ink = Color(0xFFFFFFFF), lift = Color(0x3D767680), accent = SystemBlueDark,
        isDark = true, card = Color(0xFF1C1C1E), glass = Color(0xE01C1C1E), separator = Color(0x5C545458),
    )
} else {
    KazeColors(
        paper = Color(0xFFF2F2F7), ink = Color(0xFF000000), lift = Color(0x1F767680), accent = SystemBlue,
        isDark = false, card = Color(0xFFFFFFFF), glass = Color(0xEBF9F9F9), separator = Color(0x4A3C3C43),
    )
}

/**
 * Colores según el estilo elegido en Ajustes → Diseño.
 * Material You toma los colores del fondo de pantalla (Android 12+) o los arma desde el color elegido.
 */
fun styledColors(context: android.content.Context, dark: Boolean, style: UiStyle, accent: AccentColor): KazeColors {
    val base = kazeColors(dark)
    val pick = if (accent == AccentColor.AUTO) null else Color(accent.argb)
    if (style == UiStyle.SAFARI) return if (pick == null) base else base.copy(accent = pick)
    val scheme = when {
        pick == null && android.os.Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> {
            val c = pick ?: Color(0xFF6750A4)
            val bg = if (dark) Color(0xFF141218) else Color(0xFFFEF7FF)
            val tone = { f: Float -> lerp(bg, c, f) }
            if (dark) darkColorScheme(
                primary = lerp(c, Color.White, 0.35f), surface = tone(0.05f), onSurface = Color(0xFFE6E0E9),
                surfaceContainerLow = tone(0.08f), surfaceContainer = tone(0.11f), surfaceContainerHigh = tone(0.15f),
                surfaceContainerHighest = tone(0.20f), outlineVariant = tone(0.30f),
            ) else lightColorScheme(
                primary = c, surface = tone(0.03f), onSurface = Color(0xFF1D1B20),
                surfaceContainerLow = tone(0.05f), surfaceContainer = tone(0.08f), surfaceContainerHigh = tone(0.11f),
                surfaceContainerHighest = tone(0.15f), outlineVariant = tone(0.28f),
            )
        }
    }
    return KazeColors(
        paper = scheme.surface, ink = scheme.onSurface, lift = scheme.surfaceContainerHighest,
        accent = scheme.primary, isDark = dark, card = scheme.surfaceContainerLow,
        glass = scheme.surfaceContainer, separator = scheme.outlineVariant,
        material = true, tonal = scheme.secondaryContainer, onTonal = scheme.onSecondaryContainer,
    )
}

val LocalKaze = staticCompositionLocalOf { kazeColors(false) }

@Composable
fun KazeTheme(
    dark: Boolean,
    style: UiStyle = UiStyle.SAFARI,
    accent: AccentColor = AccentColor.AUTO,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val target = remember(dark, style, accent) { styledColors(context, dark, style, accent) }
    val spec = tween<Color>(250, easing = FastOutSlowInEasing)
    val paper by animateColorAsState(target.paper, spec, label = "paper")
    val ink by animateColorAsState(target.ink, spec, label = "ink")
    val accentC by animateColorAsState(target.accent, spec, label = "accent")
    val colors = target.copy(paper = paper, ink = ink, accent = accentC)

    val scheme = if (dark) {
        darkColorScheme(
            primary = accentC, onPrimary = Color.White, background = paper, onBackground = ink,
            surface = target.card, onSurface = ink, surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = ink.copy(alpha = 0.6f),
            surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C), surfaceContainerLow = Color(0xFF1C1C1E),
            outlineVariant = target.separator, error = Color(0xFFFF453A),
        )
    } else {
        lightColorScheme(
            primary = accentC, onPrimary = Color.White, background = paper, onBackground = ink,
            surface = target.card, onSurface = ink, surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = ink.copy(alpha = 0.6f),
            surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF2F2F7),
            surfaceContainerHighest = Color(0xFFE5E5EA), surfaceContainerLow = Color(0xFFFFFFFF),
            outlineVariant = target.separator, error = SystemRed,
        )
    }
    CompositionLocalProvider(LocalKaze provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Escala tipográfica de iOS (Large Title, Headline, Body, Subhead, Footnote, Caption). */
object KazeType {
    private val f = FontFamily.Default
    val largeTitle = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 0.2.sp)
    val title2 = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val headline = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val body = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val subhead = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val footnote = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    val sectionHeader = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp)

    // Alias de la versión anterior
    val spaceTitle = title2
    val tabTitle = headline.copy(fontWeight = FontWeight.Medium)
    val small = footnote
    val sheetTitle = title2
}

val CardShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(50)

/** Relleno gris de iOS (campos, botones secundarios). */
@Composable
fun Modifier.liftCard(shape: Shape = CardShape): Modifier {
    val k = LocalKaze.current
    return this.clip(shape).background(k.lift, shape)
}

/** Tarjeta blanca (o gris oscuro) sobre el fondo agrupado. */
@Composable
fun Modifier.card(shape: Shape = CardShape): Modifier {
    val k = LocalKaze.current
    return this.clip(shape).background(k.card, shape)
}

/** Vidrio flotante: translúcido, con sombra suave y un filo de luz. */
@Composable
fun Modifier.glass(shape: Shape = PillShape, elevation: Float = 18f): Modifier {
    val k = LocalKaze.current
    return this
        .shadow(elevation.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
        .clip(shape)
        .background(k.glass, shape)
        .border(0.5.dp, (if (k.isDark) Color.White else Color.Black).copy(alpha = if (k.isDark) 0.14f else 0.06f), shape)
}

/** Se encoge y atenúa mientras el dedo está encima. */
@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource, scaleTo: Float = 0.95f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, tween(90), label = "pressScale")
    val alpha by animateFloatAsState(if (pressed) 0.6f else 1f, tween(90), label = "pressAlpha")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/** Colores de grupo (espacio) atenuados para el fondo de la página de inicio. */
fun spaceColor(argb: Int, dark: Boolean): Color {
    if (!dark) return Color(argb)
    val hsv = FloatArray(3)
    AColor.colorToHSV(argb, hsv)
    hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
    hsv[2] = (hsv[2] * 0.30f).coerceIn(0f, 1f)
    return Color(AColor.HSVToColor(hsv))
}

fun spaceBase(colors: List<Int>, dark: Boolean, paper: Color): Color =
    colors.firstOrNull()?.let { spaceColor(it, dark) } ?: paper

fun Color.isLight(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) > 0.6
