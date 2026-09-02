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

/**
 * The four real states Android's notification permission can be in (step 3.2) — a single
 * boolean (as used elsewhere in this file) can't tell "never asked" apart from "permanently
 * denied", so a CTA built on that boolean alone can end up calling a dialog that silently never
 * appears.
 */
enum class NotificationPermissionStatus {
    /** `POST_NOTIFICATIONS` has never been requested from this user — the OS dialog can still be shown. */
    NEVER_ASKED,

    /** Denied once, not permanently — the OS dialog can still be shown, with its own rationale UI. */
    DENIED_ONCE,

    /**
     * Either denied twice ("Don't ask again"), or the permission is actually granted but the
     * user separately disabled notifications from the app-wide Settings toggle — in both cases
     * the OS dialog cannot help, so a CTA must route to Settings instead of requesting again.
     */
    PERMANENTLY_DENIED,

    /** The permission is granted and notifications are enabled at the OS level. */
    GRANTED,
}

/**
 * Pure derivation of [NotificationPermissionStatus] (step 3.2) — no Android access of its own,
 * so every input must be gathered by the caller first:
 *  - [hasPostNotificationsPermission] / [notificationsEnabled] — [NotificationPermissionState
 *    .hasPostNotificationsPermission] / [NotificationPermissionState.areNotificationsEnabled];
 *  - [shouldShowRequestPermissionRationale] — `ActivityCompat
 *    .shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)`,
 *    which needs an `Activity`, not just the `Context` this file otherwise works with;
 *  - [everRequested] — [com.revio.social.data.local.preferences.UserPreferences
 *    .notificationPermissionRequested] (step 3.1). Needed because
 *    `shouldShowRequestPermissionRationale` alone returns `false` both before any request and
 *    after a permanent denial — [everRequested] is what tells those two apart.
 *
 * GRANTED requires both [hasPostNotificationsPermission] and [notificationsEnabled]: a granted
 * permission with the app-wide toggle off is treated as PERMANENTLY_DENIED, since re-requesting
 * an already-granted permission is a silent no-op — Settings is the only real path either way.
 */
fun resolvePermissionStatus(
    hasPostNotificationsPermission: Boolean,
    notificationsEnabled: Boolean,
    shouldShowRequestPermissionRationale: Boolean,
    everRequested: Boolean,
): NotificationPermissionStatus = when {
    hasPostNotificationsPermission && notificationsEnabled -> NotificationPermissionStatus.GRANTED
    !hasPostNotificationsPermission && shouldShowRequestPermissionRationale -> NotificationPermissionStatus.DENIED_ONCE
    !hasPostNotificationsPermission && !everRequested -> NotificationPermissionStatus.NEVER_ASKED
    else -> NotificationPermissionStatus.PERMANENTLY_DENIED
}
