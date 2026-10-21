/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

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

    KazeSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                when (u.phase) {
                    Updater.Phase.UP_TO_DATE -> "Kaze está al día"
                    Updater.Phase.CHECKING -> "Buscando actualizaciones…"
                    else -> if (info != null) "Nueva versión disponible" else "Actualizaciones"
                },
                style = KazeType.sheetTitle, color = k.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (info != null) "Tienes ${BuildConfig.VERSION_NAME} · nueva: ${info.versionName}" +
                    (info.sizeBytes.takeIf { it > 0 }?.let { " · %.0f MB".format(it / 1_048_576f) } ?: "")
                else "Versión instalada: ${BuildConfig.VERSION_NAME}",
                style = KazeType.small, color = k.ink55,
            )

            if (info != null && info.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp))
                        .background(k.lift).padding(14.dp),
                ) {
                    Text(
                        info.notes, style = KazeType.small, color = k.ink70,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }

            if (u.phase == Updater.Phase.DOWNLOADING) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(k.lift)) {
                    Box(Modifier.fillMaxWidth(u.progress.coerceAtLeast(0.02f)).height(6.dp).background(k.accent))
                }
                Spacer(Modifier.height(6.dp))
                Text("Descargando… ${(u.progress * 100).toInt()} %", style = KazeType.small, color = k.ink55)
            }
            if (u.phase == Updater.Phase.INSTALLING) {
                Spacer(Modifier.height(14.dp))
                Text("Instalando… Kaze se reiniciará con la versión nueva.", style = KazeType.small, color = k.ink70)
            }
            u.error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = KazeType.small, color = Color(0xFFC0563A))
            }

            Spacer(Modifier.height(22.dp))
            val busy = u.phase == Updater.Phase.DOWNLOADING || u.phase == Updater.Phase.INSTALLING ||
                u.phase == Updater.Phase.CHECKING
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (info != null) {
                    TextPill("Más tarde", k.ink70, Modifier.weight(1f)) { u.skip(info); onDismiss() }
                    TextPill(if (busy) "Espera…" else "Actualizar", k.accent, Modifier.weight(1f)) {
                        if (!busy) downloadAndInstall()
                    }
                } else {
                    TextPill("Cerrar", k.ink70, Modifier.weight(1f)) { onDismiss() }
                    TextPill("Buscar de nuevo", k.accent, Modifier.weight(1f)) {
                        if (!busy) scope.launch { withContext(Dispatchers.IO) { u.check() } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
