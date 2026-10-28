package io.github.yasarmahomedabbas.immichtv.data.settings

import android.content.SharedPreferences

/** Small string values kept on the device. */
internal interface StringStorage {
    fun get(key: String): String?

    /** Writes every entry together; a null value removes that key. */
    fun put(values: Map<String, String?>)
}

/** Seals a secret so only this install on this device can read it back. */
internal interface SecretCipher {
    fun encrypt(plain: String): String

    /** Throws if [sealed] can't be opened, for example after the device key was lost. */
    fun decrypt(sealed: String): String
}

/** [StringStorage] on private SharedPreferences. Needs a device, so it stays this thin. */
internal class SharedPreferencesStorage(private val prefs: SharedPreferences) : StringStorage {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(values: Map<String, String?>) {
        val editor = prefs.edit()
        for ((key, value) in values) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        editor.apply()
    }
}
