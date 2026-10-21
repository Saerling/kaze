/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.kaze.browser.UrlUtils
import app.kaze.browser.tabs.Tab
import app.kaze.browser.tabs.TabManager
import app.kaze.browser.ui.theme.KazeMotion
import org.mozilla.geckoview.GeckoView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(manager: TabManager) {
    val tab = manager.selected
    var mostrarPestanas by remember { mutableStateOf(false) }

    BackHandler(enabled = tab?.canGoBack == true) { tab?.session?.goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // ── Contenido web ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> GeckoView(ctx) },
                update = { view ->
                    val sesion = tab?.session
                    if (sesion != null && view.session !== sesion) {
                        view.releaseSession()
                        view.setSession(sesion)
                    }
                },
            )
        }

        // ── Barra de progreso fina ──
        val progreso by animateFloatAsState(
            targetValue = (tab?.progress ?: 100) / 100f,
            animationSpec = tween(KazeMotion.RAPIDA),
            label = "progreso",
        )
        AnimatedVisibility(visible = progreso < 1f, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(progreso)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        // ── Barra inferior (al alcance del pulgar) ──
        BarraInferior(
            tab = tab,
            cantidadPestanas = manager.tabs.size,
            onSubmit = manager::load,
            onAtras = { tab?.session?.goBack() },
            onRecargar = { tab?.session?.reload() },
            onPestanas = { mostrarPestanas = true },
        )
    }

    if (mostrarPestanas) {
        val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { mostrarPestanas = false },
            sheetState = estadoHoja,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            ListaPestanas(
                manager = manager,
                onElegir = {
                    manager.select(it)
                    mostrarPestanas = false
                },
                onNueva = {
                    manager.newTab(UrlUtils.HOME)
                    mostrarPestanas = false
                },
            )
        }
    }
}

@Composable
private fun BarraInferior(
    tab: Tab?,
    cantidadPestanas: Int,
    onSubmit: (String) -> Unit,
    onAtras: () -> Unit,
    onRecargar: () -> Unit,
    onPestanas: () -> Unit,
) {
    val foco = LocalFocusManager.current
    val pedirFoco = remember { FocusRequester() }
    var editando by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf(TextFieldValue("")) }
    val urlActual = tab?.url.orEmpty()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!editando) {
            IconButton(onClick = onAtras, enabled = tab?.canGoBack == true) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
        }

        Surface(
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = if (editando) texto else TextFieldValue(UrlUtils.display(urlActual)),
                    onValueChange = { texto = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        onSubmit(texto.text)
                        foco.clearFocus()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(pedirFoco)
                        .onFocusChanged { f ->
                            if (f.isFocused && !editando) {
                                // Al tocar la barra: mostrar la URL completa seleccionada, lista para reemplazar
                                texto = TextFieldValue(urlActual, TextRange(0, urlActual.length))
                            }
                            editando = f.isFocused
                        },
                )
            }
        }

        if (editando) {
            IconButton(onClick = { foco.clearFocus() }) {
                Icon(Icons.Filled.Close, contentDescription = "Cancelar")
            }
        } else {
            IconButton(onClick = onRecargar) {
                Icon(Icons.Filled.Refresh, contentDescription = "Recargar")
            }
            ContadorPestanas(cantidadPestanas, onPestanas)
        }
    }
}

@Composable
private fun ContadorPestanas(cantidad: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp, end = 6.dp)
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (cantidad > 99) "∞" else cantidad.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ListaPestanas(manager: TabManager, onElegir: (Tab) -> Unit, onNueva: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Pestañas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onNueva) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Nueva")
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(manager.tabs, key = { it.id }) { t ->
                FilaPestana(
                    tab = t,
                    seleccionada = t === manager.selected,
                    onClick = { onElegir(t) },
                    onCerrar = { manager.close(t) },
                )
            }
        }
    }
}

@Composable
private fun FilaPestana(tab: Tab, seleccionada: Boolean, onClick: () -> Unit, onCerrar: () -> Unit) {
    val fondo = if (seleccionada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Punto de color: pestaña cargada en memoria (azul) o descargada (gris)
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (tab.isLoaded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                UrlUtils.display(tab.url),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCerrar) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar pestaña")
        }
    }
}
