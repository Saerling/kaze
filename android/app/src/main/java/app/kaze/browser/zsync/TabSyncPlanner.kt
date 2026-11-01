package app.kaze.browser.zsync

/**
 * Reconciliación de las fijadas y Essentials entre Fyx y Zen (en ambos sentidos), sin red ni UI:
 * se puede probar con datos de mentira.
 *
 * Se compara a tres bandas: lo que hay en el teléfono, lo que hay en la cuenta y la **base**
 * (cómo quedaron ambos en la última sincronización correcta). Así se sabe quién cambió qué:
 *  - solo cambió un lado → ese cambio pasa al otro;
 *  - cambiaron los dos → gana la cuenta (el escritorio) y el cambio del teléfono se descarta, pero
 *    nunca se borra nada: si un lado borró y el otro modificó, la pestaña se conserva.
 * Las pestañas normales no entran: Zen solo sincroniza fijadas, carpetas y Essentials.
 */
object TabSyncPlanner {
    /** Dónde está una pestaña: Essential o fijada en un espacio de Zen, y su carpeta (por nombre; "" = suelta). */
    data class State(val essential: Boolean, val space: String, val folder: String) {
        companion object {
            fun essential() = State(true, "", "")
            fun pinned(space: String, folder: String) = State(false, space, folder)
        }
    }

    data class Remote(val id: String, val state: State, val url: String, val title: String)

    /**
     * Una pestaña de Fyx. [state] es null si ya no es sincronizable (se desfijó o se movió a un espacio
     * que no está en Zen); [zenId] es su registro en la cuenta, si ya tiene.
     */
    data class Local(val tabId: String, val zenId: String?, val state: State?, val url: String)

    sealed class Op {
        /** Sin cambios: solo se anota en la base. */
        data class Keep(val zenId: String, val state: State) : Op()
        /** Se borró en el escritorio y aquí no se había tocado: se cierra. */
        data class CloseLocal(val tabId: String, val zenId: String) : Op()
        /** El escritorio la movió (o la convirtió en Essential): se aplica aquí. */
        data class ApplyLocal(val tabId: String, val zenId: String, val state: State) : Op()
        /** Nueva en el escritorio (o se restaura porque allí cambió mientras aquí se cerraba). */
        data class CreateLocal(val remote: Remote) : Op()
        /** Una pestaña de aquí y una de la cuenta son la misma (misma página): se enlazan. */
        data class Link(val tabId: String, val zenId: String, val state: State) : Op()
        /** Conflicto (el escritorio la borró y aquí se cambió): se suelta el enlace y se vuelve a subir. */
        data class Unlink(val tabId: String, val zenId: String) : Op()
        /** Se cerró o desfijó aquí y en el escritorio seguía igual: se borra de la cuenta. */
        data class DeleteRemote(val zenId: String, val tabId: String?) : Op()
        /** Se movió aquí (carpeta, espacio, Essential): se aplica en la cuenta. */
        data class UpdateRemote(val tabId: String, val zenId: String, val from: State, val to: State, val url: String) : Op()
        /** Nueva aquí: se sube. */
        data class CreateRemote(val tabId: String, val state: State, val url: String) : Op()
    }

    fun plan(
        base: Map<String, State>,
        remote: Map<String, Remote>,
        local: List<Local>,
        norm: (String) -> String = { it },
    ): List<Op> {
        val ops = mutableListOf<Op>()
        val byZen = local.filter { it.zenId != null }.associateBy { it.zenId!! }
        val handledTabs = HashSet<String>()

        // 1. Lo que ya estaba enlazado (en la base, en la cuenta o con id en el teléfono).
        val known = LinkedHashSet<String>().apply { addAll(base.keys); addAll(byZen.keys) }
        for (z in known) {
            val b = base[z]
            val r = remote[z]
            val l = byZen[z]
            val ls = l?.state
            if (l != null) handledTabs += l.tabId
            when {
                // Aquí ya no está (o ya no es fijada).
                ls == null -> when {
                    r == null -> Unit // borrada en los dos lados: se olvida
                    b != null && r.state == b -> {
                        // Se cerró o desfijó aquí y allá no cambió: se borra allá. La pestaña normal se queda aquí.
                        // Sin base (no se sabe si allá cambió) no se borra: se conserva como dice la cuenta.
                        ops += Op.DeleteRemote(z, l?.tabId)
                    }
                    // Allá cambió mientras aquí se quitaba: gana el escritorio, no se pierde nada.
                    l != null -> ops += Op.ApplyLocal(l.tabId, z, r.state)
                    else -> ops += Op.CreateLocal(r)
                }
                r == null -> when {
                    b == null -> ops += Op.Unlink(l!!.tabId, z).also { handledTabs -= l.tabId } // id que la cuenta no conoce: se vuelve a subir
                    ls == b -> ops += Op.CloseLocal(l!!.tabId, z)
                    else -> ops += Op.Unlink(l!!.tabId, z).also { handledTabs -= l.tabId }
                }
                else -> {
                    val lChanged = b != null && ls != b
                    val rChanged = b == null || r.state != b
                    when {
                        ls == r.state -> ops += Op.Keep(z, ls)
                        rChanged -> ops += Op.ApplyLocal(l!!.tabId, z, r.state)
                        lChanged -> ops += Op.UpdateRemote(l!!.tabId, z, b!!, ls, l.url)
                        else -> ops += Op.Keep(z, r.state)
                    }
                }
            }
        }

        // 2. Nuevas en la cuenta: se enlazan con la misma página si ya está aquí; si no, se crean.
        val free = local.filter { it.state != null && it.tabId !in handledTabs }.toMutableList()
        for (r in remote.values) {
            if (r.id in known) continue
            val match = free.firstOrNull { it.state!!.essential == r.state.essential && (r.state.essential || it.state.space == r.state.space) && norm(it.url) == norm(r.url) }
            if (match != null) {
                free.remove(match)
                handledTabs += match.tabId
                ops += Op.Link(match.tabId, r.id, r.state)
                if (match.state != r.state) ops += Op.ApplyLocal(match.tabId, r.id, r.state)
            } else {
                ops += Op.CreateLocal(r)
            }
        }

        // 3. Nuevas aquí (o que se soltaron por conflicto): se suben.
        free.filter { it.tabId !in handledTabs }.forEach { ops += Op.CreateRemote(it.tabId, it.state!!, it.url) }
        return ops
    }
}
