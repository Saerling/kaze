/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.kaze.browser.data.Favorite
import app.kaze.browser.data.Space
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.FaviconTile
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.SystemRed
import app.kaze.browser.ui.theme.card
import app.kaze.browser.ui.theme.liftCard

/**
 * Editar Favoritos: quitar (⊖), reordenar arrastrando el asa (≡) y añadir desde las pestañas abiertas
 * (la página en la que estabas va primero) o escribiendo una dirección. No hay duplicados: lo que ya
 * está en Favoritos no se ofrece y una dirección repetida no se añade.
 */
@Composable
fun FavoritesSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val k = LocalKaze.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val wasDefault = remember { !store.favoritesEdited() }
    var items by remember { mutableStateOf(store.favorites()) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(density) { RowHeight.toPx() }
    fun commit(list: List<Favorite>) { store.setFavorites(list); items = store.favorites() }

    KazeSheet(onDismiss) {
        SheetTitle("Favoritos", "OK", onDismiss)
        if (wasDefault && items.isNotEmpty()) {
            Text(
                "Ahora se muestran tus Essentials. Al cambiar algo, Fyx guarda tu propia lista y ya no cambia sola.",
                style = KazeType.footnote, color = k.ink55, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                "Aún no tienes Favoritos. Añade una pestaña abierta o escribe una dirección.",
                style = KazeType.subhead, color = k.ink55, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(horizontal = 16.dp).card(RoundedCornerShape(14.dp)),
            ) {
                items(items, key = { Favorite.key(it.url) }) { f ->
                    val key = Favorite.key(f.url)
                    val dragging = dragKey == key
                    Row(
                        Modifier
                            .then(
                                if (dragging) Modifier.zIndex(1f).graphicsLayer {
                                    translationY = dragY; scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 18f
                                    shape = RoundedCornerShape(12.dp); clip = true
                                } else Modifier.animateItem(
                                    fadeInSpec = tween(200), fadeOutSpec = tween(140),
                                    placementSpec = spring(0.9f, 500f, IntOffset.VisibilityThreshold),
                                ),
                            )
                            .fillMaxWidth().height(RowHeight).background(k.card).padding(start = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { commit(items.filter { Favorite.key(it.url) != key }) }
                                .semantics { contentDescription = "Quitar ${f.title.ifBlank { Urls.displayHost(f.url) }} de Favoritos" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.RemoveCircle, null, tint = SystemRed, modifier = Modifier.size(22.dp)) }
                        FaviconTile(f.url, 34.dp, 19.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val host = Urls.displayHost(f.url)
                            Text(f.title.ifBlank { host }, style = KazeType.body, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // El sitio solo si dice algo más que el nombre.
                            if (f.title.isNotBlank() && f.title != host) Text(host, style = KazeType.footnote, color = k.ink55, maxLines = 1)
                        }
                        // Asa: se arrastra al momento (sin pulsación larga), como en iOS.
                        Box(
                            Modifier.size(44.dp).semantics { contentDescription = "Mover" }.pointerInput(key) {
                                detectDragGestures(
                                    onDragStart = { dragKey = key; dragY = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragEnd = { dragKey = null; dragY = 0f; commit(items) },
                                    onDragCancel = { dragKey = null; dragY = 0f; commit(items) },
                                ) { c, d ->
                                    c.consume()
                                    dragY += d.y
                                    val i = items.indexOfFirst { Favorite.key(it.url) == key }
                                    // Al pasar media fila cambia de sitio; el desplazamiento se compensa para que no salte.
                                    if (dragY > rowPx / 2 && i in 0 until items.lastIndex) {
                                        items = items.toMutableList().apply { add(i + 1, removeAt(i)) }; dragY -= rowPx
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    } else if (dragY < -rowPx / 2 && i > 0) {
                                        items = items.toMutableList().apply { add(i - 1, removeAt(i)) }; dragY += rowPx
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.DragHandle, null, tint = k.ink35, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }

        // Añadir escribiendo una dirección.
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        fun addTyped() {
            val t = text.trim()
            if (!Urls.looksLikeUrl(t)) { error = "Escribe una dirección, por ejemplo ejemplo.com"; return }
            val url = Urls.fromInput(t, app.settings.searchEngine)
            if (store.addFavorite(url, Urls.displayHost(url))) { items = store.favorites(); text = ""; error = null }
            else error = "Ya está en Favoritos"
        }
        Text(
            "Añadir", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink55,
            modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).liftCard(RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text, onValueChange = { text = it; error = null }, singleLine = true,
                textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTyped() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Dirección del Favorito" },
                decorationBox = { inner -> if (text.isEmpty()) Text("Escribe una dirección", style = KazeType.body, color = k.ink35); inner() },
            )
            Text(
                "Añadir", style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (text.isBlank()) k.ink35 else k.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = text.isNotBlank()) { addTyped() }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        error?.let { Text(it, style = KazeType.footnote, color = SystemRed, modifier = Modifier.padding(start = 20.dp, top = 6.dp)) }

        // Añadir desde las pestañas abiertas: la última que usaste primero (la página en la que estabas).
        val keys = items.map { Favorite.key(it.url) }.toSet()
        val open = store.tabs.filter { !it.isPrivate && !it.isStart && it.spaceId != Space.PRIVATE_ID && it.url.startsWith("http") }
            .sortedByDescending { it.lastUsed }.distinctBy { Favorite.key(it.pinnedUrl ?: it.url) }
            .filter { Favorite.key(it.pinnedUrl ?: it.url) !in keys }.take(5)
        if (open.isNotEmpty()) {
            Text(
                "Desde tus pestañas", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.ink55,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(RoundedCornerShape(14.dp)), verticalArrangement = Arrangement.Top) {
                open.forEach { t ->
                    val url = t.pinnedUrl ?: t.url
                    Row(
                        Modifier.fillMaxWidth().height(RowHeight).clickable {
                            if (store.addFavorite(url, t.displayTitle)) { items = store.favorites(); haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
                        }.padding(horizontal = 12.dp).semantics { contentDescription = "Añadir ${t.displayTitle} a Favoritos" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FaviconTile(url, 34.dp, 19.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.displayTitle, style = KazeType.body, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Urls.displayHost(url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
                        }
                        Icon(Icons.Outlined.AddCircle, null, tint = k.accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

private val RowHeight = 58.dp
