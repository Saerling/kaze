/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.kaze.browser.data.HistoryEntry
import app.kaze.browser.data.Suggestions
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.IosGroup
import app.kaze.browser.ui.components.IosRow
import app.kaze.browser.ui.components.IosSectionHeader
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.SiteTile
import app.kaze.browser.ui.sheets.ClipboardBridge
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.glass
import app.kaze.browser.ui.theme.liftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Búsqueda / dirección como en Safari: el campo queda abajo, sobre el teclado, y
 * encima van las coincidencias (pestañas abiertas, historial) y las sugerencias del buscador.
 * Con el campo vacío se muestran los Favoritos.
 */
@Composable
fun SearchScreen(
    initial: String,
    private: Boolean,
    onSubmit: (String) -> Unit,
    onSwitchTab: (Tab) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val engine = app.settings.searchEngine
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    var history by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var remote by remember { mutableStateOf(emptyList<String>()) }
    val query = field.text.trim()

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        history = if (query.isEmpty() || private) emptyList() else withContext(Dispatchers.IO) { app.db.suggest(query, 4) }
        remote = emptyList()
        if (query.length >= 2 && app.settings.searchSuggestions && !private && !Urls.looksLikeUrl(query)) {
            delay(150)
            remote = withContext(Dispatchers.IO) { Suggestions.fetch(engine, query) }
        }
    }
    val openTabs = remember(query) {
        if (query.length < 2) emptyList()
        else app.store.tabs.filter { !it.isPrivate && !it.isStart && (it.title.contains(query, true) || it.url.contains(query, true)) }.take(3)
    }
    val favorites = app.store.essentials()

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(k.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false) {
                if (query.isEmpty()) {
                    if (initial.isNotEmpty() && Urls.looksLikeUrl(initial)) {
                        item {
                            IosSectionHeader("Página actual")
                            IosGroup {
                                IosRow(initial, Icons.Outlined.ContentCopy, subtitle = "Copiar dirección", divider = false) {
                                    ClipboardBridge.copy?.invoke(initial)
                                }
                            }
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        item {
                            Text("Favoritos", style = KazeType.sectionHeader, color = k.ink, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp))
                            Column(Modifier.padding(horizontal = 12.dp)) {
                                favorites.take(8).chunked(4).forEach { row ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                        row.forEach { t ->
                                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                                SiteTile(t.pinnedUrl ?: t.url, t.displayTitle, onClick = { onSwitchTab(t) })
                                            }
                                        }
                                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        IosSectionHeader("Mejor coincidencia")
                        IosGroup {
                            if (Urls.looksLikeUrl(query)) {
                                IosRow(Urls.fromInput(query, engine), Icons.AutoMirrored.Outlined.CallMade, subtitle = "Ir al sitio", divider = openTabs.isNotEmpty() || history.isNotEmpty()) {
                                    onSubmit(Urls.fromInput(query, engine))
                                }
                            } else {
                                IosRow(query, Icons.Outlined.Search, subtitle = "Buscar con ${engine.label}", divider = openTabs.isNotEmpty() || history.isNotEmpty()) {
                                    onSubmit(engine.searchUrl(query))
                                }
                            }
                            openTabs.forEachIndexed { i, t ->
                                IosRow(
                                    t.displayTitle, subtitle = "Cambiar a pestaña · ${Urls.displayHost(t.url)}",
                                    leading = { Icon(Icons.Outlined.Tab, null, tint = k.accent, modifier = Modifier.size(22.dp)) },
                                    divider = i < openTabs.lastIndex || history.isNotEmpty(),
                                ) { onSwitchTab(t) }
                            }
                            history.forEachIndexed { i, h ->
                                IosRow(
                                    h.title.ifBlank { Urls.displayHost(h.url) }, subtitle = Urls.displayHost(h.url),
                                    leading = { Favicon(h.url, 22.dp) }, divider = i < history.lastIndex,
                                ) { onSubmit(h.url) }
                            }
                        }
                    }
                    val sugg = remote.filter { !it.equals(query, true) }
                    if (sugg.isNotEmpty()) {
                        item {
                            IosSectionHeader("Sugerencias de ${engine.label}")
                            IosGroup {
                                sugg.forEachIndexed { i, s ->
                                    IosRow(s, Icons.Outlined.Search, iconTint = k.ink55, divider = i < sugg.lastIndex) { onSubmit(engine.searchUrl(s)) }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Campo abajo, como en Safari, con "Cancelar".
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)
                    .glass(RoundedCornerShape(24.dp)).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).height(44.dp).liftCard(RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = k.ink55, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = KazeType.body.copy(color = k.ink),
                        cursorBrush = SolidColor(k.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { if (query.isNotEmpty()) onSubmit(Urls.fromInput(query, engine)) }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            if (field.text.isEmpty()) {
                                Text(if (private) "Búsqueda privada" else "Buscar o escribir dirección", style = KazeType.body, color = k.ink55, maxLines = 1)
                            }
                            inner()
                        },
                    )
                    if (field.text.isNotEmpty()) {
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { field = TextFieldValue("") }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Cancel, "Borrar", tint = k.ink35, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    "Cancelar", style = KazeType.body, color = k.accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
