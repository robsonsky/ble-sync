package com.hailie.adapters.android.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.hailie.domain.DeviceId
import com.hailie.runtime.ports.StateStorePort
import com.hailie.runtime.ports.SyncSnapshot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidStateStore(
    context: Context,
    private val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        },
) : StateStorePort {
    private val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs =
        EncryptedSharedPreferences.create(
            "ble_sync_store",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun key(deviceId: DeviceId) = "snap:${deviceId.raw}"

    override fun write(snap: SyncSnapshot) {
        try {
            prefs.edit().putString(key(snap.deviceId), json.encodeToString(snap)).commit()
        } catch (_: Throwable) {
            // Corruption fallback
            prefs.edit().remove(key(snap.deviceId)).commit()
        }
    }

    override fun read(deviceId: DeviceId): SyncSnapshot? =
        try {
            prefs.getString(key(deviceId), null)?.let { json.decodeFromString<SyncSnapshot>(it) }
        } catch (_: Throwable) {
            null
        }
}
