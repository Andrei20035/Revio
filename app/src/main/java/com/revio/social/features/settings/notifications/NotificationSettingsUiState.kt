package com.revio.social.features.settings.notifications

/** §11's three system-level buckets — states 1/2+5/3+4+6 respectively. */
enum class SystemNotificationsStatus {
    /** State 1 — the `POST_NOTIFICATIONS` dialog has never been shown. */
    NOT_ENABLED,

    /** States 2 (denied) and 5 (app toggle off in Android Settings). */
    DISABLED,

    /** States 3, 4, and 6 — permission granted and the app-level toggle is on. */
    ENABLED,
}

data class NotificationCategoryUiState(
    val enabled: Boolean = true,
    /** State 6 — this category's channel was individually blocked from Android Settings. */
    val blockedByChannel: Boolean = false,
)

data class NotificationSettingsUiState(
    val prefsLoaded: Boolean = false,
    val systemStatus: SystemNotificationsStatus = SystemNotificationsStatus.NOT_ENABLED,
    val likes: NotificationCategoryUiState = NotificationCategoryUiState(),
    val comments: NotificationCategoryUiState = NotificationCategoryUiState(),
    val discovery: NotificationCategoryUiState = NotificationCategoryUiState(),
    val reminders: NotificationCategoryUiState = NotificationCategoryUiState(),
    val challenges: NotificationCategoryUiState = NotificationCategoryUiState(),
) {
    /** Switches stay disabled until prefs have loaded from the server, and while notifications aren't fully enabled at the OS level (states 1/2/5). */
    val switchesInteractive: Boolean
        get() = prefsLoaded && systemStatus == SystemNotificationsStatus.ENABLED
}
