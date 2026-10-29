/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.zsync

import android.content.Context
import app.kaze.browser.data.Store
import app.kaze.browser.data.TabKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puente entre Fyx y la sincronización de Zen (Firefox Sync), basado en Z-Sync.
 * Fyx de escritorio (Zen) sube sus espacios, pestañas y Essentials a tu cuenta de Mozilla;
 * aquí se leen y se traen a los espacios de Fyx (sin duplicar lo que ya está abierto).
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

    fun signOut(context: Context) = AccountStore.clear(context)

    fun lastSync(context: Context): Long = context.getSharedPreferences("zsync", Context.MODE_PRIVATE).getLong("last", 0)

    /** Trae los espacios del escritorio. Devuelve cuántas pestañas nuevas llegaron. */
    suspend fun pull(context: Context, store: Store): Int {
        val snap = SpacesSyncService.refresh(context)
        context.getSharedPreferences("zsync", Context.MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).apply()
        return withContext(Dispatchers.Main) {
            var added = 0
            tidy(store)
            snap.spaces.forEach { zs ->
                val space = store.spaces.firstOrNull { it.name.equals(zs.name, true) }
                    ?: store.addSpace(zs.name, zs.icon?.takeIf { it.isNotBlank() && it.length <= 4 } ?: "🖥️", zs.themeColors.mapNotNull { parse(it) }.ifEmpty { listOf(0xFF6B5BFF.toInt()) })
                val have = store.tabs.filter { it.spaceId == space.id }.map { norm(it.url) }.toHashSet()
                fun add(url: String, kind: TabKind, folder: String = "") {
                    if (url.isBlank() || !url.startsWith("http") || !have.add(norm(url))) return
                    val t = store.newTab(url, spaceId = space.id, select = false)
                    if (kind != TabKind.NORMAL) store.setKind(t, kind)
                    if (folder.isNotEmpty()) store.setFolder(listOf(t), folder)
                    added++
                }
                fun items(list: List<ZenSpaces.ZenItem>, kind: TabKind) = list.forEach { item ->
                    when (item) {
                        is ZenSpaces.ZenItem.Tab -> add(item.tab.url, kind)
                        is ZenSpaces.ZenItem.Folder -> item.folder.tabs.forEach { add(it.url, TabKind.NORMAL, item.folder.name.ifBlank { "Carpeta" }) }
                        is ZenSpaces.ZenItem.Split -> item.split.tabs.forEach { add(it.url, kind) }
                    }
                }
                items(zs.pinned, TabKind.PINNED)
                items(zs.tabs, TabKind.NORMAL)
            }
            val first = store.spaces.firstOrNull()
            val ess = store.tabs.filter { it.kind == TabKind.ESSENTIAL }.map { norm(it.url) }.toHashSet()
            if (first != null) snap.essentials.values.flatten().forEach { e ->
                if (e.url.startsWith("http") && ess.add(norm(e.url))) {
                    store.setKind(store.newTab(e.url, spaceId = first.id, select = false), TabKind.ESSENTIAL); added++
                }
            }
            added
        }
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
    private fun norm(url: String) = url.substringBefore('#').substringBefore('?').trimEnd('/').removePrefix("https://").removePrefix("http://").removePrefix("www.")

    /** Arregla lo importado antes: fijadas que eran de una carpeta → a la carpeta; Essentials repetidos → uno solo. */
    private fun tidy(store: Store) {
        store.tabs.filter { it.kind == TabKind.PINNED && it.folder.isNotEmpty() }.forEach { store.setKind(it, TabKind.NORMAL) }
        val seen = HashSet<String>()
        store.tabs.filter { it.kind == TabKind.ESSENTIAL }.toList().forEach { if (!seen.add(norm(it.url))) store.close(it) }
        store.spaces.forEach { sp ->
            val s2 = HashSet<String>()
            store.tabs.filter { it.spaceId == sp.id && it.kind == TabKind.PINNED }.toList().forEach { if (!s2.add(norm(it.url))) store.close(it) }
        }
    }

    private fun parse(c: String): Int? = runCatching { android.graphics.Color.parseColor(c.trim()) }.getOrNull()
}
