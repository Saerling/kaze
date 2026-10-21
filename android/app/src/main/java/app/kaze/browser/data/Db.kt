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
 */
class Db(context: Context) : SQLiteOpenHelper(context, "kaze.db", null, VERSION) {

    companion object {
        private const val VERSION = 1
        private const val HISTORY_LIMIT = 5000
    }

    val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kaze-db").apply { priority = Thread.MIN_PRIORITY } }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE spaces(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL,
                colors TEXT NOT NULL, position INTEGER NOT NULL, isolated INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE tabs(
                id TEXT PRIMARY KEY, space_id TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL,
                kind INTEGER NOT NULL, position INTEGER NOT NULL, last_used INTEGER NOT NULL,
                state TEXT, pinned_url TEXT)""",
        )
        db.execSQL(
            """CREATE TABLE history(
                url TEXT PRIMARY KEY, title TEXT NOT NULL, last_visit INTEGER NOT NULL, visits INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE INDEX history_last ON history(last_visit DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ── Espacios ──────────────────────────────────────────────────────

    fun loadSpaces(): List<Space> = readableDatabase.rawQuery(
        "SELECT id, name, emoji, colors, position, isolated FROM spaces ORDER BY position", null,
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
                    ),
                )
            }
        }
    }

    fun saveSpace(s: Space) {
        val v = ContentValues().apply {
            put("id", s.id); put("name", s.name); put("emoji", s.emoji)
            put("colors", s.colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })
            put("position", s.position); put("isolated", if (s.isolated) 1 else 0)
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

    // ── Historial ─────────────────────────────────────────────────────

    fun recordVisit(url: String, title: String) = io.execute {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.execSQL(
            """INSERT INTO history(url, title, last_visit, visits) VALUES(?, ?, ?, 1)
               ON CONFLICT(url) DO UPDATE SET last_visit = excluded.last_visit, visits = visits + 1,
               title = CASE WHEN excluded.title = '' THEN title ELSE excluded.title END""",
            arrayOf<Any>(url, title, now),
        )
    }

    fun updateHistoryTitle(url: String, title: String) = io.execute {
        writableDatabase.execSQL("UPDATE history SET title = ? WHERE url = ?", arrayOf(title, url))
    }

    /** Recorta el historial a [HISTORY_LIMIT] entradas para que la base no crezca sin límite. */
    fun trimHistory() = io.execute {
        writableDatabase.execSQL(
            "DELETE FROM history WHERE url NOT IN (SELECT url FROM history ORDER BY last_visit DESC LIMIT $HISTORY_LIMIT)",
        )
    }

    fun history(query: String = "", limit: Int = 300): List<HistoryEntry> {
        val like = "%${query.replace("%", "").replace("_", "")}%"
        return readableDatabase.rawQuery(
            "SELECT url, title, last_visit, visits FROM history WHERE url LIKE ? OR title LIKE ? ORDER BY last_visit DESC LIMIT $limit",
            arrayOf(like, like),
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
            """SELECT url, title, last_visit, visits FROM history WHERE url LIKE ? OR title LIKE ?
               ORDER BY visits * 3 + (last_visit / 86400000) DESC LIMIT $limit""",
            arrayOf(like, like),
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
            readableDatabase.rawQuery("SELECT url FROM history WHERE url IN ($marks)", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) found.add(c.getString(0))
            }
        }
        return BooleanArray(urls.size) { urls[it] in found }
    }

    fun deleteHistory(url: String) = io.execute { writableDatabase.delete("history", "url=?", arrayOf(url)) }

    fun clearHistory() = io.execute { writableDatabase.delete("history", null, null) }
}
