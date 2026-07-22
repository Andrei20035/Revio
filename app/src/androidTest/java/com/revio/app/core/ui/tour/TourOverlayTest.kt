package com.revio.app.core.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.app.core.tour.TourStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TourOverlay este pur prezentațional și stateless, la fel ca SeePostOverlay — se compune
 * direct, fără Hilt/navigație, cu lambda-uri stub pentru onAdvance/onPostCta.
 */
@RunWith(AndroidJUnit4::class)
class TourOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fiecare pas afiseaza titlul si textul corespunzator`() {
        TourStep.entries.forEach { step ->
            val copy = tourCopyFor(step)
            composeTestRule.setContent {
                TourOverlay(
                    step = step,
                    spotlight = null,
                    onAdvance = {},
                    onPostCta = {},
                )
            }

            composeTestRule.onNodeWithText(copy.title, useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText(copy.body, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `tap oriunde pe ecran avanseaza turul pentru pasii 1-4`() {
        listOf(TourStep.Feed, TourStep.Leaderboard, TourStep.Activity, TourStep.Profile).forEach { step ->
            var advanced = false
            composeTestRule.setContent {
                TourOverlay(
                    step = step,
                    spotlight = null,
                    onAdvance = { advanced = true },
                    onPostCta = {},
                )
            }

            composeTestRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

            assertTrue("step=$step ar trebui sa avanseze la tap", advanced)
        }
    }

    @Test
    fun `pe PostCta tap in afara cutout-ului nu declanseaza onPostCta`() {
        var ctaFired = false
        val spotlight = Rect(100f, 100f, 200f, 200f)
        composeTestRule.setContent {
            TourOverlay(
                step = TourStep.PostCta,
                spotlight = spotlight,
                onAdvance = {},
                onPostCta = { ctaFired = true },
            )
        }

        composeTestRule.onRoot().performTouchInput { click(Offset(500f, 500f)) }

        assertFalse(ctaFired)
    }

    @Test
    fun `pe PostCta tap in interiorul cutout-ului declanseaza onPostCta`() {
        var ctaFired = false
        val spotlight = Rect(100f, 100f, 200f, 200f)
        composeTestRule.setContent {
            TourOverlay(
                step = TourStep.PostCta,
                spotlight = spotlight,
                onAdvance = {},
                onPostCta = { ctaFired = true },
            )
        }

        // Center of the spotlight rect.
        composeTestRule.onRoot().performTouchInput { click(Offset(150f, 150f)) }

        assertTrue(ctaFired)
    }

    @Test
    fun `nu exista niciun buton Skip`() {
        composeTestRule.setContent {
            TourOverlay(
                step = TourStep.Feed,
                spotlight = null,
                onAdvance = {},
                onPostCta = {},
            )
        }

        composeTestRule.onNodeWithText("Skip", substring = true, ignoreCase = true).assertDoesNotExist()
    }
}
