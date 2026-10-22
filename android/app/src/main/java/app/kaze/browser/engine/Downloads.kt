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
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** Guarda descargas en la carpeta Descargas del teléfono, en segundo plano. */
class Downloads(private val context: Context, private val ui: BrowserUi) {

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kaze-download") }
    private val main = Handler(Looper.getMainLooper())

    fun save(response: WebResponse) {
        val body = response.body ?: run { ui.show("La descarga está vacía"); return }
        val disposition = response.headers.entries.firstOrNull { it.key.equals("Content-Disposition", true) }?.value
        val mime = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
            ?.substringBefore(';')?.trim()
        val name = URLUtil.guessFileName(response.uri, disposition, mime)
        ui.show("Descargando $name…")
        io.execute {
            val ok = runCatching { write(body, name, mime) }.isSuccess
            main.post { ui.show(if (ok) "Descargado: $name" else "No se pudo descargar $name") }
        }
    }

    fun write(input: InputStream, name: String, mime: String?) {
        val type = mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")) ?: "application/octet-stream"
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
                    resolver.openOutputStream(uri)!!.use { src.copyTo(it, 64 * 1024) }
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
                File(dir, name).outputStream().use { src.copyTo(it, 64 * 1024) }
            }
        }
    }
}
