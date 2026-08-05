package com.wakaroute.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wakaroute.core.auth.SecretStore

/**
 * Credentials, encrypted with a key held by the Android Keystore.
 *
 * §9 and the 実装ガイド both rule out plain `SharedPreferences` here. The
 * `deviceSecret` is what proves a student owns three years of study records; in
 * plain text it can be read off a rooted phone or lifted out of a backup. The
 * app also excludes itself from backup entirely (see the manifest and
 * `res/xml/`), because a *restored* secret is worse than a lost one — two
 * installs would claim the same learner.
 *
 * Note what is **not** here: [AppPreferences] keeps the search filters and the
 * intro flag in an ordinary `DataStore`, and that is correct. The line between
 * the two files is the line between "not worth protecting" and "protects an
 * account", and it is easier to keep when they are different classes.
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(key: String): String? = preferences.getString(key, null)

    /**
     * `commit()`, and lint's advice to use `apply()` is suppressed rather than
     * followed.
     *
     * That advice is right for ordinary preferences and wrong here. The device
     * secret is returned by the server exactly once. An asynchronous write that
     * loses a race with process death leaves an account on the server that this
     * install can never prove it owns — no error, no retry, and nothing to
     * recover from. A few milliseconds on a once-per-install write is not a
     * trade worth making.
     *
     * The same reasoning covers deletion: a spent refresh token that survives
     * because its removal was still queued is a token that can be replayed.
     */
    @SuppressLint("ApplySharedPref")
    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).commit()
    }

    @SuppressLint("ApplySharedPref")
    override fun delete(key: String) {
        preferences.edit().remove(key).commit()
    }

    private companion object {
        const val FILE_NAME = "wakaroute_secure"
    }
}
