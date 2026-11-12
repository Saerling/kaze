package app.kaze.browser

import app.kaze.browser.engine.ErrorPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.WebRequestError

class ErrorPagesTest {
    private fun page(url: String, code: Int, category: Int = WebRequestError.ERROR_CATEGORY_NETWORK): String {
        val uri = ErrorPages.dataUri(url, code, category)
        assertTrue(uri.startsWith("data:text/html;charset=utf-8;base64,"))
        return String(java.util.Base64.getDecoder().decode(uri.substringAfter("base64,")), Charsets.UTF_8)
    }

    @Test fun sinConexionOfreceReintentar() {
        val html = page("https://www.ejemplo.com/a?b=1", WebRequestError.ERROR_OFFLINE)
        assertTrue(html.contains("Sin conexión"))
        assertTrue(html.contains("""href="https://www.ejemplo.com/a?b=1""""))
        assertTrue(html.contains("ejemplo.com"))
    }

    @Test fun sitioPeligrosoNoOfreceReintentar() {
        val html = page("https://malo.example/", WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI, WebRequestError.ERROR_CATEGORY_SAFEBROWSING)
        assertTrue(html.contains("Sitio engañoso"))
        assertFalse(html.contains("Reintentar"))
    }

    @Test fun laDireccionNoPuedeInyectarHtml() {
        val html = page("https://x.example/\"><script>alert(1)</script>", WebRequestError.ERROR_NET_TIMEOUT)
        assertFalse(html.contains("<script>"))
    }

    @Test fun soloSeReintentanDireccionesWeb() {
        assertFalse(page("javascript:alert(1)", WebRequestError.ERROR_UNKNOWN).contains("Reintentar"))
        assertFalse(page("intent://x#Intent;end", WebRequestError.ERROR_UNKNOWN_PROTOCOL).contains("href="))
    }

    @Test fun errorDesconocidoDeSeguridadSeMarcaComoInseguro() {
        assertEquals("La conexión no es segura", ErrorPages.info(-999, WebRequestError.ERROR_CATEGORY_SECURITY, "x").title)
        assertEquals("No se pudo abrir la página", ErrorPages.info(-999, WebRequestError.ERROR_CATEGORY_UNKNOWN, "x").title)
    }
}
