/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Contraseñas guardadas de Fyx (como el gestor de Firefox): Gecko detecta los formularios de inicio de
 * sesión, pregunta si guardar y luego rellena solo. Se guardan cifradas (AES-GCM con una clave del
 * almacén seguro de Android, que nunca sale del teléfono).
 */
class Logins(context: Context) : Autocomplete.StorageDelegate {
    private val file = File(context.filesDir, "contrasenas.bin")
    private val items: MutableList<JSONObject> by lazy { load() }
    val count get() = items.size

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
            )
        }.generateKey()
    }

    private fun load(): MutableList<JSONObject> = runCatching {
        val raw = Base64.decode(file.readText(), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12)) }
        val arr = JSONArray(String(c.doFinal(raw, 12, raw.size - 12)))
        MutableList(arr.length()) { arr.getJSONObject(it) }
    }.getOrElse { mutableListOf() }

    @Synchronized
    private fun save() = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = c.doFinal(JSONArray(items).toString().toByteArray())
        file.writeText(Base64.encodeToString(c.iv + body, Base64.NO_WRAP))
    }

    private fun host(origin: String) = origin.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")

    private fun JSONObject.toEntry() = Autocomplete.LoginEntry.Builder()
        .guid(optString("guid")).origin(optString("origin")).formActionOrigin(optString("formActionOrigin").ifEmpty { null })
        .httpRealm(optString("httpRealm").ifEmpty { null }).username(optString("username")).password(optString("password")).build()

    @Synchronized
    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val h = host(domain)
        return GeckoResult.fromValue(
            items.filter { host(it.optString("origin")) == h }.sortedByDescending { it.optLong("used") }.map { it.toEntry() }.toTypedArray(),
        )
    }

    @Synchronized
    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        val existing = items.firstOrNull { (login.guid != null && it.optString("guid") == login.guid) ||
            (host(it.optString("origin")) == host(login.origin) && it.optString("username") == login.username) }
        val o = existing ?: JSONObject().put("guid", login.guid ?: UUID.randomUUID().toString()).also { items += it }
        o.put("origin", login.origin).put("formActionOrigin", login.formActionOrigin ?: "").put("httpRealm", login.httpRealm ?: "")
            .put("username", login.username).put("password", login.password).put("used", System.currentTimeMillis())
        save()
    }

    @Synchronized
    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        items.firstOrNull { it.optString("guid") == login.guid }?.put("used", System.currentTimeMillis())
        save()
    }

    @Synchronized
    fun clear() { items.clear(); file.delete() }

    private companion object { const val ALIAS = "fyx-contrasenas" }
}
