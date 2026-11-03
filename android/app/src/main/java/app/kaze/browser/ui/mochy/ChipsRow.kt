// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/component/ChipsRow.kt
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Copyright: realidkroo y colaboradores de Mochy Music (sin aviso de copyright propio en el repositorio;
// Mochy es un fork de ViviMusic, de vivizzz007, que parte de Metrolist, de Mostafa Alagamy; ambos GPL-3.0).
// Modificado por Fyx, 2026-11.
// Cambios en Fyx: LazyRow con desplazamiento hasta la píldora elegida (en vez de Row con scroll),
// colores por parámetro, descripción para lectores de pantalla, número por píldora y píldoras extra al
// final ([trailing]); sin los márgenes de las barras del sistema ni los recursos de Mochy.

package app.kaze.browser.ui.mochy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Una píldora: su valor, el texto, un número opcional al lado y lo que lee TalkBack. */
data class Chip<E>(val value: E, val label: String, val count: Int? = null, val description: String = label)

/** Colores de [ChipsRow]: elegida y sin elegir. */
data class ChipColors(val container: Color, val label: Color, val selectedContainer: Color, val selectedLabel: Color)

@Composable
fun <E> ChipsRow(
    chips: List<Chip<E>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    colors: ChipColors,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    trailing: LazyListScope.() -> Unit = {},
) {
    // La píldora elegida siempre queda a la vista (se desliza hasta ella).
    val row = rememberLazyListState()
    LaunchedEffect(currentValue) {
        val i = chips.indexOfFirst { it.value == currentValue }
        row.animateScrollToItem(maxOf(0, i))
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = row,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips.size, key = { "chip:${chips[it].value}" }) { i ->
            val chip = chips[i]
            val isSelected = currentValue == chip.value
            MochyChip(
                label = if (chip.count != null) "${chip.label}  ${chip.count}" else chip.label,
                selected = isSelected,
                description = chip.description,
                colors = colors,
                textStyle = textStyle,
                onClick = { onValueUpdate(chip.value) },
            )
        }
        trailing()
    }
}

/** Una píldora suelta con el mismo movimiento (esquinas que se redondean y tamaño con resorte). */
@Composable
fun MochyChip(
    label: String,
    selected: Boolean,
    colors: ChipColors,
    textStyle: TextStyle,
    description: String = label,
    showCheck: Boolean = true,
    // RadioButton: una sola elegida en la fila; Button para una píldora que hace algo.
    role: Role = Role.RadioButton,
    onClick: () -> Unit,
) {
    val cornerRadius: Dp by animateDpAsState(
        targetValue = if (selected) 20.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "corner_radius"
    )

    // La descripción va en una caja que reúne la píldora (TalkBack lee "Tema …" y se puede tocar por ella).
    Box(
        Modifier.clearAndSetSemantics {
            contentDescription = description
            this.role = role
            this.selected = selected
            onClick { onClick(); true }
        },
    ) {
    FilterChip(
        label = { Text(label, style = textStyle, maxLines = 1) },
        selected = selected,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.container,
            labelColor = colors.label,
            selectedContainerColor = colors.selectedContainer,
            selectedLabelColor = colors.selectedLabel,
            selectedLeadingIconColor = colors.selectedLabel,
        ),
        onClick = onClick,
        leadingIcon = if (selected && showCheck) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(cornerRadius),
        border = null,
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    )
}
}
