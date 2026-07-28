package com.revio.social.core.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.tour.TourStep
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
    fun `fiecare_pas_afiseaza_titlul_si_textul_corespunzator`() {
        val displayedStep = mutableStateOf(TourStep.Feed)
        composeTestRule.setContent {
            TourOverlay(
                step = displayedStep.value,
                spotlight = null,
                onAdvance = {},
                onPostCta = {},
            )
        }

        TourStep.entries.forEach { step ->
            val copy = tourCopyFor(step)
            composeTestRule.runOnIdle { displayedStep.value = step }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(copy.title, useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText(copy.body, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `tap_oriunde_pe_ecran_avanseaza_turul_pentru_pasii_1-4`() {
        val displayedStep = mutableStateOf(TourStep.Feed)
        var advanced = false
        composeTestRule.setContent {
            TourOverlay(
                step = displayedStep.value,
                spotlight = null,
                onAdvance = { advanced = true },
                onPostCta = {},
            )
        }

        listOf(TourStep.Feed, TourStep.Leaderboard, TourStep.Activity, TourStep.Profile).forEach { step ->
            composeTestRule.runOnIdle {
                advanced = false
                displayedStep.value = step
            }
            composeTestRule.waitForIdle()

            composeTestRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

            assertTrue("step=$step ar trebui sa avanseze la tap", advanced)
        }
    }

    @Test
    fun `pe_PostCta_tap_in_afara_cutout-ului_nu_declanseaza_onPostCta`() {
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
    fun `pe_PostCta_tap_in_interiorul_cutout-ului_declanseaza_onPostCta`() {
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
    fun `nu_exista_niciun_buton_Skip`() {
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
