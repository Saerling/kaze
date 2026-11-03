// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/component/TopToast.kt
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Copyright: realidkroo y colaboradores de Mochy Music (sin aviso de copyright propio en el repositorio;
// Mochy es un fork de ViviMusic, de vivizzz007, que parte de Metrolist, de Mostafa Alagamy; ambos GPL-3.0).
// Modificado por Fyx, 2026-11.
// Cambios en Fyx: sin el TopToaster global (el aviso lo da BrowserUi y llega por parámetros), sin
// carátula ni recursos de Mochy (íconos de Material), el botón de acción con área de 44 dp, el color
// del botón de acción y la posición (abajo, encima de la barra; se desliza hacia abajo para quitarlo).

package app.kaze.browser.ui.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable
data class TopToast(
    val id: Long,
    val text: String,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val durationMs: Long,
)

private val ToastCard = Color(0xFF1E1E1E)
private val ToastSquare = Color(0xFFD9D9D9)
private val ToastEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val ToastTitle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = (-0.3).sp,
    color = Color.White,
)

/**
 * Dibuja [toast] abajo del [BoxScope] (Mochy lo deja caer desde arriba; en un navegador la barra de
 * direcciones puede estar arriba, así que aquí sube desde abajo). Un aviso nuevo reemplaza al que se ve.
 * [onDismiss] se llama cuando se acaba su tiempo, se desliza para quitarlo o se toca su acción.
 */
@Composable
fun BoxScope.TopToastHost(toast: TopToast?, onDismiss: (Long) -> Unit, accent: Color, modifier: Modifier = Modifier) {
    // El aviso que está saliendo (ya reemplazado o quitado) no responde a toques: su "Deshacer" ya no vale.
    val currentId by rememberUpdatedState(toast?.id)
    AnimatedContent(
        targetState = toast,
        contentKey = { it?.id },
        transitionSpec = {
            (
                slideInVertically(spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMedium)) { it + 40 } +
                    fadeIn(tween(160)) +
                    scaleIn(tween(320, easing = ToastEase), initialScale = 0.92f)
                ) togetherWith (
                slideOutVertically(tween(260, easing = ToastEase)) { it + 40 } + fadeOut(tween(200))
                ) using SizeTransform(clip = false) { _, _ -> tween(1) }
        },
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp),
        label = "topToast",
    ) { t ->
        if (t != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { ToastPill(t, active = t.id == currentId, onDismiss, accent) }
        }
    }
}

@Composable
private fun ToastPill(toast: TopToast, active: Boolean, onDismiss: (Long) -> Unit, accent: Color) {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val lift = remember(toast.id) { Animatable(0f) }
    var held by remember(toast.id) { mutableStateOf(false) }
    LaunchedEffect(toast.id, held) {
        if (held) return@LaunchedEffect
        delay(toast.durationMs)
        dismiss(toast.id)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .graphicsLayer { translationY = lift.value }
            .shadow(14.dp, RoundedCornerShape(30.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(30.dp))
            .background(ToastCard)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .pointerInput(toast.id, active) {
                if (!active) return@pointerInput
                // deslizarlo hacia abajo lo quita; solo sigue al dedo en esa dirección
                detectVerticalDragGestures(
                    onDragStart = { held = true },
                    onDragEnd = {
                        held = false
                        if (lift.value > 28.dp.toPx()) dismiss(toast.id)
                        else scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                    onDragCancel = {
                        held = false
                        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                ) { _, amount -> scope.launch { lift.snapTo((lift.value + amount).coerceAtLeast(0f)) } }
            }
            .defaultMinSize(minHeight = 52.dp)
            .padding(start = 18.dp, end = if (toast.actionLabel != null) 8.dp else 12.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = toast.text,
            style = ToastTitle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
        )
        if (toast.actionLabel != null) {
            // Área táctil de 44 dp de alto, como antes en Fyx.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        enabled = active,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (!active) return@clickable
                        toast.onAction?.invoke()
                        dismiss(toast.id)
                    }
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(accent).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    if (toast.actionLabel == "Deshacer") {
                        Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(toast.actionLabel, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 12.dp).size(26.dp).clip(CircleShape).background(ToastSquare),
            ) {
                Icon(Icons.Outlined.Check, null, tint = Color(0xFF232323), modifier = Modifier.size(16.dp))
            }
        }
    }
}
