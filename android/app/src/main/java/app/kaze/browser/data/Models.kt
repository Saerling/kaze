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

/**
 * Un perfil (como en Safari): sus propios espacios, Essentials, historial, marcadores,
 * lista de lectura y cookies. "Personal" es el perfil por defecto.
 */
@Stable
class Profile(
    val id: String = newId(),
    name: String,
    emoji: String,
    position: Int,
) {
    var name by mutableStateOf(name)
    var emoji by mutableStateOf(emoji)
    var position by mutableIntStateOf(position)

    /** Contexto de Gecko: cookies y almacenamiento separados por perfil. */
    val contextId: String? get() = if (id == DEFAULT_ID) null else "kaze-profile-$id"

    companion object {
        const val DEFAULT_ID = "personal"
    }
}

/** Un espacio de trabajo (Zen "workspace", "grupo de pestañas" en Safari): nombre, emoji y degradado. */
@Stable
class Space(
    val id: String = newId(),
    name: String,
    emoji: String,
    colors: List<Int>,
    position: Int,
    isolated: Boolean = false,
    profileId: String = Profile.DEFAULT_ID,
) {
    var profileId by mutableStateOf(profileId)
    var name by mutableStateOf(name)
    var emoji by mutableStateOf(emoji)
    /** 1 a 3 colores ARGB para el degradado de fondo. */
    var colors by mutableStateOf(colors)
    var position by mutableIntStateOf(position)
    /** Si es true, el espacio tiene sus propias cookies y sesiones (como un contenedor de Firefox). */
    var isolated by mutableStateOf(isolated)

    /** Contexto de Gecko propio si el espacio está aislado (dentro de su perfil). */
    fun contextId(profile: Profile?): String? = if (isolated) "kaze-space-$id" else profile?.contextId

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
    /** Carpeta dentro del espacio ("" = suelta). Las pestañas con el mismo nombre forman la carpeta. */
    folder: String = "",
) {
    var folder by mutableStateOf(folder)
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
    /** Historial atrás/adelante de la pestaña (título, dirección) y la posición actual. */
    var historyItems by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var historyIndex by mutableIntStateOf(0)
    /** El usuario cambió a mano la versión de escritorio (no se vuelve a aplicar la de tableta). */
    var desktopTouched = false
    var isSecure by mutableStateOf(true)
    /** true mientras Gecko tiene la página cargada en memoria. */
    var isLive by mutableStateOf(false)
    var desktopMode by mutableStateOf(false)
    /** La página tiene un artículo que se puede leer en modo Lector. */
    var readerable by mutableStateOf(false)
    var readerMode by mutableStateOf(false)
    /** Idioma detectado de la página (para ofrecer Traducir) y si ya está traducida. */
    var pageLang by mutableStateOf<String?>(null)
    var translated by mutableStateOf(false)
    /** Rastreadores bloqueados en esta página (Reporte de privacidad). */
    var trackersBlocked by mutableIntStateOf(0)

    val isStart: Boolean get() = url == Urls.START

    val displayTitle: String
        get() = if (isStart) "Página de inicio" else title.ifBlank { Urls.displayHost(url).ifBlank { url } }
}

/** Una página guardada en la lista de lectura. */
data class ReadingItem(val url: String, val title: String, val added: Long)

/** Un marcador o un elemento de la lista de lectura. */
data class SavedPage(
    val url: String,
    val title: String,
    val added: Long,
    val read: Boolean = false,
)

/** Una entrada del historial. */
data class HistoryEntry(
    val url: String,
    val title: String,
    val lastVisit: Long,
    val visits: Int,
)

fun newId(): String = UUID.randomUUID().toString()
