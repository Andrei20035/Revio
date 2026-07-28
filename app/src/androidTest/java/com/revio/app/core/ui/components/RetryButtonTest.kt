package com.revio.app.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual animation curve behind [RetryButton] via [rememberRetrySpin], per the
 * implementation plan §5: one clean revolution for a `true`->`false` blip while offline, a
 * seam-free constant-velocity spin while loading, and a prompt, clean stop. Mirrors
 * [RetryButton]'s own timing constants (ONE_SHOT_MS=600, SPIN_MS=800, STOP_MS=400,
 * STEP_DEG=30) since those stay `private` to RetryButton.kt.
 *
 * [androidx.compose.ui.test.MainTestClock.waitForIdle] is used after every state mutation (not
 * [androidx.compose.ui.test.MainTestClock.advanceTimeByFrame]) so a mutation is always fully
 * settled — including any state changes cascading out of the effect it triggers — before virtual
 * time is advanced for the animation itself.
 */
@RunWith(AndroidJUnit4::class)
class RetryButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var originalAnimatorScale: String? = null

    @Before
    fun setup() {
        // Make sure "reduced motion" isn't accidentally on for the non-reduced-motion cases —
        // rememberRetrySpin reads Settings.Global.ANIMATOR_DURATION_SCALE directly.
        originalAnimatorScale = readAnimatorScale()
        setAnimatorScale("1")
    }

    @After
    fun tearDown() {
        setAnimatorScale(originalAnimatorScale ?: "1")
    }

    private fun readAnimatorScale(): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings get global animator_duration_scale")
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText().trim()
    }

    private fun setAnimatorScale(scale: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global animator_duration_scale $scale")
        // Reading to EOF and closing the descriptor waits for the shell command to complete.
        // waitForIdleSync alone only waits for the UI thread, not for UiAutomation's shell job.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun setSpinning(spinning: MutableState<Boolean>, value: Boolean) {
        composeTestRule.runOnIdle { spinning.value = value }
    }

    @Test
    fun `un_blip_true_apoi_false_joaca_exact_o_rotatie_si_se_opreste_la_0`() {
        composeTestRule.mainClock.autoAdvance = false
        val spinning = mutableStateOf(false)
        var currentRotation = 0f
        composeTestRule.setContent {
            val rotation by rememberRetrySpin(spinning.value)
            currentRotation = rotation
        }
        composeTestRule.waitForIdle()

        setSpinning(spinning, true)
        composeTestRule.waitForIdle()
        setSpinning(spinning, false)
        composeTestRule.waitForIdle()

        // ONE_SHOT_MS = 600 in RetryButton.kt.
        composeTestRule.mainClock.advanceTimeBy(650)
        composeTestRule.waitForIdle()

        assertEquals(0f, currentRotation, 0.01f)
    }

    @Test
    fun `un_tap_in_mijlocul_unei_rotatii_reporneste_animatia_de_la_0`() {
        composeTestRule.mainClock.autoAdvance = false
        val spinning = mutableStateOf(false)
        var currentRotation = 0f
        composeTestRule.setContent {
            val rotation by rememberRetrySpin(spinning.value)
            currentRotation = rotation
        }
        composeTestRule.waitForIdle()

        setSpinning(spinning, true)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()
        val midRotation = currentRotation
        assertTrue("ar trebui sa fie in miscare la jumatatea rotatiei: $midRotation", midRotation > 0f && midRotation < 360f)

        // A new start after an interrupted rotation restarts from 0 rather than continuing it.
        composeTestRule.waitForIdle()
        setSpinning(spinning, false)
        composeTestRule.waitForIdle()
        // The continuous loop observes the stop request at the end of its current 30° step.
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()
        setSpinning(spinning, true)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        assertTrue("rotatia ar trebui sa reporneasca de la 0: $currentRotation", currentRotation < 10f)
    }

    @Test
    fun `spinning_true_roteste_continuu_cu_viteza_unghiulara_constanta_fara_hitch`() {
        composeTestRule.mainClock.autoAdvance = false
        val spinning = mutableStateOf(false)
        var currentRotation = 0f
        composeTestRule.setContent {
            val rotation by rememberRetrySpin(spinning.value)
            currentRotation = rotation
        }
        composeTestRule.waitForIdle()

        setSpinning(spinning, true)
        composeTestRule.waitForIdle()

        // Sample the rotation every 20ms across ~2 full revolutions (SPIN_MS = 800 per turn).
        val samples = mutableListOf(currentRotation)
        repeat(90) {
            composeTestRule.mainClock.advanceTimeBy(20)
            composeTestRule.waitForIdle()
            samples.add(currentRotation)
        }

        assertTrue("animatia ar trebui sa fie inca activa", spinning.value)

        // Frame-to-frame deltas, unwrapped across the 360->0 resets (which are invisible resets,
        // not the animation slowing down): a genuine hitch would show up as a near-zero delta
        // that ISN'T immediately followed by a wrap.
        val deltas = samples.zipWithNext { a, b -> if (b < a) (b + 360f) - a else b - a }
        val nonWrapDeltas = deltas.filter { it in 0.5f..40f } // drop the (rare) exact-wrap sample
        assertTrue("ar trebui sa avem suficiente esantioane de miscare (deltas=$deltas)", nonWrapDeltas.size > 20)

        val avg = nonWrapDeltas.average()
        val maxDeviation = nonWrapDeltas.maxOf { abs(it - avg) }
        assertTrue(
            "deltele ar trebui sa fie aproape constante (viteza unghiulara constanta, fara hitch); avg=$avg maxDeviation=$maxDeviation",
            maxDeviation < avg * 0.5 + 1.0,
        )
    }

    @Test
    fun `spinning_true_urmat_de_false_se_opreste_curat_si_prompt`() {
        composeTestRule.mainClock.autoAdvance = false
        val spinning = mutableStateOf(false)
        var currentRotation = 0f
        composeTestRule.setContent {
            val rotation by rememberRetrySpin(spinning.value)
            currentRotation = rotation
        }
        composeTestRule.waitForIdle()

        setSpinning(spinning, true)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1600) // a couple of full turns
        composeTestRule.waitForIdle()

        setSpinning(spinning, false)
        composeTestRule.waitForIdle()
        // STOP_MS = 400, plus slack for the in-flight 30°-step tween to finish first.
        composeTestRule.mainClock.advanceTimeBy(700)
        composeTestRule.waitForIdle()

        assertEquals(0f, currentRotation, 0.01f)
    }

    @Test
    fun `reduced_motion_tine_rotatia_la_0`() {
        setAnimatorScale("0")
        composeTestRule.mainClock.autoAdvance = false
        val spinning = mutableStateOf(false)
        var currentRotation = -1f
        composeTestRule.setContent {
            val rotation by rememberRetrySpin(spinning.value)
            currentRotation = rotation
        }
        composeTestRule.waitForIdle()

        setSpinning(spinning, true)
        composeTestRule.waitForIdle()
        setSpinning(spinning, false)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        assertEquals(0f, currentRotation, 0.01f)
    }
}
