package com.revio.app.features.leaderboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.revio.app.R

@Composable
fun LeaderboardAvatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    username: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier
            .semantics {
                role = Role.Button
                contentDescription = "Open ${username}'s profile"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    } else Modifier

    AsyncImage(
        model = url,
        contentDescription = null,
        placeholder = painterResource(R.drawable.profile_picture),
        error = painterResource(R.drawable.profile_picture),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .then(clickModifier)
            .size(size)
            .clip(CircleShape),
    )
}
