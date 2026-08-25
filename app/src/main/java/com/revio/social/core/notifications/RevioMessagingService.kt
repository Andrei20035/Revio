package com.revio.social.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.revio.social.MainActivity
import com.revio.social.R
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "RevioMessagingService"
private const val FALLBACK_CHANNEL = "account"

/** Existing event name retained for analytics dashboard continuity. */
private const val EVENT_PUSH_RECEIVED_FOREGROUND = "push_received_foreground"

/**
 * FCM entry point. Pushes are data-only so this service owns their appearance in every app
 * state, including the Revio large icon shown in the system notification card.
 */
@AndroidEntryPoint
class RevioMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar
    @Inject lateinit var analyticsClient: AnalyticsClient

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken called")
        pushTokenRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "onMessageReceived called")
        val category = message.data["category"]
        showNotification(message.data, category)
        analyticsClient.log(
            AnalyticsEvent(
                name = EVENT_PUSH_RECEIVED_FOREGROUND,
                params = mapOf(
                    "category" to AnalyticsParamValue.StringValue(category ?: "collapsed"),
                ),
            )
        )
    }

    private fun showNotification(payload: Map<String, String>, category: String?) {
        val title = payload["title"]?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
        val body = payload["body"]?.takeIf { it.isNotBlank() } ?: return
        val channelId = category.toChannelId()
        val notificationKey = payload["notification_id"]
            ?: payload["notification_ids"]
            ?: "$title\u0000$body"
        val notificationId = notificationKey.hashCode()

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            payload.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.revio_notification_logo)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(
                if (channelId == "comments" || channelId == "account") {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Notification permission not granted")
            return
        }
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun String?.toChannelId(): String = when (this?.uppercase()) {
        "LIKES" -> "likes"
        "COMMENTS" -> "comments"
        "DISCOVERY" -> "discovery"
        "REMINDERS" -> "reminders"
        "ACCOUNT" -> "account"
        else -> FALLBACK_CHANNEL
    }
}
