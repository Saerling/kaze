package app.kaze.browser

import androidx.test.core.app.ApplicationProvider
import app.kaze.browser.data.Db
import app.kaze.browser.data.Settings
import app.kaze.browser.data.Space
import app.kaze.browser.data.Store
import app.kaze.browser.data.TabKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreTest {
    private lateinit var db: Db
    private lateinit var store: Store

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("kaze.db")
        db = Db(ctx)
        store = Store(db, Settings(ctx)).apply { load() }
    }

    private fun waitDb() {
        db.io.submit {}.get(5, TimeUnit.SECONDS)
    }

    @Test fun primerArranqueCreaDosEspacios() {
        assertEquals(2, store.spaces.size)
        assertEquals(store.spaces.first().id, store.currentSpaceId)
    }

    @Test fun pestanasNuevasVanArriba() {
        val a = store.newTab("https://a.com")
        val b = store.newTab("https://b.com")
        assertEquals(listOf(b, a), store.normal(store.currentSpaceId))
        assertEquals(b.id, store.selectedTabId)
    }

    @Test fun fijarYEssentials() {
        val t = store.newTab("https://a.com")
        store.setKind(t, TabKind.PINNED)
        assertEquals(listOf(t), store.pinned(t.spaceId))
        assertEquals("https://a.com", t.pinnedUrl)
        store.setKind(t, TabKind.ESSENTIAL)
        assertEquals(listOf(t), store.essentials())
        assertTrue(store.pinned(t.spaceId).isEmpty())
    }

    @Test fun privadasNoSeGuardan() {
        store.newTab("https://secreto.com", private = true)
        assertTrue(store.visibleSpaces.any { it.id == Space.PRIVATE_ID })
        store.flush(); waitDb()
        assertTrue(db.loadTabs().none { it.url.contains("secreto") })
    }

    @Test fun persisteYRestaura() {
        val t = store.newTab("https://a.com")
        store.setKind(t, TabKind.PINNED)
        store.onSessionState(t, "{\"estado\":1}")
        store.flush(); waitDb()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val again = Store(db, Settings(ctx)).apply { load() }
        val r = again.tabById(t.id)!!
        assertEquals(TabKind.PINNED, r.kind)
        assertEquals("{\"estado\":1}", r.sessionState)
    }

    @Test fun borrarEspacioCierraSusPestanas() {
        val other = store.spaces[1]
        val t = store.newTab("https://a.com", spaceId = other.id)
        val closed = store.deleteSpace(other.id)
        assertEquals(listOf(t), closed)
        assertEquals(1, store.spaces.size)
    }

    @Test fun perfilesSeparanEspaciosEHistorial() {
        db.recordVisit("https://personal.com/", "P")
        val trabajo = store.addProfile("Trabajo", "💼")
        assertEquals(1, store.spaces.count { it.profileId == trabajo.id })
        val salientes = store.switchProfile(trabajo.id)
        assertTrue(salientes.isEmpty() || salientes.all { !it.isPrivate })
        assertEquals(listOf("General"), store.profileSpaces.map { it.name })
        waitDb()
        assertTrue(db.suggest("personal").isEmpty())
        store.switchProfile(app.kaze.browser.data.Profile.DEFAULT_ID)
        assertEquals(1, db.suggest("personal").size)
        val cerradas = store.deleteProfile(trabajo.id)
        assertTrue(cerradas.isEmpty())
        assertEquals(1, store.profiles.size)
    }

    @Test fun listaDeLectura() {
        db.addToReadingList("https://a.com/nota", "Nota")
        waitDb()
        assertEquals(listOf("Nota"), db.readingList().map { it.title })
        assertTrue(db.inReadingList("https://a.com/nota"))
        db.removeFromReadingList("https://a.com/nota")
        waitDb()
        assertTrue(db.readingList().isEmpty())
    }

    @Test fun historial() {
        db.recordVisit("https://kaze.app/", "Kaze")
        db.recordVisit("https://kaze.app/", "")
        waitDb()
        val h = db.suggest("kaze")
        assertEquals(1, h.size)
        assertEquals(2, h[0].visits)
        assertEquals("Kaze", h[0].title)
        assertTrue(db.visited(arrayOf("https://kaze.app/", "https://otro.com"))[0])
    }
}
