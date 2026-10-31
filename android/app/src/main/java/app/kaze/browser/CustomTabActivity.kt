/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.kaze.browser.data.Urls
import app.kaze.browser.ui.theme.KazeType
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * Pestaña personalizada: una página abierta desde otra app (Gmail, WhatsApp…) en una ventana ligera de Fyx,
 * con cerrar, compartir y "Abrir en Fyx". Usa el mismo motor (y sesiones iniciadas) que el navegador.
 */
class CustomTabActivity : ComponentActivity() {
    private var session: GeckoSession? = null
    private var title by mutableStateOf("")
    private var url by mutableStateOf("")
    private var progress by mutableFloatStateOf(0f)
    private var secure by mutableStateOf(true)
    private var canGoBack by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent.dataString ?: return finish()
        url = start
        val app = Kaze.get(this)
        val s = GeckoSession(GeckoSessionSettings.Builder().useTrackingProtection(true).build())
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, u: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                u?.let { url = it }
            }
            override fun onCanGoBack(session: GeckoSession, b: Boolean) { canGoBack = b }
        }
        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, t: String?) { title = t.orEmpty() }
        }
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, p: Int) { progress = if (p >= 100) 0f else p / 100f }
            override fun onSecurityChange(session: GeckoSession, info: GeckoSession.ProgressDelegate.SecurityInformation) { secure = info.isSecure }
        }
        s.open(app.runtime)
        s.loadUri(start)
        session = s
        // Color de la barra que pide la app (si lo pide), con texto legible encima.
        val asked = intent.getIntExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val bar = if (asked != 0) Color(asked) else if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F8)
            val ink = if (bar.luminance() > 0.5f) Color(0xFF1D1D1F) else Color.White
            BackHandler { if (canGoBack) session?.goBack() else finish() }
            Column(Modifier.fillMaxSize().background(bar)) {
                Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn(Icons.Outlined.Close, "Cerrar", ink) { finish() }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(title.ifBlank { Urls.displayHost(url) }, style = KazeType.subhead.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (secure) Icon(Icons.Outlined.Lock, null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(11.dp).padding(end = 2.dp))
                            Text(Urls.displayHost(url), style = KazeType.footnote, color = ink.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                    Btn(Icons.Outlined.Share, "Compartir", ink) {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null))
                    }
                    Btn(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir en Fyx", ink) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(this@CustomTabActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (progress > 0f) Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Color(0xFF0A84FF)))
                }
                AndroidView(
                    factory = { GeckoView(it).apply { setSession(s) } },
                    modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                )
            }
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
