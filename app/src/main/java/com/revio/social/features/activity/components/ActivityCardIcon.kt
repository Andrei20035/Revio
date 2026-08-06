package com.revio.social.features.activity.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revio.social.core.ui.scaling.actScaled

/** Lățimea rezervată iconiței de start în cardurile de sistem, astfel încât coloana
 *  de text să pornească din același punct indiferent de simbol. */
internal val ActivityIconSlotWidth = 32.dp

@Composable
internal fun ActivityIconSlot(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.width(ActivityIconSlotWidth.actScaled()),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
