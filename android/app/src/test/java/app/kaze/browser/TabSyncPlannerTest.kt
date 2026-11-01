package app.kaze.browser

import app.kaze.browser.zsync.TabSyncPlanner
import app.kaze.browser.zsync.TabSyncPlanner.Local
import app.kaze.browser.zsync.TabSyncPlanner.Op
import app.kaze.browser.zsync.TabSyncPlanner.Remote
import app.kaze.browser.zsync.TabSyncPlanner.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reconciliación de fijadas y Essentials con Zen: quién cambió qué y que nunca se pierdan pestañas. */
class TabSyncPlannerTest {
    private val raiz = State.pinned("s1", "")
    private val trabajo = State.pinned("s1", "Trabajo")

    private fun plan(base: Map<String, State>, remote: List<Remote>, local: List<Local>) =
        TabSyncPlanner.plan(base, remote.associateBy { it.id }, local)

    @Test fun nuevaEnElEscritorioSeCreaAqui() {
        val ops = plan(emptyMap(), listOf(Remote("z1", trabajo, "https://a.com", "A")), emptyList())
        assertEquals(listOf(Op.CreateLocal(Remote("z1", trabajo, "https://a.com", "A"))), ops)
    }

    @Test fun mismaPaginaSeEnlazaSinDuplicar() {
        val ops = plan(emptyMap(), listOf(Remote("z1", raiz, "https://a.com", "A")), listOf(Local("t1", null, raiz, "https://a.com")))
        assertEquals(listOf(Op.Link("t1", "z1", raiz)), ops)
    }

    @Test fun nuevaAquiSeSube() {
        val ops = plan(emptyMap(), emptyList(), listOf(Local("t1", null, trabajo, "https://a.com")))
        assertEquals(listOf(Op.CreateRemote("t1", trabajo, "https://a.com")), ops)
    }

    @Test fun movidaAquiSeSube() {
        val ops = plan(mapOf("z1" to raiz), listOf(Remote("z1", raiz, "https://a.com", "A")), listOf(Local("t1", "z1", trabajo, "https://a.com")))
        assertEquals(listOf(Op.UpdateRemote("t1", "z1", raiz, trabajo, "https://a.com")), ops)
    }

    @Test fun movidaAllaSeAplicaAqui() {
        val ops = plan(mapOf("z1" to raiz), listOf(Remote("z1", trabajo, "https://a.com", "A")), listOf(Local("t1", "z1", raiz, "https://a.com")))
        assertEquals(listOf(Op.ApplyLocal("t1", "z1", trabajo)), ops)
    }

    @Test fun conflictoGanaElEscritorio() {
        val otra = State.pinned("s1", "Ocio")
        val ops = plan(mapOf("z1" to raiz), listOf(Remote("z1", trabajo, "https://a.com", "A")), listOf(Local("t1", "z1", otra, "https://a.com")))
        assertEquals(listOf(Op.ApplyLocal("t1", "z1", trabajo)), ops)
    }

    @Test fun cerradaAquiSeBorraAlla() {
        val ops = plan(mapOf("z1" to raiz), listOf(Remote("z1", raiz, "https://a.com", "A")), emptyList())
        assertEquals(listOf(Op.DeleteRemote("z1", null)), ops)
    }

    @Test fun desfijadaAquiSeBorraAllaPeroSeQuedaAqui() {
        val ops = plan(mapOf("z1" to raiz), listOf(Remote("z1", raiz, "https://a.com", "A")), listOf(Local("t1", "z1", null, "https://a.com")))
        assertEquals(listOf(Op.DeleteRemote("z1", "t1")), ops)
    }

    @Test fun borradaAllaSeCierraAqui() {
        val ops = plan(mapOf("z1" to raiz), emptyList(), listOf(Local("t1", "z1", raiz, "https://a.com")))
        assertEquals(listOf(Op.CloseLocal("t1", "z1")), ops)
    }

    @Test fun borradaAllaYMovidaAquiNoSePierde() {
        val ops = plan(mapOf("z1" to raiz), emptyList(), listOf(Local("t1", "z1", trabajo, "https://a.com")))
        assertEquals(listOf(Op.Unlink("t1", "z1"), Op.CreateRemote("t1", trabajo, "https://a.com")), ops)
    }

    @Test fun cerradaAquiYMovidaAllaSeRestaura() {
        val r = Remote("z1", trabajo, "https://a.com", "A")
        val ops = plan(mapOf("z1" to raiz), listOf(r), emptyList())
        assertEquals(listOf(Op.CreateLocal(r)), ops)
    }

    @Test fun sinBaseNoSeBorraNada() {
        // Hay enlace pero no base (p. ej. se volvió a iniciar sesión): ante la duda, gana lo que dice la cuenta.
        val ops = plan(emptyMap(), listOf(Remote("z1", raiz, "https://a.com", "A")), listOf(Local("t1", "z1", null, "https://a.com")))
        assertEquals(listOf(Op.ApplyLocal("t1", "z1", raiz)), ops)
        assertTrue(ops.none { it is Op.DeleteRemote || it is Op.CloseLocal })
    }

    @Test fun essentialAquiSeSube() {
        val ops = plan(mapOf("z1" to raiz), listOf(Remote("z1", raiz, "https://a.com", "A")), listOf(Local("t1", "z1", State.essential(), "https://a.com")))
        assertEquals(listOf(Op.UpdateRemote("t1", "z1", raiz, State.essential(), "https://a.com")), ops)
    }

    @Test fun sinCambiosSoloSeAnota() {
        val ops = plan(mapOf("z1" to trabajo), listOf(Remote("z1", trabajo, "https://a.com", "A")), listOf(Local("t1", "z1", trabajo, "https://a.com")))
        assertEquals(listOf(Op.Keep("z1", trabajo)), ops)
    }

    @Test fun soloPaginasWebSeSincronizan() {
        assertTrue(TabSyncPlanner.syncable("https://a.com/"))
        assertTrue(TabSyncPlanner.syncable("http://a.com/"))
        assertFalse(TabSyncPlanner.syncable("about:blank"))
        assertFalse(TabSyncPlanner.syncable("file:///sdcard/a.html"))
        assertFalse(TabSyncPlanner.syncable("content://media/1"))
        assertFalse(TabSyncPlanner.syncable("httpfoo"))
    }

    @Test fun enlazarPorDireccionCompleta() {
        // Dos vídeos distintos de YouTube no son la misma página; el ancla y la barra final sí dan igual.
        val r = Remote("z1", raiz, "https://youtube.com/watch?v=B", "B")
        val ops = plan(emptyMap(), listOf(r), listOf(Local("t1", null, raiz, "https://youtube.com/watch?v=A")))
        assertEquals(listOf(Op.CreateLocal(r), Op.CreateRemote("t1", raiz, "https://youtube.com/watch?v=A")), ops)
        val same = plan(emptyMap(), listOf(Remote("z1", raiz, "https://a.com/x/", "A")), listOf(Local("t1", null, raiz, "https://a.com/x#top")))
        assertEquals(listOf(Op.Link("t1", "z1", raiz)), same)
    }

    @Test fun registrosIlegiblesNoCierranNiBorran() {
        val base = mapOf("z1" to raiz, "z2" to raiz)
        val remote = mapOf("z2" to Remote("z2", raiz, "https://b.com", "B"))
        val local = listOf(Local("t1", "z1", raiz, "https://a.com"), Local("t2", "z2", raiz, "https://b.com"))
        val ops = TabSyncPlanner.plan(base, remote, local)
        assertTrue(ops.any { it is Op.CloseLocal })
        // Ni confirmándolo: lo que "falta" puede estar en los registros ilegibles.
        val safe = TabSyncPlanner.guard(ops, base, remote, decryptFailures = 1, confirmClose = true, confirmDelete = true).ops
        assertTrue(safe.none { it is Op.CloseLocal || it is Op.DeleteRemote || it is Op.Unlink || it is Op.CreateRemote })
    }

    @Test fun cuentaVaciaSoloConConfirmacion() {
        val base = mapOf("z1" to raiz)
        val local = listOf(Local("t1", "z1", raiz, "https://a.com"), Local("t2", null, raiz, "https://n.com"))
        val ops = TabSyncPlanner.plan(base, emptyMap(), local)
        val g = TabSyncPlanner.guard(ops, base, emptyMap(), 0, localLinked = 1)
        assertEquals(1, g.heldCloses)
        assertFalse(g.closesAutoAcceptable) // la estabilidad no lo levanta
        assertTrue(g.ops.none { it is Op.CloseLocal })
        // El resto sigue: la nueva de aquí se sube igual.
        assertTrue(g.ops.any { it is Op.CreateRemote })
        assertTrue(TabSyncPlanner.guard(ops, base, emptyMap(), 0, localLinked = 1, autoClose = true).ops.none { it is Op.CloseLocal })
        assertEquals(1, TabSyncPlanner.guard(ops, base, emptyMap(), 0, localLinked = 1, confirmClose = true).ops.count { it is Op.CloseLocal })
    }

    @Test fun cierreMasivoSeFrenaYLaEstabilidadLoLevanta() {
        val base = (1..10).associate { "z$it" to raiz }
        val remote = (1..5).associate { "z$it" to Remote("z$it", raiz, "https://s$it.com", "") }
        val local = (1..10).map { Local("t$it", "z$it", raiz, "https://s$it.com") }
        val ops = TabSyncPlanner.plan(base, remote, local)
        val g = TabSyncPlanner.guard(ops, base, remote, 0, localLinked = 10)
        assertEquals(5, g.heldCloses)
        assertTrue(g.closesAutoAcceptable)
        assertEquals(5, TabSyncPlanner.guard(ops, base, remote, 0, localLinked = 10, autoClose = true).ops.count { it is Op.CloseLocal })
        // Pocas (hasta 3) se aplican sin más.
        val pocas = (1..8).associate { "z$it" to Remote("z$it", raiz, "https://s$it.com", "") }
        val g2 = TabSyncPlanner.guard(TabSyncPlanner.plan(base, pocas, local), base, pocas, 0, localLinked = 10)
        assertEquals(2, g2.ops.count { it is Op.CloseLocal })
        assertFalse(g2.blocked)
        // Estabilidad: la misma situación un minuto después.
        val st = TabSyncPlanner.Stability
        val fp = st.fingerprint(remote.keys, base.keys)
        val (a1, seen) = st.accept(null, fp, 1_000)
        assertFalse(a1)
        assertFalse(st.accept(seen, fp, 30_000).first)
        assertTrue(st.accept(seen, fp, 61_000).first)
        assertFalse(st.accept(seen, st.fingerprint(listOf("z1"), base.keys), 200_000).first)
    }

    @Test fun borradoMasivoEnZenSoloConConfirmacion() {
        val base = (1..10).associate { "z$it" to raiz }
        val remote = base.keys.associateWith { Remote(it, raiz, "https://$it.com", "") }
        // El teléfono perdió 5 de 10 (p. ej. se borró su base de datos): no se borran en Zen.
        val local = (1..5).map { Local("t$it", "z$it", raiz, "https://z$it.com") }
        val ops = TabSyncPlanner.plan(base, remote, local)
        val g = TabSyncPlanner.guard(ops, base, remote, 0, localLinked = 5, autoClose = true)
        assertEquals(5, g.heldDeletes)
        assertTrue(g.ops.none { it is Op.DeleteRemote })
        assertEquals(5, TabSyncPlanner.guard(ops, base, remote, 0, localLinked = 5, confirmDelete = true).ops.count { it is Op.DeleteRemote })
        // Sin ninguna aquí y más del límite: frenado aunque fueran pocas por vez.
        val none = TabSyncPlanner.plan(base, remote, emptyList())
        assertEquals(10, TabSyncPlanner.guard(none, base, remote, 0, localLinked = 0).heldDeletes)
    }

    @Test fun cerrarTuUnicaFijadaNoFrena() {
        val base = mapOf("z1" to raiz)
        val remote = mapOf("z1" to Remote("z1", raiz, "https://a.com", ""))
        val ops = TabSyncPlanner.plan(base, remote, emptyList())
        val g = TabSyncPlanner.guard(ops, base, remote, 0, localLinked = 0)
        assertFalse(g.blocked)
        assertEquals(listOf(Op.DeleteRemote("z1", null)), g.ops)
        // Y las retenidas (aviso de "Deshacer" a la vista) no cuentan ni se suben.
        val base4 = (1..4).associate { "z$it" to raiz }
        val remote4 = base4.keys.associateWith { Remote(it, raiz, "https://$it.com", "") }
        val g4 = TabSyncPlanner.guard(TabSyncPlanner.plan(base4, remote4, emptyList()), base4, remote4, 0, localLinked = 0, held = setOf("z1"))
        assertFalse(g4.blocked)
        assertEquals(3, g4.ops.count { it is Op.DeleteRemote })
    }

    @Test fun lasRetenidasNoSeSuben() {
        val ops = listOf(Op.DeleteRemote("z1", "t1"), Op.UpdateRemote("t2", "z2", raiz, trabajo, "https://b.com"), Op.DeleteRemote("z3", null))
        assertEquals(listOf(Op.DeleteRemote("z3", null)), TabSyncPlanner.withoutHeld(ops, setOf("z1", "z2")))
        assertEquals(ops, TabSyncPlanner.withoutHeld(ops, emptySet()))
    }
}
