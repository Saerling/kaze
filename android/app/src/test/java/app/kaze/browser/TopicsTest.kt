package app.kaze.browser

import app.kaze.browser.data.Tab
import app.kaze.browser.data.Topics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {
    @Test fun porDominio() {
        assertEquals(Topics.SHOPPING, Topics.of("https://www.amazon.com/dp/1", ""))
        assertEquals(Topics.SHOPPING, Topics.of("https://listado.mercadolibre.com.co/teclado", ""))
        assertEquals(Topics.VIDEO, Topics.of("https://m.youtube.com/watch?v=1", ""))
        assertEquals(Topics.MUSIC, Topics.of("https://music.youtube.com/", ""))
        assertEquals(Topics.MAIL, Topics.of("https://mail.google.com/mail/u/0/", ""))
        assertEquals(Topics.WORK, Topics.of("https://docs.google.com/document/d/1", ""))
        assertEquals(Topics.SOCIAL, Topics.of("https://x.com/home", ""))
        assertEquals(Topics.NEWS, Topics.of("https://www.bbc.com/mundo", ""))
        assertEquals(Topics.DEV, Topics.of("https://github.com/Saerling/kaze", ""))
        assertEquals(Topics.AI, Topics.of("https://claude.ai/new", ""))
        assertEquals(Topics.REFERENCE, Topics.of("https://es.wikipedia.org/wiki/Viento", ""))
    }

    @Test fun porTitulo() {
        assertEquals(Topics.SHOPPING, Topics.of("https://tienda-x.co/p/1", "Ofertas de la semana"))
        assertEquals(Topics.NEWS, Topics.of("https://diario-local.co/", "Últimas noticias de Bogotá"))
        assertEquals(Topics.AI, Topics.of("https://blog.ejemplo.com/", "Cómo usar la IA en el trabajo"))
        // Palabras completas: "ia" no coincide dentro de "Colombia".
        assertNull(Topics.of("https://viajes-ejemplo.co/", "Colombia hermosa"))
    }

    @Test fun filtrosYGrupos() {
        val tabs = listOf(
            Tab(spaceId = "s", url = "https://www.amazon.com/a", title = "A"),
            Tab(spaceId = "s", url = "https://www.ebay.com/b", title = "B"),
            Tab(spaceId = "s", url = "https://www.recetasgratis.net/1", title = "Uno"),
            Tab(spaceId = "s", url = "https://www.recetasgratis.net/2", title = "Dos"),
            Tab(spaceId = "s", url = "https://www.bbc.com/", title = "BBC", folder = "Lecturas"),
        )
        val f = Topics.filters(tabs)
        assertEquals("Lecturas", f.first().label)
        assertTrue(f.any { it.label == "Compras" && it.tabIds.size == 2 })
        assertTrue(f.any { it.label == "Recetasgratis" && it.tabIds.size == 2 })
        val g = Topics.groups(tabs)
        assertEquals(setOf("Compras", "Recetasgratis"), g.keys)
    }
}
