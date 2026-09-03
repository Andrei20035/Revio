package com.revio.social.features.activity.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.features.activity.model.ActivityItem
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the copy thresholds from the audit plan Partea II, Pasul 5 — mirrors
 * NotificationEventService.renderLikeCopy's 1 / 2-3 / 4+ bands.
 */
@RunWith(AndroidJUnit4::class)
class LikeActivityCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun item(actorCount: Int) = ActivityItem.LikeItem(
        id = "like:test:1",
        createdAt = Instant.parse("2026-08-25T10:00:00Z"),
        actorUserId = UUID.randomUUID(),
        actorUsername = "tommy82",
        actorAvatarUrl = null,
        postId = UUID.randomUUID(),
        postThumbnailUrl = null,
        brand = "Porsche",
        model = "GT3",
        actorCount = actorCount,
    )

    @Test
    fun `actorCount_1_shows_the_un-aggregated_single-liker_copy`() {
        composeTestRule.setContent { LikeActivityCard(item(actorCount = 1)) }

        composeTestRule.onNodeWithText("tommy82 liked your Porsche GT3 spot").assertExists()
    }

    @Test
    fun `actorCount_2_shows_one_other_singular`() {
        composeTestRule.setContent { LikeActivityCard(item(actorCount = 2)) }

        composeTestRule.onNodeWithText("tommy82 and 1 other liked your Porsche GT3 spot").assertExists()
    }

    @Test
    fun `actorCount_4_shows_others_plural`() {
        composeTestRule.setContent { LikeActivityCard(item(actorCount = 4)) }

        composeTestRule.onNodeWithText("tommy82 and 3 others liked your Porsche GT3 spot").assertExists()
    }
}
