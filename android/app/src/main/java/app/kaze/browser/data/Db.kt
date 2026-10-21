/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.Executors

/**
 * Base de datos local con SQLite del sistema (sin Room ni librerías extra:
 * menos código cargado en memoria y arranque más rápido).
 *
 * Todas las escrituras pasan por un único hilo en segundo plano ([io]),
 * así nunca bloquean la interfaz y no hay carreras entre escrituras.
 *
 * Historial, marcadores y lista de lectura son **por perfil**: las consultas usan
 * [profile], que Store cambia al cambiar de perfil.
 */
class Db(context: Context) : SQLiteOpenHelper(context, "kaze.db", null, VERSION) {

    companion object {
        private const val VERSION = 2
        private const val HISTORY_LIMIT = 5000
    }

    val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kaze-db").apply { priority = Thread.MIN_PRIORITY } }

    /** Perfil activo para historial, marcadores y lista de lectura. */
    @Volatile
    var profile: String = Profile.DEFAULT_ID

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE spaces(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL,
                colors TEXT NOT NULL, position INTEGER NOT NULL, isolated INTEGER NOT NULL DEFAULT 0,
                profile_id TEXT NOT NULL DEFAULT 'personal')""",
        )
        db.execSQL(
            """CREATE TABLE tabs(
                id TEXT PRIMARY KEY, space_id TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL,
                kind INTEGER NOT NULL, position INTEGER NOT NULL, last_used INTEGER NOT NULL,
                state TEXT, pinned_url TEXT)""",
        )
        createHistory(db)
        createV2(db)
    }

    private fun createHistory(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE history(
                url TEXT NOT NULL, profile_id TEXT NOT NULL DEFAULT 'personal', title TEXT NOT NULL,
                last_visit INTEGER NOT NULL, visits INTEGER NOT NULL, PRIMARY KEY(url, profile_id))""",
        )
        db.execSQL("CREATE INDEX history_last ON history(profile_id, last_visit DESC)")
    }

    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles(id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL, position INTEGER NOT NULL)")
        for (table in listOf("bookmarks", "reading_list")) {
            db.execSQL(
                """CREATE TABLE $table(url TEXT NOT NULL, profile_id TEXT NOT NULL, title TEXT NOT NULL,
                    added INTEGER NOT NULL, PRIMARY KEY(url, profile_id))""",
            )
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 (Kaze 0.3.105) → v2: perfiles, marcadores, lista de lectura e historial por perfil.
            db.execSQL("ALTER TABLE spaces ADD COLUMN profile_id TEXT NOT NULL DEFAULT 'personal'")
            db.execSQL("DROP INDEX IF EXISTS history_last")
            db.execSQL("ALTER TABLE history RENAME TO history_v1")
            createHistory(db)
            db.execSQL(
                "INSERT INTO history(url, profile_id, title, last_visit, visits) " +
                    "SELECT url, 'personal', title, last_visit, visits FROM history_v1",
            )
            db.execSQL("DROP TABLE history_v1")
            createV2(db)
        }
    }

    // ── Perfiles ──────────────────────────────────────────────────────

    fun loadProfiles(): List<Profile> = readableDatabase.rawQuery(
        "SELECT id, name, emoji, position FROM profiles ORDER BY position", null,
    ).use { c ->
        buildList { while (c.moveToNext()) add(Profile(c.getString(0), c.getString(1), c.getString(2), c.getInt(3))) }
    }

    fun saveProfile(p: Profile) {
        val v = ContentValues().apply { put("id", p.id); put("name", p.name); put("emoji", p.emoji); put("position", p.position) }
        io.execute { writableDatabase.insertWithOnConflict("profiles", null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    /** Borra el perfil con sus espacios, pestañas, historial, marcadores y lista de lectura. */
    fun deleteProfile(id: String, spaceIds: List<String>) = io.execute {
        val w = writableDatabase
        w.beginTransaction()
        try {
            spaceIds.forEach { sid -> w.delete("tabs", "space_id=?", arrayOf(sid)) }
            w.delete("spaces", "profile_id=?", arrayOf(id))
            for (table in listOf("history", "bookmarks", "reading_list")) w.delete(table, "profile_id=?", arrayOf(id))
            w.delete("profiles", "id=?", arrayOf(id))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    // ── Lista de lectura y marcadores (del perfil activo) ─────────────

    private fun savedPages(table: String): List<SavedPage> = readableDatabase.rawQuery(
        "SELECT url, title, added FROM $table WHERE profile_id=? ORDER BY added DESC LIMIT 500", arrayOf(profile),
    ).use { c -> buildList { while (c.moveToNext()) add(SavedPage(c.getString(0), c.getString(1), c.getLong(2))) } }

    private fun addPage(table: String, url: String, title: String) {
        val v = ContentValues().apply {
            put("url", url); put("profile_id", profile); put("title", title); put("added", System.currentTimeMillis())
        }
        io.execute { writableDatabase.insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    private fun removePage(table: String, url: String) {
        val p = profile
        io.execute { writableDatabase.delete(table, "profile_id=? AND url=?", arrayOf(p, url)) }
    }

    private fun hasPage(table: String, url: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM $table WHERE profile_id=? AND url=?", arrayOf(profile, url)).use { it.moveToFirst() }

    fun readingList(): List<ReadingItem> = savedPages("reading_list").map { ReadingItem(it.url, it.title, it.added) }
    fun addToReadingList(url: String, title: String) = addPage("reading_list", url, title)
    fun removeFromReadingList(url: String) = removePage("reading_list", url)
    fun inReadingList(url: String) = hasPage("reading_list", url)

    fun bookmarks(): List<SavedPage> = savedPages("bookmarks")
    fun addBookmark(url: String, title: String) = addPage("bookmarks", url, title)
    fun removeBookmark(url: String) = removePage("bookmarks", url)
    fun isBookmarked(url: String) = hasPage("bookmarks", url)

    /** Los sitios más visitados en 30 días (para "Visitados con frecuencia"), uno por dominio. */
    fun frequent(limit: Int = 8): List<HistoryEntry> {
        val since = System.currentTimeMillis() - 30L * 86_400_000
        return readableDatabase.rawQuery(
            """SELECT url, title, last_visit, visits FROM history WHERE profile_id = ? AND visits > 1 AND last_visit > ?
               ORDER BY visits DESC, last_visit DESC LIMIT 40""",
            arrayOf(profile, since.toString()),
        ).use { c ->
            val seen = HashSet<String>()
            buildList {
                while (c.moveToNext() && size < limit) {
                    val e = HistoryEntry(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3))
                    if (seen.add(Urls.displayHost(e.url))) add(e)
                }
            }
        }
    }

    // ── Espacios ──────────────────────────────────────────────────────

    fun loadSpaces(): List<Space> = readableDatabase.rawQuery(
        "SELECT id, name, emoji, colors, position, isolated, profile_id FROM spaces ORDER BY position", null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    Space(
                        id = c.getString(0),
                        name = c.getString(1),
                        emoji = c.getString(2),
                        colors = c.getString(3).split(',').mapNotNull { it.toLongOrNull()?.toInt() },
                        position = c.getInt(4),
                        isolated = c.getInt(5) == 1,
                        profileId = c.getString(6),
                    ),
                )
            }
        }
    }

    fun saveSpace(s: Space) {
        val v = ContentValues().apply {
            put("id", s.id); put("name", s.name); put("emoji", s.emoji)
            put("colors", s.colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })
            put("position", s.position); put("isolated", if (s.isolated) 1 else 0); put("profile_id", s.profileId)
        }
        io.execute { writableDatabase.insertWithOnConflict("spaces", null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    fun deleteSpace(id: String) = io.execute {
        writableDatabase.delete("spaces", "id=?", arrayOf(id))
        writableDatabase.delete("tabs", "space_id=?", arrayOf(id))
    }

    // ── Pestañas ──────────────────────────────────────────────────────

    fun loadTabs(): List<Tab> = readableDatabase.rawQuery(
        "SELECT id, space_id, url, title, kind, position, last_used, state, pinned_url FROM tabs ORDER BY position",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    Tab(
                        id = c.getString(0),
                        spaceId = c.getString(1),
                        url = c.getString(2),
                        title = c.getString(3),
                        kind = TabKind.of(c.getInt(4)),
                        position = c.getInt(5),
                        lastUsed = c.getLong(6),
                        sessionState = c.getString(7),
                        pinnedUrl = c.getString(8),
                    ),
                )
            }
        }
    }

    fun saveTab(t: Tab) {
        if (t.isPrivate) return // las privadas nunca tocan el disco
        val v = ContentValues().apply {
            put("id", t.id); put("space_id", t.spaceId); put("url", t.url); put("title", t.title)
            put("kind", t.kind.code); put("position", t.position); put("last_used", t.lastUsed)
            put("state", t.sessionState); put("pinned_url", t.pinnedUrl)
        }
        io.execute { writableDatabase.insertWithOnConflict("tabs", null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    fun saveTabs(tabs: List<Tab>) = tabs.forEach(::saveTab)

    fun deleteTab(id: String) = io.execute { writableDatabase.delete("tabs", "id=?", arrayOf(id)) }

    // ── Historial (del perfil activo) ─────────────────────────────────

    fun recordVisit(url: String, title: String) {
        val p = profile
        io.execute {
            writableDatabase.execSQL(
                """INSERT INTO history(url, profile_id, title, last_visit, visits) VALUES(?, ?, ?, ?, 1)
                   ON CONFLICT(url, profile_id) DO UPDATE SET last_visit = excluded.last_visit, visits = visits + 1,
                   title = CASE WHEN excluded.title = '' THEN title ELSE excluded.title END""",
                arrayOf<Any>(url, p, title, System.currentTimeMillis()),
            )
        }
    }

    fun updateHistoryTitle(url: String, title: String) {
        val p = profile
        io.execute {
            writableDatabase.execSQL("UPDATE history SET title = ? WHERE url = ? AND profile_id = ?", arrayOf(title, url, p))
        }
    }

    /** Recorta el historial a [HISTORY_LIMIT] entradas para que la base no crezca sin límite. */
    fun trimHistory() = io.execute {
        writableDatabase.execSQL(
            "DELETE FROM history WHERE rowid NOT IN (SELECT rowid FROM history ORDER BY last_visit DESC LIMIT $HISTORY_LIMIT)",
        )
    }

    fun history(query: String = "", limit: Int = 300): List<HistoryEntry> {
        val like = "%${query.replace("%", "").replace("_", "")}%"
        return readableDatabase.rawQuery(
            """SELECT url, title, last_visit, visits FROM history WHERE profile_id = ? AND (url LIKE ? OR title LIKE ?)
               ORDER BY last_visit DESC LIMIT $limit""",
            arrayOf(profile, like, like),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(HistoryEntry(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3)))
            }
        }
    }

    /** Sugerencias para la barra: más visitadas y recientes primero. */
    fun suggest(query: String, limit: Int = 5): List<HistoryEntry> {
        if (query.isBlank()) return emptyList()
        val like = "%${query.replace("%", "").replace("_", "")}%"
        return readableDatabase.rawQuery(
            """SELECT url, title, last_visit, visits FROM history WHERE profile_id = ? AND (url LIKE ? OR title LIKE ?)
               ORDER BY visits * 3 + (last_visit / 86400000) DESC LIMIT $limit""",
            arrayOf(profile, like, like),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(HistoryEntry(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3)))
            }
        }
    }

    fun visited(urls: Array<String>): BooleanArray {
        if (urls.isEmpty()) return BooleanArray(0)
        val found = HashSet<String>()
        urls.toList().chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT url FROM history WHERE profile_id = ? AND url IN ($marks)", arrayOf(profile) + chunk.toTypedArray(),
            ).use { c -> while (c.moveToNext()) found.add(c.getString(0)) }
        }
        return BooleanArray(urls.size) { urls[it] in found }
    }

    fun deleteHistory(url: String) {
        val p = profile
        io.execute { writableDatabase.delete("history", "url=? AND profile_id=?", arrayOf(url, p)) }
    }

    fun clearHistory() {
        val p = profile
        io.execute { writableDatabase.delete("history", "profile_id=?", arrayOf(p)) }
    }
}
