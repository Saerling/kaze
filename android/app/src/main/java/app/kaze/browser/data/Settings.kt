/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro") }

enum class TrackingLevel(val label: String) { STANDARD("Estándar"), STRICT("Estricto") }

enum class DnsMode(val label: String) { OFF("Desactivado"), CLOUDFLARE("Cloudflare"), MULLVAD("Mullvad (bloquea anuncios)") }

/**
 * Ajustes del usuario. Cada valor es estado de Compose (la UI se actualiza sola)
 * y se guarda en SharedPreferences al cambiar.
 */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("kaze", Context.MODE_PRIVATE)

    /** En celulares con poca RAM se mantienen menos pestañas cargadas. */
    val defaultLiveTabs: Int = run {
        val am = context.getSystemService(ActivityManager::class.java)
        when {
            am.isLowRamDevice -> 1
            am.memoryClass <= 256 -> 2
            else -> 3
        }
    }

    var searchEngine by pref("engine", SearchEngine.DUCKDUCKGO) { SearchEngine.valueOf(it) }
    var searchSuggestions by pref("suggest", true)
    var theme by pref("theme", ThemeMode.SYSTEM) { ThemeMode.valueOf(it) }
    var tracking by pref("tracking", TrackingLevel.STRICT) { TrackingLevel.valueOf(it) }
    var dns by pref("dns", DnsMode.OFF) { DnsMode.valueOf(it) }
    var javascript by pref("js", true)
    /** Máximo de pestañas con la página cargada en memoria a la vez. */
    var liveTabs by pref("live_tabs", defaultLiveTabs)
    /** Minutos sin usar tras los que una pestaña se duerme (0 = solo por límite). */
    var sleepAfterMinutes by pref("sleep_min", 10)
    /** Aislamiento de sitios por proceso (Fission): más seguro pero usa bastante más RAM. */
    var siteIsolation by pref("fission", false)
    var lastSpaceId by pref("last_space", "")
    var lastProfileId by pref("last_profile", "")
    /** Tamaño del texto de las páginas, en % (como "AA" de Safari). */
    var textSize by pref("text_size", 100)
    /** Total de rastreadores bloqueados (Reporte de privacidad) y desde cuándo se cuentan. */
    var trackersTotal by pref("trackers_total", 0)
    var trackersSince by pref("trackers_since", "")
    var onboarded by pref("onboarded", false)

    // ── Mecánica ──────────────────────────────────────────────────────

    private fun <T : Enum<T>> pref(key: String, default: T, parse: (String) -> T) =
        Pref(default, { runCatching { parse(p.getString(key, default.name)!!) }.getOrDefault(default) }) {
            p.edit().putString(key, it.name).apply()
        }

    private fun pref(key: String, default: Boolean) =
        Pref(default, { p.getBoolean(key, default) }) { p.edit().putBoolean(key, it).apply() }

    private fun pref(key: String, default: Int) =
        Pref(default, { p.getInt(key, default) }) { p.edit().putInt(key, it).apply() }

    private fun pref(key: String, default: String) =
        Pref(default, { p.getString(key, default) ?: default }) { p.edit().putString(key, it).apply() }

    class Pref<T>(default: T, read: () -> T, private val write: (T) -> Unit) {
        private val state = mutableStateOf(runCatching(read).getOrDefault(default))
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            if (state.value == value) return
            state.value = value
            write(value)
        }
    }
}
