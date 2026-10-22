/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import app.kaze.browser.ui.sheets.RecentlyClosedSheet
import app.kaze.browser.ui.sheets.ClearDataSheet
import app.kaze.browser.ui.sheets.MenuActions
import app.kaze.browser.ui.sheets.MainMenuSheet
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import app.kaze.browser.ui.screens.ZenPanel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.kaze.browser.data.Settings
import app.kaze.browser.data.Space
import app.kaze.browser.data.Tab
import app.kaze.browser.data.TabKind
import app.kaze.browser.data.Urls
import app.kaze.browser.engine.Engine
import app.kaze.browser.engine.PendingPrompt
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.screens.ChromeActions
import app.kaze.browser.ui.screens.FindBar
import app.kaze.browser.ui.screens.SafariBottomBar
import app.kaze.browser.ui.screens.StartPage
import app.kaze.browser.ui.screens.TabsScreen
import app.kaze.browser.ui.sheets.GroupsSheet
import app.kaze.browser.ui.sheets.LibrarySheet
import app.kaze.browser.ui.sheets.PrivacyReportSheet
import app.kaze.browser.ui.sheets.ShareSheet
import app.kaze.browser.ui.sheets.TabsQuickSheet
import app.kaze.browser.ui.screens.SearchScreen
import app.kaze.browser.ui.sheets.ClipboardBridge
import app.kaze.browser.ui.sheets.ContextMenuSheet
import app.kaze.browser.ui.sheets.CrashSheet
import app.kaze.browser.ui.sheets.MoveToSpaceSheet
import app.kaze.browser.ui.sheets.PageMenuSheet
import app.kaze.browser.ui.sheets.PermissionHost
import app.kaze.browser.ui.sheets.PromptHost
import app.kaze.browser.ui.sheets.SettingsSheet
import app.kaze.browser.ui.sheets.SpaceEditorSheet
import app.kaze.browser.ui.sheets.TabMenuSheet
import app.kaze.browser.ui.sheets.UpdateSheet
import app.kaze.browser.ui.theme.KazeTheme
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate

/** Qué pidió abrir la pantalla de búsqueda. */
private sealed interface SearchTarget {
    val initial: String
    data class NewTab(val spaceId: String, val private: Boolean, override val initial: String = "") : SearchTarget
    data class Navigate(val tab: Tab, override val initial: String) : SearchTarget
}

class MainActivity : ComponentActivity() {

    private lateinit var app: Kaze
    private lateinit var geckoView: GeckoView

    /** Estado de navegación de la UI (sobrevive mientras viva la actividad). */
    private var showTabs by mutableStateOf(false)

    private var pendingFile: Pair<PromptDelegate.FilePrompt, PendingPrompt>? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val (prompt, pending) = pendingFile ?: return@registerForActivityResult
        pendingFile = null
        val data = res.data
        val uris = buildList {
            data?.clipData?.let { c -> for (i in 0 until c.itemCount) add(c.getItemAt(i).uri) }
            if (isEmpty()) data?.data?.let(::add)
        }
        when {
            uris.isEmpty() -> pending.dismiss()
            prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE -> pending.finish(prompt.confirm(this, uris.toTypedArray()))
            else -> pending.finish(prompt.confirm(this, uris.first()))
        }
    }

    private var pendingPermission: PermissionDelegate.Callback? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val cb = pendingPermission ?: return@registerForActivityResult
        pendingPermission = null
        if (r.values.all { it }) cb.grant() else cb.reject()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Si se cerró por un error, mostrarlo ANTES de arrancar el motor (por si el fallo está ahí).
        val crash = if (savedInstanceState == null) CrashLog.take(this) else null
        if (crash != null) showCrash(crash) else startBrowser(savedInstanceState)
    }

    private fun showCrash(text: String) {
        setContent {
            KazeTheme(Engine.isDarkTheme(this, Settings(this))) {
                Box(Modifier.fillMaxSize().background(LocalKaze.current.paper)) {
                    CrashSheet(
                        text = text,
                        onCopy = {
                            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Kaze", text))
                        },
                        onShare = { share(text, "Error de Kaze") },
                        onDismiss = { startBrowser(null) },
                    )
                }
            }
        }
    }

    private fun startBrowser(savedInstanceState: Bundle?) {
        if (::app.isInitialized) return
        app = Kaze.get(this)
        geckoView = GeckoView(this).apply { visibility = View.INVISIBLE }

        app.pool.beforeClose = { s -> if (geckoView.session === s) geckoView.releaseSession() }
        app.pool.externalOpener = ::openExternal
        app.pool.androidPermissions = { perms, cb ->
            val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) cb.grant() else {
                pendingPermission = cb
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
        ClipboardBridge.copy = { text ->
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Kaze", text))
            app.ui.show("Copiado")
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val dark = Engine.isDarkTheme(this, app.settings)
            KazeTheme(dark, app.settings.uiStyle, app.settings.accent) {
                CompositionLocalProvider(LocalApp provides app) { Root() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::app.isInitialized) handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (::app.isInitialized && !showTabs) app.pool.resumeVisible()
    }

    override fun onStop() {
        super.onStop()
        if (!::app.isInitialized) return
        app.pool.hideAll()
        app.store.flush()
    }

    override fun onDestroy() {
        if (!::app.isInitialized) return super.onDestroy()
        if (geckoView.session != null) geckoView.releaseSession()
        if (isFinishing) {
            app.pool.beforeClose = null
            ClipboardBridge.copy = null
        }
        super.onDestroy()
    }

    // ── Intents: enlaces de otras apps, compartir, búsquedas ──────────

    private fun handleIntent(intent: Intent?) {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("https?://\\S+").find(text)?.value ?: Urls.fromInput(text, app.settings.searchEngine)
            }
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH ->
                intent.getStringExtra(SearchManager.QUERY)?.let { Urls.fromInput(it, app.settings.searchEngine) }
            else -> null
        } ?: return
        app.store.newTab(url)
        showTabs = false
    }

    private fun openExternal(uri: String): Boolean = try {
        val intent = if (uri.startsWith("intent:")) {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        // intent: con enlace de respaldo para navegadores
        Uri.parse(uri).getQueryParameter("browser_fallback_url")?.let { app.store.selectedTab?.let { t -> app.pool.ensure(t).loadUri(it) } }
        false
    } catch (_: Exception) {
        false
    }

    private fun share(url: String, title: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
        startActivity(Intent.createChooser(send, null))
    }

    private fun openFilePicker(prompt: PromptDelegate.FilePrompt, pending: PendingPrompt) {
        pendingFile = prompt to pending
        val mimes = prompt.mimeTypes?.filter { it.contains('/') }.orEmpty()
        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
            type = if (mimes.size == 1) mimes[0] else "*/*"
            if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE)
        }
        try {
            fileLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pendingFile = null
            pending.dismiss()
        }
    }

    private fun restart() {
        app.store.flush()
        val i = packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    // ── UI ────────────────────────────────────────────────────────────

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Root() {
        val store = app.store
        val ui = app.ui
        val k = LocalKaze.current
        val tab = store.selectedTab

        var search by remember { mutableStateOf<SearchTarget?>(null) }
        var tabMenu by remember { mutableStateOf<Tab?>(null) }
        var moveTab by remember { mutableStateOf<Tab?>(null) }
        var aaMenu by remember { mutableStateOf(false) }
        var shareMenu by remember { mutableStateOf(false) }
        var mainMenu by remember { mutableStateOf(false) }
        var clearData by remember { mutableStateOf(false) }
        var recentClosed by remember { mutableStateOf(false) }
        var tabsQuick by remember { mutableStateOf(false) }
        var groups by remember { mutableStateOf(false) }
        var editSpace by remember { mutableStateOf<Space?>(null) }
        var newSpace by remember { mutableStateOf(false) }
        var library by remember { mutableStateOf(false) }
        var settings by remember { mutableStateOf(false) }
        var privacy by remember { mutableStateOf(false) }
        var finding by remember { mutableStateOf(false) }
        var barHidden by remember { mutableStateOf(false) }
        var zenPanel by remember { mutableStateOf(false) }
        // Tras buscar, la barra queda en píldora para dejarle la pantalla a la página.
        var pillAfterSearch by remember { mutableStateOf(false) }
        // Alto máximo que ha tenido la barra (para la barra dinámica de Gecko).
        var barMaxPx by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()

        // Buscar una versión nueva en GitHub al abrir (solo en las versiones publicadas).
        LaunchedEffect(Unit) {
            val u = app.updater
            if (!BuildConfig.DEBUG && u.shouldAutoCheck()) {
                val info = withContext(Dispatchers.IO) { u.check() }
                if (info != null && !u.isSkipped(info)) u.showSheet = true
            }
        }

        // Al abrir: volver a la última pestaña del espacio, o una pestaña nueva con la página de inicio.
        LaunchedEffect(Unit) {
            if (store.selectedTab == null && !showTabs) {
                val last = store.tabs.filter { it.spaceId == store.currentSpaceId && !it.isPrivate }.maxByOrNull { it.lastUsed }
                if (last != null) store.select(last) else store.newTab(Urls.START)
            }
        }

        /** Foto de la página actual para la vista de pestañas (solo la visible: no gasta RAM extra). */
        fun snapshot(then: () -> Unit) {
            val t = store.selectedTab
            if (t == null || t.isStart || geckoView.visibility != View.VISIBLE || geckoView.session == null) {
                then(); return
            }
            geckoView.capturePixels().accept({ bmp -> if (bmp != null) app.thumbnails.save(t.id, bmp); then() }, { then() })
        }

        fun open(t: Tab) {
            store.select(t)
            showTabs = false
        }

        fun newTab(private: Boolean = false) {
            val t = store.newTab(Urls.START, private = private)
            open(t)
        }

        fun closeTab(t: Tab) {
            val next = if (t.id == store.selectedTabId) store.nextAfterClose(t) else null
            app.pool.close(t)
            app.thumbnails.delete(t.id)
            store.close(t)
            if (next != null) open(next) else if (store.selectedTab == null) showTabs = true
        }

        fun showTabsGrid() = snapshot { showTabs = true }

        /** Deslizar la barra: pestaña anterior/siguiente del espacio (como Safari). */
        fun swipeTab(delta: Int) {
            val t = store.selectedTab ?: return
            val list = (store.pinned(store.currentSpaceId) + store.normal(store.currentSpaceId)).filter { !it.isPrivate == !t.isPrivate }
            val i = list.indexOfFirst { it.id == t.id }
            val next = list.getOrNull(i + delta) ?: return
            snapshot { open(next) }
        }

        /** Ir a otro espacio (Zen): su última pestaña, o una nueva con la página de inicio. */
        fun goToSpace(id: String) {
            if (id == store.currentSpaceId) return
            store.selectSpace(id)
            val last = (store.pinned(id) + store.normal(id)).maxByOrNull { it.lastUsed }
            snapshot { if (last != null) open(last) else newTab(private = id == Space.PRIVATE_ID) }
        }

        fun stepSpace(delta: Int) {
            val list = store.profileSpaces
            val i = list.indexOfFirst { it.id == store.currentSpaceId }
            if (i < 0 || list.isEmpty()) return
            goToSpace(list[(i + delta + list.size) % list.size].id)
        }

        // Mostrar la pestaña seleccionada en el GeckoView (o pausar todo en la vista de pestañas).
        LaunchedEffect(store.selectedTabId, store.selectionNonce, showTabs, tab?.isStart) {
            val t = store.selectedTab
            if (!showTabs && t != null && !t.isStart) {
                val s: GeckoSession = app.pool.show(t)
                if (geckoView.session !== s) {
                    if (geckoView.session != null) geckoView.releaseSession()
                    geckoView.setSession(s)
                }
                geckoView.visibility = View.VISIBLE
                ui.scrollDirection = -1
                barHidden = false
            } else {
                app.pool.hideAll()
                // INVISIBLE destruye la superficie de dibujo: Gecko libera memoria de GPU.
                geckoView.visibility = View.INVISIBLE
                if (t == null && !showTabs) showTabs = true
            }
        }

        // Pantalla completa (videos): esconder barras del sistema.
        LaunchedEffect(ui.fullscreen) {
            val c = WindowCompat.getInsetsController(window, window.decorView)
            if (ui.fullscreen) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        LaunchedEffect(k.isDark) {
            val c = WindowCompat.getInsetsController(window, window.decorView)
            c.isAppearanceLightStatusBars = !k.isDark
            c.isAppearanceLightNavigationBars = !k.isDark
        }

        // Atrás: pantalla completa → buscar → historial de la página → vista de pestañas → salir.
        BackHandler(enabled = showTabs) { if (store.selectedTab != null) showTabs = false else moveTaskToBack(true) }
        BackHandler(enabled = !showTabs) {
            val t = store.selectedTab
            when {
                t != null && t.readerMode -> app.pool.setReader(t, false)
                t != null && t.canGoBack -> app.pool.sessionOf(t.id)?.goBack()
                else -> showTabsGrid()
            }
        }
        BackHandler(enabled = finding) { finding = false; tab?.let { app.pool.sessionOf(it.id)?.finder?.clear() } }
        BackHandler(enabled = ui.fullscreen) { tab?.let { app.pool.sessionOf(it.id)?.exitFullScreen() } }

        val actions = tab?.let { t ->
            ChromeActions(
                onAddress = { search = SearchTarget.Navigate(t, if (t.isStart || t.url.startsWith("about:")) "" else t.url) },
                onReload = { app.pool.sessionOf(t.id)?.reload() },
                onStop = { app.pool.sessionOf(t.id)?.stop() },
                onAa = { aaMenu = true },
                onBack = {
                    when {
                        t.readerMode -> app.pool.setReader(t, false)
                        t.canGoBack -> app.pool.sessionOf(t.id)?.goBack()
                        else -> showTabsGrid()
                    }
                },
                onForward = { app.pool.sessionOf(t.id)?.goForward() },
                onShare = { mainMenu = true },
                onLibrary = { library = true },
                onTabs = { showTabsGrid() },
                onTabsLong = { tabsQuick = true },
                onSwipeTab = ::swipeTab,
                onExpand = { barHidden = false; pillAfterSearch = false; ui.scrollDirection = -1 },
                onSpaces = { zenPanel = true },
                onHome = {
                    // Volver a la página de inicio de Kaze en esta pestaña.
                    store.onUrl(t, Urls.START)
                    pillAfterSearch = false
                },
                onNewTab = { newTab(private = t.isPrivate) },
            )
        }
        val collapsed = tab != null && !tab.isStart && (ui.scrollDirection > 0 || barHidden || pillAfterSearch)

        val imeVisible = WindowInsets.isImeVisible
        val showBar = !ui.fullscreen && tab != null && !showTabs && actions != null && (!imeVisible || finding)
        val density = LocalDensity.current
        // La página siempre deja libre el alto de la barra encogida (nunca cambia de tamaño: nada salta
        // ni se traba). Lo que la barra completa tapa de más se lo decimos a Gecko ("barra dinámica"):
        // así coloca encima los botones fijos de las páginas, como en Firefox para Android.
        val collapsedPx = with(density) { 32.dp.roundToPx() } + WindowInsets.navigationBars.getBottom(density)
        fun onBarHeight(h: Int) {
            geckoView.setVerticalClipping((h - collapsedPx).coerceAtLeast(0))
            if (h > barMaxPx) {
                barMaxPx = h
                geckoView.setDynamicToolbarMaxHeight((h - collapsedPx).coerceAtLeast(0))
            }
        }
        LaunchedEffect(showBar) { if (!showBar) geckoView.setVerticalClipping(0) }

        Box(Modifier.fillMaxSize().background(k.paper)) {
            Box(Modifier.fillMaxSize().then(if (ui.fullscreen) Modifier else Modifier.statusBarsPadding().imePadding())) {
                // La página termina donde empieza la barra (y los botones de Android): nada queda debajo.
                AndroidView(
                    factory = { geckoView },
                    modifier = Modifier.fillMaxSize()
                        .padding(bottom = with(density) { (if (showBar) collapsedPx else 0).toDp() })
                        .then(if (showBar || ui.fullscreen) Modifier else Modifier.navigationBarsPadding()),
                )

                if (tab != null && tab.isStart && !showTabs) {
                    StartPage(
                        private = tab.isPrivate,
                        onOpenUrl = { url -> app.pool.ensure(tab).loadUri(url); store.onUrl(tab, url) },
                        onOpenFavorite = ::open,
                        onFavoriteMenu = { tabMenu = it },
                        onPrivacyReport = { privacy = true },
                    )
                }

                if (showBar && tab != null && actions != null) {
                    if (finding) {
                        FindBar(
                            query = ui.findQuery.orEmpty(),
                            current = ui.findCurrent, total = ui.findTotal,
                            onQuery = { q -> find(tab, q, backwards = false, restart = true) },
                            onNext = { find(tab, ui.findQuery.orEmpty(), backwards = false) },
                            onPrev = { find(tab, ui.findQuery.orEmpty(), backwards = true) },
                            onClose = { finding = false; app.pool.sessionOf(tab.id)?.finder?.clear() },
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                                .onSizeChanged { onBarHeight(it.height) },
                        )
                    } else {
                        SafariBottomBar(
                            tab = tab,
                            space = store.spaceById(tab.spaceId),
                            tabCount = store.tabs.count { it.spaceId == tab.spaceId && it.kind != TabKind.ESSENTIAL },
                            collapsed = collapsed,
                            a = actions,
                            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { onBarHeight(it.height) },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn(tween(180)) + scaleIn(tween(240), initialScale = 1.04f),
                exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 1.04f),
            ) {
                TabsScreen(
                    onOpen = ::open,
                    onNewTab = { newTab(private = store.currentSpaceId == Space.PRIVATE_ID) },
                    onDone = { if (store.selectedTab != null) showTabs = false else newTab() },
                    onGroups = { groups = true },
                    onTabMenu = { tabMenu = it },
                    onClose = ::closeTab,
                )
            }

            search?.let { target ->
                SearchScreen(
                    initial = target.initial,
                    private = (target as? SearchTarget.NewTab)?.private ?: (target as SearchTarget.Navigate).tab.isPrivate,
                    onSubmit = { url ->
                        search = null
                        pillAfterSearch = true
                        when (target) {
                            is SearchTarget.NewTab -> {
                                store.newTab(url, spaceId = target.spaceId, private = target.private)
                                showTabs = false
                            }
                            is SearchTarget.Navigate -> {
                                app.pool.ensure(target.tab).loadUri(url)
                                store.onUrl(target.tab, url)
                                open(target.tab)
                            }
                        }
                    },
                    onSwitchTab = { t -> search = null; open(t) },
                    onDismiss = { search = null },
                )
            }

            // ── Hojas ──
            tabMenu?.let { t ->
                TabMenuSheet(t, onDismiss = { tabMenu = null }, onOpen = { open(t) }, onMove = { moveTab = t })
            }
            moveTab?.let { t -> MoveToSpaceSheet(t) { moveTab = null } }
            if (aaMenu && tab != null) {
                PageMenuSheet(
                    tab = tab,
                    onDismiss = { aaMenu = false },
                    onHideBar = { aaMenu = false; barHidden = true },
                    onPrivacyReport = { aaMenu = false; privacy = true },
                )
            }
            if (mainMenu && tab != null) {
                MainMenuSheet(
                    tab = tab,
                    onDismiss = { mainMenu = false },
                    a = MenuActions(
                        onNewTab = { newTab() },
                        onNewPrivate = { newTab(private = true) },
                        onMove = { moveTab = tab },
                        onLibrary = { library = true },
                        onRecent = { recentClosed = true },
                        onClearData = { clearData = true },
                        onDownloads = {
                            runCatching { startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                        },
                        onFind = { finding = true },
                        onShare = { shareMenu = true },
                        onSavePdf = {
                            val name = (tab.title.ifBlank { Urls.displayHost(tab.url) }).replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60) + ".pdf"
                            app.pool.sessionOf(tab.id)?.saveAsPdf()?.accept({ input ->
                                if (input != null) Thread { runCatching { app.downloads.write(input, name, "application/pdf") } }.start()
                            }, { ui.show("No se pudo guardar el PDF") })
                            ui.show("Guardando PDF en Descargas…")
                        },
                        onSettings = { settings = true },
                        onCustomizeHome = { settings = true },
                        onHelp = { store.newTab("https://github.com/Saerling/kaze/issues"); showTabs = false },
                        onDefaultBrowser = {
                            runCatching {
                                val rm = if (android.os.Build.VERSION.SDK_INT >= 29) getSystemService(android.app.role.RoleManager::class.java) else null
                                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                                    startActivity(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
                                } else {
                                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                                }
                            }
                        },
                    ),
                )
            }
            if (clearData) ClearDataSheet { clearData = false }
            if (recentClosed) {
                RecentlyClosedSheet(onDismiss = { recentClosed = false }) { url ->
                    store.newTab(url); showTabs = false
                }
            }
            if (shareMenu && tab != null) {
                ShareSheet(
                    tab = tab,
                    onDismiss = { shareMenu = false },
                    onSystemShare = { share(tab.url, tab.title) },
                    onFind = { shareMenu = false; finding = true },
                    onMove = { shareMenu = false; moveTab = tab },
                )
            }
            if (tabsQuick) {
                TabsQuickSheet(
                    tab = tab,
                    onDismiss = { tabsQuick = false },
                    onNewTab = { tabsQuick = false; newTab() },
                    onNewPrivate = { tabsQuick = false; newTab(private = true) },
                    onCloseTab = { tabsQuick = false; tab?.let(::closeTab) },
                    onCloseAll = {
                        tabsQuick = false
                        store.clearNormal(store.currentSpaceId).forEach { app.pool.close(it); app.thumbnails.delete(it.id) }
                        if (store.selectedTab == null) showTabs = true
                    },
                )
            }
            if (groups) {
                GroupsSheet(
                    onDismiss = { groups = false },
                    onSelect = { id ->
                        groups = false
                        store.selectSpace(id)
                    },
                    onNew = { groups = false; newSpace = true },
                    onEdit = { s -> groups = false; editSpace = s },
                    onProfile = { id ->
                        groups = false
                        store.switchProfile(id).forEach(app.pool::unload)
                        showTabs = true
                    },
                )
            }
            AnimatedVisibility(
                visible = zenPanel,
                enter = fadeIn(tween(160)) + slideInVertically(spring(0.85f, 420f)) { it / 5 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 6 },
            ) {
                ZenPanel(
                    essentials = store.essentials(),
                    spaces = store.profileSpaces,
                    currentSpaceId = store.currentSpaceId,
                    tabCount = { sp -> store.tabs.count { it.spaceId == sp.id && it.kind != TabKind.ESSENTIAL } },
                    onEssential = { t -> zenPanel = false; snapshot { open(t) } },
                    onSpace = { sp -> zenPanel = false; goToSpace(sp.id) },
                    onNewSpace = { zenPanel = false; newSpace = true },
                    onEditSpace = { sp -> zenPanel = false; editSpace = sp },
                    onNewTab = { zenPanel = false; newTab(private = store.currentSpaceId == Space.PRIVATE_ID) },
                    onClearSpace = {
                        zenPanel = false
                        store.clearNormal(store.currentSpaceId).forEach { app.pool.close(it); app.thumbnails.delete(it.id) }
                        if (store.selectedTab == null) newTab()
                        ui.show("Espacio limpio: se cerraron las pestañas no fijadas")
                    },
                    onCompact = { zenPanel = false; barHidden = true },
                    onDismiss = { zenPanel = false },
                )
            }
            BackHandler(enabled = zenPanel) { zenPanel = false }
            editSpace?.let { s -> SpaceEditorSheet(s) { editSpace = null } }
            if (newSpace) SpaceEditorSheet(null) { newSpace = false }
            if (library) {
                LibrarySheet(
                    onDismiss = { library = false },
                    onOpenUrl = { url ->
                        library = false
                        val t = store.selectedTab
                        if (t != null && t.isStart) { app.pool.ensure(t).loadUri(url); store.onUrl(t, url); open(t) }
                        else { store.newTab(url); showTabs = false }
                    },
                    onOpenTab = { t -> library = false; open(t) },
                )
            }
            if (privacy) PrivacyReportSheet(tab) { privacy = false }
            if (settings) {
                SettingsSheet(
                    onDismiss = { settings = false },
                    onRestart = ::restart,
                    onCheckUpdates = {
                        settings = false
                        app.updater.showSheet = true
                        scope.launch { withContext(Dispatchers.IO) { app.updater.check() } }
                    },
                )
            }
            if (app.updater.showSheet) UpdateSheet { app.updater.showSheet = false }

            ui.contextMenu?.let { req ->
                ContextMenuSheet(
                    link = req.element.linkUri,
                    image = req.element.srcUri.takeIf {
                        req.element.type == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
                    },
                    title = req.element.title ?: req.element.linkText,
                    onDismiss = { ui.contextMenu = null },
                    onOpenNew = { url, foreground ->
                        val private = url.startsWith("private:") || req.tab.isPrivate
                        val target = url.removePrefix("private:")
                        val t = store.newTab(target, spaceId = req.tab.spaceId, private = private, select = foreground)
                        if (foreground) open(t) else ui.show("Abierta en segundo plano")
                    },
                    onShare = { share(it) },
                    onDownload = { url ->
                        GeckoWebExecutor(app.runtime).fetch(WebRequest(url)).accept { r -> r?.let(app.downloads::save) }
                    },
                )
            }

            // ── Diálogos de la página ──
            ui.prompt?.let { p -> PromptHost(p, onDone = { ui.prompt = null }, onFile = ::openFilePicker) }
            ui.permission?.let { p -> PermissionHost(p) { ui.permission = null } }

            Toast(ui.toast) { ui.toast = null }
        }

        // Ajustes se abre desde la Biblioteca y desde la vista de pestañas.
        LaunchedEffect(ui.openSettings) {
            if (ui.openSettings) { settings = true; ui.openSettings = false }
        }
    }

    private fun find(tab: Tab, query: String, backwards: Boolean, restart: Boolean = false) {
        val ui = app.ui
        ui.findQuery = query
        val finder = app.pool.sessionOf(tab.id)?.finder ?: return
        if (query.isEmpty()) {
            finder.clear(); ui.findTotal = 0; ui.findCurrent = 0; return
        }
        if (restart) finder.clear()
        val flags = if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else 0
        finder.find(query, flags).accept { r ->
            ui.findTotal = r?.total ?: 0
            ui.findCurrent = r?.current ?: 0
        }
    }
}

@Composable
private fun Toast(message: String?, onGone: () -> Unit) {
    val k = LocalKaze.current
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            onGone()
        }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 84.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                message.orEmpty(),
                style = KazeType.small,
                color = k.paper,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(k.ink.copy(alpha = 0.9f)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
