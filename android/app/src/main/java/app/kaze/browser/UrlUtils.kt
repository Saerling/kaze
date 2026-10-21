/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.net.Uri
import android.util.Patterns

object UrlUtils {
    const val HOME = "https://duckduckgo.com/"
    private const val SEARCH = "https://duckduckgo.com/?q="

    private val ESQUEMAS = listOf("http://", "https://", "about:", "file://", "data:", "view-source:")

    /** Convierte lo que se escribe en la barra en una URL: dirección directa o búsqueda. */
    fun toUrl(input: String): String {
        val texto = input.trim()
        if (texto.isEmpty()) return HOME
        if (ESQUEMAS.any { texto.startsWith(it, ignoreCase = true) }) return texto
        if (!texto.contains(' ') && Patterns.WEB_URL.matcher(texto).matches()) return "https://$texto"
        return SEARCH + Uri.encode(texto)
    }

    /** Versión corta para mostrar en la barra: sin esquema ni "www.". */
    fun display(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}
