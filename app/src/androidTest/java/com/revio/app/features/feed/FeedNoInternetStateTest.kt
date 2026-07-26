package com.revio.app.features.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.app.R
import com.revio.app.core.ui.components.OfflineStateMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FeedContent.NoInternet] (see FeedScreen.kt's `LazyColumn` `when` branch) renders
 * [OfflineStateMessage] with the feed's own copy, the `no_wifi` drawable icon, and — critically —
 * no `onRetry`, since auto-retry on validated reconnect covers this state instead of a button.
 * Rendered directly (mirroring the feed's exact call) rather than through the full `FeedScreen`,
 * which needs a Hilt-backed ViewModel/NavController this module doesn't set up for tests.
 */
@RunWith(AndroidJUnit4::class)
class FeedNoInternetStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setFeedNoInternetContent() {
        composeTestRule.setContent {
            OfflineStateMessage(
                icon = {
                    Image(
                        painter = painterResource(R.drawable.no_wifi),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                    )
                },
                title = "No internet connection",
                subtitle = "Please connect to the internet and we'll load your feed automatically.",
                onRetry = null,
            )
        }
    }

    @Test
    fun `arata_titlul_si_subtitlul_feed-ului_pentru_starea_fara_internet`() {
        setFeedNoInternetContent()

        composeTestRule.onNodeWithText("No internet connection").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Please connect to the internet and we'll load your feed automatically.")
            .assertIsDisplayed()
    }

    @Test
    fun `nu_exista_niciun_nod_clickable_-_fara_buton_Retry`() {
        setFeedNoInternetContent()

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
