package com.wakaroute.core.auth

/**
 * Where credentials live.
 *
 * An interface in `core` so the session logic can be tested without a device,
 * with the real implementation backed by the Android Keystore. §9 is absolute
 * about this: **never a plain `SharedPreferences` or `DataStore`.** The device
 * secret is what proves a student owns three years of study records, and a
 * plain-text copy of it can be read off a rooted phone or lifted out of a
 * backup.
 *
 * Reads and writes can fail — a Keystore entry can be invalidated by a lock
 * screen change, and hardware-backed keys can be evicted. Failures return null
 * or throw rather than being papered over, because a silently empty store looks
 * exactly like a fresh install and would create a second account for a student
 * who already had one.
 */
interface SecretStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun delete(key: String)

    companion object {
        const val DEVICE_SECRET = "deviceSecret"
        const val DEVICE_ID = "deviceId"
        const val REFRESH_TOKEN = "refreshToken"
    }
}

/** For tests. Never reachable from the app. */
class InMemorySecretStore(initial: Map<String, String> = emptyMap()) : SecretStore {
    private val values = initial.toMutableMap()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }

    override fun delete(key: String) {
        values.remove(key)
    }

    val stored: Map<String, String> get() = values.toMap()
}
