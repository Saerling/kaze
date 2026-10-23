/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ViewList
import app.kaze.browser.ui.components.FaviconTile
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.data.Space
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.SpaceBackground
import app.kaze.browser.ui.components.ToolbarIcon
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.card
import app.kaze.browser.ui.theme.glass
import app.kaze.browser.ui.theme.liftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Vista de pestañas de Safari: cuadrícula de tarjetas con la captura de cada página,
 * fijadas arriba, y abajo [+] · grupo de pestañas · Listo.
 */
@Composable
fun TabsScreen(
    onOpen: (Tab) -> Unit,
    onNewTab: () -> Unit,
    onDone: () -> Unit,
    onGroups: () -> Unit,
    onTabMenu: (Tab) -> Unit,
    onClose: (Tab) -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val store = app.store
    val space = store.currentSpace ?: store.profileSpaces.first()
    var query by remember { mutableStateOf("") }
    val pinned = store.pinned(space.id)
    // Essentials (Zen): del perfil, visibles en todos los espacios. En la privada no se muestran.
    val essentials = if (space.id == Space.PRIVATE_ID) emptyList() else store.essentials()
    val spaces = store.visibleSpaces
    fun step(delta: Int) {
        val i = spaces.indexOfFirst { it.id == space.id }
        spaces.getOrNull(i + delta)?.let { store.selectSpace(it.id) }
    }
    val normal = store.normal(space.id).let { list ->
        if (query.isBlank()) list else list.filter { it.title.contains(query, true) || it.url.contains(query, true) }
    }
    // Cada espacio recuerda su propio desplazamiento: cambiar rápido entre espacios no deja la lista a medias.
    val grid = remember(space.id) { LazyGridState() }
    // Modos de vista: tarjetas (por defecto), compacta (3 columnas) y lista.
    val mode = app.settings.tabsView
    val cols = when (mode) { "list" -> 1; "compact" -> 3; else -> 2 }
    LaunchedEffect(space.id) {
        // Mostrar la pestaña actual sin tener que buscarla (solo si está más abajo de lo visible).
        val i = normal.indexOfFirst { it.id == store.selectedTabId }
        val header = 1 + (if (essentials.isNotEmpty()) 1 else 0) + (if (pinned.isNotEmpty()) 1 else 0)
        if (i >= 4) grid.scrollToItem(header + i)
    }

    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().pointerInput(spaces.size, space.id) {
            // Deslizar a los lados cambia de espacio, como en Zen.
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = { if (drag < -140) step(1) else if (drag > 140) step(-1) },
                onHorizontalDrag = { _, dx -> drag += dx },
            )
        },
    ) {
        SpaceBackground(if (space.id == Space.PRIVATE_ID) listOf(0xFF3A3A55.toInt()) else space.colors, strength = 0.35f)
        androidx.compose.runtime.key(space.id) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = grid,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
            horizontalArrangement = Arrangement.spacedBy(if (mode == "compact") 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (mode == "list") 8.dp else 18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).liftCard(RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = k.ink55, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Buscar pestañas", style = KazeType.body, color = k.ink55); inner() },
                    )
                    // Cambiar la vista: tarjetas → compacta → lista.
                    val (next, icon, label) = when (mode) {
                        "grid" -> Triple("compact", Icons.Outlined.Apps, "Vista compacta")
                        "compact" -> Triple("list", Icons.AutoMirrored.Outlined.ViewList, "Vista de lista")
                        else -> Triple("grid", Icons.Outlined.GridView, "Vista de tarjetas")
                    }
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable { app.settings.tabsView = next }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = k.accent, modifier = Modifier.size(19.dp)) }
                }
            }
            if (essentials.isNotEmpty() && query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "essentials") {
                    PinnedStrip(essentials, store.selectedTabId, onOpen, onTabMenu, essential = true)
                }
            }
            if (pinned.isNotEmpty() && query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "pinned") {
                    PinnedStrip(pinned, store.selectedTabId, onOpen, onTabMenu)
                }
            }
            items(normal, key = { it.id }) { t ->
                // Al cerrar o abrir pestañas, las demás se acomodan con animación.
                Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(0.85f, 380f, IntOffset.VisibilityThreshold), fadeOutSpec = tween(160))) {
                if (mode == "list") TabRow(
                    tab = t, selected = t.id == store.selectedTabId,
                    onOpen = { onOpen(t) }, onMenu = { onTabMenu(t) }, onClose = { onClose(t) },
                ) else TabCard(
                    compact = mode == "compact",
                    tab = t, selected = t.id == store.selectedTabId,
                    onOpen = { onOpen(t) }, onMenu = { onTabMenu(t) }, onClose = { onClose(t) },
                )
                }
            }
            if (normal.isEmpty() && pinned.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (query.isBlank()) "Sin pestañas" else "Sin resultados", style = KazeType.title2, color = k.ink55)
                        Spacer(Modifier.height(6.dp))
                        Text("Toca + para abrir una.", style = KazeType.subhead, color = k.ink55)
                    }
                }
            }
        }

        }

        // Selector de espacios (emojis) encima de la barra, como el de Zen.
        if (spaces.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 74.dp)
                    .glass(RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                spaces.forEach { s ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { store.selectSpace(s.id) }
                            .semantics { contentDescription = s.name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s.emoji, fontSize = 18.sp, modifier = Modifier.alpha(if (s.id == space.id) 1f else 0.4f))
                    }
                }
            }
        }

        // Barra inferior: + · grupo · Listo
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp).glass(RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(Icons.Outlined.Add, "Nueva pestaña", onClick = onNewTab)
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onGroups)
                    .semantics { contentDescription = "Grupos de pestañas" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(space.emoji + "  ", fontSize = 15.sp)
                Text(
                    if (space.id == Space.PRIVATE_ID) "Privada" else space.name,
                    style = KazeType.headline, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("  ${pinned.size + store.normal(space.id).size}", style = KazeType.subhead, color = k.ink55)
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = k.ink55, modifier = Modifier.size(18.dp))
            }
            Text(
                "Listo", style = KazeType.headline, color = k.accent,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDone).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

/** Fijadas del grupo: mosaicos pequeños arriba, como las pestañas fijadas de Safari. */
@Composable
private fun PinnedStrip(tabs: List<Tab>, selectedId: String?, onOpen: (Tab) -> Unit, onMenu: (Tab) -> Unit, essential: Boolean = false) {
    val k = LocalKaze.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                            .tap({ onOpen(t) }, { onMenu(t) }).semantics { contentDescription = t.displayTitle },
                        contentAlignment = Alignment.Center,
                    ) {
                        FaviconTile(t.pinnedUrl ?: t.url, 52.dp, 28.dp, selected = t.id == selectedId)
                        if (!essential) {
                            Icon(
                                Icons.Outlined.PushPin, null, tint = k.ink35,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(11.dp),
                            )
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TabCard(compact: Boolean = false, tab: Tab, selected: Boolean, onOpen: () -> Unit, onMenu: () -> Unit, onClose: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    var thumb by remember(tab.id) { mutableStateOf<Bitmap?>(app.thumbnails.cached(tab.id)) }
    val version = app.thumbnails.version.intValue
    LaunchedEffect(tab.id, version) {
        thumb = withContext(Dispatchers.IO) { app.thumbnails.load(tab.id) }
    }

    Column(
        Modifier
            .offset { IntOffset(dx.value.roundToInt(), 0) }
            .alpha(1f - (abs(dx.value) / 600f).coerceIn(0f, 0.7f))
            // Deslizar a un lado cierra la pestaña.
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dx.value) > 160) {
                            scope.launch { dx.animateTo(if (dx.value > 0) 900f else -900f); onClose() }
                        } else scope.launch { dx.animateTo(0f) }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f) } },
                ) { change, d -> change.consume(); scope.launch { dx.snapTo(dx.value + d) } }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (compact) 0.68f else 0.74f)
                .card(RoundedCornerShape(14.dp))
                .then(if (selected) Modifier.border(3.dp, k.accent, RoundedCornerShape(14.dp)) else Modifier.border(0.5.dp, k.rim, RoundedCornerShape(14.dp)))
                .tap(onOpen, onMenu),
        ) {
            val b = thumb
            when {
                b != null && !tab.isStart -> Image(
                    b.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                )
                else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (tab.isStart) {
                        Icon(Icons.Outlined.Home, null, tint = k.accent, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Página de inicio", style = KazeType.footnote, color = k.ink55)
                    } else {
                        FaviconTile(tab.url, 56.dp, 32.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(Urls.displayHost(tab.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(26.dp).clip(CircleShape)
                    .background(k.paper.copy(alpha = 0.85f)).clickable(onClick = onClose)
                    .semantics { contentDescription = "Cerrar pestaña" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, null, tint = k.ink70, modifier = Modifier.size(15.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!tab.isStart) {
                Favicon(tab.url, 14.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                tab.displayTitle, style = KazeType.footnote.copy(fontWeight = FontWeight.Medium), color = k.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Fila de la vista de lista: ícono, título, sitio y cerrar. */
@Composable
private fun TabRow(tab: Tab, selected: Boolean, onOpen: () -> Unit, onMenu: () -> Unit, onClose: () -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).card(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(2.dp, k.accent, RoundedCornerShape(14.dp)) else Modifier)
            .tap(onOpen, onMenu).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab.isStart) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Home, null, tint = k.accent, modifier = Modifier.size(24.dp))
            }
        } else FaviconTile(tab.url, 38.dp, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.displayTitle, style = KazeType.body.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!tab.isStart) Text(Urls.displayHost(tab.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = "Cerrar pestaña" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, null, tint = k.ink55, modifier = Modifier.size(17.dp)) }
    }
}
