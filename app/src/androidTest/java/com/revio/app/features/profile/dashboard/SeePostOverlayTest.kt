package com.revio.app.features.profile.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.app.data.model.FeedPost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Testează comportamentul de afișare/ascundere al meniului de ștergere din SeePostOverlay
 * în funcție de parametrul canDelete.
 *
 * Teste pentru FeedScreen (AuthorAvatar click, Role.Button, contentDescription) și
 * ProfileDashboardScreen (Settings/back în funcție de owner mode) necesită
 * @HiltAndroidTest + hilt-android-testing care nu este configurat în proiect.
 * La fel, testele de navigație/back stack necesită androidx.navigation:navigation-testing.
 */
@RunWith(AndroidJUnit4::class)
class SeePostOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun feedPost() = FeedPost(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        username = "testuser",
        brand = "BMW",
        model = "M3",
        imageUrl = "",
        caption = null,
        latitude = null,
        longitude = null,
        createdAt = Instant.EPOCH,
        likeCount = 0L,
        commentCount = 0L,
        likedByCurrentUser = false,
    )

    @Test
    fun `canDelete true - butonul de optiuni post este afisat`() {
        composeTestRule.setContent {
            SeePostOverlay(
                post = feedPost(),
                isLikeInFlight = false,
                isDeleting = false,
                showDeleteConfirm = false,
                onLikeToggle = {},
                onOpenComments = {},
                onDeleteClick = {},
                onConfirmDelete = {},
                onDismissDeleteConfirm = {},
                onDismiss = {},
                canDelete = true,
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Post options")
            .assertIsDisplayed()
    }

    @Test
    fun `canDelete false - butonul de optiuni post nu este randat`() {
        composeTestRule.setContent {
            SeePostOverlay(
                post = feedPost(),
                isLikeInFlight = false,
                isDeleting = false,
                showDeleteConfirm = false,
                onLikeToggle = {},
                onOpenComments = {},
                onDeleteClick = {},
                onConfirmDelete = {},
                onDismissDeleteConfirm = {},
                onDismiss = {},
                canDelete = false,
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Post options")
            .assertDoesNotExist()
    }
}
