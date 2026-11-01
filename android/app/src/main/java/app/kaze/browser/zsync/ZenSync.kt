/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.zsync

import android.content.Context
import app.kaze.browser.data.Profile
import app.kaze.browser.data.Space
import app.kaze.browser.data.Store
import app.kaze.browser.data.Tab
import app.kaze.browser.data.TabKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Puente entre Fyx y la sincronización de Zen (Firefox Sync), basado en Z-Sync.
 * Zen de escritorio sube sus espacios, fijadas, carpetas y Essentials a tu cuenta de Mozilla; aquí se
 * traen a los espacios de Fyx, y lo que cambias en el teléfono (fijar, desfijar, cerrar, mover a un
 * grupo, renombrarlo, Essentials) se sube. La programación (cuándo) está en [ZenTabSync].
 */
object ZenSync {
    fun email(context: Context): String? = AccountStore.load(context)?.email

    suspend fun completeLogin(context: Context, login: FxAWebLogin) = withContext(Dispatchers.IO) {
        val result = FxAClient(transport = AccountStore.authTransport).completeWebLogin(
            email = login.email, uid = login.uid, sessionToken = login.sessionToken,
            keyFetchToken = login.keyFetchToken, unwrapBKeyHex = login.unwrapBKey,
        )
        AccountStore.save(context, AccountStore.AccountSnapshot(email = login.email, uid = result.uid, sessionTokenHex = result.session, kBHex = FxACrypto.hex(result.kB)))
        // Sesión nueva: se empieza sin base (ante la duda gana la cuenta y no se borra nada).
        clearState(context, result.uid)
    }

    /** Cierra sesión y suelta todos los enlaces con la cuenta (llamar desde el hilo principal). */
    fun signOut(context: Context, store: Store) {
        AccountStore.clear(context)
        store.unlinkAll()
        val p = prefs(context)
        p.edit().apply {
            p.all.keys.filter { it.startsWith("base:") || it.startsWith("moves:") || it.startsWith("discarded:") || it.startsWith("unsafe:") }.forEach(::remove)
        }.remove("base").remove("discarded").remove("failed").commit()
    }

    fun lastSync(context: Context): Long = prefs(context).getLong("last", 0)

    /** Cambios del teléfono que no se pudieron subir en el último intento (se reintentan solos). */
    fun failedChanges(context: Context): Int = prefs(context).getInt("failed", 0)

    /**
     * Ids de Zen (de esta cuenta) que se soltaron al borrar un espacio aquí: no se borran allá ni se
     * vuelven a traer, hasta que el usuario pida "Volver a traer espacios descartados".
     */
    internal fun discard(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val uid = runCatching { AccountStore.load(context)?.uid }.getOrNull() ?: return
        val p = prefs(context)
        val now = p.getStringSet("discarded:$uid", emptySet()).orEmpty() + ids
        p.edit().putStringSet("discarded:$uid", now).commit()
    }

    private fun discarded(context: Context, uid: String): Set<String> =
        prefs(context).getStringSet("discarded:$uid", emptySet()).orEmpty()

    /** Cuántos espacios y pestañas de Zen están descartados en esta cuenta (para Ajustes). */
    fun discardedCount(context: Context): Int {
        val uid = runCatching { AccountStore.load(context)?.uid }.getOrNull() ?: return 0
        return discarded(context, uid).size
    }

    /** "Volver a traer espacios descartados": la próxima sincronización los trae de nuevo. */
    fun restoreDiscarded(context: Context) {
        val uid = runCatching { AccountStore.load(context)?.uid }.getOrNull() ?: return
        prefs(context).edit().remove("discarded:$uid").commit()
    }

    private fun clearState(context: Context, uid: String) {
        prefs(context).edit().remove("base:$uid").remove("moves:$uid").remove("discarded:$uid").remove("unsafe:$uid").remove("base").commit()
    }

    private data class Pending(val index: Int, val op: TabSyncPlanner.Op, val title: String)

    /** Movimiento entre espacios a medias: el id nuevo se elige antes de escribir para poder repetirlo. */
    internal data class PendingMove(val newId: String, val to: TabSyncPlanner.State)

    /**
     * Sincroniza en ambos sentidos las fijadas, sus carpetas y las Essentials con Zen.
     * Lee la cuenta, compara con lo que hay aquí y con la última sincronización ([TabSyncPlanner]),
     * aplica aquí lo que cambió allá y sube lo que cambió aquí con escrituras condicionales.
     * Devuelve cuántas pestañas nuevas llegaron del escritorio. Las pestañas normales no viajan.
     */
    suspend fun syncTabs(
        context: Context,
        store: Store,
        closeTab: (Tab) -> Unit,
        force: Boolean = false,
        held: Set<String> = emptySet(),
    ): Int {
        val generation = AccountStore.currentGeneration
        val uid = withContext(Dispatchers.IO) { AccountStore.load(context)?.uid } ?: return 0
        val snap = SpacesSyncService.refresh(context)
        val readOnly = AccountStore.isDemo(context)
        val dropped = discarded(context, uid)

        // Lo que hay en la cuenta. Las carpetas se comparan por nombre (así las ve Fyx); las subcarpetas
        // se aplanan en su carpeta principal y las carpetas "en vivo" (RSS…) no se tocan.
        val zenSpaces = snap.spaces.filter { it.id !in dropped }
        val remote = LinkedHashMap<String, TabSyncPlanner.Remote>()
        val folderIds = HashMap<Pair<String, String>, String>()
        fun put(t: ZenSpaces.ZenTab, state: TabSyncPlanner.State) {
            if (TabSyncPlanner.syncable(t.url) && t.id !in remote && t.id !in dropped) {
                remote[t.id] = TabSyncPlanner.Remote(t.id, state, t.url, t.displayTitle)
            }
        }
        fun all(f: ZenSpaces.ZenFolder): List<ZenSpaces.ZenTab> = f.tabs + f.subfolders.orEmpty().flatMap { all(it) }
        zenSpaces.forEach { zs ->
            zs.pinned.forEach { item ->
                when (item) {
                    is ZenSpaces.ZenItem.Tab -> put(item.tab, TabSyncPlanner.State.pinned(zs.id, ""))
                    is ZenSpaces.ZenItem.Split -> item.split.tabs.forEach { put(it, TabSyncPlanner.State.pinned(zs.id, "")) }
                    is ZenSpaces.ZenItem.Folder -> if (item.folder.live == null) {
                        val name = item.folder.name.trim().ifBlank { "Carpeta" }
                        folderIds.putIfAbsent(zs.id to name, item.folder.id)
                        all(item.folder).forEach { put(it, TabSyncPlanner.State.pinned(zs.id, name)) }
                    }
                }
            }
        }
        snap.essentials.values.flatten().forEach { put(it, TabSyncPlanner.State.essential()) }

        val base = loadBase(context, uid)
        dropped.forEach { base.remove(it) }
        val moves = loadMoves(context, uid)
        var added = 0
        val pending = mutableListOf<Pending>()
        val applied = withContext(Dispatchers.Main) {
            if (!AccountStore.isCurrent(generation)) return@withContext false
            quiet(store) {
                // Espacios: el enlazado, o el del mismo nombre, o uno nuevo con el ícono y colores de Zen.
                val zenToFyx = LinkedHashMap<String, Space>()
                zenSpaces.forEach { zs ->
                    val space = store.spaces.firstOrNull { it.zenId == zs.id }
                        ?: store.spaces.firstOrNull { it.zenId == null && it.name.equals(zs.name, true) }
                        ?: store.addSpace(zs.name, zs.icon?.takeIf { it.isNotBlank() && it.length <= 4 } ?: "🖥️", zs.themeColors.mapNotNull { parse(it) }.ifEmpty { listOf(0xFF6B5BFF.toInt()) })
                    if (space.zenId != zs.id) { space.zenId = zs.id; store.updateSpace(space) }
                    zenToFyx[zs.id] = space
                }
                val firstSpace = zenToFyx.values.firstOrNull()
                val profile = firstSpace?.profileId ?: Profile.DEFAULT_ID

                // Movimientos entre espacios que quedaron a medias: si el registro nuevo ya está en la cuenta,
                // la pestaña pasa a ser esa (y el viejo se borrará como cualquier pestaña que ya no está aquí).
                moves.entries.toList().forEach { (tabId, move) ->
                    val r = remote[move.newId] ?: return@forEach
                    store.tabById(tabId)?.let { store.link(it, move.newId) }
                    base[move.newId] = r.state
                    moves.remove(tabId)
                }

                fun stateOf(t: Tab): TabSyncPlanner.State? = when {
                    !TabSyncPlanner.syncable(t.pinnedUrl ?: t.url) -> null
                    t.kind == TabKind.ESSENTIAL -> TabSyncPlanner.State.essential()
                    t.kind == TabKind.PINNED -> store.spaceById(t.spaceId)?.zenId?.let { TabSyncPlanner.State.pinned(it, t.folder) }
                    else -> null
                }
                val locals = store.tabs.filter { !it.isPrivate && (it.zenId != null || store.spaceById(it.spaceId)?.profileId == profile) }
                    .map { TabSyncPlanner.Local(it.id, it.zenId, stateOf(it), it.pinnedUrl ?: it.url) }
                    .filter { it.state != null || it.zenId != null }
                val planned = TabSyncPlanner.withoutHeld(TabSyncPlanner.plan(base, remote, locals), held)
                val linked = locals.count { it.zenId != null }
                val ops = try {
                    TabSyncPlanner.guard(planned, base, remote, snap.decryptFailures, force, linked)
                } catch (e: TabSyncPlanner.Unsafe) {
                    // Si la misma situación se repite estable (un minuto después), fue a propósito: se acepta.
                    val fp = TabSyncPlanner.Stability.fingerprint(remote.keys, locals.mapNotNull { it.zenId })
                    val (ok, seen) = TabSyncPlanner.Stability.accept(loadSeen(context, uid), fp, System.currentTimeMillis())
                    saveSeen(context, uid, seen)
                    if (!ok) throw e
                    TabSyncPlanner.guard(planned, base, remote, snap.decryptFailures, force = true, localLinked = linked)
                }

                fun apply(t: Tab, st: TabSyncPlanner.State) {
                    if (st.essential) {
                        store.setFolder(listOf(t), "")
                        store.setKind(t, TabKind.ESSENTIAL)
                    } else {
                        val target = zenToFyx[st.space] ?: return
                        if (t.spaceId != target.id) store.moveToSpace(t, target.id)
                        store.setKind(t, TabKind.PINNED)
                        store.setFolder(listOf(t), st.folder)
                    }
                }
                ops.forEachIndexed { index, op ->
                    when (op) {
                        is TabSyncPlanner.Op.Keep -> base[op.zenId] = op.state
                        is TabSyncPlanner.Op.Link -> {
                            store.tabById(op.tabId)?.let { store.link(it, op.zenId) }
                            base[op.zenId] = op.state
                        }
                        is TabSyncPlanner.Op.Unlink -> {
                            store.tabById(op.tabId)?.let { store.link(it, null) }
                            base.remove(op.zenId)
                        }
                        is TabSyncPlanner.Op.ApplyLocal -> store.tabById(op.tabId)?.let { t ->
                            if (t.zenId != op.zenId) store.link(t, op.zenId)
                            apply(t, op.state)
                            base[op.zenId] = op.state
                        }
                        is TabSyncPlanner.Op.CreateLocal -> {
                            val st = op.remote.state
                            val space = if (st.essential) firstSpace else zenToFyx[st.space]
                            if (space != null) {
                                val t = store.newTab(op.remote.url, spaceId = space.id, select = false)
                                t.title = op.remote.title
                                store.link(t, op.remote.id)
                                apply(t, st)
                                base[op.remote.id] = st
                                added++
                            }
                        }
                        is TabSyncPlanner.Op.CloseLocal -> {
                            val t = store.tabById(op.tabId)
                            // La que estás viendo no se cierra de golpe: se queda abierta como pestaña normal.
                            if (t != null && t.id == store.selectedTabId) {
                                store.link(t, null)
                                store.setKind(t, TabKind.NORMAL)
                            } else if (t != null) {
                                closeTab(t)
                                store.close(t)
                            }
                            base.remove(op.zenId)
                        }
                        is TabSyncPlanner.Op.DeleteRemote -> pending += Pending(index, op, "")
                        is TabSyncPlanner.Op.UpdateRemote -> pending += Pending(index, op, store.tabById(op.tabId)?.displayTitle.orEmpty())
                        is TabSyncPlanner.Op.CreateRemote -> pending += Pending(index, op, store.tabById(op.tabId)?.displayTitle.orEmpty())
                    }
                }
            }
            true
        }
        if (!applied) return 0

        // Subida. Cada cambio va por separado: si uno falla, su base no cambia y se reintenta la próxima vez.
        val links = mutableListOf<Pair<String, String?>>() // pestaña de Fyx → id nuevo de Zen (null = soltar)
        var failed = 0
        if (pending.isNotEmpty() && !readOnly) {
            // Ids elegidos antes de escribir: si hay que repetir (reautenticación, corte), no se duplica nada.
            val ids = pending.associate { it.index to ZenSpaces.newTabRecordId() }
            val done = HashSet<Int>()
            AccountStore.withClient(context) { client ->
                failed = upload(client, pending, ids, done, remote, folderIds, base, links, moves, zenSpaces.firstOrNull()?.id) {
                    saveMoves(context, uid, moves)
                }
            }
        }

        val stillCurrent = withContext(Dispatchers.Main) {
            if (!AccountStore.isCurrent(generation)) return@withContext false
            quiet(store) { links.forEach { (tabId, zenId) -> store.tabById(tabId)?.let { store.link(it, zenId) } } }
            true
        }
        if (!stillCurrent) return 0
        // La base solo se guarda cuando los enlaces ya están en la base de datos: si la app se cierra en
        // medio, nunca queda una base que diga "enlazada" de una pestaña que en disco no lo está.
        withContext(Dispatchers.IO) { store.awaitWrites() }
        if (!AccountStore.isCurrent(generation)) return 0
        saveBase(context, uid, base)
        saveMoves(context, uid, moves)
        prefs(context).edit().putLong("last", System.currentTimeMillis()).putInt("failed", failed).remove("unsafe:$uid").commit()
        return added
    }

    /** Sube los cambios con una sola sesión de escritura. Devuelve cuántos fallaron. */
    private suspend fun upload(
        client: SyncClient,
        pending: List<Pending>,
        ids: Map<Int, String>,
        done: MutableSet<Int>,
        remote: Map<String, TabSyncPlanner.Remote>,
        folderIds: MutableMap<Pair<String, String>, String>,
        base: MutableMap<String, TabSyncPlanner.State>,
        links: MutableList<Pair<String, String?>>,
        moves: MutableMap<String, PendingMove>,
        firstZenSpace: String?,
        saveMoves: () -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val session = SpacesSyncService.session(client)
        fun folderFor(space: String, name: String): String? =
            if (name.isEmpty()) null else folderIds.getOrPut(space to name) { SpacesSyncService.createFolder(session, space, name) }
        val emptied = HashSet<String>()
        fun leaving(st: TabSyncPlanner.State?) {
            if (st != null && !st.essential && st.folder.isNotEmpty()) folderIds[st.space to st.folder]?.let(emptied::add)
        }
        var failed = 0
        /** Los errores de sesión o cancelación se relanzan (withClient reautentica y repite); el resto se cuenta. */
        fun attempt(index: Int, block: () -> Unit) {
            if (index in done) return
            try {
                block()
                done += index
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncError.Unauthorized) {
                throw e
            } catch (e: SyncError.NotSignedIn) {
                throw e
            } catch (e: Exception) {
                failed++
                android.util.Log.w("Fyx", "Sincronización de pestañas: no se pudo subir un cambio", e)
            }
        }

        // Renombrar un grupo aquí = renombrar la carpeta allá (no crear otra y vaciar la vieja). Pasa cuando
        // todas sus pestañas de la cuenta se movieron juntas a un nombre que allá todavía no existe.
        pending.filter { p ->
            val op = p.op as? TabSyncPlanner.Op.UpdateRemote ?: return@filter false
            p.index !in done && !op.from.essential && !op.to.essential && op.from.space == op.to.space &&
                op.from.folder.isNotEmpty() && op.to.folder.isNotEmpty() && op.from.folder != op.to.folder
        }
            .groupBy { (it.op as TabSyncPlanner.Op.UpdateRemote).let { op -> op.from.space to op.from.folder } }
            .forEach { (key, group) ->
                val updates = group.map { it.op as TabSyncPlanner.Op.UpdateRemote }
                val newName = updates.map { it.to.folder }.distinct().singleOrNull() ?: return@forEach
                val folderId = folderIds[key] ?: return@forEach
                val members = remote.values.filter { it.state == TabSyncPlanner.State.pinned(key.first, key.second) }.map { it.id }.toSet()
                if (folderIds.containsKey(key.first to newName) || members != updates.map { it.zenId }.toSet()) return@forEach
                attempt(-1 - group.first().index) {
                    if (SpacesSyncService.renameFolder(session, folderId, newName)) {
                        folderIds.remove(key)
                        folderIds[key.first to newName] = folderId
                        updates.forEach { base[it.zenId] = it.to }
                        group.forEach { done += it.index }
                    }
                }
            }

        for ((index, op, title) in pending) attempt(index) {
            when (op) {
                is TabSyncPlanner.Op.DeleteRemote -> {
                    leaving(base[op.zenId])
                    SpacesSyncService.delete(session, op.zenId)
                    base.remove(op.zenId)
                    op.tabId?.let { links += it to null }
                }
                is TabSyncPlanner.Op.UpdateRemote -> {
                    val from = op.from
                    val to = op.to
                    leaving(from)
                    when {
                        to.essential -> SpacesSyncService.setEssential(session, op.zenId, true, null)
                        from.essential -> {
                            SpacesSyncService.setEssential(session, op.zenId, false, to.space)
                            folderFor(to.space, to.folder)?.let { SpacesSyncService.move(session, op.zenId, to.space, it) }
                        }
                        from.space != to.space -> {
                            // Zen no mueve fijadas entre espacios: se crea en el nuevo (con un id guardado antes,
                            // para que reintentar no duplique) y luego se quita del viejo.
                            val move = moves[op.tabId]?.takeIf { it.to == to } ?: PendingMove(ids.getValue(index), to)
                            moves[op.tabId] = move
                            saveMoves()
                            SpacesSyncService.addPinned(session, move.newId, op.url, title.ifBlank { op.url }, to.space, folderFor(to.space, to.folder))
                            links += op.tabId to move.newId
                            base[move.newId] = to
                            // Si el borrado falla, la base vieja se queda y el próximo intento lo borra.
                            SpacesSyncService.delete(session, op.zenId)
                            base.remove(op.zenId)
                            moves.remove(op.tabId)
                        }
                        else -> SpacesSyncService.move(session, op.zenId, to.space, folderFor(to.space, to.folder))
                    }
                    if (from.space == to.space || to.essential || from.essential) base[op.zenId] = to
                }
                is TabSyncPlanner.Op.CreateRemote -> {
                    val st = op.state
                    val name = title.ifBlank { op.url }
                    val id = ids.getValue(index)
                    if (st.essential) {
                        SpacesSyncService.addEssential(session, id, op.url, name, firstZenSpace)
                    } else {
                        SpacesSyncService.addPinned(session, id, op.url, name, st.space, folderFor(st.space, st.folder))
                    }
                    links += op.tabId to id
                    base[id] = st
                }
                else -> Unit
            }
        }

        // Carpetas que se quedaron vacías por lo que se movió aquí: se quitan, solo si de verdad están vacías.
        // No cuenta como cambio fallido: es limpieza, y la carpeta vacía no molesta.
        emptied.forEach { folderId ->
            try {
                SpacesSyncService.deleteFolderIfEmpty(session, folderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncError.Unauthorized) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Fyx", "No se pudo quitar una carpeta vacía de Zen", e)
            }
        }
        android.util.Log.i("Fyx", "Sincronización de pestañas: ${pending.size} cambios, ${session.reads} lecturas, $failed fallidos")
        failed
    }

    /** Lo que hace la sincronización en el Store no cuenta como cambio del usuario (no se vuelve a subir). */
    private inline fun quiet(store: Store, block: () -> Unit) {
        val listener = store.onSyncChange
        store.onSyncChange = null
        try { block() } finally { store.onSyncChange = listener }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("zsync", Context.MODE_PRIVATE)

    /** Base de la última sincronización correcta de esa cuenta: id de Zen → dónde estaba la pestaña. */
    internal fun loadBase(context: Context, uid: String): MutableMap<String, TabSyncPlanner.State> {
        val out = HashMap<String, TabSyncPlanner.State>()
        val p = prefs(context)
        // Versión anterior: una sola base sin cuenta. Se pasa a la de la cuenta con sesión y se borra.
        p.getString("base", null)?.let { old ->
            if (!p.contains("base:$uid")) p.edit().putString("base:$uid", old).remove("base").commit()
            else p.edit().remove("base").commit()
        }
        val raw = p.getString("base:$uid", null) ?: return out
        runCatching {
            val o = JSONObject(raw)
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                out[k] = TabSyncPlanner.State(a.getBoolean(0), a.getString(1), a.getString(2))
            }
        }
        return out
    }

    internal fun saveBase(context: Context, uid: String, base: Map<String, TabSyncPlanner.State>) {
        val o = JSONObject()
        base.forEach { (k, s) -> o.put(k, JSONArray().put(s.essential).put(s.space).put(s.folder)) }
        prefs(context).edit().putString("base:$uid", o.toString()).commit()
    }

    private fun loadSeen(context: Context, uid: String): TabSyncPlanner.Stability.Seen? {
        val raw = prefs(context).getString("unsafe:$uid", null) ?: return null
        return runCatching { JSONArray(raw).let { TabSyncPlanner.Stability.Seen(it.getString(0), it.getLong(1)) } }.getOrNull()
    }

    private fun saveSeen(context: Context, uid: String, seen: TabSyncPlanner.Stability.Seen) {
        prefs(context).edit().putString("unsafe:$uid", JSONArray().put(seen.fingerprint).put(seen.since).toString()).commit()
    }

    private fun loadMoves(context: Context, uid: String): MutableMap<String, PendingMove> {
        val out = HashMap<String, PendingMove>()
        val raw = prefs(context).getString("moves:$uid", null) ?: return out
        runCatching {
            val o = JSONObject(raw)
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                out[k] = PendingMove(a.getString(0), TabSyncPlanner.State(a.getBoolean(1), a.getString(2), a.getString(3)))
            }
        }
        return out
    }

    private fun saveMoves(context: Context, uid: String, moves: Map<String, PendingMove>) {
        val o = JSONObject()
        moves.forEach { (k, m) -> o.put(k, JSONArray().put(m.newId).put(m.to.essential).put(m.to.space).put(m.to.folder)) }
        prefs(context).edit().putString("moves:$uid", o.toString()).commit()
    }

    /** Envía una pestaña al escritorio (queda en el espacio de Zen con el mismo nombre, o en el primero). */
    suspend fun send(context: Context, url: String, title: String, spaceName: String?) {
        val snap = SpacesSyncService.refresh(context)
        val target = snap.spaces.firstOrNull { it.name.equals(spaceName, true) } ?: snap.spaces.first()
        SpacesSyncService.addTab(context, url, title, target.id, kind = SaveKind.NORMAL)
    }

    /**
     * Contraseñas con la cuenta de Firefox: trae las de la cuenta (cifradas de extremo a extremo con la
     * clave de tu cuenta, igual que Firefox) y sube las guardadas en el teléfono que falten.
     */
    suspend fun syncPasswords(context: Context, logins: app.kaze.browser.engine.Logins): Int = withContext(Dispatchers.IO) {
        AccountStore.withClient(context) { c ->
            logins.pendingDeletes.forEach { runCatching { c.putTombstone("passwords", it) } }
            logins.clearPendingDeletes()
            val (added, upload) = logins.merge(c.getRecords("passwords"))
            upload.forEach { c.putRecord("passwords", it.getString("id"), it) }
            logins.markSynced(upload.map { it.getString("id") })
            added
        }
    }

    private fun parse(c: String): Int? = runCatching { android.graphics.Color.parseColor(c.trim()) }.getOrNull()
}

/**
 * Cuándo se sincronizan las pestañas: la subida, 3 s después del último cambio (agrupa varios cambios
 * seguidos); la bajada, al abrir la app y al volver al primer plano, como mucho una vez cada 5 minutos.
 * Sin sesión de Firefox no hace nada. Una sola sincronización a la vez.
 */
class ZenTabSync(
    private val context: Context,
    private val store: Store,
    private val closeTab: (Tab) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var pendingPush: Job? = null
    private var lastRun = 0L
    /** Pestañas de Zen retenidas (id → hasta cuándo) mientras se ve su aviso de "Deshacer". */
    private val held = HashMap<String, Long>()
    private var release: Job? = null

    /** Avisa cuando llegan pestañas nuevas del escritorio (para mostrar un aviso). */
    var onNews: ((Int) -> Unit)? = null

    /** Avisa cuando la sincronización se detuvo por seguridad (para mostrar por qué). */
    var onProblem: ((String) -> Unit)? = null

    init {
        store.onUnlinked = { spaces, tabs -> ZenSync.discard(context, spaces + tabs) }
    }

    /** Algo cambió en el teléfono: se sube en unos segundos (si llegan más cambios, se espera otra vez). */
    fun onLocalChange() {
        pendingPush?.cancel()
        pendingPush = scope.launch {
            delay(PUSH_DELAY_MS)
            runQuietly()
        }
    }

    /**
     * Retiene los cambios de la pestaña [zenId] durante [millis] (lo que se ve el aviso con "Deshacer" de un
     * cierre o un desfijado), en cualquier sincronización: la subida en cola, al volver a la app o el botón
     * Sincronizar. Aunque otro aviso tape al de "Deshacer", la retención dura hasta que vence; entonces se sube.
     */
    fun hold(zenId: String, millis: Long) {
        held[zenId] = System.currentTimeMillis() + millis
        release?.cancel()
        release = scope.launch {
            delay(millis + 100)
            onLocalChange()
        }
    }

    private fun heldNow(): Set<String> {
        val now = System.currentTimeMillis()
        held.entries.removeAll { it.value <= now }
        return held.keys.toSet()
    }

    /** La app se abrió o volvió al primer plano. */
    fun onForeground() {
        if (lastRun != 0L && System.currentTimeMillis() - lastRun < PULL_INTERVAL_MS) return
        scope.launch { runQuietly() }
    }

    /** "Sincronizar" en Ajustes: ahora mismo, y con el error a la vista. */
    suspend fun syncNow(): Int = run()

    /** "Aplicar cambios del escritorio": una vez, sin el freno de cierres o borrados masivos. */
    suspend fun syncForced(): Int = run(force = true)

    private suspend fun runQuietly() {
        try {
            val n = run()
            if (n > 0) onNews?.invoke(n)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TabSyncPlanner.Unsafe) {
            onProblem?.invoke(e.message.orEmpty())
        } catch (e: Exception) {
            android.util.Log.w("Fyx", "Sincronización de pestañas", e)
        }
    }

    private suspend fun run(force: Boolean = false): Int = mutex.withLock {
        val signedIn = withContext(Dispatchers.IO) { runCatching { ZenSync.email(context) }.getOrNull() } != null
        if (!signedIn) return 0
        lastRun = System.currentTimeMillis()
        ZenSync.syncTabs(context, store, closeTab, force, heldNow())
    }

    companion object {
        const val PUSH_DELAY_MS = 3_000L
        const val PULL_INTERVAL_MS = 5 * 60_000L
        /** Lo que dura el aviso con "Deshacer", y un poco más. */
        const val UNDO_HOLD_MS = 5_000L
    }
}
