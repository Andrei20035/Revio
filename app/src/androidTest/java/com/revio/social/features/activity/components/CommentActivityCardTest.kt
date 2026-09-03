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
 * NotificationEventService.renderCommentCopy's 1 / 2-4 / 5+ bands.
 */
@RunWith(AndroidJUnit4::class)
class CommentActivityCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun item(actorCount: Int, commentText: String = "Incredible spec, where did you find this?") = ActivityItem.CommentItem(
        id = "comment:test:1",
        createdAt = Instant.parse("2026-08-25T10:00:00Z"),
        actorUserId = UUID.randomUUID(),
        actorUsername = "charlotte_khan",
        actorAvatarUrl = null,
        postId = UUID.randomUUID(),
        postThumbnailUrl = null,
        brand = "BMW",
        model = "M4",
        commentText = commentText,
        actorCount = actorCount,
    )

    @Test
    fun `actorCount_1_shows_the_actual_comment_text`() {
        composeTestRule.setContent { CommentActivityCard(item(actorCount = 1)) }

        composeTestRule.onNodeWithText("charlotte_khan commented: \"Incredible spec, where did you find this?\"").assertExists()
    }

    @Test
    fun `actorCount_2_joins_the_conversation_copy_without_the_comment_text`() {
        composeTestRule.setContent { CommentActivityCard(item(actorCount = 2)) }

        composeTestRule.onNodeWithText("charlotte_khan and 1 other joined the conversation on your BMW M4 spot").assertExists()
    }

    @Test
    fun `actorCount_5_switches_to_the_volume-style_copy_without_a_named_actor`() {
        composeTestRule.setContent { CommentActivityCard(item(actorCount = 5)) }

        composeTestRule.onNodeWithText("5 people commented on your BMW M4 spot").assertExists()
    }
}
