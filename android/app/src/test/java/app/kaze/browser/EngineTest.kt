package app.kaze.browser

import androidx.test.core.app.ApplicationProvider
import app.kaze.browser.data.Settings
import app.kaze.browser.engine.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La configuración del motor se arma igual que en el celular: si Gecko la rechaza, falla aquí. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineTest {
    private val settings = Settings(ApplicationProvider.getApplicationContext())

    @Test fun textoNormalUsaElTamanoDelSistema() {
        settings.textSize = 100
        val built = Engine.runtimeSettings(settings).build()
        assertTrue(built.automaticFontSizeAdjustment)
    }

    @Test fun textoGrandeArrancaSinError() {
        for (size in listOf(50, 85, 115, 150, 200)) {
            settings.textSize = size
            val built = Engine.runtimeSettings(settings).build()
            assertFalse(built.automaticFontSizeAdjustment)
        }
        settings.textSize = 100
    }

    @Test fun cambiarTamanoEnVivo() {
        settings.textSize = 100
        val built = Engine.runtimeSettings(settings).build()
        // Como al arrancar con el tamaño guardado y luego moverlo con el botón aA.
        for (size in listOf(100, 130, 100, 85, 200, 100)) {
            Engine.applyTextSize(built, size)
            assertEquals(size == 100, built.automaticFontSizeAdjustment)
            if (size != 100) assertEquals(size / 100f, built.fontSizeFactor, 0.001f)
        }
    }
}
