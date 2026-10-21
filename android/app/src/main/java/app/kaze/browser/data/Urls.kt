/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import java.net.URI
import java.net.URLEncoder

object Urls {
    const val BLANK = "about:blank"
    /** Página de inicio de Kaze: se dibuja en Compose, sin cargar el motor (no gasta RAM de Gecko). */
    const val START = "kaze:inicio"

    private val SCHEMES = listOf(
        "http://", "https://", "about:", "file://", "data:", "view-source:", "moz-extension://", "resource://",
    )

    // dominio.tld, sub.dominio.tld/ruta, localhost:8080, 192.168.0.1
    private val HOST_LIKE = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([a-z0-9-]+\\.)+[a-z]{2,63})(:\\d{1,5})?(/.*)?$",
        RegexOption.IGNORE_CASE,
    )

    /** ¿Lo que se escribió en la barra es una dirección (true) o una búsqueda (false)? */
    fun looksLikeUrl(input: String): Boolean {
        val t = input.trim()
        if (t.isEmpty() || t.contains(' ')) return false
        if (SCHEMES.any { t.startsWith(it, ignoreCase = true) }) return true
        return HOST_LIKE.matches(t)
    }

    /** Convierte texto de la barra en URL: dirección directa o búsqueda con el motor elegido. */
    fun fromInput(input: String, engine: SearchEngine): String {
        val t = input.trim()
        if (t.isEmpty()) return BLANK
        if (SCHEMES.any { t.startsWith(it, ignoreCase = true) }) return t
        if (looksLikeUrl(t)) return "https://$t"
        return engine.searchUrl(t)
    }

    fun encode(q: String): String = URLEncoder.encode(q, "UTF-8")

    /** "https://www.ejemplo.com/a" → "ejemplo.com" */
    fun displayHost(url: String): String {
        if (url == START) return ""
        if (url.startsWith("about:")) return url
        val host = host(url) ?: return url.removePrefix("https://").removePrefix("http://").trimEnd('/')
        return host.removePrefix("www.")
    }

    fun host(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (_: Exception) {
        null
    }

    /** Si la URL es una búsqueda del motor, devuelve el texto buscado (para mostrarlo en la barra). */
    fun searchTermsOf(url: String, engine: SearchEngine): String? {
        val base = engine.searchUrl("").substringBefore('?')
        if (!url.startsWith(base)) return null
        val q = try {
            URI(url).rawQuery
        } catch (_: Exception) {
            null
        } ?: return null
        val param = engine.queryParam
        return q.split('&')
            .firstOrNull { it.startsWith("$param=") }
            ?.substringAfter('=')
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
    }
}

enum class SearchEngine(
    val label: String,
    private val template: String,
    /** Endpoint de sugerencias con formato OpenSearch JSON: ["consulta", ["sug1", "sug2"]] */
    val suggestTemplate: String?,
    val queryParam: String = "q",
) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
    GOOGLE("Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s", null, "query"),
    BING("Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?q=%s&type=list");

    fun searchUrl(query: String) = template.replace("%s", Urls.encode(query))
    fun suggestUrl(query: String) = suggestTemplate?.replace("%s", Urls.encode(query))
}
