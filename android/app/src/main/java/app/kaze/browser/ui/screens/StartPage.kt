/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

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

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(
            if (private) listOf(0xFF3A3A55.toInt(), 0xFF2A2A40.toInt()) else space?.colors.orEmpty(),
            strength = if (private) 0.9f else 0.5f,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 140.dp),
        ) {
            if (private) {
                Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().card(RoundedCornerShape(16.dp)).padding(20.dp)) {
                    Text("Navegación privada", style = KazeType.title2, color = k.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Zev no guardará las páginas que visites, tu historial de búsqueda ni la información de autorrelleno. " +
                            "Las pestañas privadas desaparecen al cerrarlas.",
                        style = KazeType.subhead, color = k.ink55,
                    )
                }
            }

            if (favorites.isNotEmpty() && app.settings.startFavorites) {
                Section("Favoritos")
                TileGrid(favorites.map { (it.pinnedUrl ?: it.url) to it.displayTitle }) { i, long ->
                    if (long) onFavoriteMenu(favorites[i]) else onOpenFavorite(favorites[i])
                }
            }

            if (frequent.isNotEmpty() && app.settings.startFrequent) {
                Section("Visitados con frecuencia")
                TileGrid(frequent.map { it.url to it.title.ifBlank { Urls.displayHost(it.url) } }) { i, long ->
                    if (!long) onOpenUrl(frequent[i].url)
                }
            }

            if (!private && app.settings.startPrivacy) {
                Section("Reporte de privacidad")
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().card(RoundedCornerShape(16.dp))
                        .tap(onPrivacyReport).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Shield, null, tint = k.ink, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    val n = app.settings.trackersTotal
                    Text(
                        if (n == 0) "Zev bloquea los rastreadores y te muestra cuántos detuvo." else
                            "Zev evitó que $n rastreadores te siguieran.",
                        style = KazeType.subhead, color = k.ink, modifier = Modifier.weight(1f),
                    )
                    Text(n.toString(), style = KazeType.title2, color = k.ink)
                }
            }

            if (reading.isNotEmpty() && app.settings.startReading) {
                Section("Lista de lectura")
                Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().card(RoundedCornerShape(16.dp))) {
                    reading.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().tap { onOpenUrl(r.url) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Favicon(r.url, 30.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title.ifBlank { Urls.displayHost(r.url) }, style = KazeType.subhead, color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(Urls.displayHost(r.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (favorites.isEmpty() && frequent.isEmpty() && !private) {
                Text(
                    "Toca la barra de abajo para buscar. Para añadir sitios a Favoritos, usa el botón Compartir de una página.",
                    style = KazeType.subhead, color = k.ink55, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val k = LocalKaze.current
    Text(title, style = KazeType.sectionHeader, color = k.ink, modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp))
}

@Composable
private fun TileGrid(items: List<Pair<String, String>>, onClick: (Int, Boolean) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.withIndex().chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (i, item) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        SiteTile(item.first, item.second, onClick = { onClick(i, false) }, onLongClick = { onClick(i, true) })
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
