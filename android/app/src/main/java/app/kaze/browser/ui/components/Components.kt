/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.Kaze
import app.kaze.browser.data.Space
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.liftCard
import app.kaze.browser.ui.theme.pressFeedback
import app.kaze.browser.ui.theme.spaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

val LocalApp = staticCompositionLocalOf<Kaze> { error("Kaze no inicializado") }

// ── Fondo del espacio ─────────────────────────────────────────────────

/** Papel + degradado del espacio + grano sutil. Hace fundido al cambiar de espacio. */
@Composable
fun SpaceBackground(colors: List<Int>, modifier: Modifier = Modifier) {
    val k = LocalKaze.current
    Box(modifier.fillMaxSize().background(k.paper)) {
        Crossfade(targetState = colors, animationSpec = tween(320), label = "spaceBg") { cs ->
            val dots = cs.map { spaceColor(it, k.isDark) }
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(if (k.isDark) 0.9f else 0.85f)
                    .drawBehind {
                        if (dots.isEmpty()) return@drawBehind
                        val maxDim = maxOf(size.width, size.height)
                        drawRect(dots[0])
                        if (dots.size >= 2) {
                            drawRect(
                                Brush.radialGradient(
                                    0f to dots[1].copy(alpha = 0.9f), 0.82f to dots[1].copy(alpha = 0f),
                                    center = Offset(size.width, size.height * 0.12f), radius = maxDim * 0.9f,
                                ),
                            )
                        }
                        if (dots.size >= 3) {
                            drawRect(
                                Brush.radialGradient(
                                    0f to dots[2].copy(alpha = 0.9f), 0.8f to dots[2].copy(alpha = 0f),
                                    center = Offset(size.width * 0.15f, size.height * 0.9f), radius = maxDim * 0.85f,
                                ),
                            )
                        }
                    },
            )
        }
        Box(Modifier.fillMaxSize().grain(if (k.isDark) 0.035f else 0.025f))
    }
}

private object Grain {
    // Textura de 64×64 generada una sola vez (16 KB) y repetida: el "papel" de Zen.
    val bitmap: Bitmap by lazy {
        val n = 64
        val rnd = java.util.Random(7)
        val px = IntArray(n * n) {
            val v = rnd.nextInt(256)
            ((v * 0.25).toInt() shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }
}

private fun Modifier.grain(opacity: Float) = drawWithCache {
    val paint = Paint().apply {
        shader = BitmapShader(Grain.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        alpha = (opacity * 255).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    onDrawBehind { drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
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
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)),
        )
    } else {
        LetterIcon(host ?: url, size, modifier)
    }
}

@Composable
private fun LetterIcon(text: String, size: Dp, modifier: Modifier) {
    val k = LocalKaze.current
    val letter = text.removePrefix("www.").firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '•'
    val hue = (abs(text.hashCode()) % 360).toFloat()
    val bg = Color.hsv(hue, if (k.isDark) 0.35f else 0.28f, if (k.isDark) 0.45f else 0.88f)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.toString(), fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold, color = k.ink.copy(alpha = 0.8f))
    }
}

// ── Barra superior: historial · buscar · ajustes ──────────────────────

@Composable
fun ActionBar(onHistory: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareButton(Icons.Outlined.History, "Historial", onHistory)
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .tap(onSearch)
                .liftCard()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "Buscar" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, tint = k.ink55, modifier = Modifier.size(18.dp))
            Text("Buscar o escribir dirección", style = KazeType.body, color = k.ink55, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SquareButton(Icons.Outlined.Settings, "Ajustes", onSettings)
    }
}

@Composable
fun SquareButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, size: Dp = 44.dp) {
    val k = LocalKaze.current
    Box(
        Modifier.size(size).tap(onClick).liftCard().semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = k.ink70, modifier = Modifier.size(20.dp))
    }
}

/** Toque sin onda de Material: la tarjeta se encoge un poco (como en Zen/Z-Sync). */
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

// ── Essentials ────────────────────────────────────────────────────────

@Composable
fun EssentialsGrid(
    tabs: List<Tab>,
    selectedId: String?,
    onOpen: (Tab) -> Unit,
    onMenu: (Tab) -> Unit,
) {
    if (tabs.isEmpty()) return
    val cols = when {
        tabs.size <= 3 -> tabs.size.coerceAtLeast(1)
        tabs.size == 4 -> 4
        else -> 3
    }.let { if (tabs.size > 8) 4 else it }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tabs.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .tap(onClick = { onOpen(t) }, onLongClick = { onMenu(t) })
                            .liftCard()
                            .semantics { contentDescription = t.displayTitle },
                        contentAlignment = Alignment.Center,
                    ) {
                        Favicon(t.pinnedUrl ?: t.url, 26.dp)
                        if (t.id == selectedId) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                                    .size(width = 14.dp, height = 3.dp).clip(CircleShape)
                                    .background(LocalKaze.current.ink55),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Fila de pestaña ───────────────────────────────────────────────────

@Composable
fun TabRow(
    tab: Tab,
    selected: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val k = LocalKaze.current
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(k.lift) else Modifier)
            .tap(onClick = onOpen, onLongClick = onMenu)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Favicon(tab.url, 24.dp, Modifier.alpha(if (tab.isLive || selected) 1f else 0.75f))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            tab.displayTitle,
            style = KazeType.tabTitle,
            color = if (tab.isLive || selected) k.ink else k.ink70,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Cerrar", tint = k.ink35, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Selector de espacios (emojis abajo) ───────────────────────────────

@Composable
fun SpaceSwitcher(
    spaces: List<Space>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = LocalKaze.current
    val state = rememberLazyListState()
    LaunchedEffect(currentIndex) { if (currentIndex in spaces.indices) state.animateScrollToItem(currentIndex) }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        itemsIndexed(spaces, key = { _, s -> s.id }) { i, s ->
            val selected = i == currentIndex
            val alpha by androidx.compose.animation.core.animateFloatAsState(
                if (selected) 1f else 0.38f, androidx.compose.animation.core.spring(0.8f, 400f), label = "sw",
            )
            Box(
                Modifier.size(38.dp).alpha(alpha).clip(CircleShape).clickable { onSelect(i) }
                    .semantics { contentDescription = s.name },
                contentAlignment = Alignment.Center,
            ) {
                Text(s.emoji, fontSize = 22.sp)
            }
        }
        item(key = "add") {
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onAdd)
                    .semantics { contentDescription = "Nuevo espacio" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, null, tint = k.ink35, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Píldora flotante para los controles del navegador (estilo Z-Sync). */
@Composable
fun FloatingPill(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val k = LocalKaze.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (k.isDark) Color(0xFF2A2A2C).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.9f))
            .then(Modifier.liftCard(RoundedCornerShape(50))),
        content = content,
    )
}
