package com.revio.app.features.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.revio.app.R
import com.revio.app.core.ui.components.StateMessage
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.core.util.toRelativeTime
import com.revio.app.data.model.Comment
import com.revio.app.features.feed.CommentsSheetState
import com.revio.app.core.ui.scaling.LocalFeedScale
import com.revio.app.core.ui.scaling.rememberFeedScale
import com.revio.app.core.ui.scaling.scaled

// Sheet palette — consistent with the dark feed surface.
private val SheetSurface = Color(0xFF11162E)
private val SheetAccent = Color(0xFF34D7C4)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xB3FFFFFF) // white @ 70%
private val TextTertiary = Color(0x80FFFFFF)   // white @ 50%
private val FieldBorder = Color(0x1FFFFFFF)

// Reference dimensions (Pixel 9 Pro baseline, scale == 1.0).
private val RefSheetCornerRadius = 20.dp
private val RefTitleFontSize = 16.sp
private val RefTitleBottomPadding = 8.dp
private val RefListPaddingH = 16.dp
private val RefListPaddingV = 8.dp
private val RefCommentSpacing = 18.dp
private val RefAvatarSize = 34.dp
private val RefAvatarTextSpacing = 10.dp
private val RefUsernameFontSize = 13.sp
private val RefUsernameTimestampSpacing = 8.dp
private val RefTimestampFontSize = 11.sp
private val RefCommentBodyFontSize = 13.sp
private val RefEmptyTitleFontSize = 15.sp
private val RefEmptySubtitleFontSize = 13.sp
private val RefEmptyTitleSubtitleSpacing = 4.dp
private val RefErrorFontSize = 14.sp
private val RefInputPaddingH = 12.dp
private val RefInputPaddingV = 10.dp
private val RefInputGroupSpacing = 8.dp
private val RefInputCornerRadius = 24.dp
private val RefInputFontSize = 14.sp
private val RefInputSpinnerSize = 20.dp

/**
 * Instagram-style comments overlay: a modal bottom sheet with rounded top corners, a dark
 * background, a scrollable comments list, and an input row pinned at the bottom. Dismisses on
 * swipe-down or scrim tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    state: CommentsSheetState,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Reveal the newly posted comment (server appends oldest-first, so it lands at the bottom).
    LaunchedEffect(state.comments.size) {
        if (state.comments.isNotEmpty()) {
            listState.animateScrollToItem(state.comments.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetSurface,
        shape = RoundedCornerShape(topStart = RefSheetCornerRadius, topEnd = RefSheetCornerRadius),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) },
    ) {
        // ModalBottomSheet creates a new Android window — re-derive scale inside its content.
        CompositionLocalProvider(LocalFeedScale provides rememberFeedScale()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f), // fraction — not scaled
            ) {
                Text(
                    text = "Comments",
                    color = TextPrimary,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = RefTitleFontSize.scaled(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = RefTitleBottomPadding.scaled()),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.isLoading && state.comments.isEmpty() -> CenteredBox {
                            CircularProgressIndicator(color = SheetAccent)
                        }

                        state.errorMessage != null && state.comments.isEmpty() -> CenteredBox {
                            StateMessage(
                                title = "Couldn't load comments",
                                actionLabel = "Retry",
                                onAction = onRetry,
                                accentColor = SheetAccent,
                                verticalPadding = 0.dp,
                                titleColor = TextSecondary,
                                titleFontSize = RefErrorFontSize.scaled(),
                            )
                        }

                        state.comments.isEmpty() -> CenteredBox {
                            StateMessage(
                                title = "No comments yet",
                                subtitle = "Be the first to comment.",
                                accentColor = SheetAccent,
                                verticalPadding = 0.dp,
                                titleColor = TextSecondary,
                                subtitleColor = TextTertiary,
                                titleFontSize = RefEmptyTitleFontSize.scaled(),
                                subtitleFontSize = RefEmptySubtitleFontSize.scaled(),
                                titleSubtitleSpacing = RefEmptyTitleSubtitleSpacing.scaled(),
                            )
                        }

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                horizontal = RefListPaddingH.scaled(),
                                vertical = RefListPaddingV.scaled(),
                            ),
                        ) {
                            items(state.comments, key = { it.id }) { comment ->
                                CommentRow(comment)
                                Spacer(modifier = Modifier.height(RefCommentSpacing.scaled()))
                            }
                        }
                    }
                }

                CommentInputBar(
                    draft = state.draft,
                    canSend = state.canSend,
                    isSubmitting = state.isSubmitting,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val avatarModifier = Modifier
            .size(RefAvatarSize.scaled())
            .clip(CircleShape)
        if (comment.profilePictureUrl.isNullOrBlank()) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.profile_picture),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
            )
        } else {
            AsyncImage(
                model = comment.profilePictureUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
                placeholder = painterResource(R.drawable.profile_picture),
                fallback = painterResource(R.drawable.profile_picture),
                error = painterResource(R.drawable.profile_picture),
            )
        }

        Spacer(modifier = Modifier.width(RefAvatarTextSpacing.scaled()))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.username,
                    color = TextPrimary,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = RefUsernameFontSize.scaled(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                comment.createdAt?.let { createdAt ->
                    Spacer(modifier = Modifier.width(RefUsernameTimestampSpacing.scaled()))
                    Text(
                        text = createdAt.toRelativeTime(),
                        color = TextTertiary,
                        fontFamily = Poppins,
                        fontSize = RefTimestampFontSize.scaled(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp)) // sub-pixel gap — not scaled
            Text(
                text = comment.text,
                color = TextSecondary,
                fontFamily = Poppins,
                fontSize = RefCommentBodyFontSize.scaled(),
            )
        }
    }
}

@Composable
private fun CommentInputBar(
    draft: String,
    canSend: Boolean,
    isSubmitting: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SheetSurface)
            .navigationBarsPadding() // system inset — never scaled
            .imePadding()            // keyboard inset — never scaled
            .padding(horizontal = RefInputPaddingH.scaled(), vertical = RefInputPaddingV.scaled()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RefInputGroupSpacing.scaled()),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add a comment…", color = TextTertiary) },
            enabled = !isSubmitting,
            maxLines = 4,
            shape = RoundedCornerShape(RefInputCornerRadius.scaled()),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Poppins, fontSize = RefInputFontSize.scaled()),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = SheetAccent,
                focusedBorderColor = SheetAccent,
                unfocusedBorderColor = FieldBorder,
            ),
        )

        IconButton(onClick = onSend, enabled = canSend) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(RefInputSpinnerSize.scaled()),
                    strokeWidth = 2.dp, // stroke — never scaled
                    color = SheetAccent,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send comment",
                    tint = if (canSend) SheetAccent else TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
        content()
    }
}
