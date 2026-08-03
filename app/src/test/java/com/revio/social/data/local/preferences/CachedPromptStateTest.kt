package com.revio.social.data.local.preferences

import com.revio.social.data.model.PromptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CachedPromptStateTest {

    @Test
    fun `serialize then parse round-trips a state with lastShownAt`() {
        val state = CachedPromptState(
            status = PromptStatus.DISMISSED_ONCE,
            shownCount = 1,
            lastShownAt = Instant.ofEpochMilli(1_700_000_000_000),
        )

        val roundTripped = state.serialize().toCachedPromptState()

        assertEquals(state, roundTripped)
    }

    @Test
    fun `serialize then parse round-trips a state with null lastShownAt`() {
        val state = CachedPromptState(
            status = PromptStatus.ELIGIBLE,
            shownCount = 0,
            lastShownAt = null,
        )

        val roundTripped = state.serialize().toCachedPromptState()

        assertEquals(state, roundTripped)
    }

    @Test
    fun `toCachedPromptState returns null for malformed input`() {
        assertNull("garbage".toCachedPromptState())
        assertNull("NOT_A_STATUS|1|".toCachedPromptState())
        assertNull("ELIGIBLE|not-a-number|".toCachedPromptState())
    }
}
