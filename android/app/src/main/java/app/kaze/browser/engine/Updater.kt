/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kaze.browser.BuildConfig
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en GitHub Releases. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Actualizaciones desde GitHub: cada compilación de main se publica como Release con etiqueta
 * `android-<versionCode>` y un APK por procesador. La app compara con su versión, descarga el
 * APK que le corresponde y lo instala con PackageInstaller (como una actualización normal,
 * sin desinstalar, porque todas las versiones van firmadas con la misma clave).
 */
class Updater(private val context: Context) {

    enum class Phase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, INSTALLING, UP_TO_DATE, ERROR }

    var phase by mutableStateOf(Phase.IDLE)
        internal set
    var available by mutableStateOf<UpdateInfo?>(null)
        internal set
    var progress by mutableFloatStateOf(0f)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    /** La hoja de actualización se muestra mientras esto sea true. */
    var showSheet by mutableStateOf(false)

    private val prefs = context.getSharedPreferences("kaze-update", Context.MODE_PRIVATE)
    private val apk = File(context.cacheDir, "update/kaze-update.apk")

    var autoCheck: Boolean
        get() = prefs.getBoolean("auto", true)
        set(v) = prefs.edit().putBoolean("auto", v).apply()

    /** ¿Toca revisar automáticamente? (como mucho cada 3 horas, para no gastar datos ni la cuota de GitHub). */
    fun shouldAutoCheck(): Boolean =
        autoCheck && System.currentTimeMillis() - prefs.getLong("last_check", 0) > 3 * 3_600_000L

    /** Bloqueante (llamar en segundo plano). Devuelve la actualización si hay una más nueva. */
    fun check(): UpdateInfo? {
        phase = Phase.CHECKING
        error = null
        return try {
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            val info = latestRelease()
            available = info?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            phase = if (available != null) Phase.AVAILABLE else Phase.UP_TO_DATE
            available
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo buscar actualizaciones", e)
            error = "No se pudo conectar con GitHub"
            phase = Phase.ERROR
            null
        }
    }

    /** No volver a avisar automáticamente de esta versión (sí aparece si se busca a mano). */
    fun skip(info: UpdateInfo) {
        prefs.edit().putInt("skipped", info.versionCode).apply()
        showSheet = false
    }

    fun isSkipped(info: UpdateInfo) = prefs.getInt("skipped", 0) >= info.versionCode

    private fun latestRelease(): UpdateInfo? = parseReleases(
        get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=15"),
        Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    )

    /** Bloqueante: descarga el APK con progreso. */
    fun download(info: UpdateInfo): File? {
        phase = Phase.DOWNLOADING
        progress = 0f
        error = null
        return try {
            apk.parentFile?.mkdirs()
            var conn = open(info.apkUrl)
            // GitHub redirige a otro dominio para los archivos; seguir a mano por si cambia de protocolo.
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                conn = open(next)
            }
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                apk.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            conn.disconnect()
            apk
        } catch (e: Exception) {
            Log.w(TAG, "Descarga fallida", e)
            error = "La descarga falló. Revisa tu conexión e inténtalo de nuevo."
            phase = Phase.ERROR
            null
        }
    }

    /** ¿Android permite a Kaze instalar apps? Si no, hay que activarlo una vez en Ajustes. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Entrega el APK al instalador de Android como actualización de Kaze. */
    fun install(file: File) {
        phase = Phase.INSTALLING
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("kaze.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                val pending = PendingIntent.getBroadcast(
                    context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Instalación fallida", e)
            error = "No se pudo instalar: ${e.message}"
            phase = Phase.ERROR
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        if (status == PackageInstaller.STATUS_SUCCESS) return // la app se reinicia sola con la versión nueva
        phase = Phase.ERROR
        error = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalación cancelada"
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "La versión instalada tiene otra firma: desinstálala una vez y vuelve a instalar."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "No hay espacio suficiente"
            else -> "No se pudo instalar" + (message?.let { ": $it" } ?: "")
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        setRequestProperty("User-Agent", "Pyx/${BuildConfig.VERSION_NAME}")
    }

    private fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val TAG = "PyxUpdater"
        const val ACTION_INSTALL_STATUS = "app.kaze.browser.INSTALL_STATUS"

        /** La versión Android más nueva de la respuesta de GitHub, con el APK para el procesador [abi]. */
        fun parseReleases(json: String, abi: String): UpdateInfo? {
            val releases = JSONArray(json)
            var best: UpdateInfo? = null
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                val tag = r.optString("tag_name")
                if (r.optBoolean("draft") || r.optBoolean("prerelease") || !tag.startsWith("android-")) continue
                val code = tag.removePrefix("android-").toIntOrNull() ?: continue
                if (best != null && code <= best.versionCode) continue
                val assets = r.optJSONArray("assets") ?: continue
                val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk") }
                val asset = apks.firstOrNull { abi.isNotEmpty() && it.optString("name").contains(abi) }
                    ?: apks.firstOrNull() ?: continue
                best = UpdateInfo(
                    versionCode = code,
                    versionName = r.optString("name").ifBlank { tag },
                    notes = r.optString("body").trim(),
                    apkUrl = asset.getString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                )
            }
            return best
        }
    }
}

/** Recibe el resultado del instalador de Android. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android pide confirmar ("¿Quieres actualizar esta app?"): mostrar su diálogo.
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
            return
        }
        app.kaze.browser.Kaze.ifCreated {
            it.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
