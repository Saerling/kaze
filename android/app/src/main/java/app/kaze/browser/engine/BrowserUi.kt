/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kaze.browser.data.Tab
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/** Un diálogo que pidió la página (alert, select, login, archivo...). La UI lo muestra y responde. */
class PendingPrompt(val prompt: BasePrompt, private val result: GeckoResult<PromptResponse>) {
    private var done = false
    fun finish(response: PromptResponse) {
        if (done) return
        done = true
        result.complete(response)
    }
    fun dismiss() = finish(prompt.dismiss())
}

/** Permisos que la página pide (ubicación, cámara, notificaciones...). */
sealed interface PermissionAsk {
    val host: String

    class Content(
        override val host: String,
        val permission: PermissionDelegate.ContentPermission,
        val result: GeckoResult<Int>,
    ) : PermissionAsk

    class Media(
        override val host: String,
        val video: Array<PermissionDelegate.MediaSource>?,
        val audio: Array<PermissionDelegate.MediaSource>?,
        val callback: PermissionDelegate.MediaCallback,
    ) : PermissionAsk
}

class ContextMenuRequest(val tab: Tab, val element: ContextElement)

/**
 * Estado de la UI que nace en Gecko: diálogos, menú contextual, pantalla completa, avisos.
 * Vive aparte de las pantallas para que sobreviva a rotaciones y cambios de tema.
 */
class BrowserUi {
    var prompt by mutableStateOf<PendingPrompt?>(null)
    var permission by mutableStateOf<PermissionAsk?>(null)
    var contextMenu by mutableStateOf<ContextMenuRequest?>(null)
    var fullscreen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    /** Acción del aviso (p. ej. "Deshacer"). */
    var toastAction by mutableStateOf<Pair<String, () -> Unit>?>(null)
    /** Pide abrir Ajustes desde cualquier hoja. */
    var openSettings by mutableStateOf(false)

    /** Dirección del último scroll de la página: >0 bajando, <0 subiendo. */
    var scrollDirection by mutableIntStateOf(0)

    var findQuery by mutableStateOf<String?>(null)
    var findCurrent by mutableIntStateOf(0)
    var findTotal by mutableIntStateOf(0)

    fun show(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        toastAction = if (action != null && onAction != null) action to onAction else null
        toast = message
    }
}
