package app.kaze.browser

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import app.kaze.browser.ui.theme.flowBackground
import app.kaze.browser.engine.UpdateInfo
import app.kaze.browser.ui.sheets.UpdateSheet
import app.kaze.browser.data.UiStyle
import app.kaze.browser.data.AccentColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import app.kaze.browser.data.TabKind
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.screens.ChromeActions
import app.kaze.browser.ui.screens.SafariBottomBar
import app.kaze.browser.ui.screens.SafariTopAddress
import app.kaze.browser.ui.screens.StartPage
import app.kaze.browser.ui.screens.ZenPanel
import app.kaze.browser.ui.screens.TabsScreen
import app.kaze.browser.ui.sheets.GroupsSheet
import app.kaze.browser.ui.sheets.GroupPicker
import app.kaze.browser.ui.theme.KazeTheme
import app.kaze.browser.ui.theme.LocalKaze
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import app.kaze.browser.data.SpacePalette
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import app.kaze.browser.ui.mochy.TopToastHost
import app.kaze.browser.ui.mochy.TopToast
import app.kaze.browser.ui.sheets.MenuActions
import app.kaze.browser.ui.sheets.MainMenuSheet
import app.kaze.browser.ui.sheets.TabMenuSheet
import app.kaze.browser.ui.sheets.PersonalizationSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capturas de la interfaz sin teléfono (Robolectric + Roborazzi). Salen en app/build/outputs/roborazzi/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: Kaze

    @Before fun seed() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        app = Kaze.get(ctx)
        val s = app.store
        if (s.tabs.isNotEmpty()) return
        val space = s.spaces.first()
        listOf(
            "https://discord.com/", "https://www.nytimes.com/", "https://chatgpt.com/",
            "https://www.reddit.com/", "https://github.com/", "https://www.youtube.com/",
        ).forEach { s.setKind(s.newTab(it, select = false), TabKind.ESSENTIAL) }
        listOf(
            "https://chatgpt.com/" to "ChatGPT", "https://www.youtube.com/" to "YouTube",
            "https://mobbin.com/" to "Mobbin — UI & UX design inspiration for mobile & web", "https://docs.google.com/" to "Google Docs",
        ).forEach { (u, t) -> s.newTab(u, select = false).also { tab -> tab.title = t; s.setKind(tab, TabKind.PINNED) } }
        s.newTab("https://es.wikipedia.org/wiki/Aurora_polar", select = false).title = "Aurora polar - Wikipedia"
        s.newTab("https://www.bbc.com/mundo", select = false).title = "BBC News Mundo"
        s.tabs.forEach { t -> (Urls.host(t.pinnedUrl ?: t.url))?.removePrefix("www.")?.let { runCatching { app.favicons.load(it) } } }
    }

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        KazeTheme(dark) { CompositionLocalProvider(LocalApp provides app, content = content) }
    }

    /** Miniatura falsa con aspecto de página (para ver las tarjetas como en un teléfono real). */
    private fun fakeThumb(title: String, color: Int): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(720, 1100, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(android.graphics.Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawRect(0f, 0f, 720f, 260f, p)
        p.color = android.graphics.Color.rgb(30, 30, 32); p.textSize = 64f; p.isFakeBoldText = true
        c.drawText(title, 40f, 360f, p)
        p.color = android.graphics.Color.rgb(205, 205, 210)
        for (i in 0 until 9) c.drawRect(40f, 420f + i * 64f, if (i % 3 == 2) 460f else 680f, 450f + i * 64f, p)
        return b
    }

    private fun thumbs() {
        val colors = listOf(0xFF5B8DEF, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F, 0xFF3D405B, 0xFF9C89B8).map { it.toInt() }
        app.store.tabs.forEachIndexed { i, t -> app.thumbnails.save(t.id, fakeThumb(t.displayTitle.take(16), colors[i % colors.size])) }
        Thread.sleep(800)
    }

    @Composable
    private fun Tabs() = TabsScreen({}, {}, {}, {}, {}, {})

    @Test fun pestanas_claro() {
        thumbs()
        compose.setContent { Themed(false) { Tabs() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-pestanas-claro.png")
    }

    @Test fun pestanas_oscuro() {
        thumbs()
        compose.setContent { Themed(true) { Tabs() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-pestanas-oscuro.png")
    }

    @Test fun pestanas_carpeta() {
        thumbs()
        val n = app.store.normal(app.store.currentSpaceId)
        app.store.newTab("https://github.com/", select = false).title = "GitHub"
        app.store.newTab("https://news.ycombinator.com/", select = false).title = "Hacker News"
        val list = app.store.normal(app.store.currentSpaceId)
        app.store.setFolder(list.take(3), "Lecturas")
        compose.setContent { KazeTheme(true, UiStyle.MATERIAL, AccentColor.AUTO) { CompositionLocalProvider(LocalApp provides app) { Tabs() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-carpetas.png")
    }

    @Test fun pestanas_lista() {
        app.settings.tabsView = "list"
        compose.setContent { Themed(true) { Tabs() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-pestanas-lista.png")
        app.settings.tabsView = "grid"
    }

    @Test fun inicio() {
        compose.setContent { Themed(false) { StartPage(false, {}, {}, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-inicio.png")
    }

    /** Editar Favoritos: quitar, arrastrar para ordenar y añadir desde las pestañas o escribiendo una dirección. */
    @Test fun inicio_editar_favoritos() {
        compose.setContent { Themed(false) { StartPage(false, {}, {}, {}, {}, startEditing = true) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dirección del Favorito").assertExists()
        captureScreenRoboImage("../../docs/capturas/android-favoritos-editar.png")
    }

    /** Menú de un grupo en la vista de pestañas (mantener la carpeta): Fijar grupo y Desagrupar. */
    @Test fun grupo_menu() {
        thumbs()
        val s = app.store
        val extra = listOf("https://github.com/" to "GitHub", "https://news.ycombinator.com/" to "Hacker News")
            .map { (u, t) -> s.newTab(u, select = false).also { it.title = t } }
        s.setFolder(extra, "Lecturas")
        try {
            compose.setContent { Themed(false) { Tabs() } }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Grupo Lecturas, 2 pestañas (mantén para ver opciones)").performTouchInput { longClick() }
            compose.waitForIdle()
            captureScreenRoboImage("../../docs/capturas/android-grupo-menu.png")
        } finally {
            extra.forEach { s.close(it) }
            s.recentlyClosed.clear()
        }
    }

    /** Muchos espacios: la fila de emojis se desliza a los lados, con los bordes desvanecidos. */
    @Test fun pestanas_muchos_espacios() {
        val s = app.store
        val added = listOf("📚" to "Estudio", "🎮" to "Juegos", "🎵" to "Música", "🛒" to "Compras", "✈️" to "Viajes", "🍳" to "Cocina", "💡" to "Ideas", "🏋️" to "Gimnasio", "📷" to "Fotos")
            .mapIndexed { i, (e, n) -> s.addSpace(n, e, SpacePalette.presets[i % SpacePalette.presets.size]) }
        try {
            compose.setContent { Themed(true) { Tabs() } }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../../docs/capturas/android-pestanas-espacios.png")
        } finally {
            added.forEach { s.deleteSpace(it.id) }
        }
    }

    /** Página de inicio con "Reanudar navegación": cerradas hace poco y pestañas que llegaron de Zen. */
    @Test fun inicio_reanudar() {
        val s = app.store
        s.recentlyClosed.clear()
        s.recentlyClosed.addAll(
            listOf(
                "https://www.macrumors.com/2026/06/09/safari-ios-27/" to "Safari en iOS 27: agrupación de pestañas por tema",
                "https://www.amazon.com/dp/B0CHX1W1XY" to "Audífonos inalámbricos con cancelación de ruido",
                "https://es.wikipedia.org/wiki/Aurora_polar" to "Aurora polar - Wikipedia",
            ),
        )
        val pinned = s.pinned(s.currentSpaceId)
        pinned.take(2).forEach { it.zenId = "zen-" + it.id }
        try {
            compose.setContent { Themed(false) { StartPage(false, {}, {}, {}, {}, forceSynced = true) } }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../../docs/capturas/android-inicio-reanudar.png")
        } finally {
            pinned.forEach { it.zenId = null }
            s.recentlyClosed.clear()
        }
    }

    /** Temas en la vista de pestañas: píldoras arriba y la cuadrícula filtrada por "Noticias". */
    @Test fun pestanas_temas() {
        thumbs()
        val s = app.store
        val extra = listOf(
            "https://www.nytimes.com/2026/11/01/technology/ai.html" to "The New York Times - Technology",
            "https://www.amazon.com/dp/B0CHX1W1XY" to "Audífonos inalámbricos - Amazon",
            "https://listado.mercadolibre.com.co/teclado" to "Teclado mecánico | MercadoLibre",
            "https://www.youtube.com/watch?v=abc" to "Liquid Glass en iOS 27 - YouTube",
        ).map { (u, t) -> s.newTab(u, select = false).also { it.title = t } }
        try {
            compose.setContent { Themed(false) { Tabs() } }
            compose.waitForIdle()
            // Con muchas pestañas abiertas la píldora puede quedar fuera de la fila: primero se desliza hasta ella.
            compose.onNode(androidx.compose.ui.test.hasTestTag("temas")).performScrollToNode(androidx.compose.ui.test.hasContentDescription("Tema Noticias"))
            compose.onNodeWithContentDescription("Tema Noticias").performClick()
            compose.mainClock.advanceTimeBy(1200)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("../../docs/capturas/android-pestanas-temas.png")
        } finally {
            extra.forEach { s.close(it) }
            s.recentlyClosed.clear()
        }
    }

    private fun actions() = ChromeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test fun panelZen() {
        app.store.pinned(app.store.currentSpaceId).take(3).forEach { app.store.setKind(it, TabKind.ESSENTIAL) }
        compose.setContent { Themed(false) { Box(Modifier.fillMaxSize().background(Color.White)) { FakePage()
            ZenPanel(
                essentials = app.store.essentials(),
                spaces = app.store.profileSpaces,
                currentSpaceId = app.store.currentSpaceId,
                tabCount = { sp -> app.store.normal(sp.id).size },
                onEssential = {}, onSpace = {}, onNewSpace = {}, onEditSpace = {}, onDismiss = {},
            )
        } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-panel-zen.png")
    }

    @Test fun navegador() {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("wikipedia") }
        tab.canGoBack = true
        compose.setContent {
            Themed(false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    FakePage()
                    SafariBottomBar(tab, app.store.spaceById(tab.spaceId), 7, false, actions(), Modifier.align(Alignment.BottomCenter), tabs = app.store.normal(app.store.currentSpaceId))
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-navegador.png")
    }

    @Test fun fijadas_grupo() {
        val pinned = app.store.pinned(app.store.currentSpaceId)
        app.store.setFolder(pinned.take(2), "Trabajo")
        compose.setContent { Themed(true) { CompositionLocalProvider(LocalApp provides app) { Box(Modifier.fillMaxSize()) { Tabs() } } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-fijadas-grupo.png")
    }

    /** "Añadir a un grupo…" del menú de una fijada: grupos existentes y "Nuevo grupo". */
    @Test fun fijadas_grupo_menu() {
        val pinned = app.store.pinned(app.store.currentSpaceId)
        app.store.setFolder(pinned.take(2), "Trabajo")
        val tab = pinned.last()
        compose.setContent {
            Themed(true) {
                Column(Modifier.fillMaxSize().background(LocalKaze.current.paper).padding(top = 24.dp)) {
                    GroupPicker(tab, {}, {}, {})
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-fijadas-grupo-menu.png")
    }

    @Test fun material_you() {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("wikipedia") }
        compose.setContent {
            KazeTheme(true, UiStyle.MATERIAL, AccentColor.PURPLE) {
                CompositionLocalProvider(LocalApp provides app) {
                    Box(Modifier.fillMaxSize()) {
                        Tabs()
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-material-you.png")
    }

    @Config(qualifiers = "w820dp-h891dp-xxhdpi")
    @Test fun living_flow() = flowShot(UiStyle.FLOW, "android-living-flow.png")

    @Config(qualifiers = "w820dp-h891dp-xxhdpi")
    @Test fun estilo_zen() = flowShot(UiStyle.ZEN, "android-estilo-zen.png")

    private fun flowShot(style: UiStyle, file: String) {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("wikipedia") }
        tab.canGoBack = true
        compose.setContent {
            KazeTheme(false, style, AccentColor.AUTO) {
                CompositionLocalProvider(LocalApp provides app) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight()) { Tabs() }
                        Box(Modifier.weight(1f).fillMaxHeight().background(app.ui.let { androidx.compose.ui.graphics.Color(0xFFF3F1F8) }).flowBackground(LocalKaze.current)) {
                            SafariBottomBar(tab, app.store.spaceById(tab.spaceId), 7, false, actions(), Modifier.align(Alignment.BottomCenter), compact = true)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/" + file)
    }

    @Test fun actualizacion() {
        app.updater.available = UpdateInfo(
            versionCode = 999, versionName = "Kaze Android 0.3.114", apkUrl = "", sizeBytes = 92_000_000,
            notes = "Cambios:\nAndroid: píldora para deshacer y aviso de versión nuevo\n\n- Al cerrar una pestaña sale una píldora\n  para deshacer.\n- Botón de historial en la vista de pestañas.\n- La barra ya no tapa la página.",
        )
        compose.setContent { Themed(true) { Box(Modifier.fillMaxSize()) { UpdateSheet {} } } }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("../../docs/capturas/android-actualizacion.png")
    }

    @Test fun barra_compacta() {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("wikipedia") }
        tab.canGoBack = true
        compose.setContent {
            Themed(false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    FakePage()
                    SafariBottomBar(tab, app.store.spaceById(tab.spaceId), 7, false, actions(), Modifier.align(Alignment.BottomCenter), tabs = app.store.normal(app.store.currentSpaceId), compact = true)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-barra-compacta.png")
    }

    @Test fun barra_arriba() {
        val space = app.store.currentSpaceId
        val tabs = app.store.normal(space)
        val tab = tabs.first { it.url.contains("wikipedia") }
        compose.setContent {
            Themed(false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    FakePage()
                    SafariTopAddress(tab, app.store.spaceById(tab.spaceId), false, actions(), tabs, {}, Modifier.align(Alignment.TopCenter))
                    SafariBottomBar(tab, app.store.spaceById(tab.spaceId), tabs.size, false, actions(), Modifier.align(Alignment.BottomCenter), tabs = tabs, split = true)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-barra-arriba.png")
    }

    @Config(qualifiers = "w1100dp-h800dp-xhdpi")
    @Test fun barra_tableta() {
        val space = app.store.currentSpaceId
        val tabs = app.store.normal(space)
        val tab = tabs.first { it.url.contains("wikipedia") }
        tab.canGoBack = true
        app.store.newTab("https://example.com", select = false, after = tab)
        app.store.setFolder(app.store.normal(space).take(2), "Lectura")
        val all = app.store.normal(space)
        compose.setContent {
            Themed(false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    FakePage()
                    SafariBottomBar(tab, app.store.spaceById(tab.spaceId), all.size, false, actions(), Modifier.align(Alignment.TopCenter), tabs = all, top = true, tablet = true)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-tableta-barra.png")
    }

    @Test fun navegador_barra_reducida() {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("wikipedia") }
        compose.setContent {
            Themed(true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    FakePage()
                    SafariBottomBar(tab, app.store.spaceById(tab.spaceId), 7, true, actions(), Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-navegador-reducida.png")
    }

    @Test fun grupos() {
        compose.setContent { Themed(false) { Box(Modifier.fillMaxSize()) { Tabs(); GroupsSheet({}, {}, {}, {}, {}) } } }
        compose.waitForIdle()
        captureScreenRoboImage("../../docs/capturas/android-grupos.png")
    }

    @Composable
    private fun FakePage() {
        Column(Modifier.padding(20.dp)) {
            Text("Aurora polar", fontSize = 30.sp, fontWeight = FontWeight.Normal, color = Color(0xFF202122))
            Text(
                "Una aurora polar es un fenómeno en forma de brillo o luminiscencia que aparece en el cielo nocturno, " +
                    "generalmente en zonas polares, aunque puede aparecer en otras zonas del mundo durante breves períodos. " +
                    "En el hemisferio norte se conoce como aurora boreal, y en el hemisferio sur como aurora austral.",
                fontSize = 17.sp, color = Color(0xFF202122), modifier = Modifier.padding(top = 16.dp), lineHeight = 27.sp,
            )
        }
    }

    /** Menú ⋯ con las tarjetas de acción de Mochy, en la hoja con el movimiento de Mochy. */
    @Test fun menu_principal() {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("wikipedia") }
        val a = MenuActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent { Themed(false) { Box(Modifier.fillMaxSize()) { MainMenuSheet(tab, {}, a) } } }
        compose.waitForIdle()
        captureScreenRoboImage("../../docs/capturas/android-menu.png")
    }

    /** Menú de una pestaña: "Abrir" y "Cerrar pestaña" en tarjetas grandes y el resto en la lista. */
    @Test fun menu_pestana() {
        val tab = app.store.normal(app.store.currentSpaceId).first { it.url.contains("bbc") }
        compose.setContent { Themed(true) { Box(Modifier.fillMaxSize()) { TabMenuSheet(tab, {}, {}, {}) } } }
        compose.waitForIdle()
        captureScreenRoboImage("../../docs/capturas/android-menu-pestana.png")
    }

    /** Aviso con "Deshacer" con la píldora de TopToast de Mochy. */
    @Test fun aviso() {
        val toast = TopToast(1, "Pestaña cerrada (también se quitará de Zen)", "Deshacer", {}, 60_000)
        compose.setContent {
            Themed(false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    FakePage()
                    Box(Modifier.fillMaxSize().padding(bottom = 128.dp)) {
                        TopToastHost(toast, {}, LocalKaze.current.accent)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("../../docs/capturas/android-aviso.png")
    }

    /** Ajustes → Diseño → Personalización (tarjetas de Mochy). */
    @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    @Test fun personalizacion() {
        compose.setContent {
            Themed(true) {
                Column(Modifier.fillMaxSize().background(LocalKaze.current.paper).padding(top = 24.dp)) {
                    PersonalizationSection(app.settings)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-personalizacion.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())
}
