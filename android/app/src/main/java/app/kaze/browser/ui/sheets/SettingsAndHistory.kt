/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaze.browser.BuildConfig
import app.kaze.browser.data.DnsMode
import app.kaze.browser.data.HistoryEntry
import app.kaze.browser.data.SearchEngine
import app.kaze.browser.data.ThemeMode
import app.kaze.browser.data.TrackingLevel
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.liftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.StorageController
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// ── Historial ─────────────────────────────────────────────────────────

@Composable
fun HistorySheet(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, version) { items = withContext(Dispatchers.IO) { app.db.history(query) } }

    KazeSheet(onDismiss, full = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", style = KazeType.sheetTitle, color = k.ink, modifier = Modifier.weight(1f))
            if (items.isNotEmpty() && query.isEmpty()) {
                Text("Borrar todo", style = KazeType.small, color = Color(0xFFC0563A), modifier = Modifier.clickable {
                    app.db.clearHistory(); items = emptyList()
                })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(44.dp).liftCard().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = k.ink55, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent), modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (query.isEmpty()) Text("Buscar en el historial", style = KazeType.body, color = k.ink55); inner() },
            )
        }
        if (items.isEmpty()) {
            Text(
                if (query.isEmpty()) "Aún no hay historial." else "Sin resultados.",
                style = KazeType.small, color = k.ink55, modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 2000.dp)) {
            var lastDay = ""
            items.forEach { h ->
                val day = dayLabel(h.lastVisit)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "d$day") {
                        Text(day.uppercase(), style = KazeType.caption, color = k.ink55, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp))
                    }
                }
                item(key = h.url) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(h.url) }.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Favicon(h.url, 22.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title.ifBlank { Urls.displayHost(h.url) }, style = KazeType.body, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Urls.displayHost(h.url), style = KazeType.small, color = k.ink55, maxLines = 1)
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { app.db.deleteHistory(h.url); items = items - h }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "Borrar", tint = k.ink35, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
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
    // autoCheck vive en SharedPreferences: este estado redibuja el interruptor al cambiarlo.
    var autoUpdates by remember { mutableStateOf(app.updater.autoCheck) }

    KazeSheet(onDismiss, full = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Ajustes", style = KazeType.sheetTitle, color = k.ink, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

            if (needsRestart) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).liftCard().clickable(onClick = onRestart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Algunos cambios se aplican al reiniciar Kaze.", style = KazeType.small, color = k.ink, modifier = Modifier.weight(1f))
                    Text("Reiniciar", style = KazeType.body, color = k.accent)
                }
            }

            Section("Búsqueda")
            Choice("Motor de búsqueda", SearchEngine.entries, s.searchEngine, { it.label }) { s.searchEngine = it }
            Toggle("Sugerencias mientras escribes", "Se envía lo que escribes al motor de búsqueda.", s.searchSuggestions) { s.searchSuggestions = it }

            Section("Apariencia")
            Choice("Tema", ThemeMode.entries, s.theme, { it.label }) { s.theme = it; needsRestart = true }

            Section("Memoria")
            Choice(
                "Pestañas cargadas a la vez",
                listOf(1, 2, 3, 4, 6), s.liveTabs, { if (it == s.defaultLiveTabs) "$it (recomendado)" else "$it" },
            ) { s.liveTabs = it; app.pool.trim() }
            Text(
                "Las demás pestañas se duermen: no gastan RAM y se recargan al volver a ellas.",
                style = KazeType.small, color = k.ink55, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Choice(
                "Dormir pestañas sin usar tras",
                listOf(5, 10, 30, 60, 0), s.sleepAfterMinutes, { if (it == 0) "Nunca" else "$it min" },
            ) { s.sleepAfterMinutes = it }
            Toggle(
                "Aislamiento estricto de sitios",
                "Un proceso por sitio web (Fission). Más seguro, pero usa bastante más RAM.",
                s.siteIsolation,
            ) { s.siteIsolation = it; needsRestart = true }

            Section("Privacidad")
            Choice("Protección contra rastreo", TrackingLevel.entries, s.tracking, { it.label }) { s.tracking = it; needsRestart = true }
            Choice("DNS seguro", DnsMode.entries, s.dns, { it.label }) { s.dns = it; needsRestart = true }
            Toggle("JavaScript", "Desactivarlo rompe muchos sitios.", s.javascript) { s.javascript = it; needsRestart = true }
            Action("Instalar uBlock Origin (bloqueador de anuncios)") {
                app.runtime.webExtensionController
                    .install("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi")
                    .accept({ app.ui.show("uBlock Origin instalado") }, { app.ui.show("No se pudo instalar uBlock Origin") })
                app.ui.show("Instalando uBlock Origin…")
            }
            Action("Borrar cookies y datos de sitios") {
                app.runtime.storageController.clearData(StorageController.ClearFlags.SITE_DATA or StorageController.ClearFlags.COOKIES)
                app.ui.show("Cookies y datos borrados")
            }
            Action("Borrar caché") {
                app.runtime.storageController.clearData(StorageController.ClearFlags.ALL_CACHES)
                app.favicons.clear()
                app.ui.show("Caché borrada")
            }

            Section("Actualizaciones")
            Toggle(
                "Avisarme de versiones nuevas",
                "Revisa GitHub al abrir Kaze (como mucho cada 3 horas).",
                autoUpdates,
            ) { app.updater.autoCheck = it; autoUpdates = it }
            Action("Buscar actualizaciones ahora", onCheckUpdates)

            Section("Acerca de")
            Text(
                "Kaze ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · motor Gecko (Firefox)\nInspirado en Zen Browser y Z-Sync. Código abierto (MPL 2.0).",
                style = KazeType.small, color = k.ink55, modifier = Modifier.padding(4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    val k = LocalKaze.current
    Text(title.uppercase(), style = KazeType.caption, color = k.ink55, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!value) }.padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KazeType.body, color = k.ink)
            if (subtitle != null) Text(subtitle, style = KazeType.small, color = k.ink55)
        }
        Spacer(Modifier.width(12.dp))
        KazeSwitch(value, onChange)
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, value: T, label: (T) -> String, onChange: (T) -> Unit) {
    val k = LocalKaze.current
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open }.padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = KazeType.body, color = k.ink, modifier = Modifier.weight(1f))
            Text(label(value), style = KazeType.body, color = k.ink55)
        }
        if (open) {
            Column(Modifier.fillMaxWidth().liftCard().padding(vertical = 4.dp)) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(o); open = false }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label(o), style = KazeType.body, color = k.ink, modifier = Modifier.weight(1f))
                        if (o == value) Text("✓", style = KazeType.body, color = k.accent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Action(title: String, onClick: () -> Unit) {
    val k = LocalKaze.current
    Text(
        title, style = KazeType.body, color = k.accent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
    )
}
