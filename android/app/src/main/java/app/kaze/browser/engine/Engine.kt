/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import app.kaze.browser.BuildConfig
import app.kaze.browser.data.DnsMode
import app.kaze.browser.data.Settings
import app.kaze.browser.data.ThemeMode
import app.kaze.browser.data.TrackingLevel
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Crea el único [GeckoRuntime] de la app.
 *
 * Las preferencias de Gecko sin API propia en GeckoView ([PREFS]) se aplican con
 * GeckoPreferenceController al arrancar. (Antes iban en un YAML leído con SnakeYAML,
 * pero el optimizador R8 de la versión publicada rompe esa librería y la app se cerraba.)
 */
object Engine {

    /** Preferencias de Gecko pensadas para gastar poca RAM sin perder velocidad. */
    private val PREFS = linkedMapOf<String, Any>(
        // Páginas completas guardadas en RAM para el botón "atrás" (por defecto hasta 8).
        "browser.sessionhistory.max_total_viewers" to 1,
        // Caché de red en memoria limitada a 16 MB (el resto va a disco).
        "browser.cache.memory.capacity" to 16384,
        // Caché de imágenes decodificadas: tope de 48 MB.
        "image.mem.surfacecache.max_size_kb" to 49152,
        // Descartar imágenes decodificadas que no están en pantalla.
        "image.mem.discardable" to true,
        // Videos con sonido no se reproducen solos (ahorra datos, CPU y batería).
        "media.autoplay.default" to 1,
        "media.autoplay.blocking_policy" to 2,
        // Las pestañas en segundo plano ejecutan sus temporizadores como mucho cada 10 s.
        "dom.min_background_timeout_value" to 10000,
        "dom.timeout.enable_budget_timer_throttling" to true,
        // Sin pings de rastreo ni precarga especulativa a dominios que no visitas.
        "browser.send_pings" to false,
        "network.http.speculative-parallel-limit" to 0,
        // Recolector de basura de JS más agresivo con memoria.
        "javascript.options.mem.gc_incremental_slice_ms" to 5,
        // Telemetría fuera.
        "toolkit.telemetry.enabled" to false,
        "datareporting.healthreport.uploadEnabled" to false,
    )

    fun create(context: Context, settings: Settings): GeckoRuntime {
        val runtime = GeckoRuntime.create(context, runtimeSettings(settings).build())
        // GeckoView no conserva el factor del Builder al construir: se fija ya con el motor creado.
        applyTextSize(runtime, settings.textSize)
        applyPrefs(settings)
        return runtime
    }

    /** Separado de [create] para poder probarlo sin un celular. */
    internal fun runtimeSettings(settings: Settings): GeckoRuntimeSettings.Builder {
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(
                when (settings.tracking) {
                    TrackingLevel.STANDARD -> ContentBlocking.AntiTracking.DEFAULT
                    TrackingLevel.STRICT -> ContentBlocking.AntiTracking.STRICT
                },
            )
            .enhancedTrackingProtectionLevel(
                when (settings.tracking) {
                    TrackingLevel.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
                    TrackingLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
                },
            )
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()

        val builder = GeckoRuntimeSettings.Builder()
            .contentBlocking(blocking)
            .javaScriptEnabled(settings.javascript)
            .preferredColorScheme(
                when (settings.theme) {
                    ThemeMode.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
                    ThemeMode.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                    ThemeMode.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
                },
            )
            // Fission = un proceso por sitio. Más seguro, pero varias veces más RAM en el celular.
            .fissionEnabled(settings.siteIsolation)
            // Sin extensiones instaladas no hace falta un proceso aparte para ellas.
            .extensionsProcessEnabled(false)
            .lowMemoryDetection(true)
            .webManifest(false)
            .globalPrivacyControlEnabled(true)
            .aboutConfigEnabled(true)
            // Con el ajuste automático (tamaño del sistema) Gecko no deja fijar el factor:
            // primero se apaga y luego se fija, nunca al revés.
            .apply {
                if (settings.textSize == 100) automaticFontSizeAdjustment(true)
                else automaticFontSizeAdjustment(false).fontSizeFactor(settings.textSize / 100f)
            }
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .debugLogging(false)

        when (settings.dns) {
            DnsMode.OFF -> Unit
            DnsMode.CLOUDFLARE -> builder.trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
                .trustedRecursiveResolverUri("https://mozilla.cloudflare-dns.com/dns-query")
            DnsMode.MULLVAD -> builder.trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
                .trustedRecursiveResolverUri("https://adblock.dns.mullvad.net/dns-query")
        }
        return builder
    }

    /** Se aplican en la rama "por defecto": no pisan lo que el usuario cambie en about:config. */
    @androidx.annotation.OptIn(org.mozilla.geckoview.ExperimentalGeckoViewApi::class)
    private fun applyPrefs(settings: Settings) {
        val prefs = LinkedHashMap(PREFS)
        // Con poca RAM, un solo proceso de contenido para todas las pestañas.
        if (settings.liveTabs <= 1) prefs["dom.ipc.processCount"] = 1
        val branch = GeckoPreferenceController.PREF_BRANCH_DEFAULT
        for ((name, value) in prefs) {
            runCatching {
                when (value) {
                    is Boolean -> GeckoPreferenceController.setGeckoPref(name, value, branch)
                    is Int -> GeckoPreferenceController.setGeckoPref(name, value, branch)
                    else -> GeckoPreferenceController.setGeckoPref(name, value.toString(), branch)
                }.accept({ }, { e -> Log.w(TAG, "No se pudo aplicar $name", e) })
            }.onFailure { Log.w(TAG, "No se pudo aplicar $name", it) }
        }
    }

    private const val TAG = "FyxEngine"

    /** Cambia el tamaño del texto de todas las páginas al momento (el "AA" de Safari). */
    fun applyTextSize(runtime: GeckoRuntime, percent: Int) = applyTextSize(runtime.settings, percent)

    internal fun applyTextSize(settings: GeckoRuntimeSettings, percent: Int) {
        runCatching {
            if (percent == 100) {
                settings.setAutomaticFontSizeAdjustment(true)
            } else {
                // Primero apagar el ajuste automático: con él activo, Gecko lanza un error y la app se cierra.
                settings.setAutomaticFontSizeAdjustment(false)
                settings.setFontSizeFactor(percent / 100f)
            }
        }.onFailure { Log.w(TAG, "No se pudo cambiar el tamaño del texto", it) }
    }

    fun isDarkTheme(context: Context, settings: Settings): Boolean = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
