/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.data.Tab
import app.kaze.browser.data.TabKind
import app.kaze.browser.ui.components.IosGroup
import app.kaze.browser.ui.components.IosRow
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.SystemRed
import org.mozilla.geckoview.StorageController

/** Lo que el menú principal le pide a la pantalla del navegador. */
class MenuActions(
    val onNewTab: () -> Unit,
    val onNewPrivate: () -> Unit,
    val onMove: () -> Unit,
    val onLibrary: () -> Unit,
    val onRecent: () -> Unit,
    val onClearData: () -> Unit,
    val onDownloads: () -> Unit,
    val onFind: () -> Unit,
    val onShare: () -> Unit,
    val onSavePdf: () -> Unit,
    val onSettings: () -> Unit,
    val onCustomizeHome: () -> Unit,
    val onHelp: () -> Unit,
    val onDefaultBrowser: () -> Unit,
)

/**
 * Menú principal (botón ⋯ de la barra): acciones rápidas arriba y todo lo del menú de Chrome debajo,
 * con la forma de una hoja de Safari.
 */
@Composable
fun MainMenuSheet(tab: Tab, onDismiss: () -> Unit, a: MenuActions) {
    val app = LocalApp.current
    val store = app.store
    val pool = app.pool
    val web = !tab.isStart && !tab.url.startsWith("about:")
    fun go(action: () -> Unit) = { onDismiss(); action() }

    KazeSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val fav = tab.kind == TabKind.ESSENTIAL
                QuickAction(if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (fav) "Favorito" else "Favorito", enabled = web && !tab.isPrivate, active = fav, modifier = Modifier.weight(1f)) {
                    store.setKind(tab, if (fav) TabKind.PINNED else TabKind.ESSENTIAL)
                    app.ui.show(if (fav) "Quitada de Favoritos" else "Añadida a Favoritos")
                }
                QuickAction(Icons.Outlined.Share, "Compartir", enabled = web, modifier = Modifier.weight(1f), onClick = go(a.onShare))
                QuickAction(Icons.Outlined.ContentCopy, "Copiar", enabled = web, modifier = Modifier.weight(1f)) {
                    ClipboardBridge.copy?.invoke(tab.url); onDismiss()
                }
                QuickAction(Icons.Outlined.Download, "PDF", enabled = web, modifier = Modifier.weight(1f), onClick = go(a.onSavePdf))
                QuickAction(Icons.Outlined.Refresh, "Recargar", enabled = web, modifier = Modifier.weight(1f)) {
                    pool.sessionOf(tab.id)?.reload(); onDismiss()
                }
            }

            IosGroup {
                IosRow("Nueva pestaña", Icons.Outlined.Add, onClick = go(a.onNewTab))
                IosRow("Nueva pestaña privada", Icons.Outlined.VisibilityOff, onClick = go(a.onNewPrivate))
                if (store.profileSpaces.size > 1 && !tab.isPrivate) {
                    IosRow("Mover a otro espacio", Icons.AutoMirrored.Outlined.DriveFileMove, onClick = go(a.onMove))
                }
                IosRow("Buscar en la página", Icons.Outlined.FindInPage, divider = web, onClick = go(a.onFind))
                if (web) {
                    IosRow(
                        "Versión de escritorio", Icons.Outlined.DesktopWindows, divider = false,
                        trailing = { KazeSwitch(tab.desktopMode) { pool.setDesktopMode(tab, it) } },
                    ) { pool.setDesktopMode(tab, !tab.desktopMode) }
                }
            }
            Spacer(Modifier.height(14.dp))
            IosGroup {
                IosRow("Historial", Icons.Outlined.History, onClick = go(a.onLibrary))
                IosRow("Favoritos y lista de lectura", Icons.AutoMirrored.Outlined.MenuBook, onClick = go(a.onLibrary))
                IosRow("Pestañas cerradas recientemente", Icons.Outlined.Restore, onClick = go(a.onRecent))
                IosRow("Descargas", Icons.Outlined.Download, onClick = go(a.onDownloads))
                IosRow("Borrar datos de navegación", Icons.Outlined.DeleteOutline, divider = false, onClick = go(a.onClearData))
            }
            Spacer(Modifier.height(14.dp))
            IosGroup {
                IosRow("Ajustes", Icons.Outlined.Settings, onClick = go(a.onSettings))
                IosRow("Personalizar página de inicio", Icons.Outlined.Home, onClick = go(a.onCustomizeHome))
                IosRow("Ayuda y comentarios", Icons.AutoMirrored.Outlined.HelpOutline, onClick = go(a.onHelp))
                IosRow("Establecer Pyx como predeterminado", Icons.Outlined.TaskAlt, divider = false, onClick = go(a.onDefaultBrowser))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val k = LocalKaze.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (active) k.accent.copy(alpha = 0.16f) else k.card)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (enabled) k.accent else k.ink35, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = KazeType.caption, color = if (enabled) k.ink else k.ink35, maxLines = 1)
    }
}

/** Borrar datos de navegación: elegir qué y borrarlo. */
@Composable
fun ClearDataSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var closed by remember { mutableStateOf(false) }
    KazeSheet(onDismiss) {
        SheetTitle("Borrar datos de navegación", "Cancelar", onDismiss)
        IosGroup {
            IosRow("Historial", trailing = { KazeSwitch(history) { history = it } }) { history = !history }
            IosRow("Cookies y datos de sitios", trailing = { KazeSwitch(cookies) { cookies = it } }) { cookies = !cookies }
            IosRow("Imágenes y archivos en caché", trailing = { KazeSwitch(cache) { cache = it } }) { cache = !cache }
            IosRow("Cerrar todas las pestañas", trailing = { KazeSwitch(closed) { closed = it } }, divider = false) { closed = !closed }
        }
        Spacer(Modifier.height(14.dp))
        IosGroup {
            IosRow("Borrar", titleColor = SystemRed, divider = false) {
                var flags = 0L
                if (cookies) flags = flags or StorageController.ClearFlags.SITE_DATA or StorageController.ClearFlags.COOKIES
                if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
                if (flags != 0L) app.runtime.storageController.clearData(flags)
                if (cache) app.favicons.clear()
                if (history) app.db.clearHistory()
                if (closed) app.store.tabs.toList().filter { it.kind == TabKind.NORMAL }.forEach { app.pool.close(it); app.store.close(it) }
                app.store.recentlyClosed.clear()
                app.ui.show("Datos borrados")
                onDismiss()
            }
        }
        Text(
            "Tus Favoritos, Essentials y la lista de lectura no se borran.",
            style = KazeType.footnote, color = k.ink55, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Pestañas cerradas hace poco: tocar una la abre de nuevo. */
@Composable
fun RecentlyClosedSheet(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val list = app.store.recentlyClosed
    KazeSheet(onDismiss) {
        SheetTitle("Cerradas recientemente", "OK", onDismiss)
        if (list.isEmpty()) {
            Text("No has cerrado pestañas en esta sesión.", style = KazeType.body, color = k.ink55, modifier = Modifier.padding(32.dp))
        } else {
            IosGroup {
                list.toList().forEachIndexed { i, (url, title) ->
                    IosRow(title, Icons.Outlined.Restore, divider = i < list.lastIndex) {
                        list.removeAt(i); onOpen(url); onDismiss()
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
