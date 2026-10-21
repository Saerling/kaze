/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaze.browser.data.HistoryEntry
import app.kaze.browser.data.Suggestions
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.SpaceBackground
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.liftCard
import app.kaze.browser.ui.sheets.ClipboardBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Pantalla de búsqueda / dirección. Sugiere pestañas abiertas, historial y
 * búsquedas del motor elegido mientras escribes.
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
        history = if (query.isEmpty()) emptyList() else withContext(Dispatchers.IO) { app.db.suggest(query) }
        remote = emptyList()
        if (query.length >= 2 && app.settings.searchSuggestions && !private && !Urls.looksLikeUrl(query)) {
            delay(150) // esperar a que deje de escribir
            remote = withContext(Dispatchers.IO) { Suggestions.fetch(engine, query) }
        }
    }
    val openTabs = remember(query) {
        if (query.length < 2) emptyList()
        else app.store.tabs.filter {
            !it.isPrivate && (it.title.contains(query, true) || it.url.contains(query, true))
        }.take(3)
    }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(app.store.currentSpace?.colors.orEmpty())
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = k.ink)
                }
                Row(
                    Modifier.weight(1f).height(48.dp).liftCard(RoundedCornerShape(50)).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (private) Text("🕶️  ")
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
                                Text("Buscar con ${engine.label} o escribir dirección", style = KazeType.body, color = k.ink55, maxLines = 1)
                            }
                            inner()
                        },
                    )
                    if (field.text.isNotEmpty()) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { field = TextFieldValue("") }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "Borrar", tint = k.ink55, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                if (initial.isNotEmpty() && field.text == initial && Urls.looksLikeUrl(initial)) {
                    item(key = "current") {
                        Row2(Icons.Outlined.ContentCopy, "Copiar dirección", initial) {
                            ClipboardBridge.copy?.invoke(initial)
                        }
                    }
                }
                if (query.isNotEmpty() && !Urls.looksLikeUrl(query)) {
                    item(key = "search") {
                        Row2(Icons.Outlined.Search, query, "Buscar con ${engine.label}") { onSubmit(engine.searchUrl(query)) }
                    }
                } else if (query.isNotEmpty()) {
                    item(key = "go") {
                        Row2(Icons.AutoMirrored.Outlined.CallMade, Urls.fromInput(query, engine), "Ir a la dirección") {
                            onSubmit(Urls.fromInput(query, engine))
                        }
                    }
                }
                items(openTabs, key = { "t" + it.id }) { t ->
                    Row2(Icons.Outlined.Tab, t.displayTitle, "Cambiar a la pestaña · ${Urls.displayHost(t.url)}") { onSwitchTab(t) }
                }
                items(history, key = { "h" + it.url }) { h ->
                    SiteRow(h.url, h.title.ifBlank { Urls.displayHost(h.url) }, Urls.displayHost(h.url)) { onSubmit(h.url) }
                }
                items(remote.filter { !it.equals(query, true) }, key = { "s$it" }) { s ->
                    Row2(Icons.Outlined.Search, s, null) { onSubmit(engine.searchUrl(s)) }
                }
            }
        }
    }
}

@Composable
private fun Row2(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = k.ink55, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = KazeType.body, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = KazeType.small, color = k.ink55, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SiteRow(url: String, title: String, subtitle: String, onClick: () -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Favicon(url, 22.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = KazeType.body, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = KazeType.small, color = k.ink55, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
