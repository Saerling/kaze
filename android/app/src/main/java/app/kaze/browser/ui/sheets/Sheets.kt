/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.data.Space
import app.kaze.browser.data.SpacePalette
import app.kaze.browser.data.Tab
import app.kaze.browser.data.TabKind
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.liftCard
import app.kaze.browser.ui.theme.spaceColor

/** Hoja inferior con el papel de Kaze en vez del gris de Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KazeSheet(onDismiss: () -> Unit, full: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val k = LocalKaze.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = full),
        containerColor = k.paper,
        contentColor = k.ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(k.ink.copy(alpha = 0.2f)))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp), content = content)
    }
}

@Composable
fun SheetItem(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val k = LocalKaze.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: k.ink70, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = KazeType.body, color = tint ?: k.ink)
    }
}

@Composable
private fun SheetHeader(tab: Tab) {
    val k = LocalKaze.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Favicon(tab.url, 28.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.displayTitle, style = KazeType.body, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Urls.displayHost(tab.url), style = KazeType.small, color = k.ink55, maxLines = 1)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = k.ink12)
}

/** Acciones al mantener presionada una pestaña. */
@Composable
fun TabMenuSheet(tab: Tab, onDismiss: () -> Unit, onOpen: () -> Unit, onMove: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    KazeSheet(onDismiss) {
        SheetHeader(tab)
        SheetItem(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir") { onDismiss(); onOpen() }
        if (!tab.isPrivate) {
            when (tab.kind) {
                TabKind.ESSENTIAL -> SheetItem(Icons.Outlined.StarBorder, "Quitar de Essentials") {
                    store.setKind(tab, TabKind.PINNED); onDismiss()
                }
                else -> SheetItem(Icons.Outlined.Star, "Añadir a Essentials") { store.setKind(tab, TabKind.ESSENTIAL); onDismiss() }
            }
            when (tab.kind) {
                TabKind.NORMAL -> SheetItem(Icons.Outlined.PushPin, "Fijar en el espacio") { store.setKind(tab, TabKind.PINNED); onDismiss() }
                TabKind.PINNED -> SheetItem(Icons.Outlined.PushPin, "Desfijar") { store.setKind(tab, TabKind.NORMAL); onDismiss() }
                TabKind.ESSENTIAL -> Unit
            }
            if (store.spaces.size > 1) SheetItem(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover a otro espacio") { onDismiss(); onMove() }
            SheetItem(Icons.Outlined.KeyboardArrowUp, "Subir") { store.reorder(tab, -1) }
            SheetItem(Icons.Outlined.KeyboardArrowDown, "Bajar") { store.reorder(tab, +1) }
        }
        SheetItem(Icons.Outlined.Close, "Cerrar pestaña", tint = Color(0xFFC0563A)) {
            app.pool.close(tab); store.close(tab); onDismiss()
        }
    }
}

/** Elegir el espacio de destino de una pestaña. */
@Composable
fun MoveToSpaceSheet(tab: Tab, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    KazeSheet(onDismiss) {
        Text("Mover a…", style = KazeType.sheetTitle, color = k.ink, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        app.store.spaces.sortedBy { it.position }.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = s.id != tab.spaceId) {
                    app.pool.unload(tab) // cambia el contexto de cookies si el espacio está aislado
                    app.store.moveToSpace(tab, s.id)
                    onDismiss()
                }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(16.dp))
                Text(s.name, style = KazeType.body, color = if (s.id == tab.spaceId) k.ink35 else k.ink)
            }
        }
    }
}

/** Menú "⋯" del navegador. */
@Composable
fun PageMenuSheet(
    tab: Tab,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivate: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val store = app.store
    KazeSheet(onDismiss) {
        SheetHeader(tab)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Outlined.Add, "Nueva", Modifier.weight(1f)) { onDismiss(); onNewTab() }
            QuickAction(Icons.Outlined.VisibilityOff, "Privada", Modifier.weight(1f)) { onDismiss(); onNewPrivate() }
            QuickAction(Icons.Outlined.FindInPage, "Buscar", Modifier.weight(1f)) { onDismiss(); onFind() }
            QuickAction(Icons.Outlined.Share, "Compartir", Modifier.weight(1f)) { onDismiss(); onShare() }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clickable { app.pool.setDesktopMode(tab, !tab.desktopMode) }.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DesktopWindows, null, tint = k.ink70, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
            Text("Versión de escritorio", style = KazeType.body, color = k.ink, modifier = Modifier.weight(1f))
            KazeSwitch(tab.desktopMode) { app.pool.setDesktopMode(tab, it) }
        }
        if (!tab.isPrivate) {
            when (tab.kind) {
                TabKind.NORMAL -> SheetItem(Icons.Outlined.PushPin, "Fijar en el espacio") { store.setKind(tab, TabKind.PINNED); onDismiss() }
                TabKind.PINNED -> SheetItem(Icons.Outlined.PushPin, "Desfijar") { store.setKind(tab, TabKind.NORMAL); onDismiss() }
                TabKind.ESSENTIAL -> Unit
            }
            if (tab.kind != TabKind.ESSENTIAL) {
                SheetItem(Icons.Outlined.Star, "Añadir a Essentials") { store.setKind(tab, TabKind.ESSENTIAL); onDismiss() }
            } else {
                SheetItem(Icons.Outlined.StarBorder, "Quitar de Essentials") { store.setKind(tab, TabKind.PINNED); onDismiss() }
            }
            val home = tab.pinnedUrl
            if (home != null && home != tab.url) {
                SheetItem(Icons.Outlined.Home, "Volver a la página fijada") { app.pool.ensure(tab).loadUri(home); onDismiss() }
            }
            if (store.spaces.size > 1) SheetItem(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover a otro espacio") { onDismiss(); onMove() }
        }
        SheetItem(Icons.Outlined.ContentCopy, "Copiar dirección") {
            ClipboardBridge.copy?.invoke(tab.url); onDismiss()
        }
        SheetItem(Icons.Outlined.Close, "Cerrar pestaña", tint = Color(0xFFC0563A)) {
            app.pool.close(tab); store.close(tab); onDismiss()
        }
    }
}

/** Copiar al portapapeles; lo conecta MainActivity (necesita un Context). */
object ClipboardBridge {
    var copy: ((String) -> Unit)? = null
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val k = LocalKaze.current
    Column(
        modifier.height(72.dp).tap(onClick).liftCard(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = k.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = KazeType.small, color = k.ink70)
    }
}

@Composable
fun KazeSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val k = LocalKaze.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = k.accent, checkedThumbColor = k.paper,
            uncheckedTrackColor = k.lift, uncheckedThumbColor = k.ink55, uncheckedBorderColor = k.ink12,
        ),
    )
}

/** Crear o editar un espacio: nombre, emoji, colores y aislamiento. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpaceEditorSheet(space: Space?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val store = app.store
    var name by remember { mutableStateOf(space?.name ?: "") }
    var emoji by remember { mutableStateOf(space?.emoji ?: SpacePalette.emojis.random()) }
    var colors by remember { mutableStateOf(space?.colors ?: SpacePalette.presets.random()) }
    var isolated by remember { mutableStateOf(space?.isolated ?: false) }

    fun save() {
        val n = name.trim().ifEmpty { "Espacio" }
        if (space == null) {
            val s = store.addSpace(n, emoji, colors)
            s.isolated = isolated
            store.updateSpace(s)
            store.selectSpace(s.id)
        } else {
            if (space.isolated != isolated) {
                // Cambia el contexto de cookies: hay que recargar las pestañas del espacio.
                store.tabs.filter { it.spaceId == space.id }.forEach(app.pool::unload)
            }
            space.name = n; space.emoji = emoji; space.colors = colors; space.isolated = isolated
            store.updateSpace(space)
        }
        onDismiss()
    }

    KazeSheet({ save() }, full = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(if (space == null) "Nuevo espacio" else "Editar espacio", style = KazeType.sheetTitle, color = k.ink)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(52.dp).liftCard().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (name.isEmpty()) Text("Nombre", style = KazeType.body, color = k.ink55); inner() },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("ÍCONO", style = KazeType.caption, color = k.ink55)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SpacePalette.emojis.forEach { e ->
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (e == emoji) k.ink12 else Color.Transparent)
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("COLORES", style = KazeType.caption, color = k.ink55)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpacePalette.presets.forEach { preset ->
                    val selected = preset == colors
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(preset.map { spaceColor(it, k.isDark) }.let { if (it.size == 1) it + it else it }))
                            .border(if (selected) 2.5.dp else 0.5.dp, if (selected) k.ink else k.rim, CircleShape)
                            .clickable { colors = preset },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aislar este espacio", style = KazeType.body, color = k.ink)
                    Text(
                        "Cookies y sesiones propias, como un contenedor de Firefox. Puedes tener dos cuentas del mismo sitio en espacios distintos.",
                        style = KazeType.small, color = k.ink55,
                    )
                }
                Spacer(Modifier.width(12.dp))
                KazeSwitch(isolated) { isolated = it }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (space != null && store.spaces.size > 1) {
                    TextPill("Eliminar", Color(0xFFC0563A), Modifier.weight(1f)) {
                        store.deleteSpace(space.id).forEach(app.pool::close)
                        onDismiss()
                    }
                }
                TextPill("Guardar", k.ink, Modifier.weight(1f)) { save() }
            }
            if (space != null && store.spaces.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextPill("← Mover", k.ink70, Modifier.weight(1f)) { store.moveSpace(space.id, -1) }
                    TextPill("Mover →", k.ink70, Modifier.weight(1f)) { store.moveSpace(space.id, +1) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun TextPill(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).tap(onClick).liftCard(RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Text(text, style = KazeType.body, color = color)
    }
}

/** Menú al mantener presionado un enlace o imagen en la página. */
@Composable
fun ContextMenuSheet(
    link: String?,
    image: String?,
    title: String?,
    onDismiss: () -> Unit,
    onOpenNew: (String, Boolean) -> Unit,
    onShare: (String) -> Unit,
    onDownload: (String) -> Unit,
) {
    val k = LocalKaze.current
    val target = link ?: image ?: return
    KazeSheet(onDismiss) {
        Text(
            title?.takeIf { it.isNotBlank() } ?: target, style = KazeType.small, color = k.ink55, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (link != null) {
            SheetItem(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir en pestaña nueva") { onOpenNew(link, true); onDismiss() }
            SheetItem(Icons.Outlined.Add, "Abrir en segundo plano") { onOpenNew(link, false); onDismiss() }
            SheetItem(Icons.Outlined.VisibilityOff, "Abrir en pestaña privada") { onOpenNew("private:$link", true); onDismiss() }
            SheetItem(Icons.Outlined.ContentCopy, "Copiar enlace") { ClipboardBridge.copy?.invoke(link); onDismiss() }
            SheetItem(Icons.Outlined.Share, "Compartir enlace") { onShare(link); onDismiss() }
        }
        if (image != null) {
            SheetItem(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir imagen en pestaña nueva") { onOpenNew(image, true); onDismiss() }
            SheetItem(Icons.Outlined.Download, "Descargar imagen") { onDownload(image); onDismiss() }
            SheetItem(Icons.Outlined.ContentCopy, "Copiar dirección de la imagen") { ClipboardBridge.copy?.invoke(image); onDismiss() }
        }
    }
}
