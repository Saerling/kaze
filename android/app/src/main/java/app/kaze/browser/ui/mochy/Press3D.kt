// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/component/Press3D.kt
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Copyright: realidkroo y colaboradores de Mochy Music (sin aviso de copyright propio en el repositorio;
// Mochy es un fork de ViviMusic, de vivizzz007, que parte de Metrolist, de Mostafa Alagamy; ambos GPL-3.0).
// Modificado por Fyx, 2026-11.
// Cambios en Fyx: el paquete, y la inclinación se suelta al pasar el umbral de arrastre.

package app.kaze.browser.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

private const val DefaultMaxTilt = 7f

private const val DefaultPressedScale = 0.97f

private val TiltSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 420f,
)

fun Modifier.press3D(
    maxTilt: Float = DefaultMaxTilt,
    pressedScale: Float = DefaultPressedScale,
    enabled: Boolean = true,

    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }

    var normalized by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {

                    val down = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.firstOrNull { it.pressed } ?: continue

                    val bounds = this.size
                    if (bounds.width > 0 && bounds.height > 0) {
                        val p = down.position
                        normalized = Offset(
                            x = ((p.x / bounds.width) * 2f - 1f).coerceIn(-1f, 1f),
                            y = ((p.y / bounds.height) * 2f - 1f).coerceIn(-1f, 1f),
                        )
                        size = bounds
                    }
                    scope.launch { press.animateTo(1f, TiltSpring) }

                    // Fyx: pasado el umbral de arrastre (desplazar la lista, deslizar para cerrar, reordenar)
                    // la inclinación se suelta, así no acompaña al gesto.
                    val slop = viewConfiguration.touchSlop
                    val start = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                        val moved = event.changes.firstOrNull { it.id == down.id }?.let { (it.position - start).getDistance() } ?: 0f
                        if (moved > slop) {
                            scope.launch { press.animateTo(0f, TiltSpring) }
                            while (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) Unit
                            break
                        }
                    }
                    scope.launch { press.animateTo(0f, TiltSpring) }
                }
            }
        }
        .graphicsLayer {
            val t = press.value
            if (t > 0f) {
                val s = 1f - (1f - pressedScale) * t
                scaleX = s
                scaleY = s

                rotationX = -normalized.y * maxTilt * t
                rotationY = normalized.x * maxTilt * t

                cameraDistance = (size.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                transformOrigin = origin
            }
        }
}
