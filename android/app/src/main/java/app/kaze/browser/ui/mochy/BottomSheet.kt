// Adaptado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/component/BottomSheet.kt
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Cambios en Fyx: de la hoja del reproductor de Mochy (con mini reproductor) solo se toman su
// movimiento y su arrastre: los umbrales al soltar (SheetReleaseSpeed, SheetCloseTravel,
// SheetCloseFlick), el asentado críticamente amortiguado que sigue la velocidad del dedo
// (SettleOmega/MaxSettleOmega, de BottomSheetState.release) y el paso del arrastre de una lista que
// llega a su borde superior a la hoja (preUpPostDownNestedScrollConnection). Todo lo del reproductor
// se quitó, y la hoja es modal (ventana propia, velo y "Atrás") para sustituir a ModalBottomSheet.

package app.kaze.browser.ui.mochy

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.abs

// suéltala más rápido que esto y va hacia donde iba el dedo, sin importar la distancia
private val SheetReleaseSpeed = 300.dp

// suéltala más despacio y se cierra si se bajó al menos esto; menos, vuelve a abrirse
// Fyx: más que en Mochy (36 dp) para que la hoja no se cierre sin querer al desplazar su contenido.
private val SheetCloseTravel = 120.dp

// un tirón rápido hacia abajo que acabó antes de mover la hoja también la cierra, con esta velocidad y recorrido
private val SheetCloseFlick = 900.dp
private val SheetCloseFlickTravel = 12.dp

// lo rápido que se asienta una hoja soltada quieta (el ritmo del resorte suave); un dedo más rápido lo sube
private const val SettleOmega = 8.94f
private const val MaxSettleOmega = 42f

/** El velo detrás de la hoja abierta. */
private const val ScrimAlpha = 0.32f

/**
 * Hoja modal con el movimiento de la hoja de Mochy: sube con el resorte suave, sigue al dedo y al
 * soltarla se asienta a la velocidad que traía, sin rebotar. Tocar fuera, "Atrás" o bajarla la cierran
 * (primero baja, luego [onDismissRequest]).
 */
@Composable
fun MochyModalSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    shape: Shape,
    lightSystemBars: Boolean,
    dragHandle: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Cuánto está bajada la hoja, en px (0: abierta del todo; su alto: escondida).
    val offset = remember { Animatable(Float.MAX_VALUE) }
    var height by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    // Recorrido del arrastre actual (para el tirón rápido que no alcanzó a mover la hoja).
    var dragged by remember { mutableFloatStateOf(0f) }

    // De BottomSheetState.release de Mochy: críticamente amortiguado, al menos tan rápido como el dedo.
    fun settle(target: Float, velocity: Float) {
        val from = offset.value.coerceAtMost(height)
        val distance = abs(target - from)
        val toward = if (target < from) -velocity else velocity
        val omega = maxOf(SettleOmega, if (distance > 0.5f && toward > 0f) toward / distance else 0f)
            .coerceAtMost(MaxSettleOmega)
        scope.launch {
            offset.animateTo(target, spring(dampingRatio = 1f, stiffness = omega * omega), initialVelocity = velocity)
            if (target > 0f && closing) dismiss()
        }
    }

    fun close(velocity: Float = 0f) {
        if (closing) return
        closing = true
        if (height <= 0f) dismiss() else settle(height, velocity)
    }

    fun drag(delta: Float) {
        if (closing) return
        dragged += delta
        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, height)) }
    }

    // velocidad en px/s, hacia abajo positiva
    fun release(velocity: Float) {
        if (closing) return
        val releaseSpeed = with(density) { SheetReleaseSpeed.toPx() }
        val closeTravel = with(density) { SheetCloseTravel.toPx() }
        val flick = with(density) { SheetCloseFlick.toPx() }
        val flickTravel = with(density) { SheetCloseFlickTravel.toPx() }
        val shouldClose = when {
            velocity > flick && dragged > flickTravel -> true
            abs(velocity) > releaseSpeed -> velocity > 0f
            else -> offset.value > closeTravel
        }
        dragged = 0f
        if (shouldClose) close(velocity) else settle(0f, velocity)
    }

    // Una lista dentro de la hoja: al llegar arriba, seguir bajando el dedo baja la hoja; al subirlo, la vuelve a abrir.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && offset.value > 0f && source == NestedScrollSource.UserInput) {
                    val take = (-available.y).coerceAtMost(offset.value)
                    drag(-take)
                    return Offset(0f, -take)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value > 0f && offset.value < height) {
                    release(available.y)
                    return available
                }
                dragged = 0f
                return Velocity.Zero
            }
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: (view.context as? Activity)?.window
            if (window != null) {
                window.setDimAmount(0f)
                @Suppress("DEPRECATION")
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightNavigationBars = lightSystemBars
                    isAppearanceLightStatusBars = false
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val shown = if (height > 0f) (1f - offset.value / height).coerceIn(0f, 1f) else 0f
            // velo: tocarlo cierra
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = shown }
                    .background(Color.Black.copy(alpha = ScrimAlpha))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
                    .semantics { contentDescription = "Cerrar hoja" },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight - top)
                    .onSizeChanged { size ->
                        val h = size.height.toFloat()
                        if (h != height) {
                            val first = height == 0f
                            height = h
                            if (first) scope.launch { offset.snapTo(h); settle(0f, 0f) }
                            else if (offset.value > h) scope.launch { offset.snapTo(h) }
                        }
                    }
                    .graphicsLayer { translationY = offset.value.coerceAtMost(if (height > 0f) height else 10_000f) }
                    .clip(shape)
                    .background(containerColor)
                    .semantics { paneTitle = "Hoja" }
                    .nestedScroll(nested)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> drag(delta) },
                        onDragStarted = { dragged = 0f },
                        onDragStopped = { velocity -> release(velocity) },
                    )
                    .imePadding(),
            ) {
                Box(
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = "Cerrar hoja"
                        onClick { close(); true }
                    },
                    contentAlignment = Alignment.Center,
                ) { dragHandle() }
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    content()
                }
            }
        }
    }
}
