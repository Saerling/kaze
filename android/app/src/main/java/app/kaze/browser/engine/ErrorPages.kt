/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import app.kaze.browser.data.Urls
import org.mozilla.geckoview.WebRequestError

/**
 * Páginas de error propias (sin conexión, sitio no encontrado, certificado inválido...).
 *
 * GeckoView no trae páginas de error: si la app no devuelve una en `onLoadError`, la pantalla se queda
 * en blanco. Aquí se arma una página sencilla, en español, que se ve bien en claro y oscuro y que funciona
 * aunque JavaScript esté apagado (el botón "Reintentar" es un enlace a la misma dirección).
 */
object ErrorPages {

    class Info(val title: String, val message: String, val retry: Boolean = true, val danger: Boolean = false)

    fun info(code: Int, category: Int, host: String): Info = when (code) {
        WebRequestError.ERROR_OFFLINE ->
            Info("Sin conexión", "Tu teléfono no tiene internet. Revisa el Wi‑Fi o los datos móviles y vuelve a intentarlo.")
        WebRequestError.ERROR_UNKNOWN_HOST ->
            Info("No se encontró el sitio", "No existe $host o no hay conexión para llegar a él. Revisa que la dirección esté bien escrita.")
        WebRequestError.ERROR_NET_TIMEOUT ->
            Info("El sitio tardó demasiado", "$host no respondió a tiempo. Puede estar caído o tu conexión está lenta.")
        WebRequestError.ERROR_CONNECTION_REFUSED ->
            Info("No se pudo conectar", "$host rechazó la conexión. Puede estar caído por un momento.")
        WebRequestError.ERROR_NET_INTERRUPT, WebRequestError.ERROR_NET_RESET ->
            Info("Se cortó la conexión", "La conexión con $host se interrumpió mientras cargaba la página.")
        WebRequestError.ERROR_REDIRECT_LOOP ->
            Info("La página no carga bien", "$host redirige en círculos. Borrar las cookies de este sitio suele arreglarlo.")
        WebRequestError.ERROR_SECURITY_SSL, WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_BAD_HSTS_CERT ->
            Info(
                "La conexión no es segura",
                "Fyx no pudo comprobar que este sitio sea de verdad $host: su certificado de seguridad no es válido. " +
                    "Alguien podría estar intentando ver lo que envías. Si la fecha y hora de tu teléfono están mal, corrígelas y reintenta.",
                danger = true,
            )
        WebRequestError.ERROR_HTTPS_ONLY ->
            Info("El sitio no tiene conexión segura", "$host no admite HTTPS, así que lo que envíes podría verlo alguien más.", danger = true)
        WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI ->
            Info("Sitio engañoso", "$host fue reportado por intentar robar contraseñas o datos personales. Fyx lo bloqueó.", retry = false, danger = true)
        WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI, WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI,
        WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI, WebRequestError.ERROR_HARMFULADDON_URI,
        -> Info("Sitio peligroso", "$host fue reportado por distribuir programas dañinos. Fyx lo bloqueó.", retry = false, danger = true)
        WebRequestError.ERROR_MALFORMED_URI ->
            Info("La dirección no es válida", "Revisa que esté bien escrita.", retry = false)
        WebRequestError.ERROR_UNKNOWN_PROTOCOL ->
            Info("No se puede abrir este enlace", "Fyx no sabe abrir este tipo de dirección.", retry = false)
        WebRequestError.ERROR_FILE_NOT_FOUND, WebRequestError.ERROR_FILE_ACCESS_DENIED ->
            Info("No se encontró el archivo", "El archivo no existe o Fyx no tiene permiso para abrirlo.", retry = false)
        WebRequestError.ERROR_PROXY_CONNECTION_REFUSED, WebRequestError.ERROR_UNKNOWN_PROXY_HOST ->
            Info("Falló el proxy", "No se pudo conectar con el proxy configurado en tu red.")
        WebRequestError.ERROR_PORT_BLOCKED, WebRequestError.ERROR_LOCAL_NETWORK_ACCESS_DENIED ->
            Info("Dirección bloqueada", "Por seguridad, Fyx no abre esta dirección.", retry = false)
        WebRequestError.ERROR_CONTENT_CRASHED ->
            Info("La página se cerró", "Algo falló al mostrar $host.")
        WebRequestError.ERROR_CORRUPTED_CONTENT, WebRequestError.ERROR_INVALID_CONTENT_ENCODING, WebRequestError.ERROR_UNSAFE_CONTENT_TYPE ->
            Info("La página llegó dañada", "Lo que envió $host no se puede mostrar.")
        else ->
            if (category == WebRequestError.ERROR_CATEGORY_SECURITY) Info("La conexión no es segura", "Fyx no pudo abrir $host de forma segura.", danger = true)
            else Info("No se pudo abrir la página", "Algo falló al cargar $host.")
    }

    fun html(url: String, code: Int, category: Int): String {
        val host = esc(Urls.displayHost(url).ifBlank { "el sitio" })
        val i = info(code, category, host)
        // Solo direcciones web en el enlace de reintento: nunca javascript:, data: ni intent:.
        val retry = if (i.retry && (url.startsWith("http://") || url.startsWith("https://"))) {
            """<a class="b" href="${esc(url)}">Reintentar</a>"""
        } else ""
        val accent = if (i.danger) "#ff453a" else "#0a84ff"
        return """<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${i.title}</title><style>
:root{color-scheme:light dark}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font:16px/1.45 system-ui,sans-serif;background:#f2f2f7;color:#1c1c1e}
main{max-width:26em;padding:32px 28px;text-align:center}
.i{width:56px;height:56px;border-radius:50%;margin:0 auto 20px;background:$accent;color:#fff;font:600 32px/56px system-ui,sans-serif}
h1{font-size:22px;margin:0 0 10px}p{margin:0 0 24px;opacity:.75}
.b{display:inline-block;padding:12px 28px;border-radius:999px;background:$accent;color:#fff;text-decoration:none;font-weight:600}
small{display:block;margin-top:24px;opacity:.45;word-break:break-all}
@media(prefers-color-scheme:dark){body{background:#000;color:#f2f2f7}}
</style></head><body><main><div class="i">!</div><h1>${i.title}</h1><p>${i.message}</p>$retry<small>$host</small></main></body></html>"""
    }

    /** La página lista para devolver en `onLoadError`. */
    fun dataUri(url: String, error: WebRequestError): String = dataUri(url, error.code, error.category)

    fun dataUri(url: String, code: Int, category: Int): String =
        PREFIX +
            java.util.Base64.getEncoder().encodeToString(html(url, code, category).toByteArray(Charsets.UTF_8))

    const val PREFIX = "data:text/html;charset=utf-8;base64,"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
