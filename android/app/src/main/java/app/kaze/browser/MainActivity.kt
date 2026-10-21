/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kaze.browser.ui.BrowserScreen
import app.kaze.browser.ui.theme.KazeTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as KazeApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) abrirDesdeIntent(intent)

        setContent {
            KazeTheme {
                BrowserScreen(app.tabs)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeIntent(intent)
    }

    /** Enlaces abiertos desde otras apps, o búsquedas del sistema. */
    private fun abrirDesdeIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { app.tabs.newTab(it) }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let {
                app.tabs.newTab(UrlUtils.toUrl(it))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        app.tabs.persist()
    }
}
