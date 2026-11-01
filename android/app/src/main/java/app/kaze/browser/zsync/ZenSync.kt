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
    }

    fun signOut(context: Context) {
        AccountStore.clear(context)
        prefs(context).edit().remove("base").apply()
    }

    fun lastSync(context: Context): Long = prefs(context).getLong("last", 0)

    private data class Pending(val op: TabSyncPlanner.Op, val title: String)

    /**
     * Sincroniza en ambos sentidos las fijadas, sus carpetas y las Essentials con Zen.
     * Lee la cuenta, compara con lo que hay aquí y con la última sincronización ([TabSyncPlanner]),
     * aplica aquí lo que cambió allá y sube lo que cambió aquí con escrituras condicionales.
     * Devuelve cuántas pestañas nuevas llegaron del escritorio. Las pestañas normales no viajan.
     */
    suspend fun syncTabs(context: Context, store: Store, closeTab: (Tab) -> Unit): Int {
        val snap = SpacesSyncService.refresh(context)
        prefs(context).edit().putLong("last", System.currentTimeMillis()).apply()
        val readOnly = AccountStore.isDemo(context)

        // Lo que hay en la cuenta. Las carpetas se comparan por nombre (así las ve Fyx); las subcarpetas
        // se aplanan en su carpeta principal y las carpetas "en vivo" (RSS…) no se tocan.
        val remote = LinkedHashMap<String, TabSyncPlanner.Remote>()
        val folderIds = HashMap<Pair<String, String>, String>()
        fun put(t: ZenSpaces.ZenTab, state: TabSyncPlanner.State) {
            if (t.url.startsWith("http") && t.id !in remote) remote[t.id] = TabSyncPlanner.Remote(t.id, state, t.url, t.displayTitle)
        }
        fun all(f: ZenSpaces.ZenFolder): List<ZenSpaces.ZenTab> = f.tabs + f.subfolders.orEmpty().flatMap { all(it) }
        snap.spaces.forEach { zs ->
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

        val base = loadBase(context)
        var added = 0
        val pending = mutableListOf<Pending>()
        withContext(Dispatchers.Main) {
            quiet(store) {
                // Espacios: el enlazado, o el del mismo nombre, o uno nuevo con el ícono y colores de Zen.
                val zenToFyx = LinkedHashMap<String, Space>()
                snap.spaces.forEach { zs ->
                    val space = store.spaces.firstOrNull { it.zenId == zs.id }
                        ?: store.spaces.firstOrNull { it.zenId == null && it.name.equals(zs.name, true) }
                        ?: store.addSpace(zs.name, zs.icon?.takeIf { it.isNotBlank() && it.length <= 4 } ?: "🖥️", zs.themeColors.mapNotNull { parse(it) }.ifEmpty { listOf(0xFF6B5BFF.toInt()) })
                    if (space.zenId != zs.id) { space.zenId = zs.id; store.updateSpace(space) }
                    zenToFyx[zs.id] = space
                }
                val firstSpace = zenToFyx.values.firstOrNull()
                val profile = firstSpace?.profileId ?: Profile.DEFAULT_ID
                tidy(store)

                fun stateOf(t: Tab): TabSyncPlanner.State? = when (t.kind) {
                    TabKind.ESSENTIAL -> TabSyncPlanner.State.essential()
                    TabKind.PINNED -> store.spaceById(t.spaceId)?.zenId?.let { TabSyncPlanner.State.pinned(it, t.folder) }
                    TabKind.NORMAL -> null
                }
                val locals = store.tabs.filter { !it.isPrivate && (it.zenId != null || store.spaceById(it.spaceId)?.profileId == profile) }
                    .map { TabSyncPlanner.Local(it.id, it.zenId, stateOf(it), it.pinnedUrl ?: it.url) }
                    .filter { it.state != null || it.zenId != null }
                val ops = TabSyncPlanner.plan(base, remote, locals, ::norm)

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
                for (op in ops) when (op) {
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
                    is TabSyncPlanner.Op.DeleteRemote -> pending += Pending(op, "")
                    is TabSyncPlanner.Op.UpdateRemote -> pending += Pending(op, store.tabById(op.tabId)?.displayTitle.orEmpty())
                    is TabSyncPlanner.Op.CreateRemote -> pending += Pending(op, store.tabById(op.tabId)?.displayTitle.orEmpty())
                }
            }
        }

        // Subida. Cada cambio va por separado: si uno falla, su base no cambia y se reintenta la próxima vez.
        val links = mutableListOf<Pair<String, String?>>() // pestaña de Fyx → id nuevo de Zen (null = soltar)
        if (pending.isNotEmpty() && !readOnly) AccountStore.withClient(context) { client ->
            upload(client, context, pending, remote, folderIds, base, links, snap.spaces.firstOrNull()?.id)
        }

        withContext(Dispatchers.Main) {
            quiet(store) { links.forEach { (tabId, zenId) -> store.tabById(tabId)?.let { store.link(it, zenId) } } }
        }
        saveBase(context, base)
        return added
    }

    private suspend fun upload(
        client: SyncClient,
        context: Context,
        pending: List<Pending>,
        remote: Map<String, TabSyncPlanner.Remote>,
        folderIds: MutableMap<Pair<String, String>, String>,
        base: MutableMap<String, TabSyncPlanner.State>,
        links: MutableList<Pair<String, String?>>,
        firstZenSpace: String?,
    ) = withContext(Dispatchers.IO) {
        fun folderFor(space: String, name: String): String? =
            if (name.isEmpty()) null else folderIds.getOrPut(space to name) { SpacesSyncService.createFolder(client, space, name) }
        val emptied = HashSet<String>()
        fun leaving(st: TabSyncPlanner.State?) {
            if (st != null && !st.essential && st.folder.isNotEmpty()) folderIds[st.space to st.folder]?.let(emptied::add)
        }
        val done = HashSet<String>()

        // Renombrar un grupo aquí = renombrar la carpeta allá (no crear otra y vaciar la vieja). Pasa cuando
        // todas sus pestañas de la cuenta se movieron juntas a un nombre que allá todavía no existe.
        pending.mapNotNull { it.op as? TabSyncPlanner.Op.UpdateRemote }
            .filter {
                !it.from.essential && !it.to.essential && it.from.space == it.to.space &&
                    it.from.folder.isNotEmpty() && it.to.folder.isNotEmpty() && it.from.folder != it.to.folder
            }
            .groupBy { it.from.space to it.from.folder }
            .forEach { (key, group) ->
                val newName = group.map { it.to.folder }.distinct().singleOrNull() ?: return@forEach
                val folderId = folderIds[key] ?: return@forEach
                val members = remote.values.filter { it.state == TabSyncPlanner.State.pinned(key.first, key.second) }.map { it.id }.toSet()
                if (folderIds.containsKey(key.first to newName) || members != group.map { it.zenId }.toSet()) return@forEach
                if (runCatching { SpacesSyncService.renameFolder(client, folderId, newName) }.getOrDefault(false)) {
                    folderIds.remove(key)
                    folderIds[key.first to newName] = folderId
                    group.forEach { base[it.zenId] = it.to; done += it.zenId }
                }
            }

        for ((op, title) in pending) {
            runCatching {
                when (op) {
                    is TabSyncPlanner.Op.DeleteRemote -> {
                        leaving(base[op.zenId])
                        SpacesSyncService.deleteTab(client, context, op.zenId)
                        base.remove(op.zenId)
                        op.tabId?.let { links += it to null }
                    }
                    is TabSyncPlanner.Op.UpdateRemote -> if (op.zenId !in done) {
                        val from = op.from
                        val to = op.to
                        leaving(from)
                        when {
                            to.essential -> SpacesSyncService.setEssential(client, op.zenId, true, null)
                            from.essential -> {
                                SpacesSyncService.setEssential(client, op.zenId, false, to.space)
                                folderFor(to.space, to.folder)?.let { SpacesSyncService.moveTab(client, op.zenId, to.space, it) }
                            }
                            from.space != to.space -> {
                                // Zen no mueve fijadas entre espacios: se crea en el nuevo y luego se quita del viejo.
                                val id = SpacesSyncService.addTab(client, op.url, title.ifBlank { op.url }, to.space, folderFor(to.space, to.folder), SaveKind.PINNED).recordId
                                links += op.tabId to id
                                base[id] = to
                                // Si el borrado falla, la base vieja se queda y el próximo intento la borra.
                                SpacesSyncService.deleteTab(client, context, op.zenId)
                                base.remove(op.zenId)
                            }
                            else -> SpacesSyncService.moveTab(client, op.zenId, to.space, folderFor(to.space, to.folder))
                        }
                        if (from.space == to.space || to.essential || from.essential) base[op.zenId] = to
                    }
                    is TabSyncPlanner.Op.CreateRemote -> {
                        val st = op.state
                        val name = title.ifBlank { op.url }
                        val id = if (st.essential) {
                            SpacesSyncService.addEssential(client, op.url, name, firstZenSpace)
                        } else {
                            SpacesSyncService.addTab(client, op.url, name, st.space, folderFor(st.space, st.folder), SaveKind.PINNED).recordId
                        }
                        links += op.tabId to id
                        base[id] = st
                    }
                    else -> Unit
                }
            }.onFailure { android.util.Log.w("Fyx", "Sincronización de pestañas (${op.javaClass.simpleName})", it) }
        }

        // Carpetas que se quedaron vacías por lo que se movió aquí: se quitan, solo si de verdad están vacías.
        emptied.forEach { runCatching { SpacesSyncService.deleteFolderIfEmpty(client, it) } }
    }

    /** Lo que hace la sincronización en el Store no cuenta como cambio del usuario (no se vuelve a subir). */
    private inline fun quiet(store: Store, block: () -> Unit) {
        val listener = store.onSyncChange
        store.onSyncChange = null
        try { block() } finally { store.onSyncChange = listener }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("zsync", Context.MODE_PRIVATE)

    /** Base de la última sincronización correcta: id de Zen → dónde estaba la pestaña. */
    private fun loadBase(context: Context): MutableMap<String, TabSyncPlanner.State> {
        val out = HashMap<String, TabSyncPlanner.State>()
        val raw = prefs(context).getString("base", null) ?: return out
        runCatching {
            val o = JSONObject(raw)
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                out[k] = TabSyncPlanner.State(a.getBoolean(0), a.getString(1), a.getString(2))
            }
        }
        return out
    }

    private fun saveBase(context: Context, base: Map<String, TabSyncPlanner.State>) {
        val o = JSONObject()
        base.forEach { (k, s) -> o.put(k, JSONArray().put(s.essential).put(s.space).put(s.folder)) }
        prefs(context).edit().putString("base", o.toString()).apply()
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

    /** Misma página aunque cambie la consulta o el ancla (evita duplicados como varias bandejas de Gmail). */
    internal fun norm(url: String) = url.substringBefore('#').substringBefore('?').trimEnd('/').removePrefix("https://").removePrefix("http://").removePrefix("www.")

    /**
     * Arregla lo importado por versiones anteriores (sin enlace con la cuenta): Essentials o fijadas
     * repetidas → una sola. Las enlazadas nunca se tocan aquí.
     */
    private fun tidy(store: Store) {
        val seen = store.tabs.filter { it.kind == TabKind.ESSENTIAL && it.zenId != null }.map { norm(it.pinnedUrl ?: it.url) }.toHashSet()
        store.tabs.filter { it.kind == TabKind.ESSENTIAL && it.zenId == null && !it.isPrivate }.toList()
            .forEach { if (!seen.add(norm(it.pinnedUrl ?: it.url)) && it.id != store.selectedTabId) store.close(it) }
        store.spaces.forEach { sp ->
            val pinned = store.tabs.filter { it.spaceId == sp.id && it.kind == TabKind.PINNED }
            val have = pinned.filter { it.zenId != null }.map { norm(it.pinnedUrl ?: it.url) }.toHashSet()
            pinned.filter { it.zenId == null }
                .forEach { if (!have.add(norm(it.pinnedUrl ?: it.url)) && it.id != store.selectedTabId) store.close(it) }
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

    /** Avisa cuando llegan pestañas nuevas del escritorio (para mostrar un aviso). */
    var onNews: ((Int) -> Unit)? = null

    /** Algo cambió en el teléfono: se sube en unos segundos (si llegan más cambios, se espera otra vez). */
    fun onLocalChange() {
        pendingPush?.cancel()
        pendingPush = scope.launch {
            delay(PUSH_DELAY_MS)
            runQuietly()
        }
    }

    /** La app se abrió o volvió al primer plano. */
    fun onForeground() {
        if (lastRun != 0L && System.currentTimeMillis() - lastRun < PULL_INTERVAL_MS) return
        scope.launch { runQuietly() }
    }

    /** "Sincronizar" en Ajustes: ahora mismo, y con el error a la vista. */
    suspend fun syncNow(): Int = run()

    private suspend fun runQuietly() {
        runCatching { run() }
            .onSuccess { if (it > 0) onNews?.invoke(it) }
            .onFailure { android.util.Log.w("Fyx", "Sincronización de pestañas", it) }
    }

    private suspend fun run(): Int = mutex.withLock {
        val signedIn = withContext(Dispatchers.IO) { runCatching { ZenSync.email(context) }.getOrNull() } != null
        if (!signedIn) return 0
        lastRun = System.currentTimeMillis()
        ZenSync.syncTabs(context, store, closeTab)
    }

    companion object {
        const val PUSH_DELAY_MS = 3_000L
        const val PULL_INTERVAL_MS = 5 * 60_000L
    }
}
