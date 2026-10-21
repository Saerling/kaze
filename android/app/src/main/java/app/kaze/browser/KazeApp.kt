/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.app.Application
import android.content.ComponentCallbacks2
import app.kaze.browser.tabs.TabManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Punto de entrada de la app. Hay **un solo** [GeckoRuntime] para todo Kaze:
 * crear más de uno duplicaría el motor completo en memoria.
 */
class KazeApp : Application() {

    lateinit var runtime: GeckoRuntime
        private set

    lateinit var tabs: TabManager
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = crearRuntime()
        tabs = TabManager(this, runtime)
    }

    private fun crearRuntime(): GeckoRuntime {
        val bloqueo = ContentBlocking.Settings.Builder()
            // Bloquea rastreadores, cripto-mineros y fingerprinting: menos scripts = menos RAM y CPU.
            .antiTracking(
                ContentBlocking.AntiTracking.DEFAULT or
                    ContentBlocking.AntiTracking.STP or
                    ContentBlocking.AntiTracking.CRYPTOMINING or
                    ContentBlocking.AntiTracking.FINGERPRINTING,
            )
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()

        val ajustes = GeckoRuntimeSettings.Builder()
            .contentBlocking(bloqueo)
            .aboutConfigEnabled(true)
            .consoleOutput(BuildConfig.DEBUG)
            .debugLogging(false)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .build()

        return GeckoRuntime.create(this, ajustes)
    }

    /** Android avisa cuando el sistema necesita memoria: soltamos todo lo que no está en pantalla. */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // RUNNING_LOW: la app está en pantalla pero el sistema va justo.
        // BACKGROUND o más: Kaze está en segundo plano y es candidata a ser cerrada.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            tabs.unloadAllExceptSelected()
        }
    }
}
