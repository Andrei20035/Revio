package com.revio.social.features.feed

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import com.revio.social.data.model.FeedPost
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `FeedPostCard` is only ever composed once [FeedImageGate] has already gated the post in — this
 * is the regression guard for that: the image slot is always part of the same atomic card as the
 * header/engagement/caption, and the old "Image unavailable offline" overlay (removed when the
 * feed switched to `visiblePosts`, see the implementation plan §3, §9) must never reappear.
 */
@RunWith(AndroidJUnit4::class)
class FeedGatedCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun feedPost() = FeedPost(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        username = "testuser",
        brand = "Porsche",
        model = "911",
        imageUrl = "https://example.com/test.jpg",
        caption = "Spotted downtown",
        latitude = null,
        longitude = null,
        createdAt = Instant.EPOCH,
        likeCount = 3L,
        commentCount = 1L,
        likedByCurrentUser = false,
    )

    private fun setCardContent(post: FeedPost) {
        composeTestRule.setContent {
            val context = LocalContext.current
            val imageLoader = remember { ImageLoader.Builder(context).build() }
            CompositionLocalProvider(LocalFeedImageLoader provides imageLoader) {
                FeedPostCard(
                    post = post,
                    onLikeToggle = {},
                    onOpenComments = {},
                    onShare = {},
                    onReportReasonSelected = {},
                    onAuthorClick = {},
                )
            }
        }
    }

    @Test
    fun cardul_se_randeaza_intotdeauna_cu_un_slot_de_imagine_atomic_cu_restul_continutului() {
        val post = feedPost()
        setCardContent(post)

        // The image slot (contentDescription = post.carName) is present alongside the rest of
        // the card's content in the same composition — never a header/caption with a missing image.
        composeTestRule.onNodeWithContentDescription(post.carName).assertIsDisplayed()
        composeTestRule.onNodeWithText(post.username).assertIsDisplayed()
        composeTestRule.onNodeWithText(post.caption!!).assertIsDisplayed()
    }

    @Test
    fun textul_Image_unavailable_offline_nu_mai_exista_in_card() {
        setCardContent(feedPost())

        composeTestRule.onNodeWithText("Image unavailable offline").assertDoesNotExist()
    }
}
