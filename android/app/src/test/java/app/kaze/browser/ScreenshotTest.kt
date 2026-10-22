package app.kaze.browser

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import app.kaze.browser.data.TabKind
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.screens.ChromeActions
import app.kaze.browser.ui.screens.SafariBottomBar
import app.kaze.browser.ui.screens.StartPage
import app.kaze.browser.ui.screens.ZenPanel
import app.kaze.browser.ui.screens.TabsScreen
import app.kaze.browser.ui.sheets.GroupsSheet
import app.kaze.browser.ui.theme.KazeTheme
import app.kaze.browser.ui.theme.LocalKaze
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
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

    private fun actions() = ChromeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

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
                    SafariBottomBar(tab, app.store.spaceById(tab.spaceId), 7, false, actions(), Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../../docs/capturas/android-navegador.png")
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

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())
}
