package com.revio.social.features.admin.challenge

import com.revio.social.data.model.AdminChallenge

/** The admin "Challenges" dashboard's state — a keyset-paginated list of challenges. */
data class AdminChallengesUiState(
    val challenges: List<AdminChallenge> = emptyList(),
    val isLoading: Boolean = false,
    /** True while a [AdminChallengesViewModel.loadMore] page request is in flight. */
    val isPaging: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val hasMore: Boolean = false,
) {
    val isEmpty: Boolean get() = challenges.isEmpty()
}
