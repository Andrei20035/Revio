package com.revio.social.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The push/inbox notification categories (push-notifications plan, §11; CHALLENGES added by the
 * "challenge is live" work), mapped 1:1 onto Android notification channels so Settings →
 * Notifications shows exactly the categories a user can control. Importances mirror the
 * per-category priorities from §8: comments and account are the two a user shouldn't miss,
 * discovery and reminders are the least intrusive; challenges sits at the default importance,
 * same as likes.
 */
private data class RevioNotificationChannel(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
)

private val CHANNELS = listOf(
    RevioNotificationChannel(
        id = "likes",
        name = "Likes",
        description = "Likes on your spots",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    RevioNotificationChannel(
        id = "comments",
        name = "Comments",
        description = "Comments on your spots",
        importance = NotificationManager.IMPORTANCE_HIGH,
    ),
    RevioNotificationChannel(
        id = "discovery",
        name = "Community discoveries",
        description = "New spots from the community",
        importance = NotificationManager.IMPORTANCE_LOW,
    ),
    RevioNotificationChannel(
        id = "reminders",
        name = "Leaderboard & reminders",
        description = "Leaderboard updates and reminders to post",
        importance = NotificationManager.IMPORTANCE_LOW,
    ),
    RevioNotificationChannel(
        id = "challenges",
        name = "Challenges",
        description = "New challenges going live",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    RevioNotificationChannel(
        id = "account",
        name = "Account & safety",
        description = "Account status and moderation notices",
        importance = NotificationManager.IMPORTANCE_HIGH,
    ),
)

/**
 * Registers Revio's notification channels with the system. Safe to call on every app start:
 * re-registering an existing channel id only updates its name/description, never resets a
 * user's own per-channel settings (sound, vibration, whether it's blocked) and never throws.
 * No-op below API 26, where channels don't exist.
 */
fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

    manager.createNotificationChannels(
        CHANNELS.map { channel ->
            NotificationChannel(channel.id, channel.name, channel.importance).apply {
                description = channel.description
            }
        }
    )
}
