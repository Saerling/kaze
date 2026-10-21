/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.util.concurrent.Executors

/**
 * Miniaturas de las pestañas para la vista en cuadrícula (como Safari).
 * Se guardan pequeñas (360 px de ancho, JPEG) en disco y solo unas pocas en memoria.
 */
class Thumbnails(context: Context) {
    private val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kaze-thumbs").apply { priority = Thread.MIN_PRIORITY } }
    private val memory = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Sube cada vez que se guarda una miniatura, para que la cuadrícula se redibuje. */
    val version = mutableIntStateOf(0)

    fun save(tabId: String, full: Bitmap) {
        io.execute {
            runCatching {
                val w = 360
                val h = (full.height * w / full.width.coerceAtLeast(1)).coerceAtMost(w * 2)
                val small = Bitmap.createScaledBitmap(full, w, h.coerceAtLeast(1), true)
                if (small !== full) full.recycle()
                File(dir, "$tabId.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 72, it) }
                memory.put(tabId, small)
                version.intValue++
            }
        }
    }

    fun cached(tabId: String): Bitmap? = memory.get(tabId)

    /** Bloqueante (llamar en segundo plano). */
    fun load(tabId: String): Bitmap? {
        memory.get(tabId)?.let { return it }
        val f = File(dir, "$tabId.jpg")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { memory.put(tabId, it) }
    }

    fun delete(tabId: String) {
        memory.remove(tabId)
        io.execute { File(dir, "$tabId.jpg").delete() }
    }
}
