/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.tabs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kaze.browser.UrlUtils
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.SessionState
import org.mozilla.geckoview.GeckoSessionSettings
import java.io.File

/**
 * Administra todas las pestañas y decide cuáles mantener vivas en memoria.
 *
 * Regla de RAM: como mucho [MAX_LIVE_SESSIONS] pestañas tienen el motor cargado.
 * Al pasar ese límite se descargan las menos usadas recientemente.
 */
class TabManager(context: Context, private val runtime: GeckoRuntime) {

    companion object {
        const val MAX_LIVE_SESSIONS = 3
        private const val TAG = "KazeTabs"
        private const val ARCHIVO = "tabs.json"
    }

    private val archivo = File(context.filesDir, ARCHIVO)
    private val principal = Handler(Looper.getMainLooper())

    val tabs = mutableStateListOf<Tab>()

    var selected by mutableStateOf<Tab?>(null)
        private set

    init {
        restore()
        if (tabs.isEmpty()) newTab(UrlUtils.HOME)
    }

    // ── Operaciones públicas ─────────────────────────────────────────

    fun newTab(url: String, select: Boolean = true): Tab {
        val tab = Tab(initialUrl = url)
        val i = selected?.let { tabs.indexOf(it) + 1 } ?: tabs.size
        tabs.add(i.coerceIn(0, tabs.size), tab)
        if (select) select(tab)
        return tab
    }

    fun select(tab: Tab) {
        val anterior = selected
        if (anterior != null && anterior !== tab) anterior.session?.setActive(false)

        tab.lastUsed = System.currentTimeMillis()
        val sesion = tab.ensureSession(runtime, ::attachDelegates)
        sesion.setActive(true)
        selected = tab
        trimLiveSessions()
    }

    fun close(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tab.unload()
        tabs.removeAt(idx)
        if (tabs.isEmpty()) {
            selected = null
            newTab(UrlUtils.HOME)
        } else if (selected === tab) {
            // Como en Zen: al cerrar, volver a la pestaña usada más recientemente.
            select(tabs.maxBy { it.lastUsed })
        }
    }

    fun load(input: String) {
        val url = UrlUtils.toUrl(input)
        val tab = selected ?: newTab(url)
        tab.url = url
        tab.session?.loadUri(url) ?: select(tab)
    }

    /** Descarga todas las pestañas menos la visible (llamado cuando Android pide memoria). */
    fun unloadAllExceptSelected() {
        tabs.filter { it !== selected && it.isLoaded }.forEach { it.unload() }
        Log.i(TAG, "Memoria baja: descargadas las pestañas en segundo plano")
    }

    // ── Gestión de memoria ───────────────────────────────────────────

    private fun trimLiveSessions() {
        val vivas = tabs.filter { it.isLoaded && it !== selected }.sortedByDescending { it.lastUsed }
        vivas.drop(MAX_LIVE_SESSIONS - 1).forEach {
            Log.d(TAG, "Descargando pestaña inactiva: ${it.url}")
            it.unload()
        }
    }

    // ── Delegados de Gecko: mantienen el estado de la pestaña al día ─

    private fun attachDelegates(tab: Tab, session: GeckoSession) {
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onCloseRequest(session: GeckoSession) {
                close(tab)
            }

            override fun onCrash(session: GeckoSession) {
                // El proceso de la página murió: descargarla; al volver se recarga desde su estado.
                tab.unload()
                if (selected === tab) select(tab)
            }

            override fun onKill(session: GeckoSession) {
                tab.unload()
                if (selected === tab) select(tab)
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }

            override fun onSessionStateChange(session: GeckoSession, sessionState: SessionState) {
                tab.onStateChanged(sessionState)
            }
        }

        session.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (url != null) tab.url = url
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            // Enlaces que abren ventana nueva (target="_blank", window.open)
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val nueva = Tab(initialUrl = uri)
                val sesion = GeckoSession(
                    GeckoSessionSettings.Builder()
                        .useTrackingProtection(true)
                        .suspendMediaWhenInactive(true)
                        .build(),
                )
                attachDelegates(nueva, sesion)
                nueva.adoptSession(sesion)
                tabs.add(tabs.indexOf(tab) + 1, nueva)
                // Gecko abre la sesión al recibirla; la seleccionamos en el siguiente ciclo.
                principal.post { select(nueva) }
                return GeckoResult.fromValue(sesion)
            }
        }
    }

    // ── Guardar / restaurar pestañas entre reinicios ─────────────────

    fun persist() {
        try {
            val arr = JSONArray()
            tabs.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("url", t.url)
                        .put("title", t.title)
                        .put("lastUsed", t.lastUsed)
                        .put("state", t.stateForPersistence?.toString()),
                )
            }
            val raiz = JSONObject().put("tabs", arr).put("selected", selected?.id)
            archivo.writeText(raiz.toString())
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron guardar las pestañas", e)
        }
    }

    private fun restore() {
        if (!archivo.exists()) return
        try {
            val raiz = JSONObject(archivo.readText())
            val arr = raiz.getJSONArray("tabs")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val estado = o.optString("state").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { SessionState.fromString(it) }
                tabs.add(
                    Tab(
                        id = o.getString("id"),
                        initialUrl = o.getString("url"),
                        initialTitle = o.optString("title"),
                        savedState = estado,
                    ).apply { lastUsed = o.optLong("lastUsed") },
                )
            }
            // Solo se carga en memoria la pestaña seleccionada; el resto queda descargado.
            val sel = tabs.firstOrNull { it.id == raiz.optString("selected") } ?: tabs.lastOrNull()
            sel?.let { select(it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron restaurar las pestañas", e)
            tabs.clear()
        }
    }
}
