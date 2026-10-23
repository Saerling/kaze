/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.FolderOff
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.data.Space
import androidx.compose.material.icons.outlined.PersonAdd
import app.kaze.browser.data.Profile
import app.kaze.browser.data.SpacePalette
import app.kaze.browser.data.Tab
import app.kaze.browser.data.TabKind
import app.kaze.browser.data.Urls
import app.kaze.browser.engine.Engine
import app.kaze.browser.ui.components.Favicon
import app.kaze.browser.ui.components.IosGroup
import app.kaze.browser.ui.components.IosRow
import app.kaze.browser.ui.components.IosSectionHeader
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.SystemRed
import app.kaze.browser.ui.theme.card
import app.kaze.browser.ui.theme.liftCard
import app.kaze.browser.ui.theme.spaceColor
import org.json.JSONObject

/** Hoja inferior al estilo iOS: fondo agrupado y asa gris. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KazeSheet(onDismiss: () -> Unit, full: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val k = LocalKaze.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = full),
        containerColor = k.paper,
        contentColor = k.ink,
        shape = RoundedCornerShape(topStart = if (k.material) 28.dp else 14.dp, topEnd = if (k.material) 28.dp else 14.dp),
        dragHandle = {
            Box(Modifier.padding(top = 8.dp, bottom = 6.dp).size(36.dp, 5.dp).clip(CircleShape).background(k.ink.copy(alpha = 0.22f)))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), content = content)
    }
}

/** Título de hoja con "OK" a la derecha, como los modales de iOS. */
@Composable
fun SheetTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val k = LocalKaze.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = KazeType.title2, color = k.ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, style = KazeType.headline, color = k.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PageHeader(tab: Tab) {
    val k = LocalKaze.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).card(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Favicon(tab.url, 28.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.displayTitle, style = KazeType.headline, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Urls.displayHost(tab.url), style = KazeType.footnote, color = k.ink55, maxLines = 1)
        }
    }
}

/** Copiar al portapapeles; lo conecta MainActivity (necesita un Context). */
object ClipboardBridge {
    var copy: ((String) -> Unit)? = null
}

/** Interruptor verde de iOS. */
@Composable
fun KazeSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val k = LocalKaze.current
    if (k.material) {
        // Interruptor de Material 3: pista del color principal y palomita en el círculo.
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = if (checked) { { Icon(Icons.Outlined.Check, null, Modifier.size(SwitchDefaults.IconSize), tint = k.accent) } } else null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = k.accent, checkedThumbColor = k.paper, checkedBorderColor = k.accent,
                uncheckedTrackColor = k.tonal, uncheckedThumbColor = k.ink55, uncheckedBorderColor = k.ink55,
            ),
        )
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = k.lift, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Botón grande de iOS (relleno gris o color). */
@Composable
fun TextPill(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val k = LocalKaze.current
    Box(
        modifier.height(if (k.material) 52.dp else 50.dp).tap(onClick)
            .then(if (k.material) Modifier.clip(RoundedCornerShape(50)).background(k.tonal) else Modifier.liftCard(RoundedCornerShape(14.dp))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KazeType.headline, color = color)
    }
}

// ── "AA": opciones de la página ───────────────────────────────────────

@Composable
fun PageMenuSheet(
    tab: Tab,
    onDismiss: () -> Unit,
    onHideBar: () -> Unit,
    onPrivacyReport: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val pool = app.pool
    var size by remember { mutableStateOf(app.settings.textSize) }
    var readerTheme by remember { mutableStateOf("claro") }
    var readerFont by remember { mutableStateOf("serif") }

    fun setSize(v: Int) {
        size = v.coerceIn(50, 300)
        app.settings.textSize = size
        Engine.applyTextSize(app.runtime, size)
    }

    KazeSheet(onDismiss) {
        // Tamaño del texto: A  100 %  A
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).card(RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(52.dp).clickable { setSize(size - 10) }, contentAlignment = Alignment.Center) {
                Text("A", fontSize = 14.sp, color = k.ink)
            }
            Text("$size %", style = KazeType.headline, color = k.ink, modifier = Modifier.clickable { setSize(100) }.padding(8.dp))
            Box(Modifier.weight(1f).height(52.dp).clickable { setSize(size + 10) }, contentAlignment = Alignment.Center) {
                Text("A", fontSize = 22.sp, color = k.ink)
            }
        }
        Spacer(Modifier.height(14.dp))
        IosGroup {
            IosRow(
                if (tab.readerMode) "Ocultar Lector" else "Mostrar Lector",
                Icons.Outlined.ImportContacts,
                subtitle = if (!tab.readerable && !tab.readerMode) "No hay un artículo en esta página" else null,
                enabled = tab.readerable || tab.readerMode,
            ) { pool.setReader(tab, !tab.readerMode); onDismiss() }
            val other = tab.pageLang != null && tab.pageLang != pool.userLang
            IosRow(
                if (tab.translated) "Ver original" else "Traducir al español",
                Icons.Outlined.Translate,
                subtitle = when {
                    tab.translated -> null
                    other -> "Página en ${java.util.Locale.forLanguageTag(tab.pageLang!!).getDisplayLanguage(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])}"
                    else -> "La página ya está en tu idioma"
                },
                enabled = tab.translated || other,
            ) { if (tab.translated) pool.showOriginal(tab) else pool.translate(tab); onDismiss() }
            IosRow("Ocultar barra de herramientas", Icons.Outlined.UnfoldLess) { onHideBar(); onDismiss() }
            // Si una página no carga bien, a veces es el bloqueo de rastreadores: se puede apagar solo aquí.
            var tracking by remember(tab.id) { mutableStateOf(true) }
            LaunchedEffect(tab.id, tab.url) { pool.trackingOn(tab) { tracking = it } }
            IosRow(
                "Bloquear rastreadores en este sitio", Icons.Outlined.Shield,
                subtitle = if (tracking) null else "Desactivado aquí: prueba si la página no cargaba bien",
                trailing = { KazeSwitch(tracking) { tracking = it; pool.setTracking(tab, it) } },
            ) { tracking = !tracking; pool.setTracking(tab, tracking) }
            IosRow(
                "Versión de escritorio", Icons.Outlined.DesktopWindows,
                trailing = { KazeSwitch(tab.desktopMode) { pool.setDesktopMode(tab, it) } },
            ) { pool.setDesktopMode(tab, !tab.desktopMode) }
            IosRow(
                "Reporte de privacidad", Icons.Outlined.Shield,
                subtitle = "${tab.trackersBlocked} rastreadores bloqueados en esta página", divider = false, chevron = true,
            ) { onDismiss(); onPrivacyReport() }
        }

        if (tab.readerMode) {
            IosSectionHeader("Apariencia del Lector")
            IosGroup {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(
                        "claro" to Color.White, "sepia" to Color(0xFFF8F1E3), "gris" to Color(0xFF4A4A4D), "oscuro" to Color.Black,
                    ).forEach { (name, c) ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(c)
                                .border(if (readerTheme == name) 3.dp else 1.dp, if (readerTheme == name) k.accent else k.separator, CircleShape)
                                .clickable {
                                    readerTheme = name
                                    pool.readerOptions(tab, JSONObject().put("tema", name))
                                },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("serif" to "Georgia", "sans" to "Sistema").forEach { (id, label) ->
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (readerFont == id) k.accent.copy(alpha = 0.15f) else k.lift)
                                .clickable {
                                    readerFont = id
                                    pool.readerOptions(tab, JSONObject().put("fuente", id))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, fontFamily = if (id == "serif") FontFamily.Serif else FontFamily.Default, color = if (readerFont == id) k.accent else k.ink)
                        }
                    }
                }
            }
        }
    }
}

// ── Compartir y más (botón ⬆) ─────────────────────────────────────────

@Composable
fun ShareSheet(
    tab: Tab,
    onDismiss: () -> Unit,
    onSystemShare: () -> Unit,
    onFind: () -> Unit,
    onMove: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val store = app.store
    KazeSheet(onDismiss) {
        PageHeader(tab)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigAction(Icons.Outlined.Share, "Compartir", Modifier.weight(1f)) { onDismiss(); onSystemShare() }
            BigAction(Icons.Outlined.ContentCopy, "Copiar", Modifier.weight(1f)) { ClipboardBridge.copy?.invoke(tab.url); onDismiss() }
            BigAction(Icons.Outlined.FindInPage, "Buscar", Modifier.weight(1f)) { onDismiss(); onFind() }
        }
        Spacer(Modifier.height(8.dp))
        IosGroup {
            if (!tab.isPrivate) {
                if (tab.kind == TabKind.ESSENTIAL) {
                    IosRow("Quitar de Favoritos", Icons.Outlined.StarBorder) { store.setKind(tab, TabKind.PINNED); onDismiss() }
                } else {
                    IosRow("Añadir a Favoritos", Icons.Outlined.Star) { store.setKind(tab, TabKind.ESSENTIAL); app.ui.show("Añadida a Favoritos"); onDismiss() }
                }
                IosRow("Añadir a la lista de lectura", Icons.AutoMirrored.Outlined.MenuBook) {
                    app.db.addToReadingList(tab.url, tab.title); app.ui.show("Añadida a la lista de lectura"); onDismiss()
                }
                when (tab.kind) {
                    TabKind.NORMAL -> IosRow("Fijar pestaña", Icons.Outlined.PushPin) { store.setKind(tab, TabKind.PINNED); onDismiss() }
                    TabKind.PINNED -> IosRow("Desfijar pestaña", Icons.Outlined.PushPin) { store.setKind(tab, TabKind.NORMAL); onDismiss() }
                    TabKind.ESSENTIAL -> Unit
                }
                val home = tab.pinnedUrl
                if (home != null && home != tab.url) {
                    IosRow("Volver a la página fijada", Icons.Outlined.Home) { app.pool.ensure(tab).loadUri(home); onDismiss() }
                }
                if (store.profileSpaces.size > 1) IosRow("Mover a otro grupo", Icons.AutoMirrored.Outlined.DriveFileMove) { onDismiss(); onMove() }
            }
            IosRow("Cerrar pestaña", Icons.Outlined.Close, iconTint = SystemRed, titleColor = SystemRed, divider = false) {
                app.pool.close(tab); store.close(tab); onDismiss()
            }
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val k = LocalKaze.current
    Column(
        modifier.height(70.dp).card(RoundedCornerShape(12.dp)).tap(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = k.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = KazeType.footnote, color = k.ink)
    }
}

// ── Menú rápido del botón de pestañas (mantener presionado) ───────────

@Composable
fun TabsQuickSheet(
    tab: Tab?,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivate: () -> Unit,
    onCloseTab: () -> Unit,
    onCloseAll: () -> Unit,
) {
    KazeSheet(onDismiss) {
        Spacer(Modifier.height(6.dp))
        IosGroup {
            IosRow("Nueva pestaña", Icons.Outlined.Add) { onDismiss(); onNewTab() }
            IosRow("Nueva pestaña privada", Icons.Outlined.VisibilityOff) { onDismiss(); onNewPrivate() }
            if (tab != null) IosRow("Cerrar esta pestaña", Icons.Outlined.Close) { onDismiss(); onCloseTab() }
            IosRow("Cerrar todas las pestañas del grupo", Icons.Outlined.Close, iconTint = SystemRed, titleColor = SystemRed, divider = false) {
                onDismiss(); onCloseAll()
            }
        }
    }
}

// ── Mantener presionada una pestaña o un favorito ─────────────────────

@Composable
fun TabMenuSheet(tab: Tab, onDismiss: () -> Unit, onOpen: () -> Unit, onMove: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    KazeSheet(onDismiss) {
        PageHeader(tab)
        IosGroup {
            IosRow("Abrir", Icons.AutoMirrored.Outlined.OpenInNew) { onDismiss(); onOpen() }
            if (!tab.isPrivate && !tab.isStart) {
                if (tab.kind == TabKind.ESSENTIAL) {
                    IosRow("Quitar de Favoritos", Icons.Outlined.StarBorder) { store.setKind(tab, TabKind.PINNED); onDismiss() }
                } else {
                    IosRow("Añadir a Favoritos", Icons.Outlined.Star) { store.setKind(tab, TabKind.ESSENTIAL); onDismiss() }
                }
                when (tab.kind) {
                    TabKind.NORMAL -> IosRow("Fijar pestaña", Icons.Outlined.PushPin) { store.setKind(tab, TabKind.PINNED); onDismiss() }
                    TabKind.PINNED -> IosRow("Desfijar pestaña", Icons.Outlined.PushPin) { store.setKind(tab, TabKind.NORMAL); onDismiss() }
                    TabKind.ESSENTIAL -> Unit
                }
                if (store.profileSpaces.size > 1) IosRow("Mover a otro grupo", Icons.AutoMirrored.Outlined.DriveFileMove) { onDismiss(); onMove() }
                IosRow("Copiar dirección", Icons.Outlined.ContentCopy) { ClipboardBridge.copy?.invoke(tab.url); onDismiss() }
                if (tab.folder.isNotEmpty()) {
                    IosRow("Sacar de la carpeta «${tab.folder}»", Icons.Outlined.FolderOff) { store.setFolder(listOf(tab), ""); onDismiss() }
                }
            }
            IosRow("Cerrar pestaña", Icons.Outlined.Close, iconTint = SystemRed, titleColor = SystemRed, divider = false) {
                app.pool.close(tab); store.close(tab); app.thumbnails.delete(tab.id); onDismiss()
            }
        }
    }
}

@Composable
fun MoveToSpaceSheet(tab: Tab, onDismiss: () -> Unit) {
    val app = LocalApp.current
    KazeSheet(onDismiss) {
        SheetTitle("Mover a grupo")
        Spacer(Modifier.height(8.dp))
        IosGroup {
            val list = app.store.profileSpaces.sortedBy { it.position }
            list.forEachIndexed { i, s ->
                IosRow(
                    s.name, leading = { Text(s.emoji, fontSize = 20.sp) }, divider = i < list.lastIndex,
                    enabled = s.id != tab.spaceId,
                    trailing = { if (s.id == tab.spaceId) Icon(Icons.Outlined.Check, null, tint = LocalKaze.current.accent) },
                ) {
                    app.pool.unload(tab) // cambia el contexto de cookies si el grupo está aislado
                    app.store.moveToSpace(tab, s.id)
                    onDismiss()
                }
            }
        }
    }
}

// ── Grupos de pestañas (los espacios) ─────────────────────────────────

@Composable
fun GroupsSheet(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (Space) -> Unit,
    onProfile: (String) -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val store = app.store
    KazeSheet(onDismiss) {
        SheetTitle("Grupos de pestañas", "OK", onDismiss)
        Spacer(Modifier.height(8.dp))
        IosGroup {
            val privados = store.tabs.count { it.isPrivate }
            IosRow(
                "Privada", leading = { Text("🕶️", fontSize = 20.sp) },
                subtitle = if (privados == 0) "Sin pestañas" else "$privados pestañas",
                trailing = { if (store.currentSpaceId == Space.PRIVATE_ID) Icon(Icons.Outlined.Check, null, tint = k.accent) },
                divider = false,
            ) { onSelect(Space.PRIVATE_ID) }
        }
        IosSectionHeader("Grupos")
        IosGroup {
            val list = store.profileSpaces.sortedBy { it.position }
            list.forEach { s ->
                val n = store.tabs.count { it.spaceId == s.id && it.kind != TabKind.ESSENTIAL }
                IosRow(
                    s.name, leading = { Text(s.emoji, fontSize = 20.sp) },
                    subtitle = if (n == 1) "1 pestaña" else "$n pestañas",
                    trailing = {
                        if (store.currentSpaceId == s.id) Icon(Icons.Outlined.Check, null, tint = k.accent, modifier = Modifier.padding(end = 8.dp))
                        Icon(
                            Icons.Outlined.Edit, "Editar grupo", tint = k.ink55,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { onEdit(s) }.padding(7.dp),
                        )
                    },
                ) { onSelect(s.id) }
            }
            IosRow("Nuevo grupo de pestañas", Icons.Outlined.AddBox, divider = false) { onNew() }
        }
        // Perfiles (como Safari): cada uno con sus grupos, Essentials, historial y cookies.
        var editing by remember { mutableStateOf<Profile?>(null) }
        var creating by remember { mutableStateOf(false) }
        IosSectionHeader("Perfil")
        IosGroup {
            store.profiles.sortedBy { it.position }.forEach { p ->
                IosRow(
                    p.name, leading = { Text(p.emoji, fontSize = 20.sp) },
                    subtitle = if (p.id == Profile.DEFAULT_ID) "Principal" else "Cookies, historial y grupos propios",
                    trailing = {
                        if (store.currentProfileId == p.id) Icon(Icons.Outlined.Check, null, tint = k.accent, modifier = Modifier.padding(end = 8.dp))
                        Icon(
                            Icons.Outlined.Edit, "Editar perfil", tint = k.ink55,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { editing = p }.padding(7.dp),
                        )
                    },
                ) { onProfile(p.id) }
            }
            IosRow("Nuevo perfil", Icons.Outlined.PersonAdd) { creating = true }
            IosRow("Ajustes", Icons.Outlined.Settings, divider = false) { onDismiss(); app.ui.openSettings = true }
        }
        if (creating || editing != null) {
            val p = editing
            ProfileDialog(
                profile = p,
                onDismiss = { creating = false; editing = null },
                onSave = { name, emoji ->
                    if (p == null) onProfile(store.addProfile(name, emoji).id)
                    else { p.name = name; p.emoji = emoji; store.updateProfile(p) }
                    creating = false; editing = null
                },
                onDelete = if (p != null && p.id != Profile.DEFAULT_ID) {
                    { store.deleteProfile(p.id).forEach(app.pool::close); editing = null }
                } else null,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Crear o editar un grupo: nombre, ícono, colores y aislamiento. */
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
        val n = name.trim().ifEmpty { "Grupo" }
        if (space == null) {
            val s = store.addSpace(n, emoji, colors)
            s.isolated = isolated
            store.updateSpace(s)
            store.selectSpace(s.id)
        } else {
            if (space.isolated != isolated) store.tabs.filter { it.spaceId == space.id }.forEach(app.pool::unload)
            space.name = n; space.emoji = emoji; space.colors = colors; space.isolated = isolated
            store.updateSpace(space)
        }
        onDismiss()
    }

    KazeSheet(onDismiss, full = true) {
        SheetTitle(if (space == null) "Nuevo grupo" else "Editar grupo", "OK") { save() }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).card(RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent), modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (name.isEmpty()) Text("Nombre del grupo", style = KazeType.body, color = k.ink55); inner() },
                )
            }
            IosSectionHeader("Ícono")
            FlowRow(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(RoundedCornerShape(12.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SpacePalette.emojis.forEach { e ->
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (e == emoji) k.accent.copy(alpha = 0.18f) else Color.Transparent).clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp) }
                }
            }
            IosSectionHeader("Color de la página de inicio")
            FlowRow(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpacePalette.presets.forEach { preset ->
                    val selected = preset == colors
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(preset.map { spaceColor(it, k.isDark) }.let { if (it.size == 1) it + it else it }))
                            .border(if (selected) 3.dp else 0.5.dp, if (selected) k.accent else k.separator, CircleShape)
                            .clickable { colors = preset },
                    )
                }
            }
            IosSectionHeader("Privacidad")
            IosGroup {
                IosRow(
                    "Aislar este grupo",
                    subtitle = "Cookies y sesiones propias: puedes tener dos cuentas del mismo sitio en grupos distintos.",
                    trailing = { KazeSwitch(isolated) { isolated = it } }, divider = false,
                ) { isolated = !isolated }
            }
            if (space != null && store.profileSpaces.size > 1) {
                IosSectionHeader("Orden")
                IosGroup {
                    IosRow("Mover antes") { store.moveSpace(space.id, -1) }
                    IosRow("Mover después", divider = false) { store.moveSpace(space.id, +1) }
                }
                Spacer(Modifier.height(20.dp))
                IosGroup {
                    IosRow("Eliminar grupo", titleColor = SystemRed, divider = false) {
                        store.deleteSpace(space.id).forEach(app.pool::close)
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Reporte de privacidad ─────────────────────────────────────────────

@Composable
fun PrivacyReportSheet(tab: Tab?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val total = app.settings.trackersTotal
    val since = app.settings.trackersSince.toLongOrNull()
    val days = since?.let { ((System.currentTimeMillis() - it) / 86_400_000L).coerceAtLeast(0) + 1 } ?: 0
    KazeSheet(onDismiss) {
        SheetTitle("Reporte de privacidad", "OK", onDismiss)
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(RoundedCornerShape(14.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, tint = k.ink, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    if (days > 0) "En ${if (days == 1L) "el último día" else "los últimos $days días"}, Fyx evitó que $total rastreadores te siguieran."
                    else "Fyx bloquea los rastreadores que te siguen entre sitios.",
                    style = KazeType.subhead, color = k.ink, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(total.toString(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = k.ink)
            Text("rastreadores bloqueados", style = KazeType.footnote, color = k.ink55)
        }
        if (tab != null && !tab.isStart) {
            IosSectionHeader("Esta página")
            IosGroup {
                IosRow(
                    Urls.displayHost(tab.url), leading = { Favicon(tab.url, 24.dp) },
                    trailing = { Text("${tab.trackersBlocked}", style = KazeType.headline, color = k.ink) }, divider = false,
                )
            }
        }
        Text(
            "La protección contra rastreo está en «Estricta». Puedes cambiarla en Ajustes → Privacidad.",
            style = KazeType.footnote, color = k.ink55, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
    }
}

// ── Mantener presionado un enlace o imagen en la página ───────────────

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
    val app = LocalApp.current
    val k = LocalKaze.current
    val target = link ?: image ?: return
    KazeSheet(onDismiss) {
        Text(
            title?.takeIf { it.isNotBlank() } ?: target, style = KazeType.footnote, color = k.ink55, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        IosGroup {
            if (link != null) {
                IosRow("Abrir en pestaña nueva", Icons.AutoMirrored.Outlined.OpenInNew) { onOpenNew(link, true); onDismiss() }
                IosRow("Abrir en segundo plano", Icons.Outlined.Add) { onOpenNew(link, false); onDismiss() }
                IosRow("Abrir en pestaña privada", Icons.Outlined.VisibilityOff) { onOpenNew("private:$link", true); onDismiss() }
                IosRow("Añadir a la lista de lectura", Icons.AutoMirrored.Outlined.MenuBook) {
                    app.db.addToReadingList(link, title.orEmpty()); app.ui.show("Añadido a la lista de lectura"); onDismiss()
                }
                IosRow("Copiar enlace", Icons.Outlined.ContentCopy) { ClipboardBridge.copy?.invoke(link); onDismiss() }
                IosRow("Compartir enlace", Icons.Outlined.Share, divider = image != null) { onShare(link); onDismiss() }
            }
            if (image != null) {
                IosRow("Abrir imagen en pestaña nueva", Icons.AutoMirrored.Outlined.OpenInNew) { onOpenNew(image, true); onDismiss() }
                IosRow("Guardar imagen", Icons.Outlined.Download) { onDownload(image); onDismiss() }
                IosRow("Copiar dirección de la imagen", Icons.Outlined.ContentCopy, divider = false) { ClipboardBridge.copy?.invoke(image); onDismiss() }
            }
        }
    }
}


/** Crear, renombrar o borrar un perfil. */
@Composable
private fun ProfileDialog(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val k = LocalKaze.current
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var emoji by remember { mutableStateOf(profile?.emoji ?: "💼") }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = k.paper,
        title = { Text(if (profile == null) "Nuevo perfil" else "Editar perfil", style = KazeType.headline, color = k.ink) },
        text = {
            Column {
                Text(
                    if (confirmDelete) "Se borrarán sus grupos, pestañas, historial y lista de lectura."
                    else "Cada perfil tiene sus propios grupos, Essentials, historial y cookies.",
                    style = KazeType.subhead, color = k.ink55,
                )
                if (!confirmDelete) {
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                        textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
                        modifier = Modifier.fillMaxWidth().height(46.dp).liftCard(RoundedCornerShape(10.dp)).padding(12.dp),
                        decorationBox = { inner -> if (name.isEmpty()) Text("Nombre", style = KazeType.body, color = k.ink55); inner() },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("🙂", "💼", "🎓", "🏠", "🎮", "🧪").forEach { e ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(if (e == emoji) k.ink12 else Color.Transparent)
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (confirmDelete) onDelete?.invoke() else onSave(name.trim().ifEmpty { "Perfil" }, emoji)
            }) { Text(if (confirmDelete) "Borrar" else "Guardar", color = if (confirmDelete) Color(0xFFFF3B30) else k.accent) }
        },
        dismissButton = {
            Row {
                if (onDelete != null && !confirmDelete) {
                    androidx.compose.material3.TextButton(onClick = { confirmDelete = true }) { Text("Borrar perfil", color = Color(0xFFFF3B30)) }
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar", color = k.ink70) }
            }
        },
    )
}
