/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.Kaze
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.card
import app.kaze.browser.ui.theme.glass
import app.kaze.browser.ui.theme.pressFeedback
import app.kaze.browser.ui.theme.spaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

val LocalApp = staticCompositionLocalOf<Kaze> { error("Kaze no inicializado") }

// ── Fondo suave del grupo (página de inicio y vista de pestañas) ──────

@Composable
fun SpaceBackground(colors: List<Int>, modifier: Modifier = Modifier, strength: Float = 0.55f) {
    val k = LocalKaze.current
    Box(modifier.fillMaxSize().background(k.paper)) {
        Crossfade(targetState = colors, animationSpec = tween(300), label = "spaceBg") { cs ->
            val dots = cs.map { spaceColor(it, k.isDark) }
            Box(
                Modifier.fillMaxSize().alpha(strength).drawBehind {
                    if (dots.isEmpty()) return@drawBehind
                    val maxDim = maxOf(size.width, size.height)
                    drawRect(
                        Brush.radialGradient(
                            0f to dots[0].copy(alpha = 0.9f), 1f to dots[0].copy(alpha = 0f),
                            center = Offset(size.width * 0.1f, 0f), radius = maxDim * 0.75f,
                        ),
                    )
                    dots.getOrNull(1)?.let { c ->
                        drawRect(
                            Brush.radialGradient(
                                0f to c.copy(alpha = 0.8f), 1f to c.copy(alpha = 0f),
                                center = Offset(size.width, size.height * 0.25f), radius = maxDim * 0.7f,
                            ),
                        )
                    }
                },
            )
        }
    }
}

// ── Íconos de sitios ──────────────────────────────────────────────────

@Composable
fun Favicon(url: String, size: Dp, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val host = remember(url) { Urls.host(url)?.removePrefix("www.") }
    var bmp by remember(host) { mutableStateOf(host?.let { app.favicons.cached(it) }) }
    LaunchedEffect(host) {
        if (host != null && bmp == null) bmp = withContext(Dispatchers.IO) { app.favicons.load(host) }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier.size(size).clip(RoundedCornerShape(size / 4.5f)))
    } else {
        LetterIcon(host ?: url, size, modifier)
    }
}

/**
 * Ícono del sitio sobre un mosaico claro, como los Favoritos de Safari: así los logos oscuros
 * (negros, transparentes) también se ven en modo oscuro.
 */
@Composable
fun FaviconTile(url: String, tile: Dp, icon: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val k = LocalKaze.current
    val shape = RoundedCornerShape(tile / 3.6f)
    Box(
        modifier.size(tile).clip(shape)
            .background(if (k.isDark) Color(0xFFF2F2F7) else Color.White, shape)
            .border(if (selected) 2.5.dp else 0.5.dp, if (selected) k.accent else Color.Black.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center,
    ) { Favicon(url, icon) }
}

@Composable
private fun LetterIcon(text: String, size: Dp, modifier: Modifier) {
    val letter = text.removePrefix("www.").firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '•'
    val hue = (abs(text.hashCode()) % 360).toFloat()
    val bg = Color.hsv(hue, 0.45f, 0.78f)
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4.5f)).background(bg), contentAlignment = Alignment.Center) {
        Text(letter.toString(), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ── Toques ────────────────────────────────────────────────────────────

/** Toque sin onda de Material: se encoge un poco, como en iOS. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source).clickable(interactionSource = source, indication = null, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source).combinedClickable(
        interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick,
    )
}

// ── Listas agrupadas de iOS ───────────────────────────────────────────

@Composable
fun IosSectionHeader(text: String, modifier: Modifier = Modifier) {
    val k = LocalKaze.current
    Text(
        text.uppercase(), style = KazeType.footnote, color = k.ink55,
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 22.dp, bottom = 7.dp),
    )
}

/** Bloque blanco con esquinas redondeadas y filas separadas por líneas finas. */
@Composable
fun IosGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).card(RoundedCornerShape(12.dp)), content = content)
}

@Composable
fun IosRow(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    subtitle: String? = null,
    titleColor: Color? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val k = LocalKaze.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .alpha(if (enabled) 1f else 0.4f)
                .padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> { leading(); Spacer(Modifier.width(14.dp)) }
                icon != null -> {
                    Icon(icon, null, tint = iconTint ?: k.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = KazeType.body, color = titleColor ?: k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = KazeType.footnote, color = k.ink55, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke(this)
            if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = k.ink35, modifier = Modifier.size(20.dp))
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(start = if (icon != null || leading != null) 52.dp else 16.dp),
                thickness = 0.5.dp, color = k.separator,
            )
        }
    }
}

// ── Botones ───────────────────────────────────────────────────────────

/** Botón redondo de vidrio (como los de la barra flotante de Safari). */
@Composable
fun GlassCircle(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Dp = 46.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val k = LocalKaze.current
    Box(
        modifier.size(size).glass(CircleShape, 10f).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) k.ink else k.ink35, modifier = Modifier.size(22.dp))
    }
}

/** Ícono de barra de herramientas: azul, plano, como en Safari. */
@Composable
fun ToolbarIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    val k = LocalKaze.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) (tint ?: k.accent) else k.ink35, modifier = Modifier.size(24.dp))
    }
}

/** Mosaico de sitio de la página de inicio: ícono grande en tarjeta + nombre debajo. */
@Composable
fun SiteTile(url: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val k = LocalKaze.current
    Column(modifier.tap(onClick, onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).card(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Favicon(url, 38.dp) }
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = KazeType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal), color = k.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp),
        )
    }
}
