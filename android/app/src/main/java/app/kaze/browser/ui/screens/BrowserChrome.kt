/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.ToolbarIcon
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.glass
import app.kaze.browser.ui.theme.liftCard
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Las acciones de la barra de Safari. */
class ChromeActions(
    val onAddress: () -> Unit,
    val onReload: () -> Unit,
    val onStop: () -> Unit,
    val onAa: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onShare: () -> Unit,
    val onLibrary: () -> Unit,
    val onTabs: () -> Unit,
    val onTabsLong: () -> Unit,
    /** Deslizar la barra: -1 pestaña anterior, +1 siguiente. */
    val onSwipeTab: (Int) -> Unit,
    val onExpand: () -> Unit,
)

/**
 * Barra inferior de Safari: cápsula de dirección (AA · sitio · recargar) y debajo
 * atrás, adelante, compartir, marcadores y pestañas. Al bajar por la página se
 * encoge a una cápsula pequeña con el dominio; un toque la vuelve a abrir.
 */
@Composable
fun SafariBottomBar(tab: Tab, collapsed: Boolean, a: ChromeActions, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = collapsed,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(180), targetScale = 0.92f))
        },
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier,
        label = "bar",
    ) { small ->
        if (small) CollapsedBar(tab, a.onExpand) else ExpandedBar(tab, a)
    }
}

@Composable
private fun CollapsedBar(tab: Tab, onExpand: () -> Unit) {
    val k = LocalKaze.current
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.glass(RoundedCornerShape(50), 8f).clickable(onClick = onExpand).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!tab.isSecure && !tab.isStart) {
                Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                addressLabel(tab).ifBlank { "Kaze" }, style = KazeType.footnote.copy(fontWeight = FontWeight.Medium),
                color = k.ink, maxLines = 1,
            )
        }
    }
}

@Composable
private fun ExpandedBar(tab: Tab, a: ChromeActions) {
    val k = LocalKaze.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(28.dp))
            .padding(top = 8.dp, start = 8.dp, end = 8.dp),
    ) {
        AddressPill(tab, a)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(Icons.AutoMirrored.Outlined.ArrowBackIos, "Atrás", enabled = tab.canGoBack, onClick = a.onBack)
            ToolbarIcon(Icons.AutoMirrored.Outlined.ArrowForwardIos, "Adelante", enabled = tab.canGoForward, onClick = a.onForward)
            ToolbarIcon(Icons.Outlined.IosShare, "Compartir y más", enabled = !tab.isStart, onClick = a.onShare)
            ToolbarIcon(Icons.AutoMirrored.Outlined.MenuBook, "Favoritos, lista de lectura e historial", onClick = a.onLibrary)
            TabsButton(a.onTabs, a.onTabsLong)
        }
    }
}

/** El ícono de pestañas de Safari (dos cuadrados); mantener presionado abre el menú rápido. */
@Composable
private fun TabsButton(onClick: () -> Unit, onLong: () -> Unit) {
    val k = LocalKaze.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).tap(onClick, onLong).semantics { contentDescription = "Pestañas" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset(3.dp, (-3).dp).size(15.dp).clip(RoundedCornerShape(4.dp)).background(k.accent.copy(alpha = 0.35f)))
        Box(Modifier.offset((-2).dp, 2.dp).size(16.dp).clip(RoundedCornerShape(4.dp)).background(k.accent).padding(1.8.dp)) {
            Box(Modifier.size(12.4.dp).clip(RoundedCornerShape(3.dp)).background(k.glass))
        }
    }
}

/** Texto de la cápsula: búsqueda si la página es de un buscador, si no el dominio. */
@Composable
private fun addressLabel(tab: Tab): String {
    val app = LocalApp.current
    val engine = app.settings.searchEngine
    return remember(tab.url, engine) {
        if (tab.isStart) "" else Urls.searchTermsOf(tab.url, engine) ?: Urls.displayHost(tab.url)
    }
}

@Composable
private fun AddressPill(tab: Tab, a: ChromeActions) {
    val k = LocalKaze.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val label = addressLabel(tab)

    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .offset { IntOffset(drag.value.roundToInt(), 0) }
            .liftCard(RoundedCornerShape(14.dp))
            // Deslizar a los lados cambia de pestaña (como Safari).
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val d = drag.value
                        scope.launch { drag.animateTo(0f, spring(0.8f, 500f)) }
                        if (abs(d) > 90) a.onSwipeTab(if (d < 0) 1 else -1)
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { drag.snapTo((drag.value + dx * 0.6f).coerceIn(-160f, 160f)) }
                }
            }
            // Deslizar hacia arriba abre la vista de pestañas.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -60) a.onTabs() },
                ) { change, dy -> change.consume(); total += dy }
            },
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            // "AA": opciones de la página (Lector, traducir, tamaño del texto…).
            Box(
                Modifier.size(46.dp).clickable(enabled = !tab.isStart, onClick = a.onAa)
                    .semantics { contentDescription = "Opciones de la página" },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    tab.translated -> Icon(Icons.Outlined.Translate, null, tint = k.accent, modifier = Modifier.size(19.dp))
                    else -> Text(
                        "AA",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            tab.isStart -> k.ink35
                            tab.readerMode -> k.accent
                            else -> k.ink
                        },
                    )
                }
                if (tab.readerable && !tab.readerMode) {
                    // Puntito: hay un artículo para leer en modo Lector.
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 9.dp).size(6.dp).clip(CircleShape).background(k.accent))
                }
            }
            Row(
                Modifier.weight(1f).height(46.dp).clickable(onClick = a.onAddress),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    tab.isStart || label.isBlank() -> Text("Buscar o escribir dirección", style = KazeType.body, color = k.ink55, maxLines = 1)
                    else -> {
                        if (tab.isPrivate) {
                            Text("Privada · ", style = KazeType.body.copy(fontWeight = FontWeight.Medium), color = k.ink55)
                        } else if (!tab.url.startsWith("about:")) {
                            Icon(
                                if (tab.isSecure) Icons.Outlined.Lock else Icons.Outlined.Warning, null,
                                tint = if (tab.isSecure) k.ink55 else Color(0xFFFF9500), modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            label, style = KazeType.body.copy(fontWeight = FontWeight.Medium), color = k.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                Modifier.size(46.dp).clickable(enabled = !tab.isStart) { if (tab.loading) a.onStop() else a.onReload() }
                    .semantics { contentDescription = if (tab.loading) "Detener" else "Recargar" },
                contentAlignment = Alignment.Center,
            ) {
                if (!tab.isStart) {
                    Icon(
                        if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh, null,
                        tint = k.ink, modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        // Progreso de carga: línea azul dentro de la cápsula, abajo.
        val progress by animateFloatAsState(tab.progress / 100f, tween(160), label = "progress")
        if (tab.loading) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp).fillMaxWidth(progress.coerceIn(0.05f, 1f))
                    .height(2.5.dp).clip(RoundedCornerShape(50)).background(k.accent),
            )
        }
    }
}

/** Barra de "Buscar en la página", sobre el teclado. */
@Composable
fun FindBar(
    query: String,
    current: Int,
    total: Int,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = LocalKaze.current
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(query) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier.fillMaxWidth().padding(10.dp).glass(RoundedCornerShape(22.dp)).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(38.dp).liftCard(RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onQuery(it) },
                singleLine = true,
                textStyle = KazeType.body.copy(color = k.ink),
                cursorBrush = SolidColor(k.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Buscar en la página", style = KazeType.body, color = k.ink55)
                    inner()
                },
            )
            if (text.isNotEmpty()) Text(if (total > 0) "$current de $total" else "Sin resultados", style = KazeType.footnote, color = k.ink55)
        }
        ToolbarIcon(Icons.Outlined.KeyboardArrowUp, "Anterior", enabled = total > 0, onClick = onPrev)
        ToolbarIcon(Icons.Outlined.KeyboardArrowDown, "Siguiente", enabled = total > 0, onClick = onNext)
        Text(
            "OK", style = KazeType.headline, color = k.accent,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
