/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Si Kaze se cierra por un error, guarda el detalle; al abrirla de nuevo se muestra
 * para poder copiarlo y reportarlo (sin tener que conectar el celular a un PC).
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "ultimo-cierre.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    "Fyx ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Hilo: ${thread.name}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Devuelve el último error guardado (y lo borra) o null. */
    fun take(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull().also { f.delete() }
    }
}
