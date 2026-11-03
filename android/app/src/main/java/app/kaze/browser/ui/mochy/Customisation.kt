// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/gesture/CustomisationCards.kt
// (CoverRoundingSection, CardTitle, CustomiseSliderCard, RoundingSwatch, BareSlider) y
// ui/screens/gesture/CustomisationEffects.kt (EffectCard, MiniSegmented y la lógica de
// AnimationSection: interruptor, velocidad por escalones y su vibración en cada escalón).
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Cambios en Fyx: colores por parámetro (Mochy es solo oscuro, Fyx sigue su tema), sin la fuente
// Inter ni los textos/recursos de Mochy (los textos llegan por parámetro, en español), el
// interruptor por parámetro (Fyx usa el suyo), vistas previas sin animación continua, el
// deslizador con semántica de accesibilidad (TalkBack puede ajustarlo) y la redondez en dp.

package app.kaze.browser.ui.mochy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Colores de las tarjetas de personalización (en Mochy, blanco sobre gris oscuro fijo). */
data class CustomiseColors(
    val card: Color,
    val row: Color,
    val text: Color,
    val muted: Color,
    val swatch: Color,
    val highlight: Color,
)

private val CardShape = RoundedCornerShape(22.dp)
private val SliderCardShape = RoundedCornerShape(20.dp)
private val RoundingCardHeight = 160.dp
private val StyleCardHeight = 150.dp
private val CardTextInset = 22.dp

// la esquina de muestra en la tarjeta de redondez: pegada arriba a la derecha, de este tamaño
private val SwatchWidth = 146.dp
private val SwatchHeight = 110.dp

// el arco se dibuja a esta distancia de la esquina, con tramos rectos de este largo
private val ArcGap = 19.dp
private val ArcTail = 12.dp

private val CardLabelStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.03).em,
)

private val CardValueStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.045).em,
)

private val SmallLabelStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = (-0.01).em,
)

private val CaptionStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.01).em,
)

private val SliderTitleStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    letterSpacing = (-0.03).em,
)

private val PillTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = (-0.02).em,
)

/** La tarjeta y el deslizador que fijan lo redondas que son las esquinas en toda la app. */
@Composable
fun CornerRoundingSection(
    radiusDp: Float,
    defaultRadius: Float,
    maxRadius: Float,
    smooth: Boolean,
    colors: CustomiseColors,
    onRadiusChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf((radiusDp / 2f).roundToInt()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundingCardHeight)
                .clip(CardShape)
                .background(colors.card),
        ) {
            RoundingSwatch(radiusDp = radiusDp, maxRadius = maxRadius, smooth = smooth, colors = colors, modifier = Modifier.matchParentSize())
            CardTitle(
                title = "Redondez de las esquinas",
                canReset = kotlin.math.abs(radiusDp - defaultRadius) >= 0.5f,
                colors = colors,
                onReset = onReset,
            )
            Text(
                text = "${radiusDp.roundToInt()} dp",
                style = CardValueStyle,
                color = colors.text,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CardTextInset, bottom = 20.dp),
            )
        }

        Spacer(Modifier.height(11.dp))

        CustomiseSliderCard(
            title = "Ajústala",
            startLabel = "Cuadradas",
            endLabel = "Más redondas",
            fraction = radiusDp / maxRadius,
            colors = colors,
            valueText = "${radiusDp.roundToInt()} dp",
            onFractionChange = { f ->
                val next = (f * maxRadius).roundToInt().toFloat()
                val tick = (next / 2f).roundToInt()
                // un toque cada 2 dp, así el arrastre se siente con muescas
                if (tick != lastTick) {
                    lastTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onRadiusChange(next)
            },
        )
    }
}

// el título de una tarjeta grande arriba a la izquierda, con un pequeño "restablecer" al lado; se apaga mientras todo está en su valor de siempre
@Composable
fun CardTitle(title: String, canReset: Boolean, colors: CustomiseColors, onReset: (() -> Unit)?) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = Modifier.padding(start = CardTextInset, top = CardTextInset)) {
        Text(text = title, style = CardLabelStyle, color = colors.text.copy(alpha = 0.9f))
        if (onReset != null) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Restablecer",
                tint = colors.text.copy(alpha = if (canReset) 0.8f else 0.25f),
                modifier = Modifier
                    // subido para que quede en la primera línea del título
                    .offset(x = 2.dp, y = (-9).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canReset) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onReset()
                    }
                    .padding(8.dp),
            )
        }
    }
}

// la tarjeta delgada debajo de una grande: un título (y su valor), una línea de deslizador y lo que significa cada extremo
@Composable
fun CustomiseSliderCard(
    title: String,
    startLabel: String,
    endLabel: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    colors: CustomiseColors,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SliderCardShape)
            .background(colors.card)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(start = CardTextInset, end = CardTextInset, top = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = SliderTitleStyle, color = colors.text, modifier = Modifier.weight(1f))
            if (valueText != null) Text(text = valueText, style = SliderTitleStyle, color = colors.text.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        BareSlider(
            fraction = fraction, onFractionChange = onFractionChange, enabled = enabled, colors = colors,
            description = title, valueText = valueText, steps = steps,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(startLabel, style = SmallLabelStyle, color = colors.muted)
            Text(endLabel, style = SmallLabelStyle, color = colors.muted)
        }
    }
}

// la esquina grande metida arriba a la derecha de la tarjeta, con el arco abrazando su curva
@Composable
private fun RoundingSwatch(radiusDp: Float, maxRadius: Float, smooth: Boolean, colors: CustomiseColors, modifier: Modifier) {
    Canvas(modifier = modifier) {
        // la muestra exagera el radio (x1.5) para que se note la diferencia de 2 en 2 dp
        val r = (radiusDp.coerceIn(0f, maxRadius) * 1.5f).dp.toPx()
        val left = size.width - SwatchWidth.toPx()
        val bottom = SwatchHeight.toPx()
        // solo se ve la esquina de abajo a la izquierda; las otras tres quedan fuera de la tarjeta
        if (smooth && r > 0f) {
            val outline = SmoothCornerShape((r / density).dp).createOutline(
                Size(size.width - left + r, bottom + r), layoutDirection, this,
            )
            translate(left, -r) {
                (outline as? androidx.compose.ui.graphics.Outline.Generic)?.let { drawPath(it.path, colors.swatch) }
                    ?: drawRect(colors.swatch, size = Size(size.width - left + r, bottom + r))
            }
        } else {
            drawRoundRect(
                color = colors.swatch,
                topLeft = Offset(left, -r),
                size = Size(size.width - left + r, bottom + r),
                cornerRadius = CornerRadius(r, r),
            )
        }

        val gap = ArcGap.toPx()
        val tail = ArcTail.toPx()
        val arcR = r + gap
        val x = left - gap
        val y = bottom + gap
        val path = Path().apply {
            moveTo(x, y - arcR - tail)
            lineTo(x, y - arcR)
            arcTo(
                rect = Rect(left = x, top = y - arcR * 2f, right = x + arcR * 2f, bottom = y),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(x + arcR + tail, y)
        }
        drawPath(path = path, color = colors.highlight, style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// una línea sola como en el diseño: la parte brillante es cuánto va; se toca o se arrastra en cualquier punto
@Composable
private fun BareSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    enabled: Boolean,
    colors: CustomiseColors,
    description: String,
    valueText: String?,
    steps: Int,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shown = fraction.coerceIn(0f, 1f)
    val currentShown by rememberUpdatedState(shown)
    val currentOnChange by rememberUpdatedState(onFractionChange)

    fun updateFromX(x: Float) {
        currentOnChange((x / widthPx).coerceIn(0f, 1f))
    }

    // el punto del dedo, así un deslizador por escalones se mueve aunque cada movimiento sea menor que un escalón
    var dragX by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragX += delta
        updateFromX(dragX)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = description
                if (valueText != null) stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f, steps)
                if (enabled) setProgress { v -> currentOnChange(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(enabled) { if (enabled) detectTapGestures { offset -> updateFromX(offset.x) } }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { dragX = currentShown * widthPx },
            ),
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        drawLine(colors.text.copy(alpha = 0.2f), Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
        drawLine(colors.text, Offset(start, y), Offset(start + (end - start) * shown, y), stroke, StrokeCap.Round)
    }
}

// una tarjeta con un interruptor en la esquina, lo que controla a la izquierda y una vista previa a la derecha
@Composable
fun EffectCard(
    title: String,
    colors: CustomiseColors,
    modifier: Modifier = Modifier,
    toggle: (@Composable () -> Unit)? = null,
    value: String? = null,
    previewWidth: Dp = 96.dp,
    canReset: Boolean = false,
    onReset: (() -> Unit)? = null,
    preview: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StyleCardHeight)
            .clip(CardShape)
            .background(colors.card),
    ) {
        if (preview != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(top = 58.dp).width(previewWidth).height(StyleCardHeight - 58.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp)).background(colors.row),
                content = preview,
            )
        }
        CardTitle(title = title, canReset = canReset, colors = colors, onReset = onReset)
        if (toggle != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp)) { toggle() }
        Column(
            modifier = Modifier.padding(
                start = CardTextInset,
                top = 52.dp,
                end = if (preview != null) previewWidth + 18.dp else CardTextInset,
                bottom = if (value != null) 64.dp else 18.dp,
            ),
            content = content,
        )
        if (value != null) {
            Text(
                text = value,
                style = CardValueStyle,
                color = colors.text,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CardTextInset, bottom = 20.dp),
            )
        }
    }
}

// un selector pequeño de varias opciones con una píldora que se desliza hasta la elegida
@Composable
fun MiniSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    colors: CustomiseColors,
    enabled: Boolean = true,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.row)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.82f, stiffness = 520f), label = "miniSegment")
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.highlight),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (index == selectedIndex) colors.card else colors.text,
                    tween(160),
                    label = "miniSegmentText",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = index == selectedIndex
                        }
                        .clickable(remember { MutableInteractionSource() }, null, enabled = enabled) { onSelect(index) },
                ) {
                    Text(label, style = PillTextStyle, color = color)
                }
            }
        }
    }
}

/**
 * Las animaciones de toda la app (de AnimationSection de Mochy): apagadas lo detienen todo, y la
 * velocidad va encima de la escala de animación de Android.
 */
@Composable
fun AnimationSection(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    colors: CustomiseColors,
    toggle: @Composable (Boolean, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val stops = AnimationSpeeds
    val index = stops.indexOfFirst { it >= speed - 0.01f }.takeIf { it >= 0 } ?: stops.lastIndex

    Column(modifier.fillMaxWidth()) {
        EffectCard(
            title = "Animaciones",
            colors = colors,
            toggle = { toggle(enabled, onEnabled) },
            value = if (enabled) animationSpeedLabel(speed) else "No",
        ) {
            Text(
                text = if (speed == 1f) "Sigue la velocidad de animación de Android." else "Va encima de la velocidad de animación de Android.",
                style = CaptionStyle,
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = "Velocidad de las animaciones",
            valueText = animationSpeedLabel(speed),
            startLabel = "Más lentas",
            endLabel = "Más rápidas",
            fraction = index / stops.lastIndex.toFloat(),
            colors = colors,
            steps = stops.size - 2,
            onFractionChange = { f ->
                val next = stops[(f * stops.lastIndex).roundToInt()]
                if (next != speed) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSpeed(next)
                }
            },
            enabled = enabled,
        )
        extra()
    }
}
