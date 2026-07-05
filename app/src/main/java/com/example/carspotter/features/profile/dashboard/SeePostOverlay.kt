package com.example.carspotter.features.profile.dashboard

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.example.carspotter.R
import com.example.carspotter.core.ui.components.LikeIcon
import com.example.carspotter.core.ui.components.formatCount
import com.example.carspotter.core.ui.components.interactionCountWidth
import com.example.carspotter.core.ui.theme.Poppins
import com.example.carspotter.core.util.toPostDate
import com.example.carspotter.data.model.FeedPost

private val OverlayMenuSurface = Color(0xFF1B1F33)
private val OverlayMenuBorder = Color(0x1FFFFFFF)
private val OverlayMenuDanger = Color(0xFFFF5A5F)

@Composable
fun SeePostOverlay(
    post: FeedPost,
    isLikeInFlight: Boolean,
    isDeleting: Boolean,
    showDeleteConfirm: Boolean,
    onLikeToggle: () -> Unit,
    onOpenComments: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canDelete: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val context = LocalContext.current
        val blurRadiusPx = with(LocalDensity.current) { 28.dp.roundToPx() }
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

        LaunchedEffect(dialogWindow) {
            val window = dialogWindow ?: return@LaunchedEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurEnabled = context.getSystemService(WindowManager::class.java)
                    ?.isCrossWindowBlurEnabled == true
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = blurRadiusPx }
                window.setDimAmount(if (blurEnabled) 0.25f else 0.5f)
            } else {
                window.setDimAmount(0.5f)
            }
        }

        // Outer full-screen box: tapping outside the centered content dismisses (or closes confirm panel).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (showDeleteConfirm) {
                            if (!isDeleting) onDismissDeleteConfirm()
                        } else {
                            onDismiss()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Inner content: consumes clicks so they don't reach the dismiss handler.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Date + options icon above the image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = post.createdAt.toPostDate(),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )

                    if (canDelete) {
                        PostDeleteMenu(
                            isDeleting = isDeleting,
                            onDeleteClick = onDeleteClick,
                        )
                    }
                }

                // Post image
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = post.carName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(375f / 468f)
                        .clip(RoundedCornerShape(20.dp)),
                    placeholder = painterResource(R.drawable.profile_picture),
                    error = painterResource(R.drawable.profile_picture),
                )

                Spacer(modifier = Modifier.height(10.dp))
                // Like + comment row
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Like
                    Row(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (!isLikeInFlight) onLikeToggle() },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LikeIcon(liked = post.likedByCurrentUser, size = 26.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.width(interactionCountWidth(post.likeCount, scale = 1f))) {
                            Text(
                                text = formatCount(post.likeCount),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(40.dp))

                    // Comment
                    Row(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenComments,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.comment),
                            contentDescription = "Comments",
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.width(interactionCountWidth(post.commentCount, scale = 1f))) {
                            Text(
                                text = formatCount(post.commentCount),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
            }

            if (showDeleteConfirm) {
                DeletePostConfirmPanel(
                    isDeleting = isDeleting,
                    onConfirmDelete = onConfirmDelete,
                    onDismissDeleteConfirm = onDismissDeleteConfirm,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun DeletePostConfirmPanel(
    isDeleting: Boolean,
    onConfirmDelete: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(OverlayMenuSurface)
            .border(1.dp, OverlayMenuBorder, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(20.dp),
    ) {
        Text(
            text = "Delete Post",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Are you sure you want to delete this post? All points acquired from this post will be deducted from your current SpotScore.",
            color = Color.White.copy(alpha = 0.75f),
            fontFamily = Poppins,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!isDeleting) onDismissDeleteConfirm() },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No",
                    color = Color.White.copy(alpha = if (isDeleting) 0.4f else 1f),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!isDeleting) onConfirmDelete() },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OverlayMenuDanger,
                    )
                } else {
                    Text(
                        text = "Yes",
                        color = OverlayMenuDanger,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostDeleteMenu(
    isDeleting: Boolean,
    onDeleteClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val verticalOffsetPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!isDeleting) expanded = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.post_options),
                contentDescription = "Post options",
                modifier = Modifier.size(28.dp),
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, verticalOffsetPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(OverlayMenuSurface)
                        .border(1.dp, OverlayMenuBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = false
                                onDeleteClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = OverlayMenuDanger,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Delete Post",
                            color = OverlayMenuDanger,
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
