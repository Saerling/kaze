/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent

/** Recibe los enlaces de otras apps: si piden una pestaña personalizada se abre aquí mismo; si no, en Fyx. */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val custom = intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)
        startActivity(
            Intent(intent).setClass(this, if (custom) CustomTabActivity::class.java else MainActivity::class.java)
                .apply { if (!custom) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
        finish()
    }
}
