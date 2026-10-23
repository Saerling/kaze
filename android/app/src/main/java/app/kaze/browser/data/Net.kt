/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Peticiones HTTP mínimas (sin OkHttp: una librería menos en memoria). */
internal object Http {
    fun get(url: String, maxBytes: Int = 512 * 1024, timeoutMs: Int = 4000): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Pyx")
        try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.use { ins ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}

/** Sugerencias de búsqueda del motor elegido (formato OpenSearch). Llamar fuera del hilo principal. */
object Suggestions {
    fun fetch(engine: SearchEngine, query: String): List<String> {
        val url = engine.suggestUrl(query) ?: return emptyList()
        val bytes = Http.get(url, maxBytes = 64 * 1024, timeoutMs = 2500) ?: return emptyList()
        return try {
            val arr = JSONArray(String(bytes)).getJSONArray(1)
            (0 until minOf(arr.length(), 6)).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Íconos de los sitios. Se piden directamente al sitio (sin pasar por Google ni otros
 * servicios), se guardan en disco y se mantienen pocos en memoria.
 */
class Favicons(context: Context) {
    private val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    fun cached(host: String): Bitmap? = memory.get(host)

    /** Bloqueante: llamar desde un hilo de fondo. */
    fun load(host: String): Bitmap? {
        memory.get(host)?.let { return it }
        if (host in missing) return null
        val file = File(dir, host.replace(Regex("[^a-z0-9.-]"), "_") + ".png")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { memory.put(host, it); return it }
        }
        // 1) ícono grande de iOS, 2) favicon.ico clásico, 3) el que declare la página en <link rel="icon">.
        val bmp = sequenceOf<() -> String?>(
            { "https://$host/apple-touch-icon.png" },
            { "https://$host/favicon.ico" },
            { iconFromHtml(host) },
        ).mapNotNull { it()?.let { url -> Http.get(url, maxBytes = 256 * 1024)?.let(::decode) } }.firstOrNull()
        if (bmp == null) {
            missing.add(host)
            return null
        }
        val scaled = if (bmp.width > 96) Bitmap.createScaledBitmap(bmp, 96, 96 * bmp.height / bmp.width, true) else bmp
        runCatching { file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        memory.put(host, scaled)
        return scaled
    }

    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val relIcon = Regex("rel\\s*=\\s*[\"']?[^\"'>]*icon", RegexOption.IGNORE_CASE)
    private val href = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /** Lee el inicio de la página y busca su ícono declarado (prefiere PNG sobre SVG, que Android no decodifica). */
    private fun iconFromHtml(host: String): String? {
        val html = Http.get("https://$host/", maxBytes = 192 * 1024)?.let { String(it) } ?: return null
        val head = html.substringBefore("</head>")
        val candidates = linkTag.findAll(head).map { it.value }.filter { relIcon.containsMatchIn(it) }
            .mapNotNull { tag -> href.find(tag)?.groupValues?.get(1) }
            .filter { !it.endsWith(".svg") && !it.startsWith("data:") }
            .toList()
        val pick = candidates.firstOrNull { "apple" in it || "192" in it || "180" in it } ?: candidates.firstOrNull() ?: return null
        return runCatching { java.net.URI("https://$host/").resolve(pick.replace("&amp;", "&")).toString() }.getOrNull()
    }

    private fun decode(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 16 }

    fun clear() {
        memory.evictAll()
        missing.clear()
        dir.listFiles()?.forEach { it.delete() }
    }
}
