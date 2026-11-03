/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import app.kaze.browser.zsync.ZenSync
import app.kaze.browser.ui.components.FaviconTile
import app.kaze.browser.zsync.MozillaSignInScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.outlined.Check
import app.kaze.browser.ui.theme.glassFill
import app.kaze.browser.engine.Engine
import app.kaze.browser.data.AccentColor
import app.kaze.browser.data.UiStyle
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
fun LibrarySheet(onDismiss: () -> Unit, onOpenUrl: (String) -> Unit, onOpenTab: (Tab) -> Unit, initialSection: Int = 0) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var section by remember { mutableIntStateOf(initialSection) }
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
                val favs = app.store.favorites()
                if (favs.isEmpty()) Empty("Aún no tienes Favoritos.\nEn una página, toca ⋯ → Favorito, o Editar en la página de inicio.")
                else IosGroup {
                    favs.forEachIndexed { i, f ->
                        IosRow(f.title.ifBlank { Urls.displayHost(f.url) }, subtitle = Urls.displayHost(f.url), leading = { Favicon(f.url, 26.dp) }, divider = i < favs.lastIndex) {
                            onDismiss(); app.store.favoriteTab(f)?.let(onOpenTab) ?: onOpenUrl(f.url)
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
    val context = androidx.compose.ui.platform.LocalContext.current

    KazeSheet(onDismiss, full = true) {
        SheetTitle("Ajustes", "OK", onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (needsRestart) {
                Spacer(Modifier.height(8.dp))
                IosGroup {
                    IosRow("Reiniciar Fyx para aplicar los cambios", titleColor = k.accent, divider = false, onClick = onRestart)
                }
            }

            IosSectionHeader("Búsqueda")
            IosGroup {
                Choice("Buscador", SearchEngine.entries, s.searchEngine, { it.label }) { s.searchEngine = it }
                Toggle("Sugerencias del buscador", s.searchSuggestions, divider = false) { s.searchSuggestions = it }
            }
            Note("Con las sugerencias activas, lo que escribes se envía al buscador.")

            IosSectionHeader("Diseño")
            IosGroup {
                Choice("Estilo", UiStyle.entries, s.uiStyle, { it.label }) { s.uiStyle = it }
                BarLayoutPicker(s.barLayout) { s.barLayout = it; s.barTop = false }
                Toggle("Pestañas en la barra (teléfono)", s.tabStripPhone) { s.tabStripPhone = it }
                Choice("Tema", ThemeMode.entries, s.theme, { it.label }) { s.theme = it; needsRestart = true }
                Choice("Tamaño del texto de las páginas", listOf(85, 100, 115, 130, 150, 175), s.textSize, { "$it %" }) {
                    s.textSize = it; Engine.applyTextSize(app.runtime, it)
                }
                Choice(
                    "Vista de pestañas", listOf("grid", "compact", "list"), s.tabsView,
                    { when (it) { "compact" -> "Compacta"; "list" -> "Lista"; else -> "Tarjetas" } }, divider = false,
                ) { s.tabsView = it }
            }

            // Personalización con las tarjetas de Mochy: color, esquinas, vidrio y movimiento.
            IosSectionHeader("Personalización")
            PersonalizationSection(s)
            Note("Transparencia del vidrio: más alto deja ver más la página a través de las barras; más bajo da un vidrio más opaco y con más contraste.")
            Note("Material You usa los colores de tu fondo de pantalla (Android 12 o más nuevo) con el color en Automático.")

            IosSectionHeader("Tableta")
            IosGroup {
                Choice(
                    "Modo tableta", listOf("auto", "on", "off"), s.tabletMode,
                    { when (it) { "on" -> "Activado"; "off" -> "Desactivado"; else -> "Automático" } },
                ) { s.tabletMode = it }
                Toggle("Versión de escritorio de las páginas", s.tabletDesktop, divider = false) { s.tabletDesktop = it }
            }
            Note("En tableta, Fyx usa más columnas en las pestañas, centra la barra y pide las páginas de escritorio.")


            IosSectionHeader("Página de inicio")
            IosGroup {
                Toggle("Favoritos", s.startFavorites) { s.startFavorites = it }
                Toggle("Reanudar navegación", s.startResume) { s.startResume = it }
                Toggle("Visitados con frecuencia", s.startFrequent) { s.startFrequent = it }
                Toggle("Reporte de privacidad", s.startPrivacy) { s.startPrivacy = it }
                Toggle("Lista de lectura", s.startReading, divider = false) { s.startReading = it }
            }

            IosSectionHeader("Navegador")
            IosGroup {
                IosRow("Navegador predeterminado", titleColor = k.accent) {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                }
                IosRow("Autocompletar y contraseñas (Android)", titleColor = k.accent) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                                .setData(android.net.Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
                IosRow("Descargas", titleColor = k.accent, divider = false) {
                    runCatching { context.startActivity(android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                }
            }
            Note("Fyx guarda tus contraseñas cifradas en el teléfono y las rellena solo (${app.logins.count} guardadas). También funciona con el autocompletado de Android (Google, Bitwarden…).")

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

            ZenSyncSection()

            IosSectionHeader("Privacidad y seguridad")
            IosGroup {
                Choice("Protección contra rastreo", TrackingLevel.entries, s.tracking, { it.label }) { s.tracking = it; needsRestart = true }
                Choice("DNS seguro", DnsMode.entries, s.dns, { it.label }) { s.dns = it; needsRestart = true }
                Toggle("JavaScript", s.javascript) { s.javascript = it; needsRestart = true }
                var adblock by remember { mutableStateOf(s.adblock) }
                Toggle("Bloquear anuncios (uBlock Origin)", adblock) { adblock = it; app.setAdblock(it) }
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
                IosRow("Motor", trailing = { Text("Gecko (Firefox)", style = KazeType.body, color = k.ink55) })
                IosRow("Ayuda y comentarios", titleColor = k.accent) {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Saerling/kaze/issues"))) }
                }
                // Aviso de licencia (GPL-3.0 §5 d): licencia, sin garantía, créditos y dónde está el código.
                IosRow("Licencia y créditos (NOTICE.md)", titleColor = k.accent) {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Saerling/kaze/blob/main/android/NOTICE.md"))) }
                }
                IosRow("Código fuente", titleColor = k.accent, divider = false) {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Saerling/kaze"))) }
                }
            }
            Note("Fyx para Android es software libre (GPL-3.0), sin garantía. Incluye código de Mochy Music (realidkroo) y Z-Sync (Kjell Behrends).")
            Note("Código fuente: https://github.com/Saerling/kaze · Licencia y créditos: android/NOTICE.md")
            Note("Inspirado en Safari, Zen Browser y Z-Sync; no está afiliado a ellos.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    val k = LocalKaze.current
    Text(text, style = KazeType.footnote, color = k.ink55, modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 7.dp))
}

/** "Transparencia del vidrio" (como en la familia 27 de Apple): deslizador 0–100 % con vista previa en vivo. */
/**
 * Personalización (Ajustes → Diseño), con las tarjetas de personalización de Mochy (ui/mochy/Customisation.kt):
 * color principal, redondez y curva de las esquinas, vidrio y su transparencia, y animaciones y transiciones.
 * Cada control cambia la interfaz en vivo.
 */
@Composable
internal fun PersonalizationSection(s: app.kaze.browser.data.Settings) {
    val k = LocalKaze.current
    val colors = app.kaze.browser.ui.mochy.CustomiseColors(
        card = k.card, row = k.lift, text = k.ink, muted = k.ink55, swatch = k.accent.copy(alpha = 0.35f), highlight = k.accent,
    )
    val toggle: @Composable (Boolean, (Boolean) -> Unit) -> Unit = { v, on -> KazeSwitch(v, on) }
    // Radio del estilo (lo que hay con "Restablecer").
    val styleCorner = k.copy(cornerOverride = -1).corner
    // Los deslizadores usan un valor local mientras se arrastran; la preferencia se escribe al soltar.
    var corner by remember(k.corner) { mutableStateOf(k.corner.toFloat()) }
    var glass by remember(s.glassTransparency) { mutableStateOf(s.glassTransparency) }
    var speed by remember(s.animationSpeed) { mutableStateOf(s.animationSpeed) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        // Color principal: muestras redondas (el mismo ajuste "Color" de siempre).
        app.kaze.browser.ui.mochy.EffectCard(title = "Color", colors = colors) {
            AccentSwatches(s.accent) { s.accent = it }
            Spacer(Modifier.height(6.dp))
            Text(s.accent.label, style = KazeType.footnote, color = k.ink55)
        }
        app.kaze.browser.ui.mochy.CornerRoundingSection(
            radiusDp = corner, defaultRadius = styleCorner.toFloat(), maxRadius = 32f, smooth = k.smooth, colors = colors,
            onRadiusChange = { corner = it }, onReset = { s.cornerRadius = -1 },
            onChangeFinished = { s.cornerRadius = corner.toInt() },
        )
        app.kaze.browser.ui.mochy.EffectCard(title = "Forma de las esquinas", colors = colors) {
            Text("Continuas: la curva de los íconos de apps (superelipse), sin el quiebre de un arco.", style = KazeType.footnote, color = k.ink55)
            Spacer(Modifier.height(10.dp))
            app.kaze.browser.ui.mochy.MiniSegmented(listOf("Redondas", "Continuas"), if (s.smoothCorners) 1 else 0, { s.smoothCorners = it == 1 }, colors)
        }
        app.kaze.browser.ui.mochy.EffectCard(
            title = "Vidrio", colors = colors, toggle = { toggle(s.glassOn) { s.glassOn = it } },
            canReset = !s.glassOn || s.glassTransparency != 20, onReset = { s.glassOn = true; s.glassTransparency = 20 },
            preview = {
                // Vista previa fija: una barra de vidrio sobre rayas, con el vidrio de verdad.
                Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { i -> Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth(if (i % 2 == 0) 1f else 0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(k.accent.copy(alpha = 0.6f))) }
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(28.dp).clip(RoundedCornerShape(50)).background(k.glassFill()))
            },
        ) {
            Text(if (s.glassOn) "Las barras dejan ver la página a través." else "Barras opacas, con el mayor contraste.", style = KazeType.footnote, color = k.ink55)
        }
        app.kaze.browser.ui.mochy.CustomiseSliderCard(
            title = "Transparencia del vidrio", startLabel = "Opaco", endLabel = "Transparente",
            fraction = glass / 100f, colors = colors, valueText = "$glass %", enabled = s.glassOn,
            onFractionChange = { glass = (it * 100).toInt() }, onChangeFinished = { s.glassTransparency = glass },
        )
        app.kaze.browser.ui.mochy.AnimationSection(
            enabled = s.animationsOn, onEnabled = { s.animationsOn = it },
            speed = speed / 100f, onSpeed = { speed = (it * 100).toInt() }, onSpeedFinished = { s.animationSpeed = speed },
            colors = colors, toggle = toggle,
        ) {
            Spacer(Modifier.height(11.dp))
            app.kaze.browser.ui.mochy.EffectCard(title = "Transiciones", colors = colors) {
                Text("Mochy: la vista de pestañas crece con un resorte y las tarjetas aparecen en ráfaga.", style = KazeType.footnote, color = k.ink55)
                Spacer(Modifier.height(10.dp))
                app.kaze.browser.ui.mochy.MiniSegmented(listOf("Fyx", "Mochy"), if (s.mochyTransitions) 1 else 0, { s.mochyTransitions = it == 1 }, colors, enabled = s.animationsOn)
            }
        }
    }
}

/** Muestras del color principal; la de Automático lleva una "A". */
@Composable
private fun AccentSwatches(value: AccentColor, onPick: (AccentColor) -> Unit) {
    val k = LocalKaze.current
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AccentColor.entries.forEach { c ->
            val sel = c == value
            val ring by androidx.compose.animation.animateColorAsState(if (sel) k.ink else Color.Transparent, label = "ring")
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(ring).padding(3.dp).clip(CircleShape)
                    .background(if (c == AccentColor.AUTO) k.lift else Color(c.argb))
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(c) }
                    .semantics { contentDescription = "Color ${c.label}"; selected = sel },
                contentAlignment = Alignment.Center,
            ) {
                if (c == AccentColor.AUTO) Text("A", style = KazeType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = k.ink)
                else if (sel) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Toggle(title: String, value: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    IosRow(title, divider = divider, trailing = { KazeSwitch(value, onChange) }) { onChange(!value) }
}

/** Opción con valor a la derecha; al tocarla se despliega la lista con ✓ (como iOS). */
/** Diseño de la barra, como en Ajustes de Safari: tres teléfonos con la barra dibujada y un círculo para elegir. */
@Composable
private fun BarLayoutPicker(value: String, onChange: (String) -> Unit) {
    val k = LocalKaze.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
        Text("Diseño de la barra", style = KazeType.body, color = k.ink, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("compact" to "Compacta", "bottom" to "Abajo", "top" to "Arriba").forEach { (id, label) ->
                val sel = id == value
                val scale by androidx.compose.animation.core.animateFloatAsState(if (sel) 1.08f else 1f, androidx.compose.animation.core.spring(0.5f, 500f), label = "pick")
                val line by androidx.compose.animation.animateColorAsState(if (sel) k.accent else k.ink55.copy(alpha = 0.35f), label = "pickC")
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable { onChange(id) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(56.dp, 100.dp)
                            .border(2.dp, line, RoundedCornerShape(13.dp)).padding(6.dp),
                    ) {
                        val bar = line.copy(alpha = if (sel) 0.85f else 0.6f)
                        val pill = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(bar)
                        val tools = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(3.dp).clip(RoundedCornerShape(50)).background(bar.copy(alpha = 0.5f))
                        when (id) {
                            "compact" -> Box(pill.align(Alignment.BottomCenter))
                            "bottom" -> Column(Modifier.align(Alignment.BottomCenter)) { Box(pill); Spacer(Modifier.height(4.dp)); Box(tools) }
                            else -> { Box(pill.align(Alignment.TopCenter)); Box(tools.align(Alignment.BottomCenter)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(label, style = KazeType.footnote, color = if (sel) k.ink else k.ink55)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, line, CircleShape).background(if (sel) k.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(sel, enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.5f, 600f)), exit = androidx.compose.animation.scaleOut()) {
                            Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

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


/** Sincronización con Fyx/Zen de escritorio por tu cuenta de Mozilla (como Z-Sync). */
@Composable
private fun ZenSyncSection() {
    val app = LocalApp.current
    val k = LocalKaze.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var email by remember { mutableStateOf(ZenSync.email(context)) }
    var signIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(ZenSync.lastSync(context)) }
    /** Confirmación pendiente de un cambio frenado: ("close" | "delete") y el conjunto exacto que se vio. */
    var confirm by remember { mutableStateOf<Pair<String, Set<String>>?>(null) }
    fun pull() {
        busy = true
        scope.launch {
            runCatching { app.tabSync.syncNow().also { runCatching { ZenSync.syncPasswords(context, app.logins) } } }
                .onSuccess { app.ui.show(if (it == 0) "Todo al día con el escritorio" else if (it == 1) "1 pestaña traída del escritorio" else "$it pestañas traídas del escritorio") }
                .onFailure { app.ui.show("No se pudo sincronizar: ${it.message ?: it.javaClass.simpleName}") }
            busy = false
            last = ZenSync.lastSync(context)
        }
    }
    var passwords by remember { mutableStateOf(false) }
    IosSectionHeader("Cuenta y sincronización")
    if (email == null) {
        // Sin sesión: una tarjeta que explica qué se gana y un solo botón claro.
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(k.cardShape).background(k.card).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tus pestañas y contraseñas en todos tus dispositivos", style = KazeType.headline, color = k.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Inicia sesión con tu cuenta de Mozilla, la misma de Fyx en el computador.", style = KazeType.subhead, color = k.ink55, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(
                "Iniciar sesión", style = KazeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(k.accent).clickable { signIn = true }.padding(horizontal = 28.dp, vertical = 11.dp),
            )
        }
    } else {
        // Con sesión: quién eres, cuándo se sincronizó y el botón principal a la vista.
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(k.cardShape).background(k.card).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(k.accent), contentAlignment = Alignment.Center) {
                Text(email.orEmpty().take(1).uppercase(), style = KazeType.headline, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(email.orEmpty(), style = KazeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = k.ink, maxLines = 1)
                Text(
                    if (busy) "Sincronizando…" else if (last == 0L) "Aún no se ha sincronizado" else "Sincronizado " + android.text.format.DateUtils.getRelativeTimeSpanString(last),
                    style = KazeType.footnote, color = k.ink55,
                )
                val failed = remember(busy, last) { ZenSync.failedChanges(context) }
                if (!busy && failed > 0) Text(
                    if (failed == 1) "1 cambio no se pudo subir; se reintentará" else "$failed cambios no se pudieron subir; se reintentarán",
                    style = KazeType.footnote, color = SystemRed,
                )
            }
            Text(
                "Sincronizar", style = KazeType.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = k.accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(k.accent.copy(alpha = 0.12f)).clickable(enabled = !busy) { pull() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        IosGroup {
            IosRow("Contraseñas guardadas", trailing = { Text("${app.logins.count}", style = KazeType.body, color = k.ink55) }, chevron = true) { passwords = true }
            IosRow("Enviar la pestaña actual al computador", titleColor = k.accent) {
                val t = app.store.selectedTab ?: return@IosRow
                scope.launch {
                    runCatching { ZenSync.send(context, t.url, t.displayTitle, app.store.spaceById(t.spaceId)?.name) }
                        .onSuccess { app.ui.show("Enviada al computador") }.onFailure { app.ui.show("No se pudo enviar: ${it.message}") }
                }
            }
            // El freno de seguridad retuvo cambios: se confirman por separado, con un diálogo que dice cuántas.
            // Estado en vivo: se actualiza con cada sincronización, también las de segundo plano.
            val paused by app.tabSync.paused.collectAsState()
            if (paused.closeIds.isNotEmpty()) IosRow(
                "Cerrar aquí las ${paused.closeIds.size} que faltan en el escritorio", titleColor = SystemRed, enabled = !busy,
                subtitle = "En pausa por seguridad: faltan en Zen",
            ) { confirm = "close" to paused.closeIds }
            if (paused.deleteIds.isNotEmpty()) IosRow(
                "Borrar en el escritorio las ${paused.deleteIds.size} que quitaste aquí", titleColor = SystemRed, enabled = !busy,
                subtitle = "En pausa por seguridad: faltan en este teléfono",
            ) { confirm = "delete" to paused.deleteIds }
            val discarded = remember(busy, last) { ZenSync.discardedCount(context) }
            if (discarded > 0) IosRow(
                "Volver a traer espacios descartados", enabled = !busy,
                subtitle = "Los espacios de Zen que borraste aquí vuelven en la próxima sincronización",
            ) {
                ZenSync.restoreDiscarded(context)
                pull()
            }
            IosRow("Cerrar sesión", titleColor = SystemRed, divider = false) { ZenSync.signOut(context, app.store); app.tabSync.refreshPaused(); email = null }
        }
        Note("Se sincronizan en ambos sentidos los espacios, las pestañas fijadas y sus grupos, las Essentials y las contraseñas (las pestañas normales se quedan en cada dispositivo, como en Zen). Las contraseñas viajan cifradas de extremo a extremo, como en Firefox.")
    }
    if (email == null) IosGroup {
        IosRow("Contraseñas guardadas", trailing = { Text("${app.logins.count}", style = KazeType.body, color = k.ink55) }, chevron = true, divider = false) { passwords = true }
    }
    if (passwords) PasswordsSheet { passwords = false }
    confirm?.let { (what, ids) ->
        val close = what == "close"
        val n = ids.size
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = k.paper,
            title = { Text(if (close) "¿Cerrar $n fijadas en este teléfono?" else "¿Borrar $n fijadas en el escritorio?", style = KazeType.headline, color = k.ink) },
            text = {
                Text(
                    if (close) "Faltan en Zen $n de tus fijadas sincronizadas. Si las quitaste en el escritorio, se cerrarán también aquí. No se borra nada en Zen."
                    else "Faltan en este teléfono $n de tus fijadas sincronizadas. Si las quitaste aquí a propósito, se borrarán también en Zen de escritorio. Esto no se puede deshacer desde el teléfono.",
                    style = KazeType.body, color = k.ink70,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = null
                    busy = true
                    scope.launch {
                        // Se aplica solo el conjunto que se vio; si ya es otro, no se toca nada.
                        runCatching { if (close) app.tabSync.confirmCloses(ids) else app.tabSync.confirmDeletes(ids) }
                            .onSuccess { applied ->
                                app.ui.show(
                                    if (!applied) "La situación cambió; revisa de nuevo"
                                    else if (close) "Cerradas aquí" else "Borradas en el escritorio",
                                )
                            }
                            .onFailure { app.ui.show("No se pudo sincronizar: ${it.message ?: it.javaClass.simpleName}") }
                        busy = false
                        last = ZenSync.lastSync(context)
                    }
                }) { Text(if (close) "Cerrar $n" else "Borrar $n", color = SystemRed) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Cancelar", color = k.accent) } },
        )
    }
    if (signIn) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { signIn = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            MozillaSignInScreen(
                onFinishLogin = { login ->
                    ZenSync.completeLogin(context, login)
                    email = login.email; signIn = false; pull()
                },
                onCancel = { signIn = false },
            )
        }
    }
}


/** Contraseñas guardadas: por sitio, con búsqueda; tocar copia la contraseña, ✕ la borra (también de la cuenta). */
@Composable
private fun PasswordsSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var query by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<String?>(null) }
    val list = remember(version, query) { app.logins.all().filter { query.isBlank() || it.first.contains(query, true) || it.second.contains(query, true) } }
    KazeSheet(onDismiss, full = true) {
        SheetTitle("Contraseñas", "OK", onDismiss)
        androidx.compose.foundation.text.BasicTextField(
            query, { query = it }, singleLine = true, textStyle = KazeType.body.copy(color = k.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(k.accent),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(k.lift).padding(12.dp),
            decorationBox = { inner -> if (query.isEmpty()) Text("Buscar sitio o usuario", style = KazeType.body, color = k.ink55); inner() },
        )
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) Text("No hay contraseñas guardadas.", style = KazeType.body, color = k.ink55, modifier = Modifier.padding(32.dp))
        else IosGroup {
            list.forEachIndexed { i, (site, user, pass) ->
                val key = "$site|$user"
                Row(
                    Modifier.fillMaxWidth().clickable { ClipboardBridge.copy?.invoke(pass); app.ui.show("Contraseña copiada") }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FaviconTile("https://$site", 34.dp, 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(site, style = KazeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = k.ink, maxLines = 1)
                        Text(user.ifBlank { "(sin usuario)" } + "  ·  " + (if (shown == key) pass else "••••••••"), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                    }
                    Text(if (shown == key) "Ocultar" else "Ver", style = KazeType.footnote, color = k.accent, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { shown = if (shown == key) null else key }.padding(8.dp))
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { app.logins.remove(site, user); version++ }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Borrar", tint = k.ink55, modifier = Modifier.size(16.dp))
                    }
                }
                if (i < list.lastIndex) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(k.separator))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
