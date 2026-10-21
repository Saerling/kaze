/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kaze.browser.BuildConfig
import app.kaze.browser.data.DnsMode
import app.kaze.browser.data.HistoryEntry
import app.kaze.browser.data.ReadingItem
import app.kaze.browser.data.SearchEngine
import app.kaze.browser.data.Tab
import app.kaze.browser.data.ThemeMode
import app.kaze.browser.data.TrackingLevel
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.IosGroup
import app.kaze.browser.ui.components.IosRow
import app.kaze.browser.ui.components.IosSectionHeader
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.SystemRed
import app.kaze.browser.ui.theme.card
import app.kaze.browser.ui.theme.liftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.StorageController
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// ── Marcadores: Favoritos · Lista de lectura · Historial ──────────────

@Composable
fun LibrarySheet(onDismiss: () -> Unit, onOpenUrl: (String) -> Unit, onOpenTab: (Tab) -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var section by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var reading by remember { mutableStateOf(emptyList<ReadingItem>()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(section, query, version) {
        when (section) {
            1 -> reading = withContext(Dispatchers.IO) { app.db.readingList() }
            2 -> history = withContext(Dispatchers.IO) { app.db.history(query) }
        }
    }

    KazeSheet(onDismiss, full = true) {
        SheetTitle(listOf("Favoritos", "Lista de lectura", "Historial")[section], "OK", onDismiss)
        // Control segmentado de iOS
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(34.dp).liftCard(RoundedCornerShape(9.dp)).padding(2.dp)) {
            listOf("Favoritos", "Lectura", "Historial").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(7.dp))
                        .then(if (section == i) Modifier.card(RoundedCornerShape(7.dp)) else Modifier)
                        .clickable { section = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = KazeType.footnote.copy(fontWeight = if (section == i) FontWeight.SemiBold else FontWeight.Normal), color = k.ink)
                }
            }
        }

        when (section) {
            0 -> {
                val favs = app.store.essentials()
                if (favs.isEmpty()) Empty("Aún no tienes Favoritos.\nEn una página, toca Compartir → Añadir a Favoritos.")
                else IosGroup {
                    favs.forEachIndexed { i, t ->
                        IosRow(t.displayTitle, subtitle = Urls.displayHost(t.pinnedUrl ?: t.url), leading = { Favicon(t.pinnedUrl ?: t.url, 26.dp) }, divider = i < favs.lastIndex) {
                            onDismiss(); onOpenTab(t)
                        }
                    }
                }
            }
            1 -> {
                if (reading.isEmpty()) Empty("Tu lista de lectura está vacía.\nGuarda páginas para leerlas después desde Compartir.")
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 2000.dp)) {
                    item {
                        IosGroup {
                            reading.forEachIndexed { i, r ->
                                IosRow(
                                    r.title.ifBlank { Urls.displayHost(r.url) }, subtitle = Urls.displayHost(r.url),
                                    leading = { Favicon(r.url, 26.dp) }, divider = i < reading.lastIndex,
                                    trailing = { RemoveButton { app.db.removeFromReadingList(r.url); reading = reading - r } },
                                ) { onDismiss(); onOpenUrl(r.url) }
                            }
                        }
                    }
                }
            }
            else -> {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(38.dp).liftCard(RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = k.ink55, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent), modifier = Modifier.weight(1f),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Buscar en el historial", style = KazeType.body, color = k.ink55); inner() },
                    )
                }
                if (history.isEmpty()) Empty(if (query.isEmpty()) "Aún no hay historial." else "Sin resultados.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 2000.dp)) {
                    val groups = history.groupBy { dayLabel(it.lastVisit) }
                    groups.forEach { (day, entries) ->
                        item(key = "d$day") {
                            IosSectionHeader(day)
                            IosGroup {
                                entries.forEachIndexed { i, h ->
                                    IosRow(
                                        h.title.ifBlank { Urls.displayHost(h.url) }, subtitle = Urls.displayHost(h.url),
                                        leading = { Favicon(h.url, 26.dp) }, divider = i < entries.lastIndex,
                                        trailing = { RemoveButton { app.db.deleteHistory(h.url); history = history - h } },
                                    ) { onDismiss(); onOpenUrl(h.url) }
                                }
                            }
                        }
                    }
                    if (history.isNotEmpty() && query.isEmpty()) {
                        item {
                            Spacer(Modifier.height(20.dp))
                            IosGroup { IosRow("Borrar historial", titleColor = SystemRed, divider = false) { app.db.clearHistory(); history = emptyList() } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    val k = LocalKaze.current
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Close, "Quitar", tint = k.ink35, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Empty(text: String) {
    val k = LocalKaze.current
    Text(text, style = KazeType.subhead, color = k.ink55, modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp))
}

private fun dayLabel(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val diff = (today.get(Calendar.YEAR) * 400 + today.get(Calendar.DAY_OF_YEAR)) - (c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR))
    return when (diff) {
        0 -> "Hoy"
        1 -> "Ayer"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    }
}

// ── Ajustes ───────────────────────────────────────────────────────────

@Composable
fun SettingsSheet(onDismiss: () -> Unit, onRestart: () -> Unit, onCheckUpdates: () -> Unit) {
    val app = LocalApp.current
    val s = app.settings
    val k = LocalKaze.current
    var needsRestart by remember { mutableStateOf(false) }
    var autoUpdates by remember { mutableStateOf(app.updater.autoCheck) }

    KazeSheet(onDismiss, full = true) {
        SheetTitle("Ajustes", "OK", onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (needsRestart) {
                Spacer(Modifier.height(8.dp))
                IosGroup {
                    IosRow("Reiniciar Kaze para aplicar los cambios", titleColor = k.accent, divider = false, onClick = onRestart)
                }
            }

            IosSectionHeader("Búsqueda")
            IosGroup {
                Choice("Buscador", SearchEngine.entries, s.searchEngine, { it.label }) { s.searchEngine = it }
                Toggle("Sugerencias del buscador", s.searchSuggestions, divider = false) { s.searchSuggestions = it }
            }
            Note("Con las sugerencias activas, lo que escribes se envía al buscador.")

            IosSectionHeader("Apariencia")
            IosGroup {
                Choice("Tema", ThemeMode.entries, s.theme, { it.label }, divider = false) { s.theme = it; needsRestart = true }
            }

            IosSectionHeader("Memoria")
            IosGroup {
                Choice(
                    "Pestañas cargadas a la vez", listOf(1, 2, 3, 4, 6), s.liveTabs,
                    { if (it == s.defaultLiveTabs) "$it (recomendado)" else "$it" },
                ) { s.liveTabs = it; app.pool.trim() }
                Choice(
                    "Dormir pestañas sin usar", listOf(5, 10, 30, 60, 0), s.sleepAfterMinutes,
                    { if (it == 0) "Nunca" else "Tras $it min" },
                ) { s.sleepAfterMinutes = it }
                Toggle("Aislamiento estricto de sitios", s.siteIsolation, divider = false) { s.siteIsolation = it; needsRestart = true }
            }
            Note("Las pestañas dormidas no gastan RAM y se recargan al volver a ellas. El aislamiento estricto (un proceso por sitio) es más seguro pero usa bastante más memoria.")

            IosSectionHeader("Privacidad y seguridad")
            IosGroup {
                Choice("Protección contra rastreo", TrackingLevel.entries, s.tracking, { it.label }) { s.tracking = it; needsRestart = true }
                Choice("DNS seguro", DnsMode.entries, s.dns, { it.label }) { s.dns = it; needsRestart = true }
                Toggle("JavaScript", s.javascript) { s.javascript = it; needsRestart = true }
                IosRow("Instalar bloqueador de anuncios (uBlock Origin)", titleColor = k.accent) {
                    app.runtime.webExtensionController
                        .install("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi")
                        .accept({ app.ui.show("uBlock Origin instalado") }, { app.ui.show("No se pudo instalar uBlock Origin") })
                    app.ui.show("Instalando uBlock Origin…")
                }
                IosRow("Borrar caché", titleColor = k.accent) {
                    app.runtime.storageController.clearData(StorageController.ClearFlags.ALL_CACHES)
                    app.favicons.clear()
                    app.ui.show("Caché borrada")
                }
                IosRow("Borrar cookies y datos de sitios", titleColor = SystemRed, divider = false) {
                    app.runtime.storageController.clearData(StorageController.ClearFlags.SITE_DATA or StorageController.ClearFlags.COOKIES)
                    app.ui.show("Cookies y datos borrados")
                }
            }

            IosSectionHeader("Actualizaciones")
            IosGroup {
                Toggle("Avisarme de versiones nuevas", autoUpdates) { app.updater.autoCheck = it; autoUpdates = it }
                IosRow("Buscar actualizaciones ahora", titleColor = k.accent, divider = false, onClick = onCheckUpdates)
            }

            IosSectionHeader("Acerca de")
            IosGroup {
                IosRow("Versión", trailing = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = KazeType.body, color = k.ink55) })
                IosRow("Motor", trailing = { Text("Gecko (Firefox)", style = KazeType.body, color = k.ink55) }, divider = false)
            }
            Note("Kaze es código abierto (MPL 2.0). Inspirado en Safari, Zen Browser y Z-Sync; no está afiliado a ellos.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    val k = LocalKaze.current
    Text(text, style = KazeType.footnote, color = k.ink55, modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 7.dp))
}

@Composable
private fun Toggle(title: String, value: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    IosRow(title, divider = divider, trailing = { KazeSwitch(value, onChange) }) { onChange(!value) }
}

/** Opción con valor a la derecha; al tocarla se despliega la lista con ✓ (como iOS). */
@Composable
private fun <T> Choice(title: String, options: List<T>, value: T, label: (T) -> String, divider: Boolean = true, onChange: (T) -> Unit) {
    val k = LocalKaze.current
    var open by remember { mutableStateOf(false) }
    IosRow(
        title, divider = divider || open,
        trailing = { Text(label(value), style = KazeType.body, color = k.ink55) },
        chevron = true,
    ) { open = !open }
    if (open) {
        Column(Modifier.background(k.paper.copy(alpha = 0.5f))) {
            options.forEachIndexed { i, o ->
                IosRow(
                    "   " + label(o), divider = divider || i < options.lastIndex,
                    trailing = { if (o == value) Icon(Icons.Outlined.Check, null, tint = k.accent, modifier = Modifier.size(20.dp)) },
                ) { onChange(o); open = false }
            }
        }
    }
}
