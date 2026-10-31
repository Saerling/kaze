/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

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
 * Página de inicio (como la de Safari): Favoritos, Visitados con frecuencia,
 * Reporte de privacidad y Lista de lectura. Es Compose puro: abrir una pestaña
 * nueva no arranca el motor ni gasta memoria de Gecko hasta que vas a un sitio.
 */
@Composable
fun StartPage(
    private: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenFavorite: (Tab) -> Unit,
    onFavoriteMenu: (Tab) -> Unit,
    onPrivacyReport: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val favorites = app.store.essentials()
    var frequent by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var reading by remember { mutableStateOf(emptyList<ReadingItem>()) }
    LaunchedEffect(Unit) {
        if (!private) {
            frequent = withContext(Dispatchers.IO) { app.db.frequent() }
            reading = withContext(Dispatchers.IO) { app.db.readingList().take(4) }
        }
    }
    val space = app.store.currentSpace

    Box(Modifier.fillMaxSize().flowBackground(LocalKaze.current)) {
        SpaceBackground(
            if (private) listOf(0xFF3A3A55.toInt(), 0xFF2A2A40.toInt()) else space?.colors.orEmpty(),
            strength = if (private) 0.9f else 0.5f,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Contenido centrado y con ancho máximo: en tableta no se estira de borde a borde.
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                // Saludo y fecha: la página se siente viva y da contexto sin estorbar.
                val now = remember { java.util.Calendar.getInstance() }
                val h = now.get(java.util.Calendar.HOUR_OF_DAY)
                Text(
                    if (private) "Navegación privada" else if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches",
                    style = KazeType.largeTitle, color = k.ink, modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale("es")).format(now.time).replaceFirstChar { it.uppercase() },
                    style = KazeType.subhead, color = k.ink55, modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 8.dp),
                )
                if (private) {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(Icons.Outlined.Shield, Color(0xFF8E8CF0))
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "Fyx no guarda las páginas que visites, tus búsquedas ni lo que escribas en formularios. Las pestañas privadas desaparecen al cerrarlas.",
                                style = KazeType.subhead, color = k.ink70,
                            )
                        }
                    }
                }

                if (favorites.isNotEmpty() && app.settings.startFavorites) {
                    Section("Favoritos")
                    Panel(pad = 8.dp) {
                        TileGrid(favorites.map { (it.pinnedUrl ?: it.url) to it.displayTitle }) { i, long ->
                            if (long) onFavoriteMenu(favorites[i]) else onOpenFavorite(favorites[i])
                        }
                    }
                }

                if (frequent.isNotEmpty() && app.settings.startFrequent) {
                    Section("Visitados con frecuencia")
                    // Filas con ícono, título y sitio en dos columnas: se distinguen de los Favoritos de un vistazo.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
                        val cols = if (maxWidth > 560.dp) 2 else 1
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            frequent.take(if (cols == 2) 8 else 6).chunked(cols).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { e ->
                                        Row(
                                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(k.card.copy(alpha = 0.85f))
                                                .tap { onOpenUrl(e.url) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            FaviconTile(e.url, 36.dp, 20.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(e.title.ifBlank { Urls.displayHost(e.url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(Urls.displayHost(e.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                                            }
                                        }
                                    }
                                    if (row.size < cols) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (!private && app.settings.startPrivacy) {
                    Section("Privacidad")
                    val n = app.settings.trackersTotal
                    // Tarjeta destacada: el número grande es lo que importa; el resto lo explica.
                    Row(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(k.accent.copy(alpha = 0.22f), k.accent.copy(alpha = 0.08f))))
                            .tap(onPrivacyReport).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Badge(Icons.Outlined.Shield, k.accent)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (n == 0) "Protección activa" else "%,d".format(n).replace(',', '.'), style = (if (n == 0) KazeType.headline else KazeType.largeTitle), color = k.ink)
                            Text(if (n == 0) "Fyx bloqueará los rastreadores que encuentre." else "rastreadores bloqueados", style = KazeType.subhead, color = k.ink70)
                        }
                        Text("Ver informe", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.accent)
                    }
                }

                if (reading.isNotEmpty() && app.settings.startReading) {
                    Section("Lista de lectura")
                    Panel(pad = 0.dp) {
                        reading.forEachIndexed { i, r ->
                            Row(
                                Modifier.fillMaxWidth().tap { onOpenUrl(r.url) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FaviconTile(r.url, 36.dp, 20.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title.ifBlank { Urls.displayHost(r.url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(Urls.displayHost(r.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                                }
                            }
                            if (i < reading.lastIndex) Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(0.5.dp).background(k.separator))
                        }
                    }
                }

                if (favorites.isEmpty() && frequent.isEmpty() && !private) {
                    Panel {
                        Text("Empieza a navegar", style = KazeType.headline, color = k.ink)
                        Spacer(Modifier.height(4.dp))
                        Text("Toca la barra para buscar. Para tener un sitio aquí, añádelo a Favoritos desde el menú ⋯.", style = KazeType.subhead, color = k.ink55)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val k = LocalKaze.current
    Text(title, style = KazeType.title2.copy(fontWeight = FontWeight.Bold), color = k.ink, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
}

/** Panel suave (tarjeta translúcida) que agrupa una sección. */
@Composable
private fun Panel(pad: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val k = LocalKaze.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(k.card.copy(alpha = 0.85f)).padding(pad),
        content = content,
    )
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun TileGrid(items: List<Pair<String, String>>, onClick: (Int, Boolean) -> Unit) {
    // Columnas según el ancho: 4 en teléfono, más en tableta.
    androidx.compose.foundation.layout.BoxWithConstraints {
        val cols = maxOf(4, (maxWidth / 96.dp).toInt())
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.withIndex().chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (i, item) ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            SiteTile(item.first, item.second, onClick = { onClick(i, false) }, onLongClick = { onClick(i, true) })
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
