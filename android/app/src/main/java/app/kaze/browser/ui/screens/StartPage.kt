/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import app.kaze.browser.zsync.ZenSync
import app.kaze.browser.ui.components.FaviconTile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import app.kaze.browser.ui.theme.flowBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaze.browser.data.HistoryEntry
import app.kaze.browser.data.Favorite
import app.kaze.browser.ui.sheets.FavoritesSheet
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import app.kaze.browser.data.ReadingItem
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.SiteTile
import app.kaze.browser.ui.components.SpaceBackground
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Página de inicio (como la de Safari): saludo, Favoritos (una fila; "Ver todos" para el resto),
 * Reanudar navegación, Visitados con frecuencia (una fila), Lista de lectura (tres) y una línea de
 * privacidad. Cada sección se limita a lo que cabe de un vistazo: hay aire entre ellas y la jerarquía
 * la marcan el saludo y los títulos, no los paneles. Es Compose puro: abrir una pestaña nueva no
 * arranca el motor ni gasta memoria de Gecko hasta que vas a un sitio.
 */
@Composable
fun StartPage(
    private: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenFavorite: (Tab) -> Unit,
    onFavoriteMenu: (Tab) -> Unit,
    onPrivacyReport: () -> Unit,
    /** Solo para las capturas: hace como si hubiera sesión de Firefox. */
    forceSynced: Boolean = false,
    /** Solo para las capturas: abre la hoja de Editar Favoritos. */
    startEditing: Boolean = false,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val favorites = app.store.favorites()
    var editing by remember { mutableStateOf(startEditing) }
    var showAll by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var frequent by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var reading by remember { mutableStateOf(emptyList<ReadingItem>()) }
    // ¿Hay sesión de Firefox? Leer la cuenta descifra el almacén seguro: se hace fuera del hilo principal.
    var synced by remember { mutableStateOf(forceSynced) }
    // La página se pinta al instante con lo que ya está en memoria (saludo, Favoritos, cerradas);
    // lo que sale de disco llega en una sola pasada en segundo plano y aparece con un fundido.
    LaunchedEffect(private) {
        if (private) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                runCatching { app.db.frequent() }.getOrDefault(emptyList()),
                runCatching { app.db.readingList().take(3) }.getOrDefault(emptyList()),
                runCatching { ZenSync.email(context) != null }.getOrDefault(false),
            )
        }
        frequent = loaded.first; reading = loaded.second; synced = loaded.third || forceSynced
    }
    // Reanudar navegación (Safari en iOS 27): cerradas hace poco y lo que llegó del escritorio (Zen).
    val closed = if (private) emptyList() else app.store.recentlyClosed.take(6)
    val favoriteKeys = favorites.map { Favorite.key(it.url) }.toSet()
    val essentialIds = app.store.essentials().map { it.id }.toSet()
    val remote = if (private || !synced) emptyList() else {
        val spaceIds = app.store.profileSpaces.map { it.id }.toSet()
        app.store.tabs.filter { it.zenId != null && it.id !in essentialIds && it.spaceId in spaceIds }
            .sortedByDescending { it.lastUsed }.take(6)
    }
    // Lo frecuente que ya está en Favoritos no se repite.
    val frequentShown = frequent.filter { Favorite.key(it.url) !in favoriteKeys }.distinctBy { Favorite.key(it.url) }
    val space = app.store.currentSpace
    fun open(f: Favorite) { app.store.favoriteTab(f)?.let(onOpenFavorite) ?: onOpenUrl(f.url) }

    Box(Modifier.fillMaxSize().flowBackground(LocalKaze.current)) {
        SpaceBackground(
            if (private) listOf(0xFF3A3A55.toInt(), 0xFF2A2A40.toInt()) else space?.colors.orEmpty(),
            strength = if (private) 0.9f else 0.5f,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 36.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Contenido centrado y con ancho máximo: en tableta no se estira de borde a borde.
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                // Saludo y fecha: lo único grande de la página; el resto se ordena debajo.
                val now = remember { java.util.Calendar.getInstance() }
                val h = now.get(java.util.Calendar.HOUR_OF_DAY)
                Text(
                    if (private) "Navegación privada" else if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches",
                    style = KazeType.largeTitle, color = k.ink,
                )
                Text(
                    remember { java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale("es")).format(now.time).replaceFirstChar { it.uppercase() } },
                    style = KazeType.subhead, color = k.ink55, modifier = Modifier.padding(top = 2.dp),
                )
                if (private) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(k.card.copy(alpha = 0.85f)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Badge(Icons.Outlined.Shield, Color(0xFF8E8CF0))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Fyx no guarda las páginas que visites, tus búsquedas ni lo que escribas en formularios. Las pestañas privadas desaparecen al cerrarlas.",
                            style = KazeType.subhead, color = k.ink70,
                        )
                    }
                }

                if (!private && app.settings.startFavorites) {
                    Section("Favoritos", if (favorites.isEmpty()) "Añadir" else "Editar") { editing = true }
                    if (favorites.isEmpty()) {
                        // Sin Favoritos: una invitación discreta en vez de un hueco.
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, k.ink.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                .tap { editing = true }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(k.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, null, tint = k.accent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text("Guarda aquí los sitios que más usas.", style = KazeType.subhead, color = k.ink55)
                        }
                    } else {
                        TileRows(
                            favorites.map { it.url to it.title.ifBlank { Urls.displayHost(it.url) } }, expanded = showAll,
                            onClick = { i -> open(favorites[i]) },
                            onLong = { i -> app.store.favoriteTab(favorites[i])?.let(onFavoriteMenu) ?: run { editing = true } },
                            onToggle = { showAll = !showAll },
                        )
                    }
                }

                if ((closed.isNotEmpty() || remote.isNotEmpty()) && app.settings.startResume) {
                    Section("Reanudar navegación")
                    ResumeRow(closed, remote, onOpenUrl, onOpenFavorite)
                }

                FadeIn(frequentShown.isNotEmpty() && app.settings.startFrequent) {
                    Section("Visitados con frecuencia")
                    // Una sola fila de mosaicos, como Safari: lo demás está en el historial.
                    TileRows(
                        frequentShown.map { it.url to it.title.ifBlank { Urls.displayHost(it.url) } }, expanded = false, maxRows = 1,
                        onClick = { i -> onOpenUrl(frequentShown[i].url) }, onLong = {}, onToggle = {},
                    )
                }

                FadeIn(reading.isNotEmpty() && app.settings.startReading) {
                    Section("Lista de lectura")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(k.card.copy(alpha = 0.85f))) {
                        reading.forEachIndexed { i, r ->
                            Row(
                                Modifier.fillMaxWidth().tap { onOpenUrl(r.url) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FaviconTile(r.url, 34.dp, 19.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title.ifBlank { Urls.displayHost(r.url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Urls.displayHost(r.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                                }
                            }
                            if (i < reading.lastIndex) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(k.separator))
                        }
                    }
                }

                if (!private && app.settings.startPrivacy) {
                    // Privacidad: una línea al final (lo importante es el número; el informe está a un toque).
                    val n = app.settings.trackersTotal
                    Spacer(Modifier.height(36.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(k.card.copy(alpha = 0.6f))
                            .tap(onPrivacyReport).padding(horizontal = 14.dp, vertical = 12.dp)
                            .semantics { contentDescription = "Reporte de privacidad" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Shield, null, tint = k.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (n == 0) "Protección contra rastreo activa" else "%,d".format(n).replace(',', '.') + " rastreadores bloqueados",
                            style = KazeType.subhead.copy(fontWeight = FontWeight.Medium), color = k.ink, modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = k.ink35, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    if (editing) FavoritesSheet(onDismiss = { editing = false })
}

/** Sección que entra con un fundido suave cuando su contenido llega del disco (sin saltos bruscos). */
@Composable
private fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f)) +
            androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f)),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
    ) { Column { content() } }
}

/**
 * "Reanudar navegación": fila horizontal de tarjetas con ícono, título y sitio. Primero las
 * pestañas cerradas hace poco (se abren en esta pestaña) y luego las que llegaron de Zen en el
 * computador (se abren como su pestaña).
 */
@Composable
private fun ResumeRow(closed: List<Pair<String, String>>, remote: List<Tab>, onOpenUrl: (String) -> Unit, onOpenTab: (Tab) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(closed.size, key = { "c:" + closed[it].first }) { i ->
            val (url, title) = closed[i]
            ResumeCard(url, title, Icons.Outlined.History, "Cerrada hace poco") { onOpenUrl(url) }
        }
        items(remote.size, key = { "r:" + remote[it].id }) { i ->
            val t = remote[i]
            ResumeCard(t.pinnedUrl ?: t.url, t.displayTitle, Icons.Outlined.Computer, "En tu computador") { onOpenTab(t) }
        }
    }
}

@Composable
private fun ResumeCard(url: String, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, caption: String, onClick: () -> Unit) {
    val k = LocalKaze.current
    Column(
        Modifier.width(150.dp).height(116.dp).clip(RoundedCornerShape(18.dp)).background(k.card.copy(alpha = 0.85f))
            .tap(onClick).padding(12.dp),
    ) {
        FaviconTile(url, 34.dp, 19.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            title.ifBlank { Urls.displayHost(url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.Medium), color = k.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = k.ink55, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(Urls.displayHost(url).ifBlank { caption }, style = KazeType.footnote, color = k.ink55, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Título de sección: discreto (20 sp) y con aire arriba; a la derecha, una acción opcional ("Editar"). */
@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val k = LocalKaze.current
    Row(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = KazeType.sectionHeader, color = k.ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold), color = k.accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).tap(onAction).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Mosaicos en filas (4 en teléfono, más en tableta). Recogido se ve una fila; "Ver todos" despliega el
 * resto con un resorte. Los mosaicos entran escalonados (40 ms, hasta 8) la primera vez que aparecen.
 */
@Composable
private fun TileRows(
    items: List<Pair<String, String>>, expanded: Boolean, onClick: (Int) -> Unit, onLong: (Int) -> Unit, onToggle: () -> Unit,
    maxRows: Int = Int.MAX_VALUE,
) {
    val k = LocalKaze.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = maxOf(4, (maxWidth / 92.dp).toInt())
        val rows = items.withIndex().chunked(cols).take(maxRows)
        @Composable
        fun TileRow(row: List<IndexedValue<Pair<String, String>>>) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { (i, item) ->
                    Box(Modifier.weight(1f).reveal(i), contentAlignment = Alignment.TopCenter) {
                        SiteTile(item.first, item.second, onClick = { onClick(i) }, onLongClick = { onLong(i) })
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Column {
            rows.firstOrNull()?.let { TileRow(it) }
            androidx.compose.animation.AnimatedVisibility(
                expanded && rows.size > 1,
                enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(1f, 420f), expandFrom = Alignment.Top) +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(1f, 520f), shrinkTowards = Alignment.Top),
            ) { Column { rows.drop(1).forEach { TileRow(it) } } }
            if (rows.size > 1) {
                Text(
                    if (expanded) "Mostrar menos" else "Ver todos (${items.size})",
                    style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).tap(onToggle).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Aparición escalonada: sube 10 dp y se funde con una curva que frena al final. Solo la primera vez
 * (no al volver a recomponer); sin animaciones (Fyx o Android) aparece al instante.
 */
@Composable
private fun Modifier.reveal(index: Int): Modifier {
    val p = remember { androidx.compose.animation.core.Animatable(if (app.kaze.browser.ui.mochy.AppMotion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value >= 1f) return@LaunchedEffect
        kotlinx.coroutines.delay(40L * index.coerceIn(0, 8))
        p.animateTo(1f, androidx.compose.animation.core.tween(380, easing = app.kaze.browser.ui.mochy.RevealEasing))
    }
    return graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 10.dp.toPx() }
}
