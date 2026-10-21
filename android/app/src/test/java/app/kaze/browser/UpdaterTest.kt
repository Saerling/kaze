package app.kaze.browser

import app.kaze.browser.engine.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdaterTest {
    private fun rel(tag: String, vararg apks: String, pre: Boolean = false) = """
        {"tag_name":"$tag","name":"Kaze $tag","body":"cambios","prerelease":$pre,"draft":false,
         "assets":[${apks.joinToString(",") { """{"name":"$it","size":100,"browser_download_url":"https://x/$it"}""" }}]}"""

    @Test fun eligeLaMasNuevaYSuProcesador() {
        val json = "[" + listOf(
            rel("android-104", "kaze-0.3.104-arm64-v8a.apk", "kaze-0.3.104-armeabi-v7a.apk"),
            rel("android-106", "kaze-0.3.106-armeabi-v7a.apk", "kaze-0.3.106-arm64-v8a.apk"),
            rel("desktop-200", "kaze.zip"),
            rel("android-999", "x.apk", pre = true),
        ).joinToString(",") + "]"
        val u = Updater.parseReleases(json, "arm64-v8a")!!
        assertEquals(106, u.versionCode)
        assertEquals("https://x/kaze-0.3.106-arm64-v8a.apk", u.apkUrl)
    }

    @Test fun sinVersionesAndroid() {
        assertNull(Updater.parseReleases("[" + rel("desktop-1", "a.zip") + "]", "arm64-v8a"))
    }
}
