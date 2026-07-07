package com.revio.app.features.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.revio.app.R
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.data.model.ReportReason
import com.revio.app.core.ui.scaling.LocalFeedScale
import com.revio.app.core.ui.scaling.rememberFeedScale
import com.revio.app.core.ui.scaling.scaled

// --- Dropdown palette: a slightly raised, opaque dark surface over the navy feed. ---
private val MenuSurface = Color(0xFF1B1F33)
private val MenuBorder = Color(0x1FFFFFFF) // white @ ~12% — the hairline used across the app's nav.
private val MenuTextPrimary = Color.White
private val MenuTextDanger = Color(0xFFFF5A5F) // red used for the report (destructive) actions.
private val MenuDivider = Color(0x14FFFFFF)

// Reference dimensions (Pixel 9 Pro baseline, scale == 1.0).
private val RefMenuButtonSize = 32.dp
private val RefMenuButtonIconSize = 28.dp
private val RefMenuOffsetY = 24.dp
private val RefDropdownWidth = 248.dp
private val RefDropdownCornerRadius = 16.dp
private val RefDropdownPaddingV = 6.dp
private val RefMenuRowPaddingH = 16.dp
private val RefMenuRowPaddingV = 13.dp
private val RefMenuIconSize = 20.dp
private val RefMenuIconTextSpacing = 14.dp
private val RefMenuFontSize = 14.sp
private val RefDividerPaddingH = 12.dp
private val RefReportSpinnerSize = 18.dp

/**
 * The post header's three-dot affordance plus its anchored dropdown menu.
 *
 * Tapping the [R.drawable.post_options] icon opens a rounded, elevated dropdown anchored to it.
 * The menu dismisses on outside tap (via [Popup]'s onDismissRequest) and after any selection.
 * "Share Post" is a client-only action; the three report options route a [ReportReason] up so the
 * caller can open the confirmation dialog.
 */
@Composable
fun PostOptionsMenu(
    onShare: () -> Unit,
    onReportReasonSelected: (ReportReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Popup shares the same Android window as the parent, so CompositionLocals flow through.
    // We still provide here explicitly in case PostOptionsMenu is ever used outside the feed provider.
    CompositionLocalProvider(LocalFeedScale provides rememberFeedScale()) {
        var expanded by remember { mutableStateOf(false) }
        val verticalOffsetPx = with(LocalDensity.current) { RefMenuOffsetY.scaled().roundToPx() }

        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .size(RefMenuButtonSize.scaled())
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.post_options),
                    contentDescription = "Post options",
                    modifier = Modifier.size(RefMenuButtonIconSize.scaled()),
                )
            }

            if (expanded) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = androidx.compose.ui.unit.IntOffset(0, verticalOffsetPx),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    PostOptionsDropdown(
                        onShare = {
                            expanded = false
                            onShare()
                        },
                        onReportReasonSelected = { reason ->
                            expanded = false
                            onReportReasonSelected(reason)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostOptionsDropdown(
    onShare: () -> Unit,
    onReportReasonSelected: (ReportReason) -> Unit,
) {
    val cornerRadius = RefDropdownCornerRadius.scaled()
    Column(
        modifier = Modifier
            .width(RefDropdownWidth.scaled())
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(MenuSurface)
            .border(1.dp, MenuBorder, RoundedCornerShape(cornerRadius))
            .padding(vertical = RefDropdownPaddingV.scaled()),
    ) {
        MenuRow(
            icon = Icons.Outlined.Share,
            label = "Share Post",
            tint = MenuTextPrimary,
            onClick = onShare,
        )

        MenuDividerLine()

        ReportReason.entries.forEachIndexed { index, reason ->
            MenuRow(
                icon = Icons.Outlined.Report,
                label = reason.menuLabel,
                tint = MenuTextDanger,
                onClick = { onReportReasonSelected(reason) },
            )
            if (index != ReportReason.entries.lastIndex) MenuDividerLine()
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RefMenuRowPaddingH.scaled(), vertical = RefMenuRowPaddingV.scaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(RefMenuIconSize.scaled()),
        )
        Spacer(modifier = Modifier.width(RefMenuIconTextSpacing.scaled()))
        Text(
            text = label,
            color = tint,
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = RefMenuFontSize.scaled(),
        )
    }
}

@Composable
private fun MenuDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RefDividerPaddingH.scaled())
            .height(1.dp) // border thickness — never scaled
            .background(MenuDivider),
    )
}

/**
 * "Submit Report" confirmation dialog. The message is specific to the chosen [ReportReason].
 * Buttons are disabled and a spinner replaces the confirm label while the request is in flight.
 */
@Composable
fun SubmitReportDialog(
    reason: ReportReason,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // AlertDialog creates a new Android window — re-derive scale.
    CompositionLocalProvider(LocalFeedScale provides rememberFeedScale()) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) onDismiss() },
            title = { Text("Submit Report") },
            text = { Text(reason.confirmationMessage) },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(RefReportSpinnerSize.scaled()),
                            strokeWidth = 2.dp, // stroke — never scaled
                        )
                    } else {
                        Text("Submit Report", color = MenuTextDanger, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Menu label shown in the dropdown for each report reason. */
private val ReportReason.menuLabel: String
    get() = when (this) {
        ReportReason.INCORRECT_CAR_MODEL -> "Report Incorrect Car Model"
        ReportReason.DUPLICATE_POST -> "Report Duplicate Post"
        ReportReason.INAPPROPRIATE_CONTENT -> "Report Inappropriate Content"
    }

/** Reason-specific body for the confirmation dialog. */
private val ReportReason.confirmationMessage: String
    get() = when (this) {
        ReportReason.INCORRECT_CAR_MODEL ->
            "Report this post because the car shown doesn't match the selected brand or model?"
        ReportReason.DUPLICATE_POST ->
            "Report this post as a duplicate — the same car or photo has already been posted?"
        ReportReason.INAPPROPRIATE_CONTENT ->
            "Report this post for inappropriate content such as spam, offensive, or non-car content?"
    }
