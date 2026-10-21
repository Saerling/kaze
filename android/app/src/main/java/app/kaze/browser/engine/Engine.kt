/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.Context
import android.content.res.Configuration
import app.kaze.browser.BuildConfig
import app.kaze.browser.data.DnsMode
import app.kaze.browser.data.Settings
import app.kaze.browser.data.ThemeMode
import app.kaze.browser.data.TrackingLevel
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/**
 * Crea el único [GeckoRuntime] de la app.
 *
 * Las preferencias de Gecko que no tienen API en GeckoView se escriben en un
 * YAML ([PREFS]) que GeckoView lee al arrancar (`configFilePath`). Son los mismos
 * ajustes de bajo consumo que usa Kaze en Windows.
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
        val prefsFile = writePrefs(context, settings)

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
            .configFilePath(prefsFile.absolutePath)
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
            .automaticFontSizeAdjustment(true)
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

        return GeckoRuntime.create(context, builder.build())
    }

    private fun writePrefs(context: Context, settings: Settings): File {
        val f = File(context.filesDir, "gecko-prefs.yaml")
        val sb = StringBuilder("prefs:\n")
        for ((k, v) in PREFS) {
            sb.append("  ").append(k).append(": ")
            sb.append(if (v is String) "\"$v\"" else v.toString()).append('\n')
        }
        // Con poca RAM, un solo proceso de contenido para todas las pestañas.
        if (settings.liveTabs <= 1) sb.append("  dom.ipc.processCount: 1\n")
        f.writeText(sb.toString())
        return f
    }

    fun isDarkTheme(context: Context, settings: Settings): Boolean = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
