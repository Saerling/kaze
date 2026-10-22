/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.ui.draw.shadow
import app.kaze.browser.ui.components.FaviconTile
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.graphics.Brush
import app.kaze.browser.data.Space
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
import androidx.compose.material.icons.outlined.MoreHoriz
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
import app.kaze.browser.ui.components.Favicon
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
    /** Botón del espacio (Zen): abre el panel de Essentials y espacios. */
    val onSpaces: () -> Unit,
    /** Casa: volver a la página de inicio de Kaze en esta pestaña. */
    val onHome: () -> Unit,
    val onNewTab: () -> Unit,
)

/**
 * Barra inferior: la de Safari (cápsula de dirección y botones) con lo de Zen
 * (el espacio activo, su color y cambiar de espacio deslizando).
 * Va por encima de los botones de Android; al bajar por la página se encoge.
 */
@Composable
fun SafariBottomBar(
    tab: Tab,
    space: Space?,
    tabCount: Int,
    collapsed: Boolean,
    a: ChromeActions,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = collapsed,
        // Se funde y crece desde abajo con un resorte suave (sin escalar: no hay saltos ni temblores).
        transitionSpec = {
            (fadeIn(tween(200, delayMillis = 70)) + slideInVertically(spring(0.86f, 420f)) { it / 3 }) togetherWith
                fadeOut(tween(110)) using SizeTransform(clip = false) { _, _ -> spring(0.86f, 420f) }
        },
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier,
        label = "bar",
    ) { small ->
        if (small) CollapsedBar(tab, space, a.onExpand) else ExpandedBar(tab, space, tabCount, a)
    }
}

/** Degradado suave con los colores del espacio, como el fondo de Zen. */
@Composable
private fun spaceTint(space: Space?): Brush {
    val k = LocalKaze.current
    val colors = space?.colors?.map { Color(it) }.orEmpty().ifEmpty { listOf(k.accent) }
    val alpha = if (k.isDark) 0.16f else 0.30f
    return Brush.linearGradient(colors.map { it.copy(alpha = alpha) } + colors.first().copy(alpha = alpha * 0.4f))
}

@Composable
private fun CollapsedBar(tab: Tab, space: Space?, onExpand: () -> Unit) {
    val k = LocalKaze.current
    // Píldora mínima pegada abajo (de borde a borde): toda la pantalla queda para la página.
    Column(
        Modifier.fillMaxWidth().background(k.glass).background(spaceTint(space)).clickable(onClick = onExpand)
            .navigationBarsPadding(),
    ) {
        val progress by animateFloatAsState(tab.progress / 100f, tween(160), label = "progressMini")
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (tab.loading) Box(Modifier.fillMaxWidth(progress.coerceIn(0.05f, 1f)).height(2.dp).background(k.accent))
        }
        Row(
            Modifier.fillMaxWidth().height(30.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (space != null) {
                Text(space.emoji, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
            }
            if (!tab.isSecure && !tab.isStart) {
                Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            } else if (tab.isSecure) {
                Icon(Icons.Outlined.Lock, null, tint = k.ink55, modifier = Modifier.size(11.dp))
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
private fun ExpandedBar(tab: Tab, space: Space?, tabCount: Int, a: ChromeActions) {
    val k = LocalKaze.current
    // Barra pegada abajo (de borde a borde, sin huecos), con las esquinas de arriba redondeadas.
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), ambientColor = Color.Black.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(k.glass)
            .background(spaceTint(space))
            .navigationBarsPadding()
            .padding(top = 8.dp, start = 10.dp, end = 10.dp),
    ) {
        AddressPill(tab, a)
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(Icons.AutoMirrored.Outlined.ArrowBackIos, "Atrás", enabled = tab.canGoBack, onClick = a.onBack)
            ToolbarIcon(Icons.AutoMirrored.Outlined.ArrowForwardIos, "Adelante", enabled = tab.canGoForward, onClick = a.onForward)
            HomeButton(tab.isStart, a.onHome, a.onSpaces)
            ToolbarIcon(Icons.Outlined.MoreHoriz, "Menú", onClick = a.onShare)
            ToolbarIcon(Icons.AutoMirrored.Outlined.MenuBook, "Favoritos, lista de lectura e historial", onClick = a.onLibrary)
            TabsButton(tabCount, a.onTabs, a.onTabsLong)
        }
    }
}

/** Casa: toque = página de inicio de Kaze; mantener = panel de Zen (Essentials, espacios, acciones). */
@Composable
private fun HomeButton(atHome: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val k = LocalKaze.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).tap(onClick, onLong)
            .semantics { contentDescription = "Página de inicio (mantén presionado: Essentials y espacios)" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (atHome) Icons.Filled.Home else Icons.Outlined.Home, null,
            tint = k.accent, modifier = Modifier.size(25.dp),
        )
    }
}

/** El ícono de pestañas de Safari, con el número de pestañas abiertas; mantener presionado abre el menú rápido. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit, onLong: () -> Unit) {
    val k = LocalKaze.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).tap(onClick, onLong).semantics { contentDescription = "$count pestañas" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset(3.dp, (-3).dp).size(17.dp).clip(RoundedCornerShape(5.dp)).background(k.accent.copy(alpha = 0.35f)))
        Box(
            Modifier.offset((-2).dp, 2.dp).size(19.dp).clip(RoundedCornerShape(5.dp)).background(k.accent).padding(1.8.dp)
                .clip(RoundedCornerShape(4.dp)).background(k.glass),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (count > 99) "∞" else count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = k.accent)
        }
    }
}

/**
 * Panel de Zen: Essentials arriba (siempre a mano) y los espacios del perfil para saltar entre ellos.
 */
@Composable
fun ZenPanel(
    essentials: List<Tab>,
    spaces: List<Space>,
    currentSpaceId: String,
    tabCount: (Space) -> Int,
    onEssential: (Tab) -> Unit,
    onSpace: (Space) -> Unit,
    onNewSpace: () -> Unit,
    onEditSpace: (Space) -> Unit,
    onNewTab: () -> Unit = {},
    onClearSpace: () -> Unit = {},
    onCompact: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val k = LocalKaze.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(10.dp).fillMaxWidth()
                .glass(RoundedCornerShape(26.dp))
                .background(spaceTint(spaces.firstOrNull { it.id == currentSpaceId }))
                .clickable(enabled = false) {}
                .padding(14.dp),
        ) {
            // Acciones rápidas de Zen.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZenAction(Icons.Outlined.Add, "Pestaña", onNewTab, Modifier.weight(1f))
                ZenAction(Icons.Outlined.CleaningServices, "Limpiar", onClearSpace, Modifier.weight(1f))
                ZenAction(Icons.Outlined.Fullscreen, "Compacto", onCompact, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            if (essentials.isNotEmpty()) {
                Text("Essentials", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink55)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    essentials.forEach { t ->
                        Column(
                            Modifier.width(62.dp).clip(RoundedCornerShape(14.dp)).clickable { onEssential(t) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FaviconTile(t.pinnedUrl ?: t.url, 48.dp, 26.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(t.displayTitle, style = KazeType.caption, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Text("Espacios", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink55)
            Spacer(Modifier.height(6.dp))
            spaces.forEach { sp ->
                val selected = sp.id == currentSpaceId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (selected) k.card else Color.Transparent)
                        .tap({ onSpace(sp) }, { onEditSpace(sp) })
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Brush.linearGradient(sp.colors.map { Color(it) }.let { if (it.size > 1) it else it + it })),
                        contentAlignment = Alignment.Center,
                    ) { Text(sp.emoji, fontSize = 15.sp) }
                    Spacer(Modifier.width(12.dp))
                    Text(sp.name, style = KazeType.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = k.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(tabCount(sp).toString(), style = KazeType.footnote, color = k.ink55)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onNewSpace).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).liftCard(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, null, tint = k.accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Nuevo espacio", style = KazeType.body, color = k.accent)
            }
            Text(
                "Mantén presionada la casa para abrir este panel · mantén presionado un espacio para editarlo",
                style = KazeType.caption, color = k.ink35, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
            )
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

@Composable
private fun ZenAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    val k = LocalKaze.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(k.card).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = k.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = KazeType.caption, color = k.ink)
    }
}
