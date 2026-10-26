/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaze.browser.engine.DownloadItem
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.SystemRed

/** Descargas, como en Chrome y Safari: historial con progreso, abrir al tocar y quitar de la lista. */
@Composable
fun DownloadsSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val context = LocalContext.current
    val list = app.downloads.items
    KazeSheet(onDismiss) {
        SheetTitle("Descargas", "OK", onDismiss)
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(k.lift), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Download, null, tint = k.ink55, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Sin descargas", style = KazeType.headline, color = k.ink)
                Text("Lo que descargues aparecerá aquí.", style = KazeType.subhead, color = k.ink55)
            }
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(k.corner.dp)).background(k.card).animateContentSize(spring(0.9f, 400f)),
            ) {
                list.forEachIndexed { i, d ->
                    key(d.id) { DownloadRow(d, divider = i < list.lastIndex, onOpen = { open(context, d) }, onRemove = { app.downloads.remove(d) }) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Abrir carpeta", style = KazeType.body, color = k.accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        runCatching { context.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.padding(6.dp),
                )
                Text(
                    "Borrar lista", style = KazeType.body, color = SystemRed,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { app.downloads.clear() }.padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun key(id: Long, content: @Composable () -> Unit) = androidx.compose.runtime.key(id) { content() }

private fun open(context: android.content.Context, d: DownloadItem) {
    val uri = d.uri ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), d.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun DownloadRow(d: DownloadItem, divider: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val k = LocalKaze.current
    val context = LocalContext.current
    val running = d.state == DownloadItem.State.RUNNING
    val failed = d.state == DownloadItem.State.FAILED
    val icon = when {
        failed -> Icons.Outlined.ErrorOutline
        d.mime.startsWith("image/") -> Icons.Outlined.Image
        d.mime.startsWith("video/") -> Icons.Outlined.Movie
        d.mime.startsWith("audio/") -> Icons.Outlined.MusicNote
        d.mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
    Column(
        Modifier.fillMaxWidth().clickable(enabled = d.uri != null && !running, onClick = onOpen)
            .semantics { contentDescription = d.name },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (failed) SystemRed.copy(alpha = 0.12f) else k.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (failed) SystemRed else k.accent, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = KazeType.body.copy(fontWeight = FontWeight.Medium), color = k.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                val size = { b: Long -> Formatter.formatShortFileSize(context, b) }
                Text(
                    when {
                        failed -> "No se pudo descargar"
                        running && d.total > 0 -> "${size(d.done)} de ${size(d.total)}"
                        running -> "Descargando… ${size(d.done)}"
                        else -> size(maxOf(d.done, d.total)) + " · " + android.text.format.DateUtils.getRelativeTimeSpanString(d.time)
                    },
                    style = KazeType.footnote, color = if (failed) SystemRed else k.ink55, maxLines = 1,
                )
                // Barra de progreso que avanza suave (y se esconde con una animación al terminar).
                AnimatedVisibility(running, enter = fadeIn() + expandVertically(), exit = fadeOut(tween(200)) + shrinkVertically(tween(250))) {
                    val p by animateFloatAsState(if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f) else 0.15f, spring(0.9f, 200f), label = "dl")
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(k.lift)) {
                        Box(Modifier.fillMaxWidth(p).height(4.dp).clip(RoundedCornerShape(50)).background(k.accent))
                    }
                }
            }
            if (!running) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRemove).semantics { contentDescription = "Quitar de la lista" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, null, tint = k.ink55, modifier = Modifier.size(18.dp)) }
            } else {
                Icon(Icons.Outlined.FolderOpen, null, tint = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.size(1.dp))
            }
        }
        if (divider) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(0.5.dp).background(k.separator))
    }
}
