// Copiado de Mochy Music (https://github.com/realidkroo/Mochy-Music), de realidkroo.
// Origen: app/src/main/kotlin/com/mochyy/mochymusic/ui/utils/AppMotion.kt
// Licencia: GNU General Public License v3.0 (ver android/LICENSE y android/NOTICE.md).
// Cambios en Fyx: sin las funciones de Navigation (Fyx no usa NavHost), sin el desenfoque de
// elementos (Fyx no desenfoca) y con [mochyTransitions], el estilo de transiciones elegido en Ajustes.

package app.kaze.browser.ui.mochy

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale

// las velocidades en las que se detiene el control de animación, de la más lenta a la más rápida
val AnimationSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

object AppMotion {
    /** false: nada se anima, cada animación salta directo a su final */
    var enabled by mutableStateOf(true)

    /** cuánto más rápido que la velocidad de animación de Android se mueve todo; 1 es la de Android */
    var speed by mutableFloatStateOf(1f)

    /** true: transiciones de Mochy (resortes de contenedor); false: las de siempre de Fyx */
    var mochyTransitions by mutableStateOf(true)

    private var systemScale by mutableFloatStateOf(1f)

    /** "Quitar animaciones" de Android (escala 0) o el interruptor de Fyx apagado. */
    val reduced: Boolean get() = !enabled || systemScale == 0f

    /** se le pasa al recompositor de la ventana, así cada animación de la app lo lee en cada fotograma */
    val durationScale: MotionDurationScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = if (!enabled) 0f else systemScale / speed.coerceAtLeast(0.1f)
    }

    // sigue la escala de animación de Android, como hacía la escala propia de Compose antes de reemplazarla
    fun followSystemScale(context: Context): ContentObserver {
        val resolver = context.contentResolver
        fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        systemScale = read()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemScale = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        return observer
    }
}

/** cómo se lee una velocidad en pantalla: 1x, 1.25x, 0.5x */
fun animationSpeedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "${text}x"
}
