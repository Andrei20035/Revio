package com.revio.app.data.local.auth

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
    val id: String
    val name: String

    init {
        val existing = preferences.getString("installation_id", null)
        id = existing ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).apply()
        }
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        name = "$manufacturer $model".trim().ifBlank { "Android device" }
    }
}
