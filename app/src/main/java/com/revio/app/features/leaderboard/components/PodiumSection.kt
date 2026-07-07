package com.revio.app.features.leaderboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revio.app.features.leaderboard.LeaderboardEntry

@Composable
fun PodiumSection(
    podium: List<LeaderboardEntry>,
    modifier: Modifier = Modifier,
    onUserClick: (LeaderboardEntry) -> Unit = {},
) {
    // Guard: need exactly the top 3 entries to render.
    val first  = podium.firstOrNull { it.rank == 1 } ?: return
    val second = podium.firstOrNull { it.rank == 2 } ?: return
    val third  = podium.firstOrNull { it.rank == 3 } ?: return

    // Top padding gives room for badge pills that protrude above the cards.
    // Horizontal padding mirrors the screen's 13dp side margins from Figma.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,  // cards share the same bottom edge
    ) {
        // Figma order: #2 left, #1 center, #3 right
        PodiumUserCard(entry = second, modifier = Modifier.weight(1f), onAvatarClick = { onUserClick(second) })
        PodiumUserCard(entry = first,  modifier = Modifier.weight(1f), onAvatarClick = { onUserClick(first) })
        PodiumUserCard(entry = third,  modifier = Modifier.weight(1f), onAvatarClick = { onUserClick(third) })
    }
}
