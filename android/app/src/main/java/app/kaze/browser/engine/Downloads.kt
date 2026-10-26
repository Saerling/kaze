/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** Una descarga del historial (lo que se ve en la lista de Descargas). */
class DownloadItem(val id: Long, val name: String, val mime: String, val total: Long, val time: Long) {
    enum class State { RUNNING, DONE, FAILED }
    var state by mutableStateOf(State.RUNNING)
    var done by mutableLongStateOf(0L)
    var uri by mutableStateOf<String?>(null)
}

/** Guarda descargas en la carpeta Descargas del teléfono, en segundo plano, y lleva su historial. */
class Downloads(private val context: Context, private val ui: BrowserUi) {

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kaze-download") }
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    /** Historial, la más reciente primero (como en Chrome y Safari). */
    val items = mutableStateListOf<DownloadItem>().apply { addAll(load()) }

    private fun load(): List<DownloadItem> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DownloadItem(o.getLong("id"), o.getString("name"), o.optString("mime"), o.optLong("total", -1), o.getLong("time")).apply {
                state = DownloadItem.State.valueOf(o.optString("state", "DONE")).let { if (it == DownloadItem.State.RUNNING) DownloadItem.State.FAILED else it }
                done = o.optLong("done")
                uri = o.optString("uri").ifEmpty { null }
            }
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val arr = JSONArray()
        items.take(200).forEach { d ->
            arr.put(JSONObject().put("id", d.id).put("name", d.name).put("mime", d.mime).put("total", d.total)
                .put("time", d.time).put("state", d.state.name).put("done", d.done).put("uri", d.uri ?: ""))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun remove(d: DownloadItem) { items.remove(d); persist() }
    fun clear() { items.removeAll { it.state != DownloadItem.State.RUNNING }; persist() }

    fun save(response: WebResponse) {
        val body = response.body ?: run { ui.show("La descarga está vacía"); return }
        val disposition = response.headers.entries.firstOrNull { it.key.equals("Content-Disposition", true) }?.value
        val mime = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
            ?.substringBefore(';')?.trim()
        val name = URLUtil.guessFileName(response.uri, disposition, mime)
        val total = response.headers.entries.firstOrNull { it.key.equals("Content-Length", true) }?.value?.toLongOrNull() ?: -1
        ui.show("Descargando $name…")
        io.execute {
            val ok = runCatching { write(body, name, mime, total) }.isSuccess
            main.post { ui.show(if (ok) "Descargado: $name" else "No se pudo descargar $name") }
        }
    }

    /** Copia mostrando el progreso (se avisa a la interfaz cada ~256 KB, no en cada bloque: menos trabajo). */
    private fun InputStream.copyWithProgress(out: java.io.OutputStream, item: DownloadItem) {
        val buf = ByteArray(64 * 1024)
        var n: Int
        var copied = 0L
        var lastPost = 0L
        while (read(buf).also { n = it } >= 0) {
            out.write(buf, 0, n)
            copied += n
            if (copied - lastPost >= 256 * 1024) { lastPost = copied; val c = copied; main.post { item.done = c } }
        }
        main.post { item.done = copied }
    }

    fun write(input: InputStream, name: String, mime: String?, total: Long = -1) {
        val type = mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")) ?: "application/octet-stream"
        val item = DownloadItem(System.nanoTime(), name, type, total, System.currentTimeMillis())
        main.post { items.add(0, item) }
        try {
            writeTo(input, name, type, item)
            main.post { item.state = DownloadItem.State.DONE; persist() }
        } catch (e: Exception) {
            main.post { item.state = DownloadItem.State.FAILED; persist() }
            throw e
        }
    }

    private fun writeTo(input: InputStream, name: String, type: String, item: DownloadItem) {
        input.use { src ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, type)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("sin acceso a Descargas")
                try {
                    resolver.openOutputStream(uri)!!.use { src.copyWithProgress(it, item) }
                    main.post { item.uri = uri.toString() }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                // Android 8–9: carpeta de descargas propia de la app (no requiere permisos).
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                File(dir, name).outputStream().use { src.copyWithProgress(it, item) }
            }
        }
    }
}
