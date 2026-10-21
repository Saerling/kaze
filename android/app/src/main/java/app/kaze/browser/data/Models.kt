/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Dónde vive una pestaña, igual que en Zen:
 *  - ESSENTIAL: arriba en la cuadrícula, compartida por todos los espacios
 *  - PINNED:    fijada dentro de su espacio, sobrevive aunque se "limpie" el espacio
 *  - NORMAL:    pestaña normal del espacio
 */
enum class TabKind(val code: Int) {
    ESSENTIAL(0), PINNED(1), NORMAL(2);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

/** Un espacio de trabajo (Zen "workspace"): nombre, emoji y degradado de colores. */
@Stable
class Space(
    val id: String = newId(),
    name: String,
    emoji: String,
    colors: List<Int>,
    position: Int,
    isolated: Boolean = false,
) {
    var name by mutableStateOf(name)
    var emoji by mutableStateOf(emoji)
    /** 1 a 3 colores ARGB para el degradado de fondo. */
    var colors by mutableStateOf(colors)
    var position by mutableIntStateOf(position)
    /** Si es true, el espacio tiene sus propias cookies y sesiones (como un contenedor de Firefox). */
    var isolated by mutableStateOf(isolated)

    /** Identificador de contexto de Gecko para aislar cookies del espacio. */
    val contextId: String? get() = if (isolated) "kaze-space-$id" else null

    companion object {
        /** Espacio virtual de las pestañas privadas; nunca se guarda en disco. */
        const val PRIVATE_ID = "private"
    }
}

/**
 * Una pestaña. Lo que se ve (título, URL, progreso) es estado de Compose;
 * la sesión de Gecko vive aparte en [app.kaze.browser.engine.SessionPool].
 */
@Stable
class Tab(
    val id: String = newId(),
    spaceId: String,
    url: String,
    title: String = "",
    kind: TabKind = TabKind.NORMAL,
    position: Int = 0,
    lastUsed: Long = System.currentTimeMillis(),
    /** Estado serializado de Gecko (historial atrás/adelante, scroll, formularios). */
    var sessionState: String? = null,
    val isPrivate: Boolean = false,
    /** Para pestañas fijadas/essentials: la URL "de casa" a la que se puede volver. */
    pinnedUrl: String? = null,
) {
    var spaceId by mutableStateOf(spaceId)
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var kind by mutableStateOf(kind)
    var position by mutableIntStateOf(position)
    var lastUsed by mutableLongStateOf(lastUsed)
    var pinnedUrl by mutableStateOf(pinnedUrl)

    // Estado efímero (no se guarda)
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isSecure by mutableStateOf(true)
    /** true mientras Gecko tiene la página cargada en memoria. */
    var isLive by mutableStateOf(false)
    var desktopMode by mutableStateOf(false)

    val displayTitle: String
        get() = title.ifBlank { Urls.displayHost(url).ifBlank { url } }
}

/** Una entrada del historial. */
data class HistoryEntry(
    val url: String,
    val title: String,
    val lastVisit: Long,
    val visits: Int,
)

fun newId(): String = UUID.randomUUID().toString()
