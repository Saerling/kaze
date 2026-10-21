/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.kaze.browser.data.Db
import app.kaze.browser.data.Settings
import app.kaze.browser.data.Store
import app.kaze.browser.data.Tab
import app.kaze.browser.data.Urls
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.HistoryDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.ScrollDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse

/**
 * Administra las sesiones de Gecko (una por pestaña *cargada*).
 *
 * Es el corazón del ahorro de RAM:
 *  - Solo hay como mucho `settings.liveTabs` páginas cargadas; las demás se "duermen":
 *    se guarda su estado (historial, scroll, formularios) y se cierra la sesión.
 *  - Una pestaña sin usar más de `settings.sleepAfterMinutes` se duerme sola.
 *  - Si Android avisa de memoria baja, se duermen todas menos la visible.
 *  - Al volver a una pestaña dormida, se restaura desde su estado guardado.
 */
class SessionPool(
    private val runtime: GeckoRuntime,
    private val store: Store,
    private val settings: Settings,
    private val db: Db,
    private val ui: BrowserUi,
    private val downloads: Downloads,
) {
    private val sessions = HashMap<String, GeckoSession>()
    private val main = Handler(Looper.getMainLooper())

    /** Lo pone MainActivity: suelta la sesión del GeckoView antes de cerrarla. */
    var beforeClose: ((GeckoSession) -> Unit)? = null

    /** Abre esquemas que no son web (tel:, mailto:, intent:, market:...) con otras apps. */
    var externalOpener: ((String) -> Boolean)? = null

    /** La pestaña que está en pantalla (activa). */
    var visibleTabId: String? = null
        private set

    private val sweeper = object : Runnable {
        override fun run() {
            sweepIdle()
            main.postDelayed(this, 60_000)
        }
    }

    init {
        main.postDelayed(sweeper, 60_000)
    }

    fun sessionOf(tabId: String): GeckoSession? = sessions[tabId]

    /** Devuelve la sesión de la pestaña, cargándola si estaba dormida. */
    fun ensure(tab: Tab): GeckoSession {
        sessions[tab.id]?.let { return it }
        val s = newSession(tab)
        s.open(runtime)
        val state = tab.sessionState?.let { runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull() }
        when {
            state != null -> s.restoreState(state)
            tab.isStart -> Unit // la página de inicio la dibuja Kaze; el motor espera a que se navegue
            else -> s.loadUri(tab.url)
        }
        sessions[tab.id] = s
        tab.isLive = true
        return s
    }

    /** Pone [tab] en primer plano: la activa a ella y pausa la anterior. */
    fun show(tab: Tab): GeckoSession {
        val prev = visibleTabId
        if (prev != null && prev != tab.id) sessions[prev]?.setActive(false)
        val s = ensure(tab)
        s.setActive(true)
        visibleTabId = tab.id
        trim()
        return s
    }

    /** Ninguna pestaña visible (por ejemplo, en la pantalla de espacios): todo en pausa. */
    fun hideAll() {
        visibleTabId?.let { sessions[it]?.setActive(false) }
    }

    fun resumeVisible() {
        visibleTabId?.let { sessions[it]?.setActive(true) }
    }

    fun unload(tab: Tab) {
        val s = sessions.remove(tab.id) ?: return
        ports.remove(tab.id)
        beforeClose?.invoke(s)
        s.setActive(false)
        s.close()
        tab.isLive = false
        tab.loading = false
        tab.progress = 100
        if (visibleTabId == tab.id) visibleTabId = null
    }

    fun close(tab: Tab) {
        unload(tab)
    }

    /** Respeta el máximo de páginas cargadas: duerme las usadas hace más tiempo. */
    fun trim() {
        val max = settings.liveTabs.coerceAtLeast(1)
        val live = sessions.keys.mapNotNull(store::tabById).filter { it.id != visibleTabId }
            .sortedByDescending { it.lastUsed }
        live.drop(max - 1).forEach {
            Log.d(TAG, "Durmiendo pestaña (límite): ${it.url}")
            unload(it)
        }
    }

    private fun sweepIdle() {
        val minutes = settings.sleepAfterMinutes
        if (minutes <= 0) return
        val limit = System.currentTimeMillis() - minutes * 60_000L
        sessions.keys.mapNotNull(store::tabById)
            .filter { it.id != visibleTabId && it.lastUsed < limit }
            .forEach {
                Log.d(TAG, "Durmiendo pestaña (inactiva): ${it.url}")
                unload(it)
            }
    }

    /** Llamado cuando Android pide memoria. */
    fun onLowMemory() {
        sessions.keys.mapNotNull(store::tabById).filter { it.id != visibleTabId }.forEach(::unload)
    }

    /** Cambia entre versión móvil y de escritorio de la página. */
    fun setDesktopMode(tab: Tab, desktop: Boolean) {
        tab.desktopMode = desktop
        sessions[tab.id]?.let { s ->
            s.settings.userAgentMode =
                if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            s.settings.viewportMode =
                if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            s.reload()
        }
    }

    // ── Creación de sesiones y delegados ──────────────────────────────

    private fun newSession(tab: Tab): GeckoSession {
        val space = store.spaceById(tab.spaceId)
        val b = GeckoSessionSettings.Builder()
            .usePrivateMode(tab.isPrivate)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .allowJavascript(settings.javascript)
        space?.contextId(store.profileById(space.profileId))?.let(b::contextId)
        if (tab.desktopMode) {
            b.userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            b.viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
        }
        return GeckoSession(b.build()).also { attach(tab, it) }
    }

    // ── Extensión interna (modo Lector) ───────────────────────────────

    /** La pone Kaze cuando termina de instalar la extensión interna. */
    var extension: WebExtension? = null
        set(value) {
            field = value
            if (value != null) sessions.forEach { (id, s) -> store.tabById(id)?.let { connectExtension(it, s) } }
        }
    private val ports = HashMap<String, WebExtension.Port>()

    private fun connectExtension(tab: Tab, s: GeckoSession) {
        val ext = extension ?: return
        s.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                ports[tab.id] = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        val m = message as? JSONObject ?: return
                        main.post {
                            when (m.optString("type")) {
                                "readerable" -> tab.readerable = m.optBoolean("value")
                                "reader" -> tab.readerMode = m.optBoolean("active")
                                "error" -> ui.show(m.optString("message"))
                            }
                        }
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (ports[tab.id] === port) ports.remove(tab.id)
                    }
                })
            }
        }, "kaze")
    }

    /** Activa o desactiva el modo Lector en la pestaña. */
    fun setReader(tab: Tab, on: Boolean, options: JSONObject? = null) {
        val port = ports[tab.id] ?: run { ui.show("El Lector aún no está listo en esta página"); return }
        options?.let { port.postMessage(JSONObject().put("action", "options").put("options", it)) }
        port.postMessage(JSONObject().put("action", if (on) "reader" else "exitReader"))
        if (!on) tab.readerMode = false
    }

    fun readerOptions(tab: Tab, options: JSONObject) {
        ports[tab.id]?.postMessage(JSONObject().put("action", "options").put("options", options))
    }

    // ── Traducir página ───────────────────────────────────────────────

    /** Idioma del teléfono (destino de las traducciones). */
    val userLang: String get() = java.util.Locale.getDefault().language.ifBlank { "es" }

    fun translate(tab: Tab) {
        val s = sessions[tab.id] ?: return
        val from = tab.pageLang
        if (from.isNullOrBlank()) { ui.show("No se reconoce el idioma de esta página"); return }
        ui.show("Traduciendo… (la primera vez descarga el idioma)")
        val opts = TranslationsController.SessionTranslation.TranslationOptions.Builder().downloadModel(true).build()
        s.sessionTranslation?.translate(from, userLang, opts)
            ?.accept({ main.post { tab.translated = true } }, { main.post { ui.show("No se pudo traducir esta página") } })
    }

    fun showOriginal(tab: Tab) {
        sessions[tab.id]?.sessionTranslation?.restoreOriginalPage()?.accept({ main.post { tab.translated = false } }, { })
    }

    // ── Reporte de privacidad ─────────────────────────────────────────

    private fun countTracker(tab: Tab) {
        tab.trackersBlocked++
        if (settings.trackersSince.isEmpty()) settings.trackersSince = System.currentTimeMillis().toString()
        settings.trackersTotal = settings.trackersTotal + 1
    }

    private fun attach(tab: Tab, s: GeckoSession) {
        connectExtension(tab, s)
        s.translationsSessionDelegate = object : TranslationsController.SessionTranslation.Delegate {
            override fun onTranslationStateChange(
                session: GeckoSession,
                state: TranslationsController.SessionTranslation.TranslationState?,
            ) {
                val lang = state?.detectedLanguages?.docLangTag
                main.post { tab.pageLang = lang }
            }
        }
        s.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (event.antiTrackingCategory != 0) main.post { countTracker(tab) }
            }
        }
        s.contentDelegate = Content(tab)
        s.progressDelegate = Progress(tab)
        s.navigationDelegate = Navigation(tab)
        s.historyDelegate = History(tab)
        s.permissionDelegate = Permissions(tab)
        s.promptDelegate = Prompts()
        s.scrollDelegate = object : ScrollDelegate {
            private var lastY = 0
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                val dy = scrollY - lastY
                lastY = scrollY
                if (tab.id == visibleTabId && kotlin.math.abs(dy) > 6) ui.scrollDirection = if (dy > 0) 1 else -1
                if (scrollY <= 0) ui.scrollDirection = -1
            }
        }
    }

    private inner class Content(private val tab: Tab) : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            store.onTitle(tab, title.orEmpty())
        }

        override fun onCloseRequest(session: GeckoSession) {
            unload(tab)
            store.close(tab)
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            ui.fullscreen = fullScreen
        }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
            if (element.linkUri != null || element.srcUri != null) ui.contextMenu = ContextMenuRequest(tab, element)
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            downloads.save(response)
        }

        override fun onCrash(session: GeckoSession) = recover("se cerró inesperadamente")
        override fun onKill(session: GeckoSession) = recover("se descargó por falta de memoria")

        private fun recover(why: String) {
            Log.w(TAG, "La página ${tab.url} $why")
            val wasVisible = tab.id == visibleTabId
            sessions.remove(tab.id)?.let { s -> beforeClose?.invoke(s); runCatching { s.close() } }
            tab.isLive = false
            if (wasVisible) {
                visibleTabId = null
                store.select(tab) // la UI vuelve a pedir la sesión y se recarga desde su estado
            }
        }
    }

    private inner class Progress(private val tab: Tab) : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            tab.loading = true
            tab.progress = 5
            tab.readerable = false
            tab.readerMode = false
            tab.translated = false
            tab.trackersBlocked = 0
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            tab.loading = false
            tab.progress = 100
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            tab.progress = progress
        }

        override fun onSecurityChange(session: GeckoSession, info: ProgressDelegate.SecurityInformation) {
            tab.isSecure = info.isSecure || tab.url.startsWith("about:")
        }

        override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) {
            store.onSessionState(tab, state.toString())
        }
    }

    private inner class Navigation(private val tab: Tab) : NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url != null) store.onUrl(tab, url)
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            tab.canGoBack = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            tab.canGoForward = canGoForward
        }

        override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
            val scheme = request.uri.substringBefore(':').lowercase()
            if (scheme in WEB_SCHEMES) return null
            val opened = externalOpener?.invoke(request.uri) ?: false
            if (!opened && scheme != "intent") ui.show("No hay una app para abrir este enlace")
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // Enlaces con target="_blank" o window.open(): pestaña nueva en el mismo espacio.
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
            val child = store.newTab(uri, spaceId = tab.spaceId, private = tab.isPrivate, select = false)
            val s = newSession(child)
            sessions[child.id] = s
            child.isLive = true
            main.post { store.select(child) }
            return GeckoResult.fromValue(s)
        }
    }

    private inner class History(private val tab: Tab) : HistoryDelegate {
        override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
            if (tab.isPrivate) return GeckoResult.fromValue(false)
            val skip = HistoryDelegate.VISIT_REDIRECT_SOURCE or HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT or
                HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
            if (flags and HistoryDelegate.VISIT_TOP_LEVEL == 0 || flags and skip != 0) return GeckoResult.fromValue(false)
            if (!url.startsWith("http")) return GeckoResult.fromValue(false)
            db.recordVisit(url, if (tab.url == url) tab.title else "")
            return GeckoResult.fromValue(true)
        }

        override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray> {
            val result = GeckoResult<BooleanArray>()
            db.io.execute { result.complete(runCatching { db.visited(urls) }.getOrElse { BooleanArray(urls.size) }) }
            return result
        }
    }

    private inner class Permissions(private val tab: Tab) : PermissionDelegate {
        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: PermissionDelegate.ContentPermission,
        ): GeckoResult<Int> {
            // Reproducción automática y almacenamiento: decisiones silenciosas, sin molestar.
            when (perm.permission) {
                PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                PermissionDelegate.PERMISSION_STORAGE_ACCESS,
                -> return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_ALLOW)
                PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                PermissionDelegate.PERMISSION_TRACKING,
                PermissionDelegate.PERMISSION_XR,
                -> return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
            }
            val result = GeckoResult<Int>()
            ui.permission = PermissionAsk.Content(Urls.displayHost(perm.uri), perm, result)
            return result
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<PermissionDelegate.MediaSource>?,
            audio: Array<PermissionDelegate.MediaSource>?,
            callback: PermissionDelegate.MediaCallback,
        ) {
            ui.permission = PermissionAsk.Media(Urls.displayHost(uri), video, audio, callback)
        }

        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<String>?,
            callback: PermissionDelegate.Callback,
        ) {
            val req = androidPermissions
            if (permissions.isNullOrEmpty() || req == null) callback.grant() else req(permissions, callback)
        }
    }

    /** Lo pone MainActivity: pide permisos de Android (cámara, micrófono, ubicación). */
    var androidPermissions: ((Array<String>, PermissionDelegate.Callback) -> Unit)? = null

    private inner class Prompts : PromptDelegate {
        private fun <T : PromptDelegate.BasePrompt> ask(p: T): GeckoResult<PromptDelegate.PromptResponse> {
            val result = GeckoResult<PromptDelegate.PromptResponse>()
            ui.prompt?.dismiss()
            ui.prompt = PendingPrompt(p, result)
            return result
        }

        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) = ask(prompt)
        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) = ask(prompt)
        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) = ask(prompt)
        override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt) = ask(prompt)
        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) = ask(prompt)
        override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt) = ask(prompt)
        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt) = ask(prompt)
        override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) = ask(prompt)
        override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt) = ask(prompt)
        override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) = ask(prompt)
        override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) = ask(prompt)
    }

    companion object {
        private const val TAG = "KazeSessions"
        private val WEB_SCHEMES = setOf(
            "http", "https", "about", "data", "blob", "file", "javascript", "moz-extension", "resource", "view-source",
        )
    }
}
