package com.kadhafi.aetherhop.core.util

import android.content.Context
import android.os.Build
import java.util.UUID

object DeviceIdentity {
    private const val PREFS_NAME = "aetherhop_prefs"
    private const val KEY_DEVICE_ID = "key_device_id"
    private const val KEY_DEVICE_NAME = "key_device_name"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var name = prefs.getString(KEY_DEVICE_NAME, null)
        if (name == null) {
            name = "${Build.MODEL.ifBlank { "AetherHop" }}-${UUID.randomUUID().toString().take(4)}"
            prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
        }
        return name
    }
}
