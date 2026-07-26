package com.revio.app.features.upload

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the LocationStatusChip states from the location-retry fix: the retry affordance shown
 * only on [LocationStatus.Unavailable], the "Location added" copy being fully retired in favor
 * of icon-only when no place name resolved, and no chip at all while [LocationStatus.Idle].
 */
@RunWith(AndroidJUnit4::class)
class LocationStatusChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unavailable_showsPostingWithoutLocationAndRetry_andRetryInvokesCallback() {
        var retryCount = 0
        composeTestRule.setContent {
            LocationStatusChip(
                status = LocationStatus.Unavailable(LocationFailure.NoFix),
                town = null,
                country = null,
                onRetry = { retryCount++ },
            )
        }

        composeTestRule.onNodeWithText("Posting without location").assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_retry").assertIsDisplayed().performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun resolving_showsGettingLocationText_withNoRetryNode() {
        composeTestRule.setContent {
            LocationStatusChip(
                status = LocationStatus.Resolving,
                town = null,
                country = null,
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Getting location…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_retry").assertDoesNotExist()
    }

    @Test
    fun resolved_withTownAndCountry_showsCityCountry() {
        composeTestRule.setContent {
            LocationStatusChip(
                status = LocationStatus.Resolved,
                town = "Bucharest",
                country = "Romania",
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Bucharest, Romania").assertIsDisplayed()
    }

    @Test
    fun resolved_withoutTownOrCountry_showsNoLocationAddedText() {
        composeTestRule.setContent {
            LocationStatusChip(
                status = LocationStatus.Resolved,
                town = null,
                country = null,
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithTag("location_chip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Location added").assertDoesNotExist()
    }

    @Test
    fun idle_rendersNoChip() {
        composeTestRule.setContent {
            LocationStatusChip(
                status = LocationStatus.Idle,
                town = null,
                country = null,
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithTag("location_chip").assertDoesNotExist()
    }
}
