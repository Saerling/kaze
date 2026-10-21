/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.FloatingPill
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.liftCard

/** Barra superior del navegador: candado, dirección y recargar, sobre el color del espacio. */
@Composable
fun BrowserTopBar(tab: Tab, tint: Color, onAddress: () -> Unit, onReload: () -> Unit, onStop: () -> Unit) {
    val k = LocalKaze.current
    val app = LocalApp.current
    val engine = app.settings.searchEngine
    val label = remember(tab.url, engine) { Urls.searchTermsOf(tab.url, engine) ?: Urls.displayHost(tab.url) }

    Column(Modifier.fillMaxWidth().background(tint).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .height(44.dp)
                .tap(onAddress)
                .liftCard(RoundedCornerShape(50))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                when {
                    tab.isPrivate -> Text("🕶️")
                    tab.url.startsWith("about:") -> Unit
                    tab.isSecure -> Icon(Icons.Outlined.Lock, "Conexión segura", tint = k.ink55, modifier = Modifier.size(16.dp))
                    else -> Icon(Icons.Outlined.Warning, "Conexión no segura", tint = Color(0xFFC0563A), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                label.ifBlank { "Buscar o escribir dirección" },
                style = KazeType.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (label.isBlank()) k.ink55 else k.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { if (tab.loading) onStop() else onReload() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (tab.loading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                    if (tab.loading) "Detener" else "Recargar",
                    tint = k.ink70, modifier = Modifier.size(20.dp),
                )
            }
        }
        // Progreso de carga: línea fina bajo la barra.
        val progress by animateFloatAsState(tab.progress / 100f, tween(160), label = "progress")
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (tab.loading) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0.05f, 1f)).height(2.dp).background(k.accent))
            }
        }
    }
}

/** Controles flotantes abajo: atrás/adelante a la izquierda, espacios y menú a la derecha. */
@Composable
fun BrowserBottomPills(
    tab: Tab,
    visible: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpaces: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingPill {
                Row(Modifier.padding(horizontal = 4.dp)) {
                    PillButton(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", enabled = true, onClick = onBack)
                    PillButton(Icons.AutoMirrored.Outlined.ArrowForward, "Adelante", enabled = tab.canGoForward, onClick = onForward)
                }
            }
            FloatingPill {
                Row(Modifier.padding(horizontal = 4.dp)) {
                    PillButton(Icons.Outlined.GridView, "Espacios y pestañas", enabled = true, onClick = onSpaces)
                    PillButton(Icons.Outlined.MoreHoriz, "Menú", enabled = true, onClick = onMenu)
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val k = LocalKaze.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = k.ink, modifier = Modifier.size(22.dp).alpha(if (enabled) 1f else 0.3f))
    }
}

/** Barra de "buscar en la página". */
@Composable
fun FindBar(
    query: String,
    current: Int,
    total: Int,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = LocalKaze.current
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(query) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    FloatingPill(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.padding(start = 18.dp, end = 4.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onQuery(it) },
                singleLine = true,
                textStyle = KazeType.body.copy(color = k.ink),
                cursorBrush = SolidColor(k.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Buscar en la página", style = KazeType.body, color = k.ink55)
                    inner()
                },
            )
            if (text.isNotEmpty()) {
                Text(if (total > 0) "$current/$total" else "0/0", style = KazeType.small, color = k.ink55)
                Spacer(Modifier.width(4.dp))
            }
            PillButton(Icons.Outlined.KeyboardArrowUp, "Anterior", enabled = total > 0, onClick = onPrev)
            PillButton(Icons.Outlined.KeyboardArrowDown, "Siguiente", enabled = total > 0, onClick = onNext)
            PillButton(Icons.Outlined.Close, "Cerrar", enabled = true, onClick = onClose)
        }
    }
}
