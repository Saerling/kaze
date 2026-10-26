/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import app.kaze.browser.data.TabKind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.wrapContentHeight
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
import app.kaze.browser.ui.theme.pressFeedback
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
    /** Mantener atrás (-1) o adelante (+1): lista de páginas anteriores o siguientes. */
    val onHistoryMenu: (Int) -> Unit = {},
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
    /** Pestañas del espacio: se deslizan en la barra y las vecinas asoman a los lados (como Safari). */
    tabs: List<Tab> = emptyList(),
    onSelectTab: (Tab) -> Unit = {},
    /** Barra arriba (uso tipo escritorio) en vez de abajo. */
    top: Boolean = false,
    /** Diseño compacto de Safari: una sola píldora flotante (atrás · sitio · ⋯). */
    compact: Boolean = false,
    /** Tableta con la barra arriba: una fila como Safari en iPad (pestañas en línea). */
    tablet: Boolean = false,
    /** Diseño "Arriba": aquí solo van los botones (la dirección va en SafariTopAddress). */
    split: Boolean = false,
    /** Cerrar una pestaña desde la fila de pestañas de la tableta. */
    onCloseTab: (Tab) -> Unit = {},
) {
    if (split) {
        AnimatedVisibility(
            !collapsed, modifier = modifier,
            enter = slideInVertically(spring(0.86f, 420f)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
        ) { ExpandedBar(tab, space, tabCount, a, tabs, onSelectTab, showAddress = false, onCloseTab = onCloseTab) }
        return
    }
    if (tablet) {
        TabletBar(tab, space, tabCount, a, tabs, onSelectTab, onCloseTab, modifier)
        return
    }
    if (compact) {
        CompactBar(tab, space, tabCount, collapsed, a, modifier, tabs, onSelectTab, onCloseTab)
        return
    }
    AnimatedContent(
        targetState = collapsed,
        // Se funde y crece desde abajo con un resorte suave (sin escalar: no hay saltos ni temblores).
        transitionSpec = {
            (fadeIn(tween(200, delayMillis = 70)) + slideInVertically(spring(0.86f, 420f)) { it / 3 }) togetherWith
                fadeOut(tween(110)) using SizeTransform(clip = false) { _, _ -> spring(0.86f, 420f) }
        },
        contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter,
        modifier = modifier,
        label = "bar",
    ) { small ->
        CompositionLocalProvider(LocalBarTop provides top) {
            if (small) CollapsedBar(tab, space, a.onExpand) else ExpandedBar(tab, space, tabCount, a, tabs, onSelectTab, onCloseTab = onCloseTab)
        }
    }
}

/** La barra está arriba: esquinas redondeadas abajo y sin el margen de los botones de Android. */
private val LocalBarTop = androidx.compose.runtime.compositionLocalOf { false }

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
            .then(if (LocalBarTop.current) Modifier else Modifier.navigationBarsPadding()),
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
                addressLabel(tab).ifBlank { "Fyx" }, style = KazeType.footnote.copy(fontWeight = FontWeight.Medium),
                color = k.ink, maxLines = 1,
            )
        }
    }
}

@Composable
private fun ExpandedBar(tab: Tab, space: Space?, tabCount: Int, a: ChromeActions, tabs: List<Tab>, onSelectTab: (Tab) -> Unit, showAddress: Boolean = true, onCloseTab: (Tab) -> Unit = {}) {
    val k = LocalKaze.current
    val top = LocalBarTop.current
    val r = if (k.material) 28.dp else 22.dp
    val shape = if (top) RoundedCornerShape(bottomStart = r, bottomEnd = r) else RoundedCornerShape(topStart = r, topEnd = r)
    // Barra pegada abajo (de borde a borde, sin huecos), con las esquinas de arriba redondeadas.
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (k.material) 0.dp else 14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f))
            .clip(shape)
            .background(k.glass)
            .then(if (k.material) Modifier else Modifier.background(spaceTint(space)))
            .then(if (top) Modifier.padding(bottom = 6.dp) else Modifier.navigationBarsPadding())
            .padding(top = 8.dp, start = 10.dp, end = 10.dp),
    ) {
        Column(Modifier.widthIn(max = 760.dp).align(Alignment.CenterHorizontally)) {
        if (LocalApp.current.settings.tabStripPhone && tabs.size > 1) TabStrip(tab, tabs, onSelectTab, onCloseTab, Modifier.padding(bottom = 6.dp), minTab = 110.dp, maxTab = 170.dp)
        if (showAddress) TabPager(tab, tabs, a, onSelectTab)
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(tab.canGoBack, enter = fadeIn(tween(160)) + expandHorizontally(spring(0.85f, 500f)), exit = fadeOut(tween(110)) + shrinkHorizontally(tween(160))) { ToolbarIcon(Icons.AutoMirrored.Outlined.ArrowBackIos, "Atrás (mantén para ver el historial)", enabled = tab.canGoBack, onLong = { a.onHistoryMenu(-1) }, onClick = a.onBack) }
            AnimatedVisibility(tab.canGoForward, enter = fadeIn(tween(160)) + expandHorizontally(spring(0.85f, 500f)), exit = fadeOut(tween(110)) + shrinkHorizontally(tween(160))) { ToolbarIcon(Icons.AutoMirrored.Outlined.ArrowForwardIos, "Adelante (mantén para ver el historial)", enabled = tab.canGoForward, onLong = { a.onHistoryMenu(1) }, onClick = a.onForward) }
            HomeButton(tab.isStart, a.onHome, a.onSpaces)
            ToolbarIcon(Icons.Outlined.MoreHoriz, "Menú", onClick = a.onShare)
            ToolbarIcon(Icons.AutoMirrored.Outlined.MenuBook, "Favoritos, lista de lectura e historial", onClick = a.onLibrary)
            TabsButton(tabCount, a.onTabs, a.onTabsLong)
        }
        }
    }
}

/** Casa: toque = página de inicio de Kaze; mantener = panel de Zen (Essentials, espacios, acciones). */
@Composable
private fun HomeButton(atHome: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val k = LocalKaze.current
    // En Material You, la casa activa va dentro de una píldora tonal (como la barra de navegación de M3).
    val pill by animateFloatAsState(if (atHome && k.material) 1f else 0f, spring(0.7f, 500f), label = "homePill")
    Box(
        Modifier.size(width = 56.dp, height = 48.dp).clip(RoundedCornerShape(50)).tap(onClick, onLong)
            .semantics { contentDescription = "Página de inicio (mantén presionado: Essentials y espacios)" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 56.dp * pill.coerceAtLeast(0.01f), height = 32.dp).clip(RoundedCornerShape(50)).background(k.tonal.copy(alpha = pill)))
        Icon(
            if (atHome) Icons.Filled.Home else Icons.Outlined.Home, null,
            tint = k.accent, modifier = Modifier.size(25.dp),
        )
    }
}

/** El ícono de pestañas de Safari, con el número de pestañas abiertas; mantener presionado abre el menú rápido. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit, onLong: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val k = LocalKaze.current
    Box(
        Modifier.size(size).clip(CircleShape).tap(onClick, onLong).semantics { contentDescription = "$count pestañas" },
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
            Modifier.navigationBarsPadding().padding(10.dp).widthIn(max = 560.dp).fillMaxWidth()
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

/**
 * Las pestañas del espacio en la barra, como en Safari: la actual en el centro y las vecinas
 * asomando a los lados. Deslizar cambia de pestaña con la animación de página del carrusel.
 */
@Composable
private fun TabPager(tab: Tab, tabs: List<Tab>, a: ChromeActions, onSelectTab: (Tab) -> Unit) {
    val index = tabs.indexOfFirst { it.id == tab.id }
    if (tabs.size < 2 || index < 0) {
        AddressPill(tab, a)
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = index) { tabs.size }
    LaunchedEffect(tab.id, tabs.size) {
        val i = tabs.indexOfFirst { it.id == tab.id }
        if (i >= 0 && i != pager.currentPage) pager.animateScrollToPage(i)
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(pager.settledPage) {
        tabs.getOrNull(pager.settledPage)?.let {
            if (it.id != tab.id) {
                // Cada cambio de pestaña al deslizar se siente como un "clic" suave, como los selectores de iOS.
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                onSelectTab(it)
            }
        }
    }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        pageSpacing = 8.dp,
        key = { tabs.getOrNull(it)?.id ?: it },
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        val t = tabs[page]
        if (t.id == tab.id) AddressPill(tab, a, swipe = false) else MiniPill(t)
    }
}

/** Pastilla de una pestaña vecina en la barra (solo su sitio, atenuada). */
@Composable
private fun MiniPill(t: Tab) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().height(46.dp)
            .then(if (k.material) Modifier.clip(RoundedCornerShape(50)).background(k.tonal.copy(alpha = 0.6f)) else Modifier.liftCard(RoundedCornerShape(14.dp)))
            .alpha(0.75f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!t.isStart) { FaviconTile(t.url, 22.dp, 14.dp); Spacer(Modifier.width(8.dp)) }
        Text(
            if (t.isStart) "Página de inicio" else Urls.displayHost(t.url).ifBlank { t.displayTitle },
            style = KazeType.body.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddressPill(tab: Tab, a: ChromeActions, swipe: Boolean = true) {
    val k = LocalKaze.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val label = addressLabel(tab)

    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .offset { IntOffset(drag.value.roundToInt(), 0) }
            .then(if (k.material) Modifier.clip(RoundedCornerShape(50)).background(k.tonal) else Modifier.liftCard(RoundedCornerShape(14.dp)))
            // Deslizar a los lados cambia de pestaña (como Safari).
            .pointerInput(tab.id, swipe) {
                if (!swipe) return@pointerInput
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

/**
 * Mini menú del historial de la pestaña (mantener atrás o adelante): fichas que se despliegan desde
 * la barra, la más cercana abajo. Toca una para ir directo a esa página.
 */
@Composable
fun HistoryPopup(tab: Tab, direction: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    val k = LocalKaze.current
    val items = tab.historyItems
    val cur = tab.historyIndex
    val indices = if (direction < 0) (cur - 1 downTo 0).toList() else (cur + 1..items.lastIndex).toList()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = if (direction < 0) Alignment.BottomStart else Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 120.dp)
                .widthIn(max = 420.dp).heightIn(max = 420.dp)
                .clip(RoundedCornerShape(24.dp)).background(k.glass).padding(8.dp)
                .verticalScroll(rememberScrollState(), reverseScrolling = true),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (direction < 0) "Páginas anteriores" else "Páginas siguientes",
                style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink55,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            // De arriba a abajo: la más lejana arriba y la más cercana junto a la barra.
            indices.reversed().forEachIndexed { pos, i ->
                val (title, url) = items[i]
                androidx.compose.animation.AnimatedVisibility(
                    shown,
                    enter = fadeIn(tween(160, delayMillis = (indices.size - pos) * 25)) +
                        slideInVertically(spring(0.8f, 500f)) { it / 2 },
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(k.card)
                            .clickable { onGo(i); onDismiss() }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FaviconTile(url, 30.dp, 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title.ifBlank { Urls.displayHost(url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Urls.displayHost(url), style = KazeType.caption, color = k.ink55, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Barra compacta de Safari: una píldora flotante con atrás, el sitio y "⋯". Al bajar por la página
 * se encoge; "⋯" abre un menú vertical con el resto de acciones. Deslizar hacia arriba abre las pestañas.
 */
@Composable
private fun CompactBar(tab: Tab, space: Space?, tabCount: Int, collapsed: Boolean, a: ChromeActions, modifier: Modifier, tabs: List<Tab> = emptyList(), onSelectTab: (Tab) -> Unit = {}, onCloseTab: (Tab) -> Unit = {}) {
    val k = LocalKaze.current
    val showStrip = LocalApp.current.settings.tabStripPhone && tabs.size > 1
    var menu by remember { mutableStateOf(false) }
    val height by animateDpAsState(if (collapsed) 36.dp else 50.dp, spring(0.8f, 420f), label = "compactH")
    val side by animateDpAsState(if (collapsed) 64.dp else 12.dp, spring(0.8f, 420f), label = "compactW")
    val enter = fadeIn(tween(160)) + scaleIn(spring(0.6f, 520f), initialScale = 0.8f)
    val exit = fadeOut(tween(110)) + scaleOut(tween(140), targetScale = 0.85f)
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(start = side, end = side, bottom = 8.dp)) {
    // Fila de pestañas (opcional): una cápsula de vidrio encima de la barra, solo con la barra abierta.
    AnimatedVisibility(showStrip && !collapsed, enter = fadeIn(tween(180)) + expandVertically(spring(0.85f, 420f)), exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
        Box(Modifier.padding(bottom = 8.dp).glass(RoundedCornerShape(18.dp), 10f).padding(4.dp)) {
            TabStrip(tab, tabs, onSelectTab, onCloseTab, minTab = 110.dp, maxTab = 170.dp)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        // Como Safari en iOS: burbujas de vidrio a los lados (atrás, adelante, ⋯) y la píldora grande del sitio.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                // Minimizada, toda la franja abre la barra: el área táctil llega a 48 dp aunque la píldora se vea pequeña.
                .then(if (collapsed) Modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = a.onExpand) else Modifier)
                .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -50) a.onTabs() }) { c, dy -> c.consume(); total += dy }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Atrás y adelante solo aparecen cuando sirven (como Safari).
            AnimatedVisibility(!collapsed && tab.canGoBack, enter = enter, exit = exit) {
                Bubble(Icons.AutoMirrored.Outlined.ArrowBackIos, "Atrás (mantén para ver el historial)", tab.canGoBack, { a.onHistoryMenu(-1) }, a.onBack, Modifier.padding(end = 8.dp))
            }
            AnimatedVisibility(!collapsed && tab.canGoForward, enter = enter, exit = exit) {
                Bubble(Icons.AutoMirrored.Outlined.ArrowForwardIos, "Adelante (mantén para ver el historial)", tab.canGoForward, { a.onHistoryMenu(1) }, a.onForward, Modifier.padding(end = 8.dp))
            }
            val pillSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val progress by animateFloatAsState(if (tab.loading) tab.progress / 100f else 0f, tween(200), label = "cp")
            val accent = k.accent
            Row(
                Modifier.weight(1f).height(height).pressFeedback(pillSource, 0.97f).glass(RoundedCornerShape(50), 16f).clip(RoundedCornerShape(50))
                    // Progreso de carga dentro de la píldora, de izquierda a derecha por su borde de abajo.
                    .drawWithContent {
                        drawContent()
                        if (tab.loading) {
                            val h = 2.5.dp.toPx()
                            drawRect(accent, androidx.compose.ui.geometry.Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0.05f, 1f), h))
                        }
                    }
                    .clickable(pillSource, indication = null) { if (collapsed) a.onExpand() else a.onAddress() }
                    .semantics { contentDescription = if (collapsed) "Mostrar la barra" else "Buscar o escribir dirección" }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(tab.isSecure && !tab.isStart, enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f), exit = fadeOut(tween(110))) {
                    Icon(Icons.Outlined.Lock, null, tint = k.ink55, modifier = Modifier.padding(end = 5.dp).size(12.dp))
                }
                // El sitio cambia con un deslizamiento suave al navegar o cambiar de pestaña.
                AnimatedContent(
                    targetState = addressLabel(tab).ifBlank { "Buscar o escribir dirección" },
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(spring(0.8f, 500f)) { it / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
                    },
                    label = "compactLabel",
                ) { text ->
                    Text(
                        text,
                        style = (if (collapsed) KazeType.footnote else KazeType.body).copy(fontWeight = FontWeight.Medium),
                        color = if (tab.isStart) k.ink55 else k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(!collapsed, enter = enter, exit = exit) {
                Bubble(Icons.Outlined.MoreHoriz, "Más", true, a.onShare, { menu = true }, Modifier.padding(start = 8.dp))
            }
        }
    }
    }
    if (menu) CompactMenu(tab, tabCount, a) { menu = false }
}

/** Burbuja de vidrio redonda de la barra compacta. */
@Composable
private fun Bubble(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onLong: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(50.dp).glass(CircleShape, 16f), contentAlignment = Alignment.Center) {
        ToolbarIcon(icon, label, enabled = enabled, onLong = onLong, onClick = onClick)
    }
}

/**
 * Diseño "Arriba" de Safari: la dirección arriba y los botones abajo.
 * Al bajar por la página los botones se esconden y la dirección se encoge.
 */
@Composable
fun SafariTopAddress(tab: Tab, space: Space?, collapsed: Boolean, a: ChromeActions, tabs: List<Tab>, onSelectTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val k = LocalKaze.current
    val r = if (k.material) 28.dp else 22.dp
    AnimatedContent(
        targetState = collapsed,
        transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(110)) using SizeTransform(clip = false) { _, _ -> spring(0.86f, 420f) } },
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxWidth(),
        label = "topAddress",
    ) { small ->
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = r, bottomEnd = r)).background(k.glass)
                .then(if (k.material) Modifier else Modifier.background(spaceTint(space))),
        ) {
            if (small) {
                Text(
                    addressLabel(tab).ifBlank { "Fyx" }, style = KazeType.footnote.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().height(32.dp).clickable(onClick = a.onExpand).wrapContentHeight(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp).widthIn(max = 760.dp).align(Alignment.Center)) {
                    TabPager(tab, tabs, a, onSelectTab)
                }
            }
        }
    }
}

/** Menú vertical de la barra compacta (como el "⋯" de Safari en iOS). */
@Composable
private fun CompactMenu(tab: Tab, tabCount: Int, a: ChromeActions, onDismiss: () -> Unit) {
    val k = LocalKaze.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomEnd,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            shown,
            enter = fadeIn(tween(140)) + scaleIn(spring(0.8f, 600f), initialScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 70.dp).width(250.dp)
                    .glass(RoundedCornerShape(22.dp), 20f).padding(vertical = 6.dp),
            ) {
                @Composable
                fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, action: () -> Unit) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = enabled) { onDismiss(); action() }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = KazeType.body, color = if (enabled) k.ink else k.ink35, modifier = Modifier.weight(1f))
                        Icon(icon, null, tint = if (enabled) k.ink else k.ink35, modifier = Modifier.size(20.dp))
                    }
                }
                item(Icons.Outlined.Translate, "Opciones de la página (AA)", enabled = !tab.isStart, action = a.onAa)
                item(Icons.AutoMirrored.Outlined.ArrowForwardIos, "Adelante", enabled = tab.canGoForward, action = a.onForward)
                item(Icons.Outlined.Refresh, "Recargar", enabled = !tab.isStart, action = a.onReload)
                item(Icons.Outlined.Home, "Página de inicio", action = a.onHome)
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(0.5.dp).background(k.separator))
                item(Icons.Outlined.Add, "Nueva pestaña", action = a.onNewTab)
                item(Icons.Outlined.GridView, "Pestañas ($tabCount)", action = a.onTabs)
                item(Icons.AutoMirrored.Outlined.MenuBook, "Favoritos e historial", action = a.onLibrary)
                item(Icons.Outlined.MoreHoriz, "Más opciones", action = a.onShare)
            }
        }
    }
}

/**
 * Tableta: como Safari en iPad/Mac con pestañas separadas. Arriba, la dirección centrada con los
 * controles en cápsulas a los lados ([espacios] [‹ | ›] … [⋯ + pestañas]); debajo, la fila de
 * pestañas de igual ancho (la activa resaltada). Con muchas pestañas, la fila se desliza.
 */
@Composable
private fun TabletBar(tab: Tab, space: Space?, tabCount: Int, a: ChromeActions, tabs: List<Tab>, onSelectTab: (Tab) -> Unit, onCloseTab: (Tab) -> Unit, modifier: Modifier) {
    val k = LocalKaze.current
    val list = if (tabs.any { it.id == tab.id }) tabs else listOf(tab)
    Column(
        modifier.fillMaxWidth().background(k.glass)
            .then(if (k.material) Modifier else Modifier.background(spaceTint(space))),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 10.dp)) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Como Safari: círculo de la barra lateral (favoritos, lectura e historial) y la píldora
                // del perfil, que aquí es el espacio de Zen, con su color, ícono y nombre.
                Capsule { BarIcon(Icons.AutoMirrored.Outlined.MenuBook, "Favoritos, lista de lectura e historial", onClick = a.onLibrary) }
                SpacePill(space, a.onSpaces)
                AnimatedVisibility(
                    tab.canGoBack || tab.canGoForward,
                    enter = fadeIn(tween(160)) + expandHorizontally(spring(0.85f, 500f)),
                    exit = fadeOut(tween(110)) + shrinkHorizontally(tween(160)),
                ) {
                    Capsule {
                        if (tab.canGoBack) BarIcon(Icons.AutoMirrored.Outlined.ArrowBackIos, "Atrás (mantén para ver el historial)", { a.onHistoryMenu(-1) }, a.onBack)
                        if (tab.canGoBack && tab.canGoForward) Box(Modifier.width(0.5.dp).height(18.dp).background(k.separator))
                        if (tab.canGoForward) BarIcon(Icons.AutoMirrored.Outlined.ArrowForwardIos, "Adelante (mantén para ver el historial)", { a.onHistoryMenu(1) }, a.onForward)
                    }
                }
            }
            // La dirección, centrada (deja sitio a las cápsulas de los lados).
            TabletAddress(tab, a, Modifier.align(Alignment.Center).width(minOf(520.dp, maxWidth - 420.dp).coerceAtLeast(200.dp)))
            Row(Modifier.align(Alignment.CenterEnd)) {
                Capsule {
                    BarIcon(Icons.Outlined.MoreHoriz, "Menú", onClick = a.onShare)
                    BarIcon(Icons.Outlined.Add, "Pestaña nueva", onClick = a.onNewTab)
                    TabsButton(tabCount, a.onTabs, a.onTabsLong, size = 40.dp)
                }
            }
        }
        // Fila de pestañas: todas del mismo ancho; si no caben (menos de 120 dp cada una), se desliza.
        if (list.size > 1) TabStrip(tab, list, onSelectTab, onCloseTab, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp))
    }
}

/** Cápsula que agrupa botones (como en Safari). */
@Composable
private fun Capsule(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.height(36.dp).shadow(4.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(50)).background(k.card).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Botón pequeño de la barra de tableta: ícono de 19 dp en un área de 40×36 dp. */
@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val k = LocalKaze.current
    Box(
        Modifier.size(40.dp, 36.dp).clip(RoundedCornerShape(50))
            .then(if (onLong != null) Modifier.tap(onClick, onLong) else Modifier.tap(onClick))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = k.accent, modifier = Modifier.size(19.dp)) }
}

/** Pestaña activa en tableta: la dirección, en una píldora de 36 dp (AA · sitio · recargar). */
@Composable
private fun TabletAddress(tab: Tab, a: ChromeActions, modifier: Modifier) {
    val k = LocalKaze.current
    val accent = k.accent
    val progress by animateFloatAsState(if (tab.loading) tab.progress / 100f else 0f, tween(200), label = "tp")
    Row(
        modifier.height(36.dp).shadow(3.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.12f)).clip(RoundedCornerShape(50)).background(k.card)
            .drawWithContent {
                drawContent()
                if (tab.loading) drawRect(accent, androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0.05f, 1f), 2.dp.toPx()))
            }
            .clickable(onClick = a.onAddress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("AA", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = a.onAa).padding(horizontal = 12.dp, vertical = 8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (tab.isSecure && !tab.isStart) {
                Icon(Icons.Outlined.Lock, null, tint = k.ink55, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                addressLabel(tab).ifBlank { "Buscar o escribir dirección" }, style = KazeType.subhead.copy(fontWeight = FontWeight.Medium),
                color = if (tab.isStart) k.ink55 else k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClick = if (tab.loading) a.onStop else a.onReload), contentAlignment = Alignment.Center) {
            Icon(if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh, if (tab.loading) "Detener" else "Recargar", tint = k.ink55, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Pestaña de la fila de tableta: ícono y título centrados y una ✕ para cerrarla.
 * La activa es una tarjeta clara con sombra suave; si está en un grupo, lleva una raya del color del grupo.
 */
@Composable
private fun TabChip(t: Tab, selected: Boolean, group: Color?, modifier: Modifier, onClose: () -> Unit, onClick: () -> Unit) {
    val k = LocalKaze.current
    val bg by androidx.compose.animation.animateColorAsState(if (selected) k.card else group?.copy(alpha = 0.16f) ?: Color.Transparent, tween(180), label = "chip")
    Box(
        modifier.fillMaxHeight()
            .then(if (selected) Modifier.shadow(2.dp, RoundedCornerShape(9.dp), ambientColor = Color.Black.copy(alpha = 0.1f)) else Modifier)
            .clip(RoundedCornerShape(9.dp)).background(bg).clickable(onClick = onClick)
            .semantics { contentDescription = t.displayTitle },
    ) {
        Row(Modifier.fillMaxSize().padding(start = 28.dp, end = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (t.isStart) Icon(Icons.Outlined.Home, null, tint = k.ink55, modifier = Modifier.size(14.dp))
            else FaviconTile(t.url, 16.dp, 11.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                if (t.isStart) "Inicio" else t.displayTitle.ifBlank { Urls.displayHost(t.url) },
                style = KazeType.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) k.ink else k.ink55, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // ✕ a la izquierda, como Safari (tamaño táctil de 28 dp).
        Box(
            Modifier.align(Alignment.CenterStart).size(28.dp).clip(CircleShape).clickable(onClick = onClose)
                .semantics { contentDescription = "Cerrar pestaña" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, null, tint = k.ink55, modifier = Modifier.size(12.dp)) }
        if (group != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp).height(2.5.dp).clip(RoundedCornerShape(50)).background(group))
        }
    }
}

/** Píldora del espacio (el "perfil" de Safari): color del espacio, su ícono, nombre y ⌄. */
@Composable
private fun SpacePill(space: Space?, onClick: () -> Unit) {
    val k = LocalKaze.current
    val c = space?.colors?.firstOrNull()?.let { Color(it) } ?: k.accent
    // Texto oscuro sobre colores claros y blanco sobre oscuros (siempre legible).
    val on = if (c.luminance() > 0.55f) Color(0xFF1D1D1F) else Color.White
    Row(
        Modifier.height(36.dp).shadow(3.dp, RoundedCornerShape(50), ambientColor = c.copy(alpha = 0.4f)).clip(RoundedCornerShape(50))
            .background(c).tap(onClick).padding(horizontal = 14.dp)
            .semantics { contentDescription = "Espacio ${space?.name.orEmpty()} (cambiar de espacio)" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (space != null) Text(space.emoji, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text(space?.name ?: "Espacios", style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold), color = on, maxLines = 1)
        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = on, modifier = Modifier.padding(start = 2.dp).size(16.dp))
    }
}


/**
 * Fila de pestañas (tableta y, si se activa, teléfono). Las pestañas de un mismo grupo van juntas,
 * detrás de una etiqueta con el color y nombre del grupo (como los grupos de Chrome y Safari);
 * tocar la etiqueta pliega o despliega el grupo. Cada pestaña del grupo lleva su color de fondo y una raya.
 * Mantener y arrastrar mueve una pestaña; soltarla encima de otra crea un grupo o la añade al suyo.
 */
@Composable
fun TabStrip(
    tab: Tab, tabs: List<Tab>, onSelectTab: (Tab) -> Unit, onCloseTab: (Tab) -> Unit, modifier: Modifier = Modifier,
    minTab: androidx.compose.ui.unit.Dp = 120.dp, maxTab: androidx.compose.ui.unit.Dp = 600.dp,
) {
    val store = LocalApp.current.store
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val folded = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    // Los miembros de cada grupo, juntos donde aparece el primero.
    val ordered = run {
        val seen = HashSet<String>()
        buildList { tabs.forEach { t -> if (t.folder.isEmpty()) add(t) else if (seen.add(t.folder)) addAll(tabs.filter { it.folder == t.folder }) } }
    }
    val visible = ordered.filter { it.folder.isEmpty() || folded[it.folder] != true }
    val groups = ordered.map { it.folder }.filter { it.isNotEmpty() }.distinct()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(30.dp)) {
        val n = visible.size.coerceAtLeast(1)
        val w = ((maxWidth - 4.dp * (n - 1) - 74.dp * groups.size) / n).coerceIn(minTab, maxTab)
        val step = with(density) { (w + 4.dp).toPx() }
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragX by remember { mutableFloatStateOf(0f) }
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(tab.id, n) {
            val i = visible.indexOfFirst { it.id == tab.id }
            if (i >= 0) state.animateScrollToItem(i)
        }
        androidx.compose.foundation.lazy.LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // La etiqueta sale aunque el grupo esté plegado (entonces sus pestañas se esconden tras ella).
            ordered.forEachIndexed { oi, t ->
                if (t.folder.isNotEmpty() && ordered.getOrNull(oi - 1)?.folder != t.folder) {
                    val name = t.folder
                    item(key = "g:$name") {
                        val c = folderColor(name)
                        val count = ordered.count { it.folder == name }
                        Row(
                            Modifier.animateItem().height(24.dp).clip(RoundedCornerShape(50)).background(c)
                                .tap { folded[name] = folded[name] != true }.padding(horizontal = 10.dp)
                                .semantics { contentDescription = "Grupo $name, $count pestañas (tocar para plegar o desplegar)" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                            if (folded[name] == true) Text("  $count", style = KazeType.footnote, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                val i = visible.indexOf(t)
                if (i >= 0) item(key = t.id) {
                    val dragging = dragId == t.id
                    val drag = Modifier.pointerInput(t.id, visible.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragId = t.id; dragX = 0f; haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                            onDragEnd = {
                                val slots = dragX / step
                                val r = kotlin.math.round(slots).toInt()
                                val target = visible.getOrNull(i + r)
                                if (target != null && target.id != t.id && t.kind == TabKind.NORMAL && target.kind == TabKind.NORMAL) {
                                    if (abs(slots - r) < 0.25f) {
                                        store.setFolder(listOf(target, t), target.folder.ifEmpty { store.newFolderName(t.spaceId) })
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                                    } else {
                                        val dest = visible.getOrNull(i + (if (slots > 0) kotlin.math.ceil(slots).toInt() else kotlin.math.floor(slots).toInt()))
                                        if (dest != null && dest.id != t.id) {
                                            // Al sacarla de su grupo hacia una pestaña suelta, deja el grupo.
                                            if (t.folder != dest.folder) store.setFolder(listOf(t), dest.folder)
                                            store.moveBefore(t, dest)
                                        }
                                    }
                                }
                                dragId = null; dragX = 0f
                            },
                            onDragCancel = { dragId = null; dragX = 0f },
                        ) { c, d -> c.consume(); dragX += d.x }
                    }
                    TabChip(
                        t, t.id == tab.id, t.folder.takeIf { it.isNotEmpty() }?.let { folderColor(it) },
                        (if (dragging) Modifier.zIndex(1f).offset { IntOffset(dragX.roundToInt(), 0) }.graphicsLayer { scaleX = 1.04f; scaleY = 1.04f } else Modifier.animateItem())
                            .width(w).then(drag),
                        onClose = { onCloseTab(t) },
                    ) { if (t.id != tab.id) onSelectTab(t) }
                }
            }
        }
    }
}
