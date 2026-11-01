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

    /**
     * Misma página para enlazar una pestaña de aquí con una de la cuenta: la URL completa (con su
     * consulta: dos vídeos de YouTube son distintos), sin el ancla ni la barra final.
     */
    fun sameUrl(url: String): String = url.substringBefore('#').trimEnd('/')

    /** Solo se sincronizan páginas web: about:, file:, content:… se quedan en el teléfono. */
    fun syncable(url: String): Boolean = url.startsWith("https://", true) || url.startsWith("http://", true)

    /**
     * Resultado del freno: las operaciones que sí se hacen y las que quedaron frenadas esperando a que el
     * usuario las confirme. Lo demás (traer lo nuevo, subir lo no frenado) sigue funcionando.
     */
    data class Guarded(
        val ops: List<Op>,
        /** Ids de Zen de los cierres en el teléfono frenados (faltan en Zen). */
        val heldCloseIds: Set<String> = emptySet(),
        /** Ids de Zen de los borrados en Zen frenados (faltan en el teléfono). */
        val heldDeleteIds: Set<String> = emptySet(),
        /** Los cierres frenados son solo "muchos de golpe" (la cuenta trae lo de antes): los puede levantar [Stability]. */
        val closesAutoAcceptable: Boolean = false,
        /** Se pidió confirmar un conjunto que ya no es el que está frenado: no se aplicó nada de eso. */
        val confirmMismatch: Boolean = false,
    ) {
        val heldCloses: Int get() = heldCloseIds.size
        val heldDeletes: Int get() = heldDeleteIds.size
        val blocked: Boolean get() = heldCloseIds.isNotEmpty() || heldDeleteIds.isNotEmpty()
    }

    /**
     * Freno de seguridad sobre el plan, antes de tocar nada:
     *  - si hubo registros que no se pudieron descifrar, no se cierra ni se borra nada (lo que "falta"
     *    puede estar en ellos), ni se vuelven a subir las que parecen haber desaparecido. Nunca se salta;
     *  - cierres aquí: si la cuenta no trae ninguna de las ya sincronizadas (vacía o con otras), o se
     *    cerrarían más del 30 % (y más de 3), se frenan. Los "muchos de golpe" se pueden aceptar solos con
     *    [Stability] ([autoClose]); lo otro, solo con confirmación del usuario;
     *  - borrados en Zen: si se borrarían más del 30 % (y más de 3), o el teléfono se quedó sin ninguna
     *    teniendo más de ese límite, se frenan y solo los confirma el usuario.
     * El usuario confirma exactamente el conjunto que vio ([confirmedCloses], [confirmedDeletes]): si el que
     * está frenado ahora es otro, no se aplica nada de eso y se marca [Guarded.confirmMismatch].
     * Las retenidas ([held]: avisos de "Deshacer" a la vista) no cuentan.
     */
    fun guard(
        ops: List<Op>,
        base: Map<String, State>,
        remote: Map<String, Remote>,
        decryptFailures: Int,
        localLinked: Int = -1,
        held: Set<String> = emptySet(),
        autoClose: Boolean = false,
        confirmedCloses: Set<String>? = null,
        confirmedDeletes: Set<String>? = null,
    ): Guarded {
        var out = withoutHeld(ops, held)
        if (decryptFailures > 0) {
            val unlinked = out.filterIsInstance<Op.Unlink>().map { it.tabId }.toSet()
            out = out.filterNot {
                it is Op.CloseLocal || it is Op.DeleteRemote || it is Op.Unlink || (it is Op.CreateRemote && it.tabId in unlinked)
            }
        }
        val synced = base.keys.count { it !in held }
        if (synced == 0) return Guarded(out)
        val limit = maxOf(3, (synced * 0.3).toInt())

        val closeIds = out.filterIsInstance<Op.CloseLocal>().map { it.zenId }.toSet()
        // Ninguna de las ya sincronizadas sigue en la cuenta: tan sospechoso como una cuenta vacía.
        val noneLeft = remote.isEmpty() || closeIds.size >= synced
        var closesBlocked = closeIds.isNotEmpty() && (noneLeft || (closeIds.size > limit && !autoClose))
        val deleteIds = out.filterIsInstance<Op.DeleteRemote>().map { it.zenId }.toSet()
        var deletesBlocked = deleteIds.isNotEmpty() && (deleteIds.size > limit || (localLinked == 0 && synced > limit))

        var mismatch = false
        if (closesBlocked && confirmedCloses != null) {
            if (confirmedCloses == closeIds) closesBlocked = false else mismatch = true
        }
        if (deletesBlocked && confirmedDeletes != null) {
            if (confirmedDeletes == deleteIds) deletesBlocked = false else mismatch = true
        }

        if (closesBlocked) out = out.filterNot { it is Op.CloseLocal }
        if (deletesBlocked) out = out.filterNot { it is Op.DeleteRemote }
        return Guarded(
            ops = out,
            heldCloseIds = if (closesBlocked) closeIds else emptySet(),
            heldDeleteIds = if (deletesBlocked) deleteIds else emptySet(),
            closesAutoAcceptable = closesBlocked && !noneLeft,
            confirmMismatch = mismatch,
        )
    }

    /** Aviso para el usuario cuando el freno retuvo cambios. */
    fun explain(g: Guarded): String? {
        if (!g.blocked) return null
        val parts = buildList {
            if (g.heldCloses > 0) add("faltan en Zen ${g.heldCloses} fijadas que aquí no se cerraron")
            if (g.heldDeletes > 0) add("faltan aquí ${g.heldDeletes} fijadas que no se borraron en Zen")
        }
        return "Sincronización en pausa por seguridad: " + parts.joinToString(" y ") +
            ". Lo demás sigue sincronizándose. Revísalo en Ajustes › Cuenta y sincronización."
    }

    /**
     * Quita las operaciones de pestañas retenidas (cerradas o desfijadas con un aviso de "Deshacer" a la
     * vista): ni se borran en Zen ni se tocan; su base no cambia y se suben cuando vence la retención.
     */
    fun withoutHeld(ops: List<Op>, held: Set<String>): List<Op> = if (held.isEmpty()) ops else ops.filterNot {
        (it is Op.DeleteRemote && it.zenId in held) || (it is Op.UpdateRemote && it.zenId in held)
    }

    /**
     * Para que unos cierres "muchos de golpe" no queden frenados para siempre: si la misma situación (mismo
     * conjunto de ids en la cuenta y en el teléfono) se repite en una sincronización al menos un minuto
     * después, se aceptan. Solo vale para cierres en el teléfono (recuperables desde el escritorio); nunca
     * para borrar en Zen ni con la cuenta vacía.
     */
    object Stability {
        const val MIN_GAP_MS = 60_000L

        data class Seen(val fingerprint: String, val since: Long)

        fun fingerprint(remoteIds: Collection<String>, localIds: Collection<String>): String =
            (remoteIds.sorted().joinToString(",") + "|" + localIds.sorted().joinToString(",")).hashCode().toString(16) +
                ":" + remoteIds.size + ":" + localIds.size

        /** ¿Se acepta ahora? Y lo que hay que recordar para la próxima vez. */
        fun accept(previous: Seen?, fingerprint: String, now: Long): Pair<Boolean, Seen> =
            if (previous != null && previous.fingerprint == fingerprint) {
                (now - previous.since >= MIN_GAP_MS) to previous
            } else {
                false to Seen(fingerprint, now)
            }
    }

    fun plan(
        base: Map<String, State>,
        remote: Map<String, Remote>,
        local: List<Local>,
        norm: (String) -> String = ::sameUrl,
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
