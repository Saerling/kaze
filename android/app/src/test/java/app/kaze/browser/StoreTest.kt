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

    @Test fun gruposDeFijadasYEnlaceConZen() {
        val sid = store.currentSpaceId
        val a = store.newTab("https://a.com").also { store.setKind(it, TabKind.PINNED) }
        val b = store.newTab("https://b.com").also { store.setKind(it, TabKind.PINNED) }
        val n = store.newTab("https://n.com")
        var cambios = 0
        store.onSyncChange = { cambios++ }
        store.setFolder(listOf(a, b), store.newFolderName(sid, TabKind.PINNED))
        assertEquals(listOf("Grupo"), store.folders(sid, TabKind.PINNED))
        assertTrue(store.folders(sid).isEmpty()) // los grupos de normales son aparte
        store.setFolder(listOf(n), "Grupo")
        assertEquals(listOf(a, b), store.folderTabs(sid, "Grupo", TabKind.PINNED))
        store.renameFolder(sid, "Grupo", "Trabajo", TabKind.PINNED)
        assertEquals(listOf("Trabajo"), store.folders(sid, TabKind.PINNED))
        assertEquals("Grupo", n.folder)
        assertTrue(cambios >= 2) // mover fijadas avisa a la sincronización
        val antes = cambios
        store.setFolder(listOf(n), "Otro") // las normales no viajan a Zen
        assertEquals(antes, cambios)
        // Al desfijar sale de su grupo; el enlace con Zen se guarda en la base de datos.
        store.link(a, "zen-a")
        store.setKind(b, TabKind.NORMAL)
        assertEquals("", b.folder)
        store.flush(); waitDb()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val again = Store(db, Settings(ctx)).apply { load() }
        assertEquals("zen-a", again.tabById(a.id)!!.zenId)
        assertEquals("Trabajo", again.tabById(a.id)!!.folder)
    }

    @Test fun alPasarAV4SeQuitanSoloLasRepetidasExactas() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db.close()
        ctx.deleteDatabase("kaze.db")
        val path = ctx.getDatabasePath("kaze.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { v3 ->
            v3.execSQL("CREATE TABLE spaces(id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL, colors TEXT NOT NULL, position INTEGER NOT NULL, isolated INTEGER NOT NULL DEFAULT 0, profile_id TEXT NOT NULL DEFAULT 'personal')")
            v3.execSQL("CREATE TABLE tabs(id TEXT PRIMARY KEY, space_id TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, kind INTEGER NOT NULL, position INTEGER NOT NULL, last_used INTEGER NOT NULL, state TEXT, pinned_url TEXT, folder TEXT NOT NULL DEFAULT '')")
            v3.execSQL("CREATE TABLE history(url TEXT NOT NULL, profile_id TEXT NOT NULL DEFAULT 'personal', title TEXT NOT NULL, last_visit INTEGER NOT NULL, visits INTEGER NOT NULL, PRIMARY KEY(url, profile_id))")
            v3.execSQL("CREATE TABLE profiles(id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL, position INTEGER NOT NULL)")
            v3.execSQL("CREATE TABLE bookmarks(url TEXT NOT NULL, profile_id TEXT NOT NULL, title TEXT NOT NULL, added INTEGER NOT NULL, PRIMARY KEY(url, profile_id))")
            v3.execSQL("CREATE TABLE reading_list(url TEXT NOT NULL, profile_id TEXT NOT NULL, title TEXT NOT NULL, added INTEGER NOT NULL, PRIMARY KEY(url, profile_id))")
            v3.execSQL("INSERT INTO spaces VALUES('s1','A','🌿','1',0,0,'personal')")
            v3.execSQL("INSERT INTO spaces VALUES('s2','B','🌿','1',1,0,'otro')")
            // Fijadas: dos iguales (sobra una) y dos vídeos distintos (se quedan los dos).
            v3.execSQL("INSERT INTO tabs VALUES('p1','s1','https://yt.com/watch?v=A','',1,0,1,NULL,'https://yt.com/watch?v=A','')")
            v3.execSQL("INSERT INTO tabs VALUES('p2','s1','https://yt.com/watch?v=A','',1,1,1,NULL,'https://yt.com/watch?v=A','')")
            v3.execSQL("INSERT INTO tabs VALUES('p3','s1','https://yt.com/watch?v=B','',1,2,1,NULL,'https://yt.com/watch?v=B','')")
            // Essentials: la misma en dos perfiles distintos se queda en los dos.
            v3.execSQL("INSERT INTO tabs VALUES('e1','s1','https://a.com/','',0,0,1,NULL,'https://a.com/','')")
            v3.execSQL("INSERT INTO tabs VALUES('e2','s2','https://a.com/','',0,0,1,NULL,'https://a.com/','')")
            v3.execSQL("INSERT INTO tabs VALUES('e3','s1','https://a.com/','',0,1,1,NULL,'https://a.com/','')")
            v3.version = 3
        }
        db = Db(ctx)
        assertEquals(listOf("e1", "e2", "p1", "p3"), db.loadTabs().map { it.id }.sorted())
    }

    @Test fun borrarEspacioEnlazadoSoloSueltaLosEnlaces() {
        val sid = store.currentSpaceId
        val space = store.spaceById(sid)!!
        space.zenId = "zs"
        val t = store.newTab("https://a.com").also { store.setKind(it, TabKind.PINNED); store.link(it, "zt") }
        var soltados: Pair<List<String>, List<String>>? = null
        var subidas = 0
        store.onUnlinked = { s, tabs -> soltados = s to tabs }
        store.onSyncChange = { subidas++ }
        store.deleteSpace(sid)
        assertEquals(listOf("zs") to listOf("zt"), soltados)
        assertEquals(null, t.zenId)
        assertEquals(0, subidas) // nada que subir: en Zen no se borra nada
    }

    @Test fun laBaseEsDeCadaCuentaYSeEsperaALaBaseDeDatos() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val st = app.kaze.browser.zsync.TabSyncPlanner.State.pinned("s", "G")
        app.kaze.browser.zsync.ZenSync.saveBase(ctx, "uid-1", mapOf("z1" to st))
        assertEquals(mapOf("z1" to st), app.kaze.browser.zsync.ZenSync.loadBase(ctx, "uid-1"))
        assertTrue(app.kaze.browser.zsync.ZenSync.loadBase(ctx, "uid-2").isEmpty())
        // Cerrar sesión cambia la generación: una sincronización en curso ya no aplica ni guarda nada.
        val gen = app.kaze.browser.zsync.AccountStore.currentGeneration
        runCatching { app.kaze.browser.zsync.AccountStore.clear(ctx) }
        assertTrue(!app.kaze.browser.zsync.AccountStore.isCurrent(gen))
        // El enlace ya está en disco cuando awaitWrites vuelve (la base se guarda después).
        val t = store.newTab("https://a.com").also { store.setKind(it, TabKind.PINNED) }
        store.link(t, "z9")
        store.awaitWrites()
        assertEquals("z9", db.loadTabs().first { it.id == t.id }.zenId)
    }

    @Test fun limpiezaRespetaGruposYPrefiereLaEnlazada() {
        val w = db.writableDatabase
        val sid = store.currentSpaceId
        fun put(id: String, folder: String, zen: String?) = w.execSQL(
            "INSERT INTO tabs(id, space_id, url, title, kind, position, last_used, state, pinned_url, folder, zen_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, sid, "https://a.com/", "", 1, 0, 1, null, "https://a.com/", folder, zen),
        )
        put("g1", "Trabajo", null)
        put("g2", "Ocio", null) // misma página en otro grupo: se queda
        put("r1", "", null)
        put("r2", "", "zen-r2") // repetida en la raíz: se queda la enlazada con Zen
        db.dedupeImported(w)
        assertEquals(listOf("g1", "g2", "r2"), db.loadTabs().map { it.id }.sorted())
    }

    @Test fun deshacerDesfijadoDejaTodoComoEstaba() {
        val sid = store.currentSpaceId
        val a = store.newTab("https://a.com").also { store.setKind(it, TabKind.PINNED) }
        val b = store.newTab("https://b.com").also { store.setKind(it, TabKind.PINNED) }
        store.setFolder(listOf(a), "G")
        a.pinnedUrl = "https://a.com/casa"
        val pos = a.position
        store.setKind(a, TabKind.NORMAL)
        store.repin(a, "G", "https://a.com/casa", pos)
        assertEquals(listOf(a, b), store.pinned(sid))
        assertEquals("G", a.folder)
        assertEquals("https://a.com/casa", a.pinnedUrl)
        store.close(b)
        store.repin(b, "", null, 0) // ya cerrada: no hace nada
        assertTrue(store.tabById(b.id) == null)
    }

    @Test fun laBaseAntiguaPasaALaCuenta() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val p = ctx.getSharedPreferences("zsync", android.content.Context.MODE_PRIVATE)
        p.edit().putString("base", "{\"z1\":[false,\"s\",\"\"]}").remove("base:uid-v").commit()
        val base = app.kaze.browser.zsync.ZenSync.loadBase(ctx, "uid-v")
        assertEquals(setOf("z1"), base.keys)
        assertTrue(!p.contains("base") && p.contains("base:uid-v"))
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
