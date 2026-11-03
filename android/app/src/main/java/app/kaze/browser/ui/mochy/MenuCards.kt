// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/component/PlaylistSheetKit.kt
// (SheetActionCardRow y SheetActionCard), las tarjetas grandes que NewActionGrid
// (ui/component/NewMenuComponents.kt) pone arriba de cada menú.
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Cambios en Fyx: colores por parámetro (en Mochy son blanco sobre gris oscuro fijo), alto mínimo
// por parámetro (cuatro tarjetas por fila caben en un teléfono), estado "activo", el rebote de
// pressBounce al tocar y semántica de botón; el ícono es un ImageVector de Material.

package app.kaze.browser.ui.mochy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ActionCardMinHeight = 112.dp

/**
 * Las tarjetas grandes de arriba de un menú, para las acciones de siempre. Todas quedan tan altas
 * como la más alta, así un texto que se parte en dos líneas con letra grande no las deja disparejas.
 */
@Composable
fun SheetActionCardRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Colores de [SheetActionCard]. */
data class ActionCardColors(val surface: Color, val activeSurface: Color, val icon: Color, val text: Color, val disabled: Color)

/** Una tarjeta grande para una acción principal: el ícono arriba y el nombre abajo. */
@Composable
fun SheetActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    colors: ActionCardColors,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    minHeight: Dp = ActionCardMinHeight,
    iconTint: Color? = null,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = minHeight)
            .pressBounce(source, 0.94f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) colors.activeSurface else colors.surface)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = if (enabled) iconTint ?: colors.icon else colors.disabled, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = if (enabled) iconTint ?: colors.text else colors.disabled,
            style = textStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
