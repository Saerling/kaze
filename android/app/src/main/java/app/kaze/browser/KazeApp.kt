/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Process
import app.kaze.browser.data.Db
import app.kaze.browser.data.Favicons
import app.kaze.browser.data.Settings
import app.kaze.browser.data.Store
import app.kaze.browser.engine.BrowserUi
import app.kaze.browser.engine.Downloads
import app.kaze.browser.engine.Engine
import app.kaze.browser.engine.SessionPool
import app.kaze.browser.engine.Updater
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File

/**
 * Ojo: GeckoView abre varios procesos propios (uno por pestaña, GPU, medios...)
 * y Android crea esta clase en **cada uno**. Si el motor se crea en todos, la app
 * se cierra al abrirla (era el fallo de la versión anterior). Por eso aquí no se
 * crea nada: todo se construye perezosamente en [Kaze], solo desde la actividad,
 * que únicamente existe en el proceso principal.
 */
class KazeApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!isMainProcess()) return
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Kaze.ifCreated { if (it.engineStarted) it.pool.onLowMemory() }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (isMainProcess()) Kaze.ifCreated { if (it.engineStarted) it.pool.onLowMemory() }
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/${Process.myPid()}/cmdline").readText().trim('\u0000', ' ') }.getOrNull()
        }
        return name == null || name == packageName
    }
}

/** Todo lo que la app necesita, creado una sola vez y solo en el proceso principal. */
class Kaze private constructor(context: Context) {
    val settings = Settings(context)
    val db = Db(context)
    val store = Store(db, settings).apply { load() }
    val ui = BrowserUi()
    val favicons = Favicons(context)
    val downloads = Downloads(context, ui)
    val updater = Updater(context)

    /** El motor se crea al primer uso (la pantalla de espacios se dibuja antes, el arranque se siente más rápido). */
    private val runtimeLazy = lazy { Engine.create(context, settings).also(::setupExtensions) }
    private val poolLazy = lazy { SessionPool(runtime, store, settings, db, ui, downloads) }
    val runtime: GeckoRuntime by runtimeLazy
    val pool: SessionPool by poolLazy

    /** true cuando el motor ya existe (para no crearlo solo por un aviso de memoria). */
    val engineStarted: Boolean get() = poolLazy.isInitialized()

    init {
        db.trimHistory()
    }

    private fun setupExtensions(runtime: GeckoRuntime) {
        // Solo instalamos extensiones cuando el usuario lo pide desde Ajustes: se aceptan sin otro diálogo.
        runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
        }
    }

    companion object {
        @Volatile
        private var instance: Kaze? = null

        fun get(context: Context): Kaze =
            instance ?: synchronized(this) { instance ?: Kaze(context.applicationContext).also { instance = it } }

        fun ifCreated(block: (Kaze) -> Unit) {
            instance?.let(block)
        }
    }
}
