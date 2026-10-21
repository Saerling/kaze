/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.sheets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaze.browser.engine.PendingPrompt
import app.kaze.browser.engine.PermissionAsk
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import app.kaze.browser.ui.theme.liftCard
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import java.util.Calendar

/**
 * Muestra los diálogos que pide la página. [onFile] abre el selector de archivos
 * del sistema (vive en MainActivity porque necesita un ActivityResult).
 */
@Composable
fun PromptHost(pending: PendingPrompt, onDone: () -> Unit, onFile: (PromptDelegate.FilePrompt, PendingPrompt) -> Unit) {
    fun finish(r: PromptDelegate.PromptResponse) { pending.finish(r); onDone() }
    fun cancel() { pending.dismiss(); onDone() }

    when (val p = pending.prompt) {
        is PromptDelegate.AlertPrompt -> Simple(p.title, p.message, "Aceptar", null, { finish(p.dismiss()) }, { cancel() })
        is PromptDelegate.ButtonPrompt -> Simple(
            p.title, p.message, "Aceptar", "Cancelar",
            { finish(p.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) },
            { finish(p.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) },
        )
        is PromptDelegate.BeforeUnloadPrompt -> Simple(
            "¿Salir de la página?", "Es posible que los cambios que hiciste no se guarden.", "Salir", "Quedarme",
            { finish(p.confirm(AllowOrDeny.ALLOW)) }, { finish(p.confirm(AllowOrDeny.DENY)) },
        )
        is PromptDelegate.RepostConfirmPrompt -> Simple(
            "¿Reenviar el formulario?", "La página volverá a enviar los datos que escribiste.", "Reenviar", "Cancelar",
            { finish(p.confirm(AllowOrDeny.ALLOW)) }, { finish(p.confirm(AllowOrDeny.DENY)) },
        )
        is PromptDelegate.PopupPrompt -> Simple(
            "Ventana emergente bloqueada", "Esta página quiere abrir:\n${p.targetUri ?: "otra ventana"}", "Permitir", "Bloquear",
            { finish(p.confirm(AllowOrDeny.ALLOW)) }, { finish(p.confirm(AllowOrDeny.DENY)) },
        )
        is PromptDelegate.TextPrompt -> TextInput(p.title, p.message, p.defaultValue.orEmpty(), { finish(p.confirm(it)) }, { cancel() })
        is PromptDelegate.AuthPrompt -> Auth(p, { u, pw -> finish(p.confirm(u, pw)) }, { cancel() })
        is PromptDelegate.ChoicePrompt -> Choice(p, ::finish) { cancel() }
        is PromptDelegate.DateTimePrompt -> DateTime(p, ::finish) { cancel() }
        is PromptDelegate.FilePrompt -> DisposableEffect(p) {
            onFile(p, pending)
            onDone()
            onDispose { }
        }
        else -> DisposableEffect(p) { cancel(); onDispose { } } // color y otros poco comunes
    }
}

@Composable
private fun Simple(
    title: String?,
    message: String?,
    ok: String,
    no: String?,
    onOk: () -> Unit,
    onNo: () -> Unit,
) {
    val k = LocalKaze.current
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = k.paper,
        title = title?.takeIf { it.isNotBlank() }?.let { { Text(it, style = KazeType.body, color = k.ink) } },
        text = message?.let { { Text(it, style = KazeType.body, color = k.ink70) } },
        confirmButton = { TextButton(onClick = onOk) { Text(ok, color = k.accent) } },
        dismissButton = no?.let { { TextButton(onClick = onNo) { Text(it, color = k.ink70) } } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false) {
    val k = LocalKaze.current
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = KazeType.body.copy(color = k.ink), cursorBrush = SolidColor(k.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().height(48.dp).liftCard().padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, style = KazeType.body, color = k.ink55); inner() },
    )
}

@Composable
private fun TextInput(title: String?, message: String?, initial: String, onOk: (String) -> Unit, onCancel: () -> Unit) {
    val k = LocalKaze.current
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel, containerColor = k.paper,
        title = title?.takeIf { it.isNotBlank() }?.let { { Text(it, style = KazeType.body, color = k.ink) } },
        text = {
            Column {
                message?.let { Text(it, style = KazeType.body, color = k.ink70); Spacer(Modifier.height(10.dp)) }
                Field(v, { v = it }, "")
            }
        },
        confirmButton = { TextButton(onClick = { onOk(v) }) { Text("Aceptar", color = k.accent) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar", color = k.ink70) } },
    )
}

@Composable
private fun Auth(p: PromptDelegate.AuthPrompt, onOk: (String, String) -> Unit, onCancel: () -> Unit) {
    val k = LocalKaze.current
    var user by remember { mutableStateOf(p.authOptions.username.orEmpty()) }
    var pass by remember { mutableStateOf(p.authOptions.password.orEmpty()) }
    AlertDialog(
        onDismissRequest = onCancel, containerColor = k.paper,
        title = { Text("Iniciar sesión", style = KazeType.body, color = k.ink) },
        text = {
            Column {
                p.message?.let { Text(it, style = KazeType.small, color = k.ink70); Spacer(Modifier.height(10.dp)) }
                Field(user, { user = it }, "Usuario")
                Spacer(Modifier.height(8.dp))
                Field(pass, { pass = it }, "Contraseña", password = true)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(user, pass) }) { Text("Entrar", color = k.accent) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar", color = k.ink70) } },
    )
}

/** Listas desplegables <select> (una o varias opciones) y menús. */
@Composable
private fun Choice(p: PromptDelegate.ChoicePrompt, finish: (PromptDelegate.PromptResponse) -> Unit, onCancel: () -> Unit) {
    val k = LocalKaze.current
    val multiple = p.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
    // Aplana grupos <optgroup> en una sola lista.
    val flat = remember(p) {
        buildList {
            fun add(c: PromptDelegate.ChoicePrompt.Choice, depth: Int) {
                if (c.separator) return
                if (c.items != null) {
                    add(c to -1); c.items!!.forEach { add(it, depth + 1) }
                } else add(c to depth)
            }
            p.choices.forEach { add(it, 0) }
        }
    }
    var chosen by remember { mutableStateOf(flat.filter { it.first.selected && it.second >= 0 }.map { it.first.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onCancel, containerColor = k.paper,
        title = p.title?.takeIf { it.isNotBlank() }?.let { { Text(it, style = KazeType.body, color = k.ink) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(flat, key = { it.first.id + it.second }) { (c, depth) ->
                    if (depth < 0) {
                        Text(c.label, style = KazeType.caption, color = k.ink55, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !c.disabled) {
                                if (multiple) chosen = if (c.id in chosen) chosen - c.id else chosen + c.id
                                else finish(p.confirm(c))
                            }.padding(start = (depth * 12).dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) {
                                Checkbox(
                                    checked = c.id in chosen, onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = k.accent, uncheckedColor = k.ink55),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                c.label, style = KazeType.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (c.disabled) k.ink35 else if (!multiple && c.selected) k.accent else k.ink,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiple) TextButton(onClick = { finish(p.confirm(chosen.toTypedArray())) }) { Text("Aceptar", color = k.accent) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar", color = k.ink70) } },
    )
}

/** Fechas y horas con los selectores nativos de Android; el resto como texto. */
@Composable
private fun DateTime(p: PromptDelegate.DateTimePrompt, finish: (PromptDelegate.PromptResponse) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    when (p.type) {
        PromptDelegate.DateTimePrompt.Type.DATE -> DisposableEffect(p) {
            val c = Calendar.getInstance()
            p.defaultValue?.split('-')?.takeIf { it.size == 3 }?.let {
                c.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt())
            }
            val d = DatePickerDialog(context, { _, y, m, day ->
                finish(p.confirm("%04d-%02d-%02d".format(y, m + 1, day)))
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            d.setOnCancelListener { onCancel() }
            d.show()
            onDispose { d.setOnCancelListener(null); d.dismiss() }
        }
        PromptDelegate.DateTimePrompt.Type.TIME -> DisposableEffect(p) {
            val parts = p.defaultValue?.split(':')
            val d = TimePickerDialog(context, { _, h, m ->
                finish(p.confirm("%02d:%02d".format(h, m)))
            }, parts?.getOrNull(0)?.toIntOrNull() ?: 12, parts?.getOrNull(1)?.toIntOrNull() ?: 0, true)
            d.setOnCancelListener { onCancel() }
            d.show()
            onDispose { d.setOnCancelListener(null); d.dismiss() }
        }
        else -> TextInput(
            p.title, "Formato: " + when (p.type) {
                PromptDelegate.DateTimePrompt.Type.MONTH -> "AAAA-MM"
                PromptDelegate.DateTimePrompt.Type.WEEK -> "AAAA-Www"
                else -> "AAAA-MM-DDTHH:MM"
            },
            p.defaultValue.orEmpty(), { finish(p.confirm(it)) }, onCancel,
        )
    }
}

/** Permisos de la página: ubicación, notificaciones, cámara, micrófono. */
@Composable
fun PermissionHost(ask: PermissionAsk, onDone: () -> Unit) {
    val (what, allow, deny) = when (ask) {
        is PermissionAsk.Content -> Triple(
            when (ask.permission.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> "conocer tu ubicación"
                PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "enviarte notificaciones"
                PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "reproducir contenido protegido (DRM)"
                PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS ->
                    "acceder a dispositivos de tu red local"
                else -> "usar un permiso especial"
            },
            { ask.result.complete(PermissionDelegate.ContentPermission.VALUE_ALLOW) },
            { ask.result.complete(PermissionDelegate.ContentPermission.VALUE_DENY) },
        )
        is PermissionAsk.Media -> Triple(
            listOfNotNull(
                ask.video?.takeIf { it.isNotEmpty() }?.let { "la cámara" },
                ask.audio?.takeIf { it.isNotEmpty() }?.let { "el micrófono" },
            ).joinToString(" y ").ifEmpty { "un dispositivo multimedia" }.let { "usar $it" },
            { ask.callback.grant(ask.video?.firstOrNull(), ask.audio?.firstOrNull()) },
            { ask.callback.reject() },
        )
    }
    Simple(
        ask.host, "Este sitio quiere $what.", "Permitir", "Bloquear",
        { allow(); onDone() }, { deny(); onDone() },
    )
}
