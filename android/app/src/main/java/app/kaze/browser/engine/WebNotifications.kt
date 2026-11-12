/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.kaze.browser.MainActivity
import app.kaze.browser.R
import app.kaze.browser.data.Urls
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

/**
 * Notificaciones de los sitios (chats, correo, calendarios...) como notificaciones de Android.
 *
 * Llegan mientras la pestaña del sitio está despierta. Al tocarlas se abre Fyx y se le avisa a la página,
 * que es la que decide qué mostrar. El permiso por sitio lo pregunta Fyx antes (diálogo "enviarte notificaciones").
 */
class WebNotifications(private val context: Context) : WebNotificationDelegate {
    private val live = HashMap<String, WebNotification>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    override fun onShowNotification(notification: WebNotification) {
        runCatching {
            if (!allowed()) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sitios web", NotificationManager.IMPORTANCE_DEFAULT))
            val tag = notification.tag
            synchronized(live) {
                live[tag] = notification
                // Tope por si un sitio envía muchísimas: las más viejas dejan de avisar a la página al tocarlas.
                if (live.size > MAX) live.keys.take(live.size - MAX).forEach(live::remove)
            }
            val open = Intent(context, MainActivity::class.java).setAction(ACTION_CLICK).putExtra(EXTRA_TAG, tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(
                context, tag.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val site = if (notification.privateBrowsing) null else notification.source?.let(Urls::displayHost)?.takeIf { it.isNotBlank() }
            val built = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(notification.title.orEmpty().ifBlank { site ?: "Fyx" })
                .setContentText(notification.text)
                .setStyle(Notification.BigTextStyle().bigText(notification.text))
                .setSubText(site)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            manager.notify(tag, ID, built)
            notification.show()
        }.onFailure { Log.w(TAG, "No se pudo mostrar la notificación", it) }
    }

    override fun onCloseNotification(notification: WebNotification) {
        synchronized(live) { live.remove(notification.tag) }
        runCatching { manager.cancel(notification.tag, ID) }
    }

    /** El usuario tocó la notificación: se le avisa a la página (si su pestaña sigue viva). */
    fun click(tag: String) {
        val n = synchronized(live) { live.remove(tag) } ?: return
        runCatching { n.click() }
    }

    companion object {
        const val ACTION_CLICK = "app.kaze.browser.NOTIFICACION_SITIO"
        const val EXTRA_TAG = "tag"
        private const val CHANNEL = "sitios"
        private const val ID = 7301
        private const val MAX = 64
        private const val TAG = "FyxNotif"
    }
}
