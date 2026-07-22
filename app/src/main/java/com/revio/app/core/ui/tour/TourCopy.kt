package com.revio.app.core.ui.tour

import com.revio.app.core.tour.TourStep

/** Headline + body copy shown by [TourOverlay] for a given [TourStep]. */
data class TourCopy(val title: String, val body: String)

fun tourCopyFor(step: TourStep): TourCopy = when (step) {
    TourStep.Feed -> TourCopy(
        title = "Every scroll could hide a gem.",
        body = "Discover amazing spots shared by fellow car people.",
    )
    TourStep.Leaderboard -> TourCopy(
        title = "Think you can make the top?",
        body = "Follow the leaderboard and chase your next position.",
    )
    TourStep.Activity -> TourCopy(
        title = "Don't miss the good stuff.",
        body = "Your recent interactions and updates show up here.",
    )
    TourStep.Profile -> TourCopy(
        title = "This is your corner of Revio.",
        body = "Your spots, progress, and car-spotting identity live here.",
    )
    TourStep.PostCta -> TourCopy(
        title = "Ready to join the hunt?",
        body = "Post your first spot and start your collection.",
    )
}
