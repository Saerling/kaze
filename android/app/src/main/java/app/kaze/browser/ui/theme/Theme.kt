/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Valores de shared/design/tokens.json (misma paleta que Kaze escritorio)
object KazeColors {
    val Acento = Color(0xFF3A8DFF)
    val AcentoSecundario = Color(0xFF5EE6C8)
    val Violeta = Color(0xFF6B4BFF)
    val FondoOscuro = Color(0xFF14161C)
    val SuperficieOscura = Color(0xFF1E2129)
    val SuperficieOscura2 = Color(0xFF272B35)
    val TextoOscuro = Color(0xFFE8EAF0)
    val TextoTenueOscuro = Color(0xFF9AA0AE)
    val FondoClaro = Color(0xFFF5F6F9)
    val SuperficieClara = Color(0xFFFFFFFF)
    val TextoClaro = Color(0xFF14161C)
}

object KazeMotion {
    const val RAPIDA = 120
    const val NORMAL = 220
    const val LENTA = 360
}

private val Oscuro = darkColorScheme(
    primary = KazeColors.Acento,
    secondary = KazeColors.AcentoSecundario,
    tertiary = KazeColors.Violeta,
    background = KazeColors.FondoOscuro,
    surface = KazeColors.SuperficieOscura,
    surfaceVariant = KazeColors.SuperficieOscura2,
    onBackground = KazeColors.TextoOscuro,
    onSurface = KazeColors.TextoOscuro,
    onSurfaceVariant = KazeColors.TextoTenueOscuro,
)

private val Claro = lightColorScheme(
    primary = KazeColors.Acento,
    secondary = KazeColors.AcentoSecundario,
    tertiary = KazeColors.Violeta,
    background = KazeColors.FondoClaro,
    surface = KazeColors.SuperficieClara,
    surfaceVariant = Color(0xFFE9EBF0),
    onBackground = KazeColors.TextoClaro,
    onSurface = KazeColors.TextoClaro,
)

private val Formas = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun KazeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Oscuro else Claro,
        shapes = Formas,
        content = content,
    )
}
