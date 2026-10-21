package app.kaze.browser

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.kaze.browser.data.Db
import app.kaze.browser.data.Settings
import app.kaze.browser.data.Store
import app.kaze.browser.data.TabKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quien tiene Kaze 0.3.105 (base v1) debe actualizar sin perder pestañas, espacios ni historial. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun v1aV2() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("kaze.db")
        val path = ctx.getDatabasePath("kaze.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { v1 ->
            v1.execSQL("CREATE TABLE spaces(id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL, colors TEXT NOT NULL, position INTEGER NOT NULL, isolated INTEGER NOT NULL DEFAULT 0)")
            v1.execSQL("CREATE TABLE tabs(id TEXT PRIMARY KEY, space_id TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, kind INTEGER NOT NULL, position INTEGER NOT NULL, last_used INTEGER NOT NULL, state TEXT, pinned_url TEXT)")
            v1.execSQL("CREATE TABLE history(url TEXT PRIMARY KEY, title TEXT NOT NULL, last_visit INTEGER NOT NULL, visits INTEGER NOT NULL)")
            v1.execSQL("CREATE INDEX history_last ON history(last_visit DESC)")
            v1.execSQL("INSERT INTO spaces VALUES('s1','Mío','🌿','1,2',0,0)")
            v1.execSQL("INSERT INTO tabs VALUES('t1','s1','https://a.com/','A',0,0,1,NULL,'https://a.com/')")
            v1.execSQL("INSERT INTO tabs VALUES('t2','s1','https://b.com/','B',2,0,2,NULL,NULL)")
            v1.execSQL("INSERT INTO history VALUES('https://a.com/','A',${System.currentTimeMillis()},4)")
            v1.version = 1
        }
        val db = Db(ctx)
        val store = Store(db, Settings(ctx)).apply { load() }
        assertEquals(listOf("Mío"), store.profileSpaces.map { it.name })
        assertEquals(listOf("A"), store.essentials().map { it.title })
        assertEquals(TabKind.NORMAL, store.tabById("t2")!!.kind)
        assertEquals(4, db.suggest("a.com").single().visits)
        assertEquals(1, db.frequent().size)
    }
}
