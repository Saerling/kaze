/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import app.kaze.browser.ui.mochy.press3D
import androidx.compose.ui.platform.testTag
import app.kaze.browser.ui.mochy.Chip
import app.kaze.browser.ui.mochy.ChipColors
import app.kaze.browser.ui.mochy.ChipsRow
import app.kaze.browser.ui.mochy.MochyChip
import app.kaze.browser.data.Topics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import app.kaze.browser.data.TabKind
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.animation.core.animateFloat
import app.kaze.browser.ui.mochy.ContainerCloseEasing
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.selected
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
import androidx.compose.animation.animateContentSize
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Al abrir la vista, las tarjetas entran escalonadas (35 ms entre cada una); después, sin retrasos al desplazar.
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); opening = false }
    val k = LocalKaze.current
    val store = app.store
    val space = store.currentSpace ?: store.profileSpaces.first()
    var query by remember { mutableStateOf("") }
    val spaces = store.visibleSpaces
    fun step(delta: Int) {
        val i = spaces.indexOfFirst { it.id == space.id }
        spaces.getOrNull(i + delta)?.let { store.selectSpace(it.id) }
    }
    // Tema elegido en cada espacio (como Safari en iOS 27): cada espacio recuerda el suyo.
    val topicKeys = remember { androidx.compose.runtime.mutableStateMapOf<String, String?>() }
    // Modos de vista: tarjetas (por defecto), compacta (3 columnas) y lista.
    val mode = app.settings.tabsView
    // En tableta caben más columnas.
    val tablet = app.ui.tablet
    val cols = when (mode) { "list" -> if (tablet) 2 else 1; "compact" -> if (tablet) 6 else 3; else -> if (tablet) 4 else 2 }

    // ── Arrastrar para ordenar y soltar encima para hacer carpetas ──
    val haptics = LocalHapticFeedback.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverKey by remember { mutableStateOf<String?>(null) }
    var mergeReady by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    // Centro de cada carpeta en la pantalla: la carpeta abierta crece desde ahí y vuelve ahí al cerrarse.
    val folderCenters = remember { HashMap<String, Offset>() }
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    LaunchedEffect(hoverKey) {
        mergeReady = false
        if (hoverKey != null && hoverKey != dragId) {
            delay(450)
            mergeReady = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    fun keyAt(grid: LazyGridState, p: Offset): String? = grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        p.x >= info.offset.x && p.x <= info.offset.x + info.size.width &&
            p.y >= info.offset.y && p.y <= info.offset.y + info.size.height
    }?.key as? String
    fun dragModifier(t: Tab, grid: LazyGridState): Modifier = Modifier.pointerInput(t.id) {
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
                hoverKey = keyAt(grid, dragStart + dragOffset)?.takeIf { it != t.id && (it.startsWith("f:") || store.tabById(it) != null) }
            },
            onDragEnd = {
                val target = hoverKey
                when {
                    // Casi no se movió: es una pulsación larga normal → menú de la pestaña.
                    dragOffset.getDistance() < 24f -> onTabMenu(t)
                    target == null -> Unit
                    target.startsWith("f:") && mergeReady -> store.setFolder(listOf(t), target.removePrefix("f:"))
                    mergeReady -> store.tabById(target)?.let { other ->
                        val name = other.folder.ifEmpty { store.newFolderName(t.spaceId) }
                        store.setFolder(listOf(other, t), name)
                    }
                    target.startsWith("f:") -> store.folderTabs(t.spaceId, target.removePrefix("f:")).firstOrNull()?.let { store.moveBefore(t, it) }
                    else -> store.tabById(target)?.let { store.moveBefore(t, it) }
                }
                dragId = null; hoverKey = null; mergeReady = false; dragOffset = Offset.Zero
            },
            onDragCancel = { dragId = null; hoverKey = null; mergeReady = false; dragOffset = Offset.Zero },
        )
    }
    @Composable
    fun Modifier.dragVisual(key: String): Modifier {
        val target by animateFloatAsState(if (hoverKey == key && mergeReady) 0.9f else 1f, spring(0.85f, 500f), label = "merge")
        return if (dragId == key) this.zIndex(10f).graphicsLayer {
            translationX = dragOffset.x; translationY = dragOffset.y
            scaleX = 1.07f; scaleY = 1.07f; shadowElevation = 28f; alpha = 0.95f
        } else this.graphicsLayer { scaleX = target; scaleY = target }
    }

    /** La cuadrícula de un espacio (todo sale de [sp]: al cambiar de espacio, la que se va sigue mostrando el suyo). */
    @Composable
    fun SpaceGrid(sp: Space) {
        val pinned = store.pinned(sp.id)
        // Essentials (Zen): del perfil, visibles en todos los espacios. En la privada no se muestran.
        val essentials = if (sp.id == Space.PRIVATE_ID) emptyList() else store.essentials()
        val allNormal = store.normal(sp.id)
        // Temas (como Safari en iOS 27): píldoras "Todas" + grupos, temas y sitios detectados.
        // Se recalcula solo cuando cambian las direcciones, títulos o grupos de las pestañas.
        val topicSig = allNormal.joinToString("\u0001") { it.id + it.url + it.title + it.folder }
        val topicFilters = remember(topicSig) { Topics.filters(allNormal) }
        val topicKey = topicKeys[sp.id]
        val topic = topicFilters.firstOrNull { it.key == topicKey }
        val normal = allNormal.let { list ->
            if (topic == null) list else list.filter { it.id in topic.tabIds }
        }.let { list ->
            if (query.isBlank()) list else list.filter { it.title.contains(query, true) || it.url.contains(query, true) }
        }
        val flat = query.isNotBlank() || topic != null
        fun groupByTopic() {
            val groups = Topics.groups(allNormal)
            groups.forEach { (name, list) -> store.setFolder(list, name) }
            if (groups.isNotEmpty()) { topicKeys.remove(sp.id); haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
        }
        val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        LaunchedEffect(Unit) {
            // Mostrar la pestaña actual sin tener que buscarla (solo si está más abajo de lo visible).
            val i = normal.indexOfFirst { it.id == store.selectedTabId }
            val header = 1 + (if (topicFilters.isNotEmpty()) 1 else 0) + (if (essentials.isNotEmpty()) 1 else 0) + (if (pinned.isNotEmpty()) 1 else 0)
            if (i >= 4) grid.scrollToItem(header + i)
        }
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
                    // Organizar pestañas (como Safari en iPadOS 27): agrupa en carpetas las del mismo sitio.
                    // ✦ abre un menú: por sitio (lo de antes) o por tema (Compras, Video, Noticias…).
                    var organizeMenu by remember { mutableStateOf(false) }
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable { organizeMenu = true }
                            .semantics { contentDescription = "Organizar pestañas" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = k.accent, modifier = Modifier.size(19.dp))
                        androidx.compose.material3.DropdownMenu(organizeMenu, { organizeMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Crear grupos por tema") },
                                onClick = { organizeMenu = false; groupByTopic() },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Agrupar por sitio") },
                                onClick = {
                                    organizeMenu = false
                                    if (store.organize(sp.id) > 0) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                },
                            )
                        }
                    }
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
            if (topicFilters.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "topics") {
                    TopicChips(topicFilters, topicKey, allNormal.count { !it.isStart }, onPick = { topicKeys[sp.id] = it }, onGroup = ::groupByTopic)
                }
            }
            if (essentials.isNotEmpty() && query.isBlank() && topic == null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "essentials") {
                    Column { PinnedStrip(essentials, store.selectedTabId, onOpen, onTabMenu, essential = true) }
                }
            }
            if (pinned.isNotEmpty() && query.isBlank() && topic == null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "pinned") {
                    Column(Modifier.zIndex(1f)) { PinnedStrip(pinned, store.selectedTabId, onOpen, onTabMenu) }
                }
            }
            // Pestañas sueltas y carpetas (una carpeta ocupa el lugar de su primera pestaña).
            // Con búsqueda o tema activo se ven las pestañas sueltas (también las de dentro de grupos).
            val entries: List<Any> = if (flat) normal else {
                val seen = mutableSetOf<String>()
                normal.mapNotNull { t ->
                    when {
                        t.folder.isEmpty() -> t
                        seen.add(t.folder) -> t.folder
                        else -> null
                    }
                }
            }
            itemsIndexed(entries, key = { _, e -> if (e is Tab) e.id else "f:$e" }) { index, e ->
                // Al cerrar, abrir o reordenar pestañas, las demás se acomodan con animación.
                val key = if (e is Tab) e.id else "f:$e"
                Box(
                    Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(0.85f, 360f, IntOffset.VisibilityThreshold), fadeOutSpec = tween(160))
                        .staggerIn(index, opening)
                        .dragVisual(key),
                ) {
                    if (e is Tab) {
                        val t = e
                        if (mode == "list") TabRow(
                            tab = t, selected = t.id == store.selectedTabId,
                            onOpen = { onOpen(t) }, onMenu = { onTabMenu(t) }, onClose = { onClose(t) }, drag = dragModifier(t, grid),
                        ) else TabCard(
                            compact = mode == "compact",
                            tab = t, selected = t.id == store.selectedTabId,
                            onOpen = { onOpen(t) }, onMenu = { onTabMenu(t) }, onClose = { onClose(t) }, drag = dragModifier(t, grid),
                            highlight = hoverKey == key && mergeReady,
                        )
                    } else {
                        val name = e as String
                        FolderCard(
                            name, store.folderTabs(sp.id, name), list = mode == "list", highlight = hoverKey == key && mergeReady,
                            modifier = Modifier.onGloballyPositioned { folderCenters[name] = it.boundsInRoot().center },
                            onPin = { pinGroup(store, app, sp.id, name) },
                            onUngroup = { store.ungroup(sp.id, name) },
                        ) { openFolder = name }
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

    // Deslizar a los lados cambia de espacio, como en Zen. Mientras se desliza, la cuadrícula sigue un poco al
    // dedo (con resistencia) y al soltar vuelve con un resorte: se nota que se puede deslizar, también en el borde.
    var drag by remember { mutableFloatStateOf(0f) }
    val follow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val maxFollow = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { rootSize = it.size }.pointerInput(spaces.size, space.id) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    if (drag < -140) step(1) else if (drag > 140) step(-1)
                    scope.launch { follow.animateTo(0f, spring(0.85f, 420f)) }
                },
                onDragCancel = { scope.launch { follow.animateTo(0f, spring(0.85f, 420f)) } },
                onHorizontalDrag = { _, dx ->
                    drag += dx
                    scope.launch { follow.snapTo((drag * 0.25f).coerceIn(-maxFollow, maxFollow)) }
                },
            )
        },
    ) {
        SpaceBackground(if (space.id == Space.PRIVATE_ID) listOf(0xFF3A3A55.toInt()) else space.colors, strength = 0.35f)
        // Cambio de espacio: la cuadrícula nueva entra desde el lado hacia el que vas y la anterior sale por el otro
        // (un quinto del ancho, con fundido): se entiende la dirección sin cruzar la pantalla entera.
        androidx.compose.animation.AnimatedContent(
            targetState = space.id,
            transitionSpec = {
                val from = spaces.indexOfFirst { it.id == initialState }
                val to = spaces.indexOfFirst { it.id == targetState }
                val dir = if (to >= from) 1 else -1
                (androidx.compose.animation.slideInHorizontally(spring(0.9f, 380f, IntOffset.VisibilityThreshold)) { w -> dir * w / 5 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(spring(1f, 500f, IntOffset.VisibilityThreshold)) { w -> -dir * w / 5 } + fadeOut(tween(120)))
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { translationX = follow.value },
            label = "espacio",
        ) { sid -> SpaceGrid(store.spaceById(sid) ?: space) }

        // Selector de espacios (emojis) encima de la barra, como el de Zen. Con muchos espacios se desliza a los
        // lados (con los bordes desvanecidos donde hay más) y siempre se acerca al espacio activo.
        if (spaces.size > 1) {
            SpaceSwitcher(spaces, space.id, onPick = { store.selectSpace(it) }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 74.dp, start = 24.dp, end = 24.dp))
        }

        // Barra inferior: + · grupo · Listo
        // Como Safari: botones redondos flotantes (+ e historial), la píldora del grupo al centro y ✓ para volver.
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(50.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                ToolbarIcon(Icons.Outlined.Add, "Nueva pestaña", onClick = onNewTab)
            }
            Box(Modifier.size(50.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                ToolbarIcon(Icons.Outlined.History, "Historial", onClick = onHistory)
            }
            Row(
                Modifier.weight(1f).height(50.dp).glass(RoundedCornerShape(50)).clickable(onClick = onGroups)
                    .semantics { contentDescription = "Grupos de pestañas" }.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // El nombre del espacio cambia con un deslizamiento corto en la misma dirección que la cuadrícula.
                androidx.compose.animation.AnimatedContent(
                    targetState = space,
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 40)) + androidx.compose.animation.slideInVertically(spring(0.9f, 500f, IntOffset.VisibilityThreshold)) { it / 2 }) togetherWith
                            (fadeOut(tween(100)) + androidx.compose.animation.slideOutVertically(spring(1f, 700f, IntOffset.VisibilityThreshold)) { -it / 2 })
                    },
                    contentKey = { it.id },
                    modifier = Modifier.weight(1f, fill = false),
                    label = "nombreEspacio",
                ) { sp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sp.emoji + "  ", fontSize = 15.sp)
                        Text(
                            if (sp.id == Space.PRIVATE_ID) "Privada" else sp.name,
                            style = KazeType.headline, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("  ${store.pinned(sp.id).size + store.normal(sp.id).size}", style = KazeType.subhead, color = k.ink55, maxLines = 1)
                    }
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = k.ink55, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(k.accent).clickable(onClick = onDone)
                    .semantics { contentDescription = "Listo" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        }

        // Carpeta abierta: el velo se funde y la carpeta crece desde su tarjeta (y vuelve a ella al cerrarse).
        AnimatedVisibility(
            visible = openFolder != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(220, delayMillis = 40)),
        ) {
            var shown by remember { mutableStateOf(openFolder.orEmpty()) }
            LaunchedEffect(openFolder) { openFolder?.let { shown = it } }
            val p by transition.animateFloat(
                transitionSpec = {
                    if (targetState == androidx.compose.animation.EnterExitState.Visible) spring(0.86f, 380f, visibilityThreshold = 0.001f)
                    else tween(260, easing = ContainerCloseEasing)
                },
                label = "carpeta",
            ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            val center = Offset(rootSize.width / 2f, rootSize.height / 2f)
            val from = folderCenters[shown] ?: center
            FolderView(
                name = shown,
                tabs = store.folderTabs(space.id, shown),
                selectedId = store.selectedTabId,
                onOpen = { openFolder = null; onOpen(it) },
                onClose = onClose,
                onRename = { new -> store.renameFolder(space.id, shown, new); val n = new.trim().ifEmpty { shown }; folderCenters[shown]?.let { folderCenters[n] = it }; shown = n; openFolder = shown },
                onUngroup = { store.ungroup(space.id, shown); openFolder = null },
                onPin = { openFolder = null; pinGroup(store, app, space.id, shown) },
                onDismiss = { openFolder = null },
                panel = Modifier.graphicsLayer {
                    val q = p.coerceIn(0f, 1.2f)
                    val s = 0.3f + 0.7f * q
                    scaleX = s; scaleY = s
                    translationX = (from.x - center.x) * (1f - q)
                    translationY = (from.y - center.y) * (1f - q)
                    alpha = (q * 2.5f).coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** Fija todas las pestañas de un grupo de normales (quedan como grupo de fijadas, la carpeta de Zen). */
private fun pinGroup(store: app.kaze.browser.data.Store, app: app.kaze.browser.Kaze, spaceId: String, name: String) {
    if (store.pinGroup(spaceId, name).isNotEmpty()) app.ui.show("Grupo «$name» fijado")
}

private fun unpinGroup(store: app.kaze.browser.data.Store, app: app.kaze.browser.Kaze, spaceId: String, name: String) {
    if (store.unpinGroup(spaceId, name).isNotEmpty()) app.ui.show("Grupo «$name» desfijado")
}

/**
 * Menú de un grupo (mantener la etiqueta o la carpeta): fijar o desfijar el grupo entero, desagrupar y,
 * en la fila de pestañas, plegarlo o desplegarlo. Fijar conserva el grupo, así Zen lo ve como carpeta.
 */
@Composable
internal fun GroupMenu(
    expanded: Boolean, pinned: Boolean, onDismiss: () -> Unit, onPinToggle: () -> Unit, onUngroup: () -> Unit,
    foldLabel: String? = null, onFold: () -> Unit = {},
) {
    val k = LocalKaze.current
    androidx.compose.material3.DropdownMenu(expanded, onDismiss) {
        if (foldLabel != null) androidx.compose.material3.DropdownMenuItem(
            text = { Text(foldLabel) }, onClick = { onDismiss(); onFold() },
            leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null, tint = k.ink70) },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(if (pinned) "Desfijar grupo" else "Fijar grupo") }, onClick = { onDismiss(); onPinToggle() },
            leadingIcon = { Icon(Icons.Outlined.PushPin, null, tint = k.ink70) },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Desagrupar") }, onClick = { onDismiss(); onUngroup() },
            leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = k.ink70) },
        )
    }
}

/**
 * Selector de espacios: emojis en una cápsula de vidrio. Si no caben, se desliza a los lados; los bordes se
 * desvanecen solo donde hay más espacios y, al cambiar de espacio, la fila se desplaza hasta dejarlo centrado.
 */
@Composable
private fun SpaceSwitcher(spaces: List<Space>, currentId: String, onPick: (String) -> Unit, modifier: Modifier) {
    val k = LocalKaze.current
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val active = spaces.indexOfFirst { it.id == currentId }
    LaunchedEffect(active, spaces.size) {
        if (active < 0) return@LaunchedEffect
        val vp = state.layoutInfo.viewportSize.width
        val item = with(density) { 38.dp.roundToPx() }
        state.animateScrollToItem(active, -(vp / 2 - item / 2).coerceAtLeast(0))
    }
    Box(modifier.glass(RoundedCornerShape(50))) {
        androidx.compose.foundation.lazy.LazyRow(
            state = state,
            modifier = Modifier
                .fadingEdges(state)
                .testTag("espacios"),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(spaces.size, key = { spaces[it].id }) { i ->
                val s = spaces[i]
                val on = s.id == currentId
                val bg by androidx.compose.animation.animateColorAsState(if (on) k.ink.copy(alpha = if (k.isDark) 0.16f else 0.08f) else Color.Transparent, spring(stiffness = 500f), label = "espacioFondo")
                val a by animateFloatAsState(if (on) 1f else 0.45f, spring(stiffness = 500f), label = "espacioAlfa")
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(bg).clickable { onPick(s.id) }
                        .semantics { contentDescription = s.name; selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.emoji, fontSize = 18.sp, modifier = Modifier.graphicsLayer { alpha = a })
                }
            }
        }
    }
}

/**
 * Fila de píldoras de temas: "Todas" + grupos, temas y sitios. La elegida se rellena con el
 * color de acento; la cuadrícula de abajo se filtra y las tarjetas se acomodan con resorte.
 */
@Composable
private fun TopicChips(
    filters: List<Topics.Filter>,
    selected: String?,
    total: Int,
    onPick: (String?) -> Unit,
    onGroup: () -> Unit,
) {
    val k = LocalKaze.current
    // Píldoras de ChipsRow de Mochy (ui/mochy/ChipsRow.kt): la elegida se redondea con un resorte.
    val colors = ChipColors(container = k.lift, label = k.ink, selectedContainer = k.accent, selectedLabel = Color.White)
    val style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold)
    val chips = listOf(Chip<String?>(null, "Todas", total)) +
        filters.map { f -> Chip<String?>(f.key, "${f.emoji} ${f.label}", f.tabIds.size, description = "Tema ${f.label}") }
    ChipsRow(
        chips = chips,
        currentValue = selected,
        onValueUpdate = { key -> onPick(if (key != null && key == selected) null else key) },
        colors = colors,
        textStyle = style,
        modifier = Modifier.testTag("temas"),
    ) {
        item(key = "group") {
            MochyChip("✦ Crear grupos por tema", selected = false, colors = colors, textStyle = style, role = androidx.compose.ui.semantics.Role.Button, onClick = onGroup)
        }
    }
}

/** Fijadas del grupo: mosaicos pequeños arriba, como las pestañas fijadas de Safari. */
@Composable
private fun PinnedStrip(tabs: List<Tab>, selectedId: String?, onOpen: (Tab) -> Unit, onMenu: (Tab) -> Unit, essential: Boolean = false) {
    val k = LocalKaze.current
    Text(
        if (essential) "Essentials" else "Fijadas", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink55,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    if (!essential) {
        PinnedGroups(tabs, selectedId, onOpen, onMenu)
        return
    }
    // Columnas según el ancho (4 en teléfono, más en tableta): los íconos no quedan perdidos en columnas enormes.
    androidx.compose.foundation.layout.BoxWithConstraints {
    val cols = maxOf(4, ((maxWidth + 8.dp) / 76.dp).toInt())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.chunked(cols).forEach { row ->
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
                                modifier = Modifier.align(Alignment.Center).offset(24.dp, (-24).dp).size(11.dp),
                            )
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }
}

/**
 * Fijadas del espacio: píldoras con ícono y nombre (como la lista de Zen). Las que están en un grupo van
 * dentro de su tarjeta de color, que se pliega o despliega al tocar la etiqueta (son las carpetas de Zen).
 *
 * Mantener y arrastrar una fijada: encima de otra fijada (un momento, hasta que vibra) crea un grupo con las
 * dos o la mete en el grupo de la otra; encima de un grupo la añade; fuera de los grupos la saca del suyo.
 * Mantener sin mover abre su menú. Funciona igual en las tres vistas (tarjetas, compacta y lista).
 */
@Composable
private fun PinnedGroups(tabs: List<Tab>, selectedId: String?, onOpen: (Tab) -> Unit, onMenu: (Tab) -> Unit) {
    val k = LocalKaze.current
    val store = LocalApp.current.store
    val haptics = LocalHapticFeedback.current
    val loose = tabs.filter { it.folder.isEmpty() }
    val groups = tabs.filter { it.folder.isNotEmpty() }.groupBy { it.folder }
    val open = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    // Arrastre: dónde está cada píldora y cada grupo en la pantalla, y qué hay bajo el dedo.
    val bounds = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(hover) {
        ready = false
        val h = hover ?: return@LaunchedEffect
        // Sobre un grupo se suelta al instante; sobre otra fijada hay que esperar un momento (como las carpetas de iOS).
        if (!h.startsWith("t:")) { ready = true; return@LaunchedEffect }
        delay(450)
        ready = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun targetAt(p: Offset, self: Tab): String? {
        val hit = bounds.entries.filter { it.value.contains(p) }.map { it.key }
        return hit.firstOrNull { it.startsWith("t:") && it != "t:${self.id}" }
            ?: hit.firstOrNull { it.startsWith("g:") && it != "g:${self.folder}" }
            ?: hit.firstOrNull { it == "loose" && self.folder.isNotEmpty() }
    }
    fun reset() { dragId = null; hover = null; ready = false; dragOffset = Offset.Zero }
    fun drop(t: Tab) {
        val target = hover
        when {
            dragOffset.getDistance() < 24f -> onMenu(t)
            target == null -> Unit
            target == "loose" -> store.setFolder(listOf(t), "")
            target.startsWith("g:") -> store.setFolder(listOf(t), target.removePrefix("g:"))
            else -> store.tabById(target.removePrefix("t:"))?.let { other ->
                if (ready) {
                    store.setFolder(listOf(other, t), other.folder.ifEmpty { store.newFolderName(other.spaceId, TabKind.PINNED) })
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                } else {
                    // Soltar sin esperar: se pone en su lugar (y en su grupo, si la otra está en uno).
                    if (t.folder != other.folder) store.setFolder(listOf(t), other.folder)
                    store.moveBefore(t, other)
                }
            }
        }
        reset()
    }

    androidx.compose.foundation.layout.BoxWithConstraints {
        val cols = maxOf(2, ((maxWidth + 8.dp) / 220.dp).toInt())

        @Composable
        fun Chip(t: Tab, modifier: Modifier) {
            val key = "t:${t.id}"
            // Una píldora que sale de pantalla (grupo plegado, pestaña cerrada) deja de ser destino.
            androidx.compose.runtime.DisposableEffect(key) { onDispose { bounds.remove(key) } }
            val dragging = dragId == t.id
            val merge = hover == key && ready
            val scale by animateFloatAsState(if (merge) 0.92f else 1f, spring(0.55f, 500f), label = "fijada")
            val bg = when {
                merge -> k.accent.copy(alpha = 0.26f)
                t.id == selectedId -> k.accent.copy(alpha = 0.18f)
                else -> k.card
            }
            Row(
                modifier.height(44.dp)
                    .onGloballyPositioned { bounds[key] = it.boundsInRoot() }
                    .then(
                        if (dragging) Modifier.zIndex(10f).graphicsLayer {
                            // Sigue al dedo aunque el resto se acomode mientras tanto (sin saltos).
                            val now = bounds[key]?.topLeft ?: (dragStart - grab)
                            val at = dragStart + dragOffset - grab - now
                            translationX = at.x; translationY = at.y
                            scaleX = 1.06f; scaleY = 1.06f; shadowElevation = 22f; alpha = 0.96f
                            shape = RoundedCornerShape(50); clip = true
                        } else Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .then(if (merge) Modifier.border(1.5.dp, k.accent, RoundedCornerShape(50)) else Modifier)
                    .clickable { onOpen(t) }
                    .pointerInput(t.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                grab = local
                                dragStart = (bounds[key]?.topLeft ?: Offset.Zero) + local
                                dragOffset = Offset.Zero
                                dragId = t.id
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, d ->
                                change.consume()
                                dragOffset += d
                                hover = targetAt(dragStart + dragOffset, t)
                            },
                            onDragEnd = { drop(t) },
                            onDragCancel = { reset() },
                        )
                    }
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = t.displayTitle },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(t.pinnedUrl ?: t.url, 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(t.displayTitle, style = KazeType.subhead, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }

        @Composable
        fun Chips(list: List<Tab>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.chunked(cols).forEach { row ->
                Row(
                    Modifier.zIndex(if (row.any { it.id == dragId }) 1f else 0f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { t -> androidx.compose.runtime.key(t.id) { Chip(t, Modifier.weight(1f)) } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Column {
            // Zona de las sueltas: soltar aquí una fijada de un grupo la saca del grupo.
            val looseTarget = hover == "loose"
            val fromGroup = tabs.firstOrNull { it.id == dragId }?.folder?.isNotEmpty() == true
            val looseBg by androidx.compose.animation.animateColorAsState(
                if (looseTarget) k.accent.copy(alpha = 0.12f) else Color.Transparent, spring(stiffness = 500f), label = "sueltas",
            )
            Box(
                Modifier.fillMaxWidth().zIndex(if (loose.any { it.id == dragId }) 1f else 0f)
                    .onGloballyPositioned { bounds["loose"] = it.boundsInRoot() }
                    .clip(RoundedCornerShape(22.dp))
                    .background(looseBg),
            ) {
                if (loose.isNotEmpty()) Chips(loose)
                // Sin sueltas, al arrastrar desde un grupo aparece (con un resorte) un sitio para sacarla.
                androidx.compose.animation.AnimatedVisibility(
                    visible = loose.isEmpty() && fromGroup,
                    enter = androidx.compose.animation.expandVertically(spring(0.85f, 500f)) + fadeIn(tween(160)),
                    exit = androidx.compose.animation.shrinkVertically(spring(0.85f, 500f)) + fadeOut(tween(120)),
                ) {
                    Box(
                        Modifier.padding(bottom = 8.dp).fillMaxWidth().height(44.dp).border(1.dp, k.ink.copy(alpha = 0.18f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Suelta aquí para sacarla del grupo", style = KazeType.footnote, color = k.ink55) }
                }
            }
            groups.entries.forEachIndexed { gi, (name, list) ->
                androidx.compose.runtime.key("g:$name") {
                    val c = folderColor(name)
                    val gk = "g:$name"
                    androidx.compose.runtime.DisposableEffect(gk) { onDispose { bounds.remove(gk) } }
                    val target = hover == gk || list.any { hover == "t:${it.id}" && ready }
                    val expanded = open[name] ?: (list.any { it.id == selectedId })
                    val tint by androidx.compose.animation.animateColorAsState(
                        c.copy(alpha = if (target) 0.26f else if (k.isDark) 0.14f else 0.10f), spring(stiffness = 500f), label = "grupo",
                    )
                    Column(
                        Modifier.padding(top = if (gi == 0 && loose.isEmpty()) 0.dp else 8.dp)
                            .fillMaxWidth().zIndex(if (list.any { it.id == dragId }) 1f else 0f)
                            .onGloballyPositioned { bounds[gk] = it.boundsInRoot() }
                            .clip(RoundedCornerShape(18.dp)).background(tint)
                            .then(if (target) Modifier.border(1.5.dp, c, RoundedCornerShape(18.dp)) else Modifier)
                            .animateContentSize(spring(0.85f, 500f)).padding(6.dp),
                    ) {
                        // Tocar pliega o despliega; mantener abre el menú del grupo (Desfijar grupo, Desagrupar).
                        var menu by remember { mutableStateOf(false) }
                        val app = LocalApp.current
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).tap({ open[name] = !expanded }, { menu = true })
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Grupo $name, ${list.size} pestañas (mantén para ver opciones)" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroupMenu(
                                menu, pinned = true, onDismiss = { menu = false },
                                onPinToggle = { unpinGroup(store, app, list.first().spaceId, name) },
                                onUngroup = { store.ungroup(list.first().spaceId, name, TabKind.PINNED) },
                            )
                            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Folder, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(name, style = KazeType.body.copy(fontWeight = FontWeight.SemiBold), color = k.ink, maxLines = 1, modifier = Modifier.weight(1f))
                            // Vista previa: los íconos del grupo cuando está plegado.
                            if (!expanded) list.take(4).forEach { Favicon(it.pinnedUrl ?: it.url, 16.dp, Modifier.padding(start = 4.dp)) }
                            Text("  ${list.size}", style = KazeType.footnote, color = k.ink55)
                            val turn by animateFloatAsState(if (expanded) 180f else 0f, spring(0.85f, 400f), label = "flecha")
                            Icon(
                                Icons.Outlined.KeyboardArrowDown, null, tint = k.ink55,
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
                            )
                        }
                        if (expanded) { Spacer(Modifier.height(4.dp)); Chips(list) }
                    }
                }
            }
        }
    }
    // Lo que ya no se ve (cerradas, grupos plegados o que desaparecieron) no puede ser destino.
    val alive = (loose + groups.filter { (name, list) -> open[name] ?: list.any { it.id == selectedId } }.values.flatten())
        .map { "t:${it.id}" }.toSet() + groups.keys.map { "g:$it" } + "loose"
    androidx.compose.runtime.SideEffect { bounds.keys.filter { it !in alive }.forEach { bounds.remove(it) } }
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
    // Al cerrar con ✕ la tarjeta se encoge un poco y se funde (rápido, acelera al salir) antes de irse.
    val gone = remember { Animatable(0f) }
    val close: () -> Unit = {
        scope.launch { gone.animateTo(1f, tween(170, easing = androidx.compose.animation.core.FastOutLinearInEasing)); onClose() }
    }
    var thumb by remember(tab.id) { mutableStateOf<Bitmap?>(app.thumbnails.cached(tab.id)) }
    val version = app.thumbnails.version.intValue
    LaunchedEffect(tab.id, version) {
        thumb = withContext(Dispatchers.IO) { app.thumbnails.load(tab.id) }
    }

    Column(
        Modifier
            .offset { IntOffset(dx.value.roundToInt(), 0) }
            .graphicsLayer {
                alpha = (1f - (abs(dx.value) / 600f).coerceIn(0f, 0.7f)) * (1f - gone.value)
                val s = 1f - 0.08f * gone.value
                scaleX = s; scaleY = s
            }
            // Deslizar a un lado cierra la pestaña: basta con pasar 160 px o lanzarla rápido; la tarjeta sigue con
            // la velocidad del dedo (resorte con velocidad inicial) y, si no llega, vuelve a su sitio igual.
            .pointerInput(tab.id) {
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); total = 0f },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val fling = abs(v) > 1400f && (v > 0) == (dx.value > 0)
                        if (abs(dx.value) > 160 || fling) {
                            val dir = if ((if (fling) v else dx.value) > 0) 1f else -1f
                            scope.launch { dx.animateTo(dir * 1100f, spring(1f, 320f), initialVelocity = v); onClose() }
                        } else scope.launch { dx.animateTo(0f, spring(0.8f, 500f), initialVelocity = v) }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f, spring(0.8f, 500f)) } },
                ) { change, d ->
                    change.consume()
                    total += d
                    tracker.addPosition(change.uptimeMillis, Offset(total, 0f))
                    scope.launch { dx.snapTo(dx.value + d) }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // En tableta la pantalla es apaisada: la vista previa también (se ve completa y nítida).
                .aspectRatio(if (LocalApp.current.ui.tablet) 1.3f else if (compact) 0.68f else 0.74f)
                // Se inclina hacia el dedo al apretarla (Press3D de Mochy); el encogido lo pone tap().
                .press3D(maxTilt = 5f, pressedScale = 1f)
                .card(k.cardShape)
                .then(if (selected) Modifier.border(3.dp, k.accent, k.cardShape) else Modifier.border(0.5.dp, k.rim, k.cardShape))
                .then(if (highlight) Modifier.border(3.dp, k.accent, k.cardShape) else Modifier)
                .then(drag)
                .tap(onOpen),
        ) {
            val b = thumb
            when {
                b != null && !tab.isStart -> Image(
                    b.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().clip(k.cardShape),
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
                    .background(k.paper.copy(alpha = 0.85f)).clickable(onClick = close)
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
private fun FolderCard(
    name: String, tabs: List<Tab>, list: Boolean, highlight: Boolean, modifier: Modifier = Modifier,
    onPin: () -> Unit = {}, onUngroup: () -> Unit = {}, onOpen: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val color = folderColor(name)
    // Mantener la carpeta abre su menú (Fijar grupo, Desagrupar).
    var menu by remember { mutableStateOf(false) }
    if (list) {
        Row(
            modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).card(RoundedCornerShape(14.dp))
                .then(if (highlight) Modifier.border(2.dp, k.accent, RoundedCornerShape(14.dp)) else Modifier)
                .tap(onOpen, { menu = true }).padding(horizontal = 10.dp, vertical = 10.dp)
                .semantics { contentDescription = "Grupo $name, ${tabs.size} pestañas (mantén para ver opciones)" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Folder, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(name, style = KazeType.body.copy(fontWeight = FontWeight.SemiBold), color = k.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${tabs.size}", style = KazeType.footnote, color = k.ink55)
            GroupMenu(menu, pinned = false, onDismiss = { menu = false }, onPinToggle = onPin, onUngroup = onUngroup)
        }
        return
    }
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.74f).clip(k.cardShape)
                .background(color.copy(alpha = if (k.isDark) 0.20f else 0.16f))
                .border(if (highlight) 3.dp else 1.dp, if (highlight) k.accent else color.copy(alpha = 0.35f), k.cardShape)
                .tap(onOpen, { menu = true }).padding(8.dp)
                .semantics { contentDescription = "Grupo $name, ${tabs.size} pestañas (mantén para ver opciones)" },
        ) {
            GroupMenu(menu, pinned = false, onDismiss = { menu = false }, onPinToggle = onPin, onUngroup = onUngroup)
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
        // Etiqueta de color, igual que en la fila de pestañas: se distingue de una pestaña de un vistazo.
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Folder, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(name, style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  ${tabs.size}", style = KazeType.footnote, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Color estable para cada carpeta, a partir de su nombre. */
internal fun folderColor(name: String): Color {
    val palette = listOf(0xFF7C6CF6, 0xFF3BA7F5, 0xFF2DB89A, 0xFFF0A23B, 0xFFE8618C, 0xFF9B7CF6, 0xFF5AC8FA)
    return Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
}

/** Carpeta abierta: nombre editable, sus pestañas y "Desagrupar". */
@Composable
private fun FolderView(
    name: String, tabs: List<Tab>, selectedId: String?, onOpen: (Tab) -> Unit, onClose: (Tab) -> Unit,
    onRename: (String) -> Unit, onUngroup: () -> Unit, onDismiss: () -> Unit, onPin: () -> Unit = {}, panel: Modifier = Modifier,
) {
    val k = LocalKaze.current
    var text by remember(name) { mutableStateOf(name) }
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (text != name) onRename(text); onDismiss()
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth().heightIn(max = 620.dp)
                .then(panel)
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
                // ⋯: menú del grupo (Fijar grupo, Desagrupar).
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }.semantics { contentDescription = "Opciones del grupo" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MoreHoriz, null, tint = k.accent, modifier = Modifier.size(22.dp))
                    GroupMenu(menu, pinned = false, onDismiss = { menu = false }, onPinToggle = onPin, onUngroup = onUngroup)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                    Box(Modifier.animateItem(fadeInSpec = tween(200), placementSpec = spring(0.85f, 380f, IntOffset.VisibilityThreshold), fadeOutSpec = tween(150)).staggerIn(i, true)) {
                        TabCard(tab = t, selected = t.id == selectedId, onOpen = { onOpen(t) }, onMenu = {}, onClose = { onClose(t) })
                    }
                }
            }
        }
    }
}


/**
 * Entrada escalonada: sube 16 dp y aparece con una curva que frena al final (ease-out fuerte).
 * Solo mientras la vista se está abriendo; las tarjetas que aparecen al desplazar no esperan.
 * Con "Quitar animaciones" de Android la duración es 0 y se muestran al instante.
 */
@Composable
private fun Modifier.staggerIn(index: Int, active: Boolean): Modifier {
    val p = remember { androidx.compose.animation.core.Animatable(if (active) 0f else 1f) }
    val mochy = app.kaze.browser.ui.mochy.AppMotion.mochyTransitions
    LaunchedEffect(Unit) {
        // Sin animaciones (Fyx o Android) no se espera la ráfaga: aparecen al instante.
        if (app.kaze.browser.ui.mochy.AppMotion.reduced) { p.snapTo(1f); return@LaunchedEffect }
        if (p.value < 1f) {
            if (mochy) {
                // Aparición de Mochy (RevealOnAppear): ráfaga escalonada de 60 ms y su curva, sube 20 dp.
                kotlinx.coroutines.delay(app.kaze.browser.ui.mochy.BurstStepMs.toLong() * index.coerceIn(0, app.kaze.browser.ui.mochy.BurstMaxSteps))
                p.animateTo(1f, tween(app.kaze.browser.ui.mochy.RevealFadeInMs, easing = app.kaze.browser.ui.mochy.RevealEasing))
            } else {
                kotlinx.coroutines.delay(35L * index.coerceIn(0, 8))
                p.animateTo(1f, tween(320, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
            }
        }
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * (if (mochy) app.kaze.browser.ui.mochy.RevealSlideDistance else 16.dp).toPx()
    }
}

/**
 * Desvanece los bordes de una fila deslizable solo donde hay más contenido (como la fila de temas de Safari).
 * Se dibuja con una máscara en una capa propia: no cambia el diseño ni recalcula nada al desplazarse.
 */
internal fun Modifier.fadingEdges(state: androidx.compose.foundation.lazy.LazyListState, edge: androidx.compose.ui.unit.Dp = 28.dp): Modifier =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = (edge.toPx() / size.width).coerceAtMost(0.4f)
            val l = if (state.canScrollBackward) w else 0f
            val r = if (state.canScrollForward) w else 0f
            if (l == 0f && r == 0f) return@drawWithContent
            drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to (if (l > 0f) Color.Transparent else Color.Black), l to Color.Black,
                    (1f - r) to Color.Black, 1f to (if (r > 0f) Color.Transparent else Color.Black),
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
