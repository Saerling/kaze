// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: los resortes y curvas de app/src/main/kotlin/com/mochyy/mochymusic/ui/component/ContainerTransform.kt
// (ContainerOpenDamping/Stiffness, ContainerCloseMillis/Easing), la forma en que Home se hunde y
// vuelve al abrir una página en ui/screens/NavigationBuilder.kt (HomeRecedeSpring, HomeReturnEase)
// y la aparición de elementos de ui/component/RevealOnAppear.kt (RevealEasing, RevealFadeInMs,
// RevealSlideDistance y la ráfaga escalonada BurstStepMs/BurstMaxSteps).
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Cambios en Fyx: las piezas como transiciones de AnimatedVisibility (Fyx abre sus pantallas encima,
// sin NavHost) y sin el recorte que se transforma desde la tarjeta (necesitaría SharedTransitionLayout).

package app.kaze.browser.ui.mochy

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.unit.dp

// el resorte con el que una página crece desde su tarjeta
const val ContainerOpenDamping = 0.88f
const val ContainerOpenStiffness = 150f

// y la curva con la que vuelve a su sitio
const val ContainerCloseMillis = 560
val ContainerCloseEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

/** Curva de aparición de Mochy (frena fuerte al final). */
val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
const val RevealFadeInMs = 520
val RevealSlideDistance = 20.dp
const val BurstStepMs = 60
const val BurstMaxSteps = 6

/**
 * Una pantalla que se abre encima (la vista de pestañas): crece con el resorte del contenedor, como una
 * página de Mochy que sale de su tarjeta. El umbral pequeño evita que el resorte salte el último 1 %.
 */
fun containerEnter(): EnterTransition =
    scaleIn(spring(ContainerOpenDamping, ContainerOpenStiffness, visibilityThreshold = 0.001f), initialScale = 0.92f) +
        fadeIn(tween(220, easing = RevealEasing))

/** Al cerrarla vuelve con la curva de cierre del contenedor (más corta: 320 ms, para no estorbar). */
fun containerExit(): ExitTransition =
    scaleOut(tween(320, easing = ContainerCloseEasing), targetScale = 0.92f) +
        fadeOut(tween(220, easing = ContainerCloseEasing))
