/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
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
    onHistory: () -> Unit = {},
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
    // En tableta caben más columnas.
    val tablet = app.ui.tablet
    val cols = when (mode) { "list" -> if (tablet) 2 else 1; "compact" -> if (tablet) 6 else 3; else -> if (tablet) 4 else 2 }
    LaunchedEffect(space.id) {
        // Mostrar la pestaña actual sin tener que buscarla (solo si está más abajo de lo visible).
        val i = normal.indexOfFirst { it.id == store.selectedTabId }
        val header = 1 + (if (essentials.isNotEmpty()) 1 else 0) + (if (pinned.isNotEmpty()) 1 else 0)
        if (i >= 4) grid.scrollToItem(header + i)
    }

    // ── Arrastrar para ordenar y soltar encima para hacer carpetas ──
    val haptics = LocalHapticFeedback.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverKey by remember { mutableStateOf<String?>(null) }
    var mergeReady by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hoverKey) {
        mergeReady = false
        if (hoverKey != null && hoverKey != dragId) {
            delay(450)
            mergeReady = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    fun keyAt(p: Offset): String? = grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        p.x >= info.offset.x && p.x <= info.offset.x + info.size.width &&
            p.y >= info.offset.y && p.y <= info.offset.y + info.size.height
    }?.key as? String
    fun dragModifier(t: Tab): Modifier = Modifier.pointerInput(t.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { local ->
                val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == t.id }
                dragStart = (info?.offset?.let { Offset(it.x.toFloat(), it.y.toFloat()) } ?: Offset.Zero) + local
                dragOffset = Offset.Zero
                dragId = t.id
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, d ->
                change.consume()
                dragOffset += d
                hoverKey = keyAt(dragStart + dragOffset)?.takeIf { it != t.id && (it.startsWith("f:") || store.tabById(it) != null) }
            },
            onDragEnd = {
                val target = hoverKey
                when {
                    // Casi no se movió: es una pulsación larga normal → menú de la pestaña.
                    dragOffset.getDistance() < 24f -> onTabMenu(t)
                    target == null -> Unit
                    target.startsWith("f:") && mergeReady -> store.setFolder(listOf(t), target.removePrefix("f:"))
                    mergeReady -> store.tabById(target)?.let { other ->
                        val name = other.folder.ifEmpty { store.newFolderName(space.id) }
                        store.setFolder(listOf(other, t), name)
                    }
                    target.startsWith("f:") -> store.folderTabs(space.id, target.removePrefix("f:")).firstOrNull()?.let { store.moveBefore(t, it) }
                    else -> store.tabById(target)?.let { store.moveBefore(t, it) }
                }
                dragId = null; hoverKey = null; mergeReady = false; dragOffset = Offset.Zero
            },
            onDragCancel = { dragId = null; hoverKey = null; mergeReady = false; dragOffset = Offset.Zero },
        )
    }
    @Composable
    fun Modifier.dragVisual(key: String): Modifier {
        val target by animateFloatAsState(if (hoverKey == key && mergeReady) 0.9f else 1f, spring(0.6f, 500f), label = "merge")
        return if (dragId == key) this.zIndex(10f).graphicsLayer {
            translationX = dragOffset.x; translationY = dragOffset.y
            scaleX = 1.07f; scaleY = 1.07f; shadowElevation = 28f; alpha = 0.95f
        } else this.graphicsLayer { scaleX = target; scaleY = target }
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
            // Pestañas sueltas y carpetas (una carpeta ocupa el lugar de su primera pestaña).
            val entries: List<Any> = if (query.isNotBlank()) normal else {
                val seen = mutableSetOf<String>()
                normal.mapNotNull { t ->
                    when {
                        t.folder.isEmpty() -> t
                        seen.add(t.folder) -> t.folder
                        else -> null
                    }
                }
            }
            items(entries, key = { e -> if (e is Tab) e.id else "f:$e" }) { e ->
                // Al cerrar, abrir o reordenar pestañas, las demás se acomodan con animación.
                val key = if (e is Tab) e.id else "f:$e"
                Box(
                    Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(0.82f, 360f, IntOffset.VisibilityThreshold), fadeOutSpec = tween(160))
                        .dragVisual(key),
                ) {
                    if (e is Tab) {
                        val t = e
                        if (mode == "list") TabRow(
                            tab = t, selected = t.id == store.selectedTabId,
                            onOpen = { onOpen(t) }, onMenu = { onTabMenu(t) }, onClose = { onClose(t) }, drag = dragModifier(t),
                        ) else TabCard(
                            compact = mode == "compact",
                            tab = t, selected = t.id == store.selectedTabId,
                            onOpen = { onOpen(t) }, onMenu = { onTabMenu(t) }, onClose = { onClose(t) }, drag = dragModifier(t),
                            highlight = hoverKey == key && mergeReady,
                        )
                    } else {
                        val name = e as String
                        FolderCard(name, store.folderTabs(space.id, name), list = mode == "list", highlight = hoverKey == key && mergeReady) { openFolder = name }
                    }
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
            ToolbarIcon(Icons.Outlined.History, "Historial", onClick = onHistory)
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

        // Carpeta abierta: se abre desde el centro con sus pestañas.
        AnimatedVisibility(
            visible = openFolder != null,
            enter = fadeIn(tween(180)) + scaleIn(spring(0.8f, 420f), initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.9f),
        ) {
            var shown by remember { mutableStateOf(openFolder.orEmpty()) }
            LaunchedEffect(openFolder) { openFolder?.let { shown = it } }
            FolderView(
                name = shown,
                tabs = store.folderTabs(space.id, shown),
                selectedId = store.selectedTabId,
                onOpen = { openFolder = null; onOpen(it) },
                onClose = onClose,
                onRename = { new -> store.renameFolder(space.id, shown, new); shown = new.trim().ifEmpty { shown }; openFolder = shown },
                onUngroup = { store.ungroup(space.id, shown); openFolder = null },
                onDismiss = { openFolder = null },
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
private fun TabCard(
    compact: Boolean = false, tab: Tab, selected: Boolean, onOpen: () -> Unit, onMenu: () -> Unit, onClose: () -> Unit,
    drag: Modifier = Modifier, highlight: Boolean = false,
) {
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
                .card(RoundedCornerShape(k.corner.dp))
                .then(if (selected) Modifier.border(3.dp, k.accent, RoundedCornerShape(k.corner.dp)) else Modifier.border(0.5.dp, k.rim, RoundedCornerShape(k.corner.dp)))
                .then(if (highlight) Modifier.border(3.dp, k.accent, RoundedCornerShape(k.corner.dp)) else Modifier)
                .then(drag)
                .tap(onOpen),
        ) {
            val b = thumb
            when {
                b != null && !tab.isStart -> Image(
                    b.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(k.corner.dp)),
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
private fun TabRow(tab: Tab, selected: Boolean, onOpen: () -> Unit, onMenu: () -> Unit, onClose: () -> Unit, drag: Modifier = Modifier) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).card(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(2.dp, k.accent, RoundedCornerShape(14.dp)) else Modifier)
            .then(drag).tap(onOpen).padding(horizontal = 10.dp, vertical = 8.dp),
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

/** Carpeta en la vista de pestañas: mosaico con las miniaturas de hasta 4 pestañas. */
@Composable
private fun FolderCard(name: String, tabs: List<Tab>, list: Boolean, highlight: Boolean, onOpen: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val color = folderColor(name)
    if (list) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).card(RoundedCornerShape(14.dp))
                .then(if (highlight) Modifier.border(2.dp, k.accent, RoundedCornerShape(14.dp)) else Modifier)
                .tap(onOpen).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Folder, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(name, style = KazeType.body.copy(fontWeight = FontWeight.SemiBold), color = k.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${tabs.size}", style = KazeType.footnote, color = k.ink55)
        }
        return
    }
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.74f).clip(RoundedCornerShape(k.corner.dp))
                .background(color.copy(alpha = if (k.isDark) 0.20f else 0.16f))
                .border(if (highlight) 3.dp else 1.dp, if (highlight) k.accent else color.copy(alpha = 0.35f), RoundedCornerShape(k.corner.dp))
                .tap(onOpen).padding(8.dp),
        ) {
            // Cuadrícula 2×2 de miniaturas (o íconos si la pestaña está dormida).
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.take(4).chunked(2).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { t ->
                            val thumb = remember(t.id, app.thumbnails.version.intValue) { app.thumbnails.cached(t.id) }
                            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp)).background(k.card), contentAlignment = Alignment.Center) {
                                if (thumb != null && !t.isStart) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                                else Favicon(t.url, 22.dp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  ${tabs.size}", style = KazeType.footnote, color = k.ink55)
        }
    }
}

/** Color estable para cada carpeta, a partir de su nombre. */
private fun folderColor(name: String): Color {
    val palette = listOf(0xFF7C6CF6, 0xFF3BA7F5, 0xFF2DB89A, 0xFFF0A23B, 0xFFE8618C, 0xFF9B7CF6, 0xFF5AC8FA)
    return Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
}

/** Carpeta abierta: nombre editable, sus pestañas y "Desagrupar". */
@Composable
private fun FolderView(
    name: String, tabs: List<Tab>, selectedId: String?, onOpen: (Tab) -> Unit, onClose: (Tab) -> Unit,
    onRename: (String) -> Unit, onUngroup: () -> Unit, onDismiss: () -> Unit,
) {
    val k = LocalKaze.current
    var text by remember(name) { mutableStateOf(name) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (text != name) onRename(text); onDismiss()
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth().heightIn(max = 620.dp)
                .clip(RoundedCornerShape(30.dp)).background(k.paper)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = folderColor(name), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = KazeType.title2.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
                    keyboardActions = KeyboardActions(onDone = { onRename(text) }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Desagrupar", style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold), color = k.accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onUngroup).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(tabs, key = { it.id }) { t ->
                    Box(Modifier.animateItem()) {
                        TabCard(tab = t, selected = t.id == selectedId, onOpen = { onOpen(t) }, onMenu = {}, onClose = { onClose(t) })
                    }
                }
            }
        }
    }
}
