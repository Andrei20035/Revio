package com.revio.app.features.leaderboard.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.R
import com.revio.app.features.leaderboard.RankMovement

private val ColorUp   = Color(0xFF28AB00)
private val ColorDown = Color(0xFFFF0000)
private val ColorKeep = Color(0xFF737373)

@Composable
fun RankMovementIndicator(
    movement: RankMovement,
    placesMoved: Int,
    modifier: Modifier = Modifier,
) {
    val iconRes = when (movement) {
        RankMovement.UP   -> R.drawable.places_up
        RankMovement.DOWN -> R.drawable.places_down
        RankMovement.KEEP -> R.drawable.places_keep
    }
    val textColor = when (movement) {
        RankMovement.UP   -> ColorUp
        RankMovement.DOWN -> ColorDown
        RankMovement.KEEP -> ColorKeep
    }

    val placeLabel = if (placesMoved == 1) "Place" else "Places"

    val label = when (movement) {
        RankMovement.UP   -> "$placesMoved $placeLabel"
        RankMovement.DOWN -> "$placesMoved $placeLabel"
        RankMovement.KEEP -> ""
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.wrapContentSize(),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
