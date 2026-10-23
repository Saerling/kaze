/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado de espacios y pestañas en memoria (lo que dibuja la UI) con
 * escritura a SQLite en segundo plano. Todo se llama desde el hilo principal.
 */
class Store(private val db: Db, private val settings: Settings) {

    val profiles = mutableStateListOf<Profile>()
    /** Todos los espacios de todos los perfiles (la UI usa [visibleSpaces], solo los del perfil activo). */
    val spaces = mutableStateListOf<Space>()
    val tabs = mutableStateListOf<Tab>()

    var currentProfileId by mutableStateOf(Profile.DEFAULT_ID)
        private set
    val currentProfile: Profile? get() = profiles.firstOrNull { it.id == currentProfileId }

    /** Espacios del perfil activo, en orden. */
    val profileSpaces by derivedStateOf {
        spaces.filter { it.profileId == currentProfileId }.sortedBy { it.position }
    }

    var currentSpaceId by mutableStateOf("")
        private set
    var selectedTabId by mutableStateOf<String?>(null)
        private set
    /** Cambia cada vez que se selecciona una pestaña (aunque sea la misma), para que la UI la vuelva a mostrar. */
    var selectionNonce by mutableIntStateOf(0)
        private set

    val currentSpace: Space? get() = spaceById(currentSpaceId)
    val selectedTab: Tab? get() = selectedTabId?.let(::tabById)

    /** Espacios visibles en el selector: los guardados + "Privado" si hay pestañas privadas. */
    val visibleSpaces by derivedStateOf {
        val list = profileSpaces
        if (tabs.any { it.isPrivate }) list + privateSpace else list
    }

    val privateSpace = Space(
        id = Space.PRIVATE_ID, name = "Privado", emoji = "🕶️",
        colors = listOf(0xFF3B3355.toInt(), 0xFF5B4B8A.toInt()), position = Int.MAX_VALUE,
    )

    private val main = Handler(Looper.getMainLooper())
    private val pendingState = LinkedHashSet<String>()
    private val flushTask = Runnable { flush() }

    fun load() {
        profiles.clear(); spaces.clear(); tabs.clear()
        profiles.addAll(db.loadProfiles())
        if (profiles.none { it.id == Profile.DEFAULT_ID }) {
            Profile(Profile.DEFAULT_ID, "Personal", "🙂", 0).also { profiles.add(0, it); db.saveProfile(it) }
        }
        spaces.addAll(db.loadSpaces())
        if (spaces.none { it.profileId == Profile.DEFAULT_ID }) seed(Profile.DEFAULT_ID)
        tabs.addAll(db.loadTabs().filter { t -> spaces.any { it.id == t.spaceId } })
        currentProfileId = settings.lastProfileId.takeIf { id -> profiles.any { it.id == id } } ?: Profile.DEFAULT_ID
        db.profile = currentProfileId
        currentSpaceId = settings.lastSpaceId.takeIf { id -> profileSpaces.any { it.id == id } } ?: profileSpaces.first().id
    }

    private fun seed(profileId: String) {
        val presets = SpacePalette.presets
        val list = if (profileId == Profile.DEFAULT_ID) listOf(
            Space(name = "Personal", emoji = "🌿", colors = presets[0], position = 0, profileId = profileId),
            Space(name = "Trabajo", emoji = "💼", colors = presets[3], position = 1, profileId = profileId),
        ) else listOf(Space(name = "General", emoji = "🌐", colors = presets[3], position = 0, profileId = profileId))
        list.forEach { spaces.add(it); db.saveSpace(it) }
    }

    // ── Perfiles ──────────────────────────────────────────────────────

    fun profileById(id: String?): Profile? = profiles.firstOrNull { it.id == id }

    /** Perfil al que pertenece una pestaña (por su espacio). */
    fun profileOf(t: Tab): Profile? = profileById(spaceById(t.spaceId)?.profileId)

    fun addProfile(name: String, emoji: String): Profile {
        val p = Profile(name = name, emoji = emoji, position = (profiles.maxOfOrNull { it.position } ?: -1) + 1)
        profiles.add(p)
        db.saveProfile(p)
        seed(p.id)
        return p
    }

    fun updateProfile(p: Profile) = db.saveProfile(p)

    /**
     * Cambia de perfil. Devuelve las pestañas del perfil anterior, que el llamador
     * debe dormir (otras cookies, otro historial: no deben seguir cargadas).
     */
    fun switchProfile(id: String): List<Tab> {
        if (id == currentProfileId || profileById(id) == null) return emptyList()
        val leaving = tabs.filter { !it.isPrivate && spaceById(it.spaceId)?.profileId == currentProfileId }
        flush()
        currentProfileId = id
        settings.lastProfileId = id
        db.profile = id
        selectedTabId = null
        currentSpaceId = profileSpaces.first().id
        return leaving
    }

    /** Borra un perfil (no el principal) con todo lo suyo. Devuelve sus pestañas para cerrarlas. */
    fun deleteProfile(id: String): List<Tab> {
        if (id == Profile.DEFAULT_ID) return emptyList()
        if (currentProfileId == id) switchProfile(Profile.DEFAULT_ID)
        val ids = spaces.filter { it.profileId == id }.map { it.id }
        val closed = tabs.filter { it.spaceId in ids }
        tabs.removeAll(closed)
        spaces.removeAll { it.id in ids }
        profiles.removeAll { it.id == id }
        db.deleteProfile(id, ids)
        return closed
    }

    // ── Consultas ─────────────────────────────────────────────────────

    fun spaceById(id: String): Space? = if (id == Space.PRIVATE_ID) privateSpace else spaces.firstOrNull { it.id == id }
    fun tabById(id: String): Tab? = tabs.firstOrNull { it.id == id }

    /** Essentials del perfil activo (en Zen se ven en todos los espacios). */
    fun essentials(): List<Tab> = tabs.filter {
        it.kind == TabKind.ESSENTIAL && spaceById(it.spaceId)?.profileId == currentProfileId
    }.sortedBy { it.position }
    fun pinned(spaceId: String): List<Tab> =
        tabs.filter { it.spaceId == spaceId && it.kind == TabKind.PINNED }.sortedBy { it.position }
    fun normal(spaceId: String): List<Tab> =
        tabs.filter { it.spaceId == spaceId && it.kind == TabKind.NORMAL }.sortedBy { it.position }

    // ── Espacios ──────────────────────────────────────────────────────

    fun selectSpace(id: String) {
        val s = spaceById(id) ?: return
        if (id != Space.PRIVATE_ID && s.profileId != currentProfileId) return
        currentSpaceId = id
        if (id != Space.PRIVATE_ID) settings.lastSpaceId = id
    }

    fun addSpace(name: String, emoji: String, colors: List<Int>): Space {
        val s = Space(
            name = name, emoji = emoji, colors = colors, profileId = currentProfileId,
            position = (profileSpaces.maxOfOrNull { it.position } ?: -1) + 1,
        )
        spaces.add(s)
        db.saveSpace(s)
        return s
    }

    fun updateSpace(s: Space) {
        if (s.id != Space.PRIVATE_ID) db.saveSpace(s)
    }

    /** Borra el espacio y sus pestañas (no las Essentials, que son globales). Devuelve las pestañas cerradas. */
    fun deleteSpace(id: String): List<Tab> {
        if (profileSpaces.size <= 1 || id == Space.PRIVATE_ID) return emptyList()
        // Las Essentials del espacio se pasan a otro espacio del perfil (son de todo el perfil).
        val heir = profileSpaces.first { it.id != id }
        tabs.filter { it.spaceId == id && it.kind == TabKind.ESSENTIAL }.forEach { it.spaceId = heir.id; db.saveTab(it) }
        val closed = tabs.filter { it.spaceId == id && it.kind != TabKind.ESSENTIAL }
        tabs.removeAll(closed)
        spaces.removeAll { it.id == id }
        db.deleteSpace(id)
        if (currentSpaceId == id) selectSpace(profileSpaces.first().id)
        if (selectedTabId in closed.map { it.id }) selectedTabId = null
        return closed
    }

    fun moveSpace(id: String, delta: Int) {
        val ordered = profileSpaces.toMutableList()
        val i = ordered.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in ordered.indices) return
        ordered.add(j, ordered.removeAt(i))
        ordered.forEachIndexed { idx, s -> if (s.position != idx) { s.position = idx; db.saveSpace(s) } }
    }

    // ── Pestañas ──────────────────────────────────────────────────────

    fun newTab(url: String, spaceId: String = currentSpaceId, private: Boolean = false, select: Boolean = true): Tab {
        val sid = if (private) Space.PRIVATE_ID else spaceId
        // Las pestañas nuevas van arriba de la lista, como en Zen.
        tabs.filter { it.spaceId == sid && it.kind == TabKind.NORMAL }.forEach { it.position += 1 }
        val t = Tab(spaceId = sid, url = url, kind = TabKind.NORMAL, position = 0, isPrivate = private)
        tabs.add(t)
        if (!private) persistPositions(sid, TabKind.NORMAL)
        if (select) select(t)
        return t
    }

    fun select(t: Tab) {
        t.lastUsed = System.currentTimeMillis()
        selectedTabId = t.id
        selectionNonce++
        if (t.kind != TabKind.ESSENTIAL && t.spaceId != currentSpaceId) currentSpaceId = t.spaceId
        db.saveTab(t)
    }

    fun clearSelection() {
        selectedTabId = null
    }

    /** Pestañas cerradas hace poco (solo en memoria; las privadas no se guardan). */
    val recentlyClosed = androidx.compose.runtime.mutableStateListOf<Pair<String, String>>()

    fun close(t: Tab) {
        if (!t.isPrivate && !t.isStart && t.url.isNotBlank()) {
            recentlyClosed.removeAll { it.first == t.url }
            recentlyClosed.add(0, t.url to t.displayTitle)
            if (recentlyClosed.size > 12) recentlyClosed.removeAt(recentlyClosed.lastIndex)
        }
        tabs.remove(t)
        db.deleteTab(t.id)
        if (selectedTabId == t.id) selectedTabId = null
        if (t.isPrivate && tabs.none { it.isPrivate } && currentSpaceId == Space.PRIVATE_ID) {
            selectSpace(profileSpaces.first().id)
        }
    }

    /** Siguiente pestaña a mostrar al cerrar [closed]: la usada más recientemente del mismo espacio. */
    fun nextAfterClose(closed: Tab): Tab? =
        tabs.filter { it.id != closed.id && it.spaceId == closed.spaceId && it.kind != TabKind.ESSENTIAL }
            .maxByOrNull { it.lastUsed }

    fun setKind(t: Tab, kind: TabKind) {
        if (t.isPrivate || t.kind == kind) return
        val oldKind = t.kind
        t.kind = kind
        t.pinnedUrl = if (kind == TabKind.NORMAL) null else (t.pinnedUrl ?: t.url)
        // Al fijar, va al final de los fijados; al desfijar, arriba de las normales.
        t.position = when (kind) {
            TabKind.NORMAL -> -1
            else -> (tabs.filter { it.kind == kind && it.id != t.id && (kind == TabKind.ESSENTIAL || it.spaceId == t.spaceId) }
                .maxOfOrNull { it.position } ?: -1) + 1
        }
        persistPositions(t.spaceId, kind)
        persistPositions(t.spaceId, oldKind)
        db.saveTab(t)
    }

    fun moveToSpace(t: Tab, spaceId: String) {
        if (t.isPrivate || t.spaceId == spaceId) return
        t.spaceId = spaceId
        if (t.kind == TabKind.ESSENTIAL) t.kind = TabKind.PINNED
        t.position = -1
        persistPositions(spaceId, t.kind)
        db.saveTab(t)
    }

    fun reorder(t: Tab, delta: Int) {
        val siblings = when (t.kind) {
            TabKind.ESSENTIAL -> essentials()
            TabKind.PINNED -> pinned(t.spaceId)
            TabKind.NORMAL -> normal(t.spaceId)
        }.toMutableList()
        val i = siblings.indexOf(t)
        val j = i + delta
        if (i < 0 || j !in siblings.indices) return
        siblings.add(j, siblings.removeAt(i))
        siblings.forEachIndexed { idx, s -> if (s.position != idx) { s.position = idx; db.saveTab(s) } }
    }

    /** Cierra todas las pestañas normales del espacio (las fijadas se quedan). Devuelve las cerradas. */
    fun clearNormal(spaceId: String): List<Tab> {
        val closed = normal(spaceId)
        closed.forEach { close(it) }
        return closed
    }

    // ── Carpetas y orden de las pestañas ──────────────────────────────

    /** Carpetas del espacio, en el orden de su primera pestaña. */
    fun folders(spaceId: String): List<String> = normal(spaceId).map { it.folder }.filter { it.isNotEmpty() }.distinct()

    fun folderTabs(spaceId: String, name: String): List<Tab> = normal(spaceId).filter { it.folder == name }

    /** Mete pestañas en una carpeta (la crea si no existe). */
    fun setFolder(list: List<Tab>, name: String) {
        list.filter { !it.isPrivate }.forEach { it.folder = name; db.saveTab(it) }
    }

    /** Nombre libre para una carpeta nueva: "Carpeta", "Carpeta 2"… */
    fun newFolderName(spaceId: String): String {
        val used = folders(spaceId).toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) "Carpeta" else "Carpeta $it" }.first { it !in used }
    }

    fun renameFolder(spaceId: String, old: String, new: String) {
        val name = new.trim().ifEmpty { return }
        setFolder(folderTabs(spaceId, old), name)
    }

    /** Organizar pestañas: las sueltas del mismo sitio (2 o más) van a una carpeta con su nombre. */
    fun organize(spaceId: String): Int {
        val groups = normal(spaceId).filter { it.folder.isEmpty() && !it.isStart }
            .groupBy { siteName(it.url) }.filter { it.key.isNotBlank() && it.value.size >= 2 }
        groups.forEach { (name, list) -> setFolder(list, name) }
        return groups.size
    }

    private fun siteName(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: return ""
        val parts = host.split('.')
        val core = if (parts.size >= 3 && parts[parts.size - 2].length <= 3) parts[parts.size - 3] else parts.getOrElse(parts.size - 2) { parts[0] }
        return core.replaceFirstChar { it.uppercase() }
    }

    fun ungroup(spaceId: String, name: String) = setFolder(folderTabs(spaceId, name), "")

    /** Arrastrar y soltar: pone la pestaña [moved] en el lugar de [target] (las demás se corren). */
    fun moveBefore(moved: Tab, target: Tab) {
        if (moved.id == target.id || moved.spaceId != target.spaceId) return
        val list = normal(moved.spaceId).toMutableList()
        val from = list.indexOf(moved)
        val to = list.indexOf(target)
        if (from < 0 || to < 0) return
        list.removeAt(from)
        list.add(to, moved)
        list.forEachIndexed { i, t -> if (t.position != i) { t.position = i; db.saveTab(t) } }
    }

    private fun persistPositions(spaceId: String, kind: TabKind) {
        val list = when (kind) {
            TabKind.ESSENTIAL -> essentials()
            TabKind.PINNED -> pinned(spaceId)
            TabKind.NORMAL -> normal(spaceId)
        }
        list.forEachIndexed { idx, s -> if (s.position != idx) s.position = idx; db.saveTab(s) }
    }

    // ── Cambios que llegan desde Gecko ────────────────────────────────

    fun onTitle(t: Tab, title: String) {
        if (t.title == title) return
        t.title = title
        if (!t.isPrivate) {
            db.updateHistoryTitle(t.url, title)
            schedulePersist(t)
        }
    }

    fun onUrl(t: Tab, url: String) {
        if (t.url == url) return
        t.url = url
        schedulePersist(t)
    }

    fun onSessionState(t: Tab, state: String) {
        t.sessionState = state
        schedulePersist(t)
    }

    /** Agrupa escrituras: el estado de sesión cambia con cada scroll, no hace falta guardarlo cada vez. */
    private fun schedulePersist(t: Tab) {
        if (t.isPrivate) return
        val wasEmpty = pendingState.isEmpty()
        pendingState.add(t.id)
        if (wasEmpty) main.postDelayed(flushTask, 3_000)
    }

    fun flush() {
        main.removeCallbacks(flushTask)
        val ids = pendingState.toList()
        pendingState.clear()
        ids.mapNotNull(::tabById).forEach(db::saveTab)
    }
}

/** Degradados de espacio inspirados en las paletas de Zen. */
object SpacePalette {
    val presets: List<List<Int>> = listOf(
        listOf(0xFFDCE8D4, 0xFFB9D3B0),                 // salvia (como Z-Sync)
        listOf(0xFFF1E3D3, 0xFFE8C7A8),                 // arena
        listOf(0xFFF5D9DF, 0xFFE9B5C4, 0xFFF3E1C9),     // rosa
        listOf(0xFFD7E3F4, 0xFFB7CBEA),                 // cielo
        listOf(0xFFE3DAF5, 0xFFC9B8EC, 0xFFD6E6F7),     // lavanda
        listOf(0xFFD4EEEA, 0xFFA8DAD2),                 // menta
        listOf(0xFFF6EBC4, 0xFFF0D58C),                 // limón
        listOf(0xFFF4D6C8, 0xFFE9A98C, 0xFFF3DCC2),     // durazno
        listOf(0xFFE2E2E2, 0xFFC8C8CC),                 // grafito
    ).map { l -> l.map { it.toInt() } }

    val emojis = listOf(
        "🌿", "💼", "🏠", "🎮", "📚", "🎵", "🎨", "✈️", "🛒", "💡", "🧪", "📈",
        "🍳", "⚽", "🎬", "📷", "💻", "🧘", "❤️", "⭐", "🔥", "🌙", "☕", "🐾",
        "😴", "🎓", "🛠️", "📝", "🌍", "🍿", "💬", "🧩",
    )
}
