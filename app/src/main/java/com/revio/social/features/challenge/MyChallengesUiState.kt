package com.revio.social.features.challenge

import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.data.model.ChallengeSummary

/** The "My Challenges" screen's state — see the plan's §3/§6/§7. */
sealed interface MyChallengesUiState {
    data object Loading : MyChallengesUiState

    /** No participation at all: no summary worth showing, no active challenge, no history. */
    data object Empty : MyChallengesUiState

    data class Error(val message: String) : MyChallengesUiState

    data class Content(
        val summary: ChallengeSummary,
        /** The caller's currently-active challenge, if any — shown in its own "ACTIVE NOW" section. */
        val active: ChallengeHistoryItem?,
        /** Everything else, newest-ended-first. */
        val past: List<ChallengeHistoryItem>,
        /** True while a [MyChallengesViewModel.loadMore] page request is in flight. */
        val isPaging: Boolean = false,
        val hasMore: Boolean = false,
    ) : MyChallengesUiState
}
