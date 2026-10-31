/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken
import app.kaze.browser.Kaze

/** Servicio de pestañas personalizadas: las apps lo usan para preparar Fyx antes de abrir un enlace. */
class CustomTabs : CustomTabsService() {
    // Calentar: arranca el motor para que el enlace abra al instante.
    override fun warmup(flags: Long): Boolean = runCatching { Kaze.get(this).runtime; true }.getOrDefault(false)
    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean = true
    override fun mayLaunchUrl(sessionToken: CustomTabsSessionToken, url: Uri?, extras: Bundle?, otherLikelyBundles: MutableList<Bundle>?) = true
    override fun extraCommand(commandName: String, args: Bundle?): Bundle? = null
    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?) = false
    override fun requestPostMessageChannel(sessionToken: CustomTabsSessionToken, postMessageOrigin: Uri) = false
    override fun postMessage(sessionToken: CustomTabsSessionToken, message: String, extras: Bundle?) = RESULT_FAILURE_DISALLOWED
    override fun validateRelationship(sessionToken: CustomTabsSessionToken, relation: Int, origin: Uri, extras: Bundle?) = false
    override fun receiveFile(sessionToken: CustomTabsSessionToken, uri: Uri, purpose: Int, extras: Bundle?) = false
}
