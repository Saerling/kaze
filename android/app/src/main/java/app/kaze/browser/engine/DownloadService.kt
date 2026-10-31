/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.kaze.browser.Kaze
import app.kaze.browser.MainActivity

/**
 * Mantiene vivas las descargas aunque salgas de Fyx (Android no cierra la app mientras haya una descarga)
 * y muestra su progreso en una notificación. Se detiene sola al terminar; al final avisa que acabó.
 */
class DownloadService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val items = Kaze.get(this@DownloadService).downloads.items
            val running = items.filter { it.state == DownloadItem.State.RUNNING }
            if (running.isEmpty()) {
                val last = items.firstOrNull()
                if (last != null) nm().notify(DONE_ID, done(last))
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            nm().notify(ID, progress(running))
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel(this)
        val running = Kaze.get(this).downloads.items.filter { it.state == DownloadItem.State.RUNNING }
        ServiceCompat.startForeground(
            this, ID, progress(running),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        main.removeCallbacks(tick)
        main.postDelayed(tick, 500)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun open() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progress(running: List<DownloadItem>): Notification {
        val total = running.sumOf { it.total.coerceAtLeast(0) }
        val done = running.sumOf { it.done }
        val title = if (running.size == 1) running[0].name else "Descargando ${running.size} archivos"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (total > 0) "${android.text.format.Formatter.formatShortFileSize(this, done)} de ${android.text.format.Formatter.formatShortFileSize(this, total)}" else "Descargando…")
            .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(open())
            .build()
    }

    private fun done(item: DownloadItem): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (item.state == DownloadItem.State.DONE) "Descarga completa" else "No se pudo descargar")
            .setContentText(item.name)
            .setAutoCancel(true)
            .setContentIntent(open())
            .build()

    companion object {
        private const val CHANNEL = "descargas"
        private const val ID = 4201
        private const val DONE_ID = 4202

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

        fun start(context: Context) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
