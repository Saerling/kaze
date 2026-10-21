/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.SessionState
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.UUID

/**
 * Una pestaña. Puede estar:
 *  - **viva**: tiene una [GeckoSession] abierta (la página ocupa RAM en Gecko);
 *  - **descargada**: solo guardamos su [SessionState] (URL, historial, scroll) y no ocupa RAM del motor.
 *
 * Al seleccionarla, [ensureSession] la vuelve a cargar desde el estado guardado.
 */
class Tab(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String,
    initialTitle: String = "",
    private var savedState: SessionState? = null,
) {
    var url by mutableStateOf(initialUrl)
        internal set
    var title by mutableStateOf(initialTitle)
        internal set
    var progress by mutableIntStateOf(100)
        internal set
    var canGoBack by mutableStateOf(false)
        internal set
    var canGoForward by mutableStateOf(false)
        internal set
    var isLoaded by mutableStateOf(false)
        private set

    /** Momento en que se usó por última vez; sirve para decidir qué pestaña descargar primero. */
    var lastUsed: Long = System.currentTimeMillis()
        internal set

    var session: GeckoSession? = null
        private set

    /** Último estado conocido (para guardarlo en disco aunque la pestaña siga viva). */
    val stateForPersistence: SessionState? get() = savedState

    val displayTitle: String get() = title.ifBlank { url }

    /** Abre la sesión si está descargada. Devuelve la sesión viva. */
    internal fun ensureSession(runtime: GeckoRuntime, attach: (Tab, GeckoSession) -> Unit): GeckoSession {
        session?.let { return it }

        val settings = GeckoSessionSettings.Builder()
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .build()
        val nueva = GeckoSession(settings)
        attach(this, nueva)
        nueva.open(runtime)

        val estado = savedState
        if (estado != null) {
            nueva.restoreState(estado)
        } else {
            nueva.loadUri(url)
        }
        session = nueva
        isLoaded = true
        return nueva
    }

    /** Envuelve una sesión que Gecko ya creó (por ejemplo, un enlace con target="_blank"). */
    internal fun adoptSession(nueva: GeckoSession) {
        session = nueva
        isLoaded = true
    }

    internal fun onStateChanged(state: SessionState) {
        savedState = state
    }

    /** Libera el motor de esta pestaña conservando su estado. */
    internal fun unload() {
        val s = session ?: return
        s.setActive(false)
        s.close()
        session = null
        isLoaded = false
        canGoBack = false
        canGoForward = false
        progress = 100
    }
}
