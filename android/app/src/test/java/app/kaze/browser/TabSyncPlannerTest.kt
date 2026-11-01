package app.kaze.browser

import app.kaze.browser.zsync.TabSyncPlanner
import app.kaze.browser.zsync.TabSyncPlanner.Local
import app.kaze.browser.zsync.TabSyncPlanner.Op
import app.kaze.browser.zsync.TabSyncPlanner.Remote
import app.kaze.browser.zsync.TabSyncPlanner.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test fun registrosIlegiblesNoCierranNiBorran() {
        val base = mapOf("z1" to raiz, "z2" to raiz)
        val remote = mapOf("z2" to Remote("z2", raiz, "https://b.com", "B"))
        val local = listOf(Local("t1", "z1", raiz, "https://a.com"), Local("t2", "z2", raiz, "https://b.com"))
        val ops = TabSyncPlanner.plan(base, remote, local)
        assertTrue(ops.any { it is Op.CloseLocal })
        val safe = TabSyncPlanner.guard(ops, base, remote, decryptFailures = 1)
        assertTrue(safe.none { it is Op.CloseLocal || it is Op.DeleteRemote || it is Op.Unlink || it is Op.CreateRemote })
    }

    @Test fun cuentaVaciaNoCierraNada() {
        val base = mapOf("z1" to raiz)
        val ops = TabSyncPlanner.plan(base, emptyMap(), listOf(Local("t1", "z1", raiz, "https://a.com")))
        assertThrows(TabSyncPlanner.Unsafe::class.java) { TabSyncPlanner.guard(ops, base, emptyMap(), 0) }
    }

    @Test fun cierreMasivoSeDetiene() {
        val base = (1..10).associate { "z$it" to raiz }
        val remote = (1..5).associate { "z$it" to Remote("z$it", raiz, "https://s$it.com", "") }
        val local = (1..10).map { Local("t$it", "z$it", raiz, "https://s$it.com") }
        val ops = TabSyncPlanner.plan(base, remote, local)
        assertEquals(5, ops.count { it is Op.CloseLocal })
        assertThrows(TabSyncPlanner.Unsafe::class.java) { TabSyncPlanner.guard(ops, base, remote, 0) }
        // Pocas (hasta 3) sí se aplican.
        val pocas = (1..8).associate { "z$it" to Remote("z$it", raiz, "https://s$it.com", "") }
        assertEquals(2, TabSyncPlanner.guard(TabSyncPlanner.plan(base, pocas, local), base, pocas, 0).count { it is Op.CloseLocal })
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
}
