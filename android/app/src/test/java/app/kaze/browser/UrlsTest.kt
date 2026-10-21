package app.kaze.browser

import app.kaze.browser.data.SearchEngine
import app.kaze.browser.data.Urls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlsTest {
    private val ddg = SearchEngine.DUCKDUCKGO

    @Test fun direcciones() {
        assertTrue(Urls.looksLikeUrl("wikipedia.org"))
        assertTrue(Urls.looksLikeUrl("es.wikipedia.org/wiki/Viento"))
        assertTrue(Urls.looksLikeUrl("localhost:8080"))
        assertTrue(Urls.looksLikeUrl("192.168.1.1"))
        assertTrue(Urls.looksLikeUrl("https://x.com"))
        assertFalse(Urls.looksLikeUrl("clima en bogota"))
        assertFalse(Urls.looksLikeUrl("hola"))
        assertFalse(Urls.looksLikeUrl("node.js tutorial"))
    }

    @Test fun desdeLaBarra() {
        assertEquals("https://github.com", Urls.fromInput("github.com", ddg))
        assertEquals("https://duckduckgo.com/?q=gatos+tiernos", Urls.fromInput("gatos tiernos", ddg))
        assertEquals("about:config", Urls.fromInput("about:config", ddg))
        assertEquals("https://www.google.com/search?q=hola", Urls.fromInput("hola", SearchEngine.GOOGLE))
    }

    @Test fun mostrar() {
        assertEquals("youtube.com", Urls.displayHost("https://www.youtube.com/watch?v=1"))
        assertEquals("gatos tiernos", Urls.searchTermsOf("https://duckduckgo.com/?q=gatos+tiernos&ia=web", ddg))
        assertEquals("cielo", Urls.searchTermsOf("https://www.startpage.com/sp/search?query=cielo", SearchEngine.STARTPAGE))
        assertEquals(null, Urls.searchTermsOf("https://github.com/?q=x", ddg))
    }
}
