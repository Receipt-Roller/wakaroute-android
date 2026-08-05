package com.wakaroute.core.auth

import java.util.UUID

/**
 * The identifier for this installation.
 *
 * **Not a credential.** It is a label saying which install is calling, and the
 * server treats it as one: sending only a `deviceId` returns 401, by design.
 * `ANDROID_ID` can be read off the device, so if it authenticated anything,
 * whoever read it would own that student's account.
 *
 * The server rejects anything under 16 characters. The reason is worth keeping
 * in mind rather than treating as a validation rule: short identifiers collide
 * between installs, and a collision here connects one student to another
 * student's three years of records.
 */
interface DeviceIdProvider {
    /** Stable for the life of the install. */
    fun deviceId(): String
}

/**
 * Generates an id once and keeps it.
 *
 * Deliberately a generated UUID rather than `Settings.Secure.ANDROID_ID`. The
 * 実装ガイド allows either, and a UUID is better on both counts that matter: it
 * is not readable by other apps, and it is not tied to hardware the student
 * might share or resell. The cost — a factory reset or a reinstall makes a new
 * one — is exactly what account linking (§6 of the 実装ガイド) exists to solve.
 */
class StoredDeviceIdProvider(private val store: SecretStore) : DeviceIdProvider {

    override fun deviceId(): String {
        store.read(SecretStore.DEVICE_ID)
            ?.takeIf { it.length >= MINIMUM_LENGTH }
            ?.let { return it }

        // 32 hex characters, comfortably over the server's minimum.
        val generated = UUID.randomUUID().toString().replace("-", "")
        store.write(SecretStore.DEVICE_ID, generated)
        return generated
    }

    companion object {
        /** The server rejects anything shorter with a 400. */
        const val MINIMUM_LENGTH = 16
    }
}
