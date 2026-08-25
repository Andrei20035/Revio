package com.revio.social.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the Android-level layers of notification state (push-notifications plan, §11) that
 * Revio's own preferences can't see: the `POST_NOTIFICATIONS` runtime permission (API 33+ only),
 * the app's global notification toggle, and each channel's importance. Revio preferences govern
 * whether the server *sends*; this governs whether Android would ever *show* it.
 */
@Singleton
class NotificationPermissionState @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True below API 33 (no runtime permission exists there) or once the user has granted it. */
    fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** The app-wide toggle in Android Settings — false if the permission is missing/denied, or the user disabled it there. */
    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** True if [channelId] exists and its importance was set to `IMPORTANCE_NONE` from Android Settings. */
    fun isChannelBlocked(channelId: String): Boolean {
        val channel = NotificationManagerCompat.from(context).getNotificationChannel(channelId) ?: return false
        return channel.importance == android.app.NotificationManager.IMPORTANCE_NONE
    }
}
