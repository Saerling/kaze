/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.kaze.browser.BuildConfig
import app.kaze.browser.engine.Updater
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Hay una versión nueva": notas, descargar con progreso e instalar como actualización. */
@Composable
fun UpdateSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = app.updater
    val info = u.available

    fun downloadAndInstall() {
        val i = info ?: return
        if (!u.canInstall()) {
            // Android pide, una sola vez, permitir que Kaze instale apps.
            context.startActivity(u.installPermissionIntent())
            app.ui.show("Activa «Permitir de esta fuente» y vuelve a Kaze")
            return
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) { u.download(i) } ?: return@launch
            withContext(Dispatchers.IO) { u.install(file) }
        }
    }

    val busy = u.phase == Updater.Phase.DOWNLOADING || u.phase == Updater.Phase.INSTALLING ||
        u.phase == Updater.Phase.CHECKING
    KazeSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
            // Cabecera llamativa: ícono con degradado, título y versión vieja → nueva.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(k.accent, k.accent.copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (info != null) Icons.Outlined.RocketLaunch else Icons.Outlined.CheckCircle, null,
                        tint = Color.White, modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (u.phase) {
                            Updater.Phase.UP_TO_DATE -> "Kaze está al día"
                            Updater.Phase.CHECKING -> "Buscando actualizaciones…"
                            else -> if (info != null) "¡Nueva versión!" else "Actualizaciones"
                        },
                        style = KazeType.sheetTitle, color = k.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(BuildConfig.VERSION_NAME, k.lift, k.ink55)
                        if (info != null) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = k.ink55, modifier = Modifier.size(14.dp))
                            Chip(info.versionName.substringAfterLast(' '), k.accent.copy(alpha = 0.16f), k.accent)
                            info.sizeBytes.takeIf { it > 0 }?.let { Chip("%.0f MB".format(it / 1_048_576f), k.lift, k.ink55) }
                        }
                    }
                }
            }

            if (info != null && info.notes.isNotBlank()) {
                val (title, items) = remember(info.notes) { parseNotes(info.notes) }
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(k.corner.dp)).background(k.card).padding(16.dp),
                ) {
                    Text("Novedades", style = KazeType.footnote.copy(fontWeight = FontWeight.SemiBold), color = k.accent)
                    if (title.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(title, style = KazeType.headline, color = k.ink)
                    }
                    items.forEach { item ->
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(k.accent))
                            Spacer(Modifier.width(10.dp))
                            Text(item, style = KazeType.subhead, color = k.ink70)
                        }
                    }
                }
            }

            u.error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = KazeType.small, color = Color(0xFFC0563A))
            }

            Spacer(Modifier.height(20.dp))
            if (info != null) {
                // Botón grande que se llena con el progreso de la descarga.
                val progress by animateFloatAsState(
                    if (u.phase == Updater.Phase.DOWNLOADING) u.progress.coerceAtLeast(0.03f) else if (u.phase == Updater.Phase.INSTALLING) 1f else 0f,
                    tween(250), label = "dl",
                )
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50))
                        .background(if (busy) k.accent.copy(alpha = 0.25f) else k.accent)
                        .clickable(enabled = !busy) { downloadAndInstall() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(progress).fillMaxHeight().background(k.accent))
                    Text(
                        when (u.phase) {
                            Updater.Phase.DOWNLOADING -> "Descargando… ${(u.progress * 100).toInt()} %"
                            Updater.Phase.INSTALLING -> "Instalando…"
                            else -> "Actualizar ahora"
                        },
                        style = KazeType.headline, color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Más tarde", style = KazeType.body, color = k.ink55,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                        .clickable { u.skip(info); onDismiss() }.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                if (u.phase == Updater.Phase.INSTALLING) {
                    Text("Kaze se reiniciará con la versión nueva.", style = KazeType.small, color = k.ink55, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextPill("Cerrar", k.ink70, Modifier.weight(1f)) { onDismiss() }
                    TextPill("Buscar de nuevo", k.accent, Modifier.weight(1f)) {
                        if (!busy) scope.launch { withContext(Dispatchers.IO) { u.check() } }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text, style = KazeType.caption.copy(fontWeight = FontWeight.SemiBold), color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/**
 * Las notas de cada versión salen del mensaje del commit: un título y viñetas "- …" partidas en
 * varias líneas. Se unen para leerlas como una lista.
 */
internal fun parseNotes(notes: String): Pair<String, List<String>> {
    val lines = notes.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Cambios:", true) }
    if (lines.isEmpty()) return "" to emptyList()
    val title = lines.first().removePrefix("Android:").trim().replaceFirstChar { it.uppercase() }
    val items = mutableListOf<String>()
    val plain = StringBuilder()
    for (l in lines.drop(1)) {
        when {
            l.startsWith("- ") || l.startsWith("• ") -> items += l.drop(2).trim()
            items.isNotEmpty() -> items[items.lastIndex] = items.last() + " " + l
            else -> plain.append(if (plain.isEmpty()) l else " $l")
        }
    }
    if (plain.isNotEmpty()) items.add(0, plain.toString())
    return title to items.map { it.trim().removeSuffix(".") + "." }
}

/** "Kaze se cerró la última vez": muestra el error para copiarlo o compartirlo. */
@Composable
fun CrashSheet(text: String, onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    val k = LocalKaze.current
    KazeSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Kaze se cerró la última vez", style = KazeType.sheetTitle, color = k.ink)
            Spacer(Modifier.height(6.dp))
            Text("Copia o comparte este detalle para poder arreglarlo.", style = KazeType.small, color = k.ink55)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp))
                    .background(k.lift).padding(14.dp),
            ) {
                Text(text, style = KazeType.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = k.ink70, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextPill("Copiar", k.ink70, Modifier.weight(1f), onCopy)
                TextPill("Compartir", k.accent, Modifier.weight(1f)) { onShare(); onDismiss() }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
