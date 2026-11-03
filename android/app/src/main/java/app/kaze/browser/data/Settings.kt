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

/** Estilo de la interfaz: Safari (vidrio, azul) o Material You (colores del fondo de pantalla, Android 12+). */
/** FLOW = "Living Flow Design" de ColorOS 17: vidrio como gota de agua, esquinas grandes, degradados que fluyen y movimiento elástico. */
enum class UiStyle(val label: String) { SAFARI("Safari"), MATERIAL("Material You"), FLOW("Living Flow (ColorOS)"), ZEN("Zen") }

/** Color principal. AUTO = azul en Safari, el del fondo de pantalla en Material You. */
enum class AccentColor(val label: String, val argb: Long) {
    AUTO("Automático", 0), BLUE("Azul", 0xFF007AFF), TEAL("Verde azulado", 0xFF2A9D8F), GREEN("Verde", 0xFF34A853),
    PURPLE("Morado", 0xFF7E57C2), PINK("Rosa", 0xFFE0559A), ORANGE("Naranja", 0xFFF08A24), RED("Rojo", 0xFFE5484D),
}

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
    var tracking by pref("tracking", TrackingLevel.STANDARD) { TrackingLevel.valueOf(it) }
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
    /** Vista de pestañas: "grid" (tarjetas, por defecto), "compact" (3 columnas) o "list". */
    var tabsView by pref("tabs_view", "grid")
    /** Modo tableta: "auto" (pantallas de 600 dp o más), "on" u "off". */
    var tabletMode by pref("tablet_mode", "auto")
    /** Barra de direcciones arriba (uso tipo escritorio) en vez de abajo. */
    var barTop by pref("bar_top", false)
    /** Diseño de la barra, como en Safari: "compact" (una píldora flotante), "bottom" o "top". */
    /** Teléfono: fila de pestañas (con grupos) encima de la barra. */
    var tabStripPhone by pref("tab_strip_phone", true)
    /** Bloqueador de anuncios (uBlock Origin, se instala solo). */
    var adblock by pref("adblock", true)
    var barLayout by pref("bar_layout2", "compact")
    /** Sitios (dominios) donde el usuario apagó el bloqueo de rastreadores, separados por coma. */
    var trackingOff by pref("tracking_off", "")
    /** En tableta, pedir la versión de escritorio de las páginas. */
    var tabletDesktop by pref("tablet_desktop", true)
    var uiStyle by pref("ui_style", UiStyle.SAFARI) { UiStyle.valueOf(it) }
    var accent by pref("accent", AccentColor.AUTO) { AccentColor.valueOf(it) }
    // Secciones de la página de inicio.
    var startFavorites by pref("start_favorites", true)
    var startFrequent by pref("start_frequent", true)
    var startPrivacy by pref("start_privacy", true)
    var startReading by pref("start_reading", true)
    /** Total de rastreadores bloqueados (Reporte de privacidad) y desde cuándo se cuentan. */
    var trackersTotal by pref("trackers_total", 0)
    var trackersSince by pref("trackers_since", "")
    var onboarded by pref("onboarded", false)
    /** Transparencia del vidrio (barras y hojas flotantes), 0–100 %. 20 = el vidrio de siempre. */
    var glassTransparency by pref("glass_transparency", 20)
    // ── Personalización (tarjetas de Mochy en Ajustes → Diseño) ──
    /** Radio de las esquinas en dp; -1 = el del estilo elegido. */
    var cornerRadius by pref("corner_radius", -1)
    /** Esquinas continuas (superelipse de Mochy) en vez de arcos de círculo. */
    var smoothCorners by pref("smooth_corners", false)
    /** Vidrio en las barras; apagado, las barras son opacas. */
    var glassOn by pref("glass_on", true)
    /** Animaciones de Fyx (apagadas, todo salta a su final; "Quitar animaciones" de Android también cuenta). */
    var animationsOn by pref("animations_on", true)
    /** Velocidad de las animaciones en %, sobre la de Android (50–200). */
    var animationSpeed by pref("animation_speed", 100)
    /** Transiciones de Mochy (resortes de contenedor) o las de siempre de Fyx. */
    var mochyTransitions by pref("mochy_transitions", true)
    /** Sección "Reanudar navegación" en la página de inicio. */
    var startResume by pref("start_resume", true)

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
