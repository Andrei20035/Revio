package com.revio.social.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.util.toRelativeTime
import com.revio.social.data.model.label
import com.revio.social.data.remote.dto.admin.AdminUserSummaryDto
import com.revio.social.data.remote.dto.admin.ModerationViolationDto
import com.revio.social.data.remote.dto.admin.UserModerationResponseDto

private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val CardShape = RoundedCornerShape(12.dp)
private val AccentYellow = Color(0xFFF0AB25)
private val DangerRed = Color(0xFFFF5A5F)
private val WarningOrange = Color(0xFFFFA500)

/** Search a user by username/email, then view + act on their moderation state. */
@Composable
fun AdminUserScreen(
    navController: NavController,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val uiState by viewModel.userState.collectAsState()

    AppScreenBackgroundWithTopBar(
        title = if (uiState.isShowingDetail) "User moderation" else "Find user",
        onBack = {
            if (uiState.isShowingDetail) viewModel.backToSearch() else navController.popBackStack()
        },
    ) {
        if (uiState.isShowingDetail) {
            UserDetailContent(
                uiState = uiState,
                onBan = { days, permanent, reason -> viewModel.ban(days, permanent, reason) },
                onUnban = { viewModel.unban() },
                onRevoke = { viewModel.revokeViolation(it) },
                onRetry = { viewModel.retryUserModeration() },
            )
        } else {
            SearchContent(
                query = uiState.query,
                results = uiState.searchResults,
                isSearching = uiState.isSearching,
                searchError = uiState.searchError,
                onQueryChanged = { viewModel.onQueryChanged(it) },
                onSearch = { viewModel.searchUsers() },
                onUserSelected = { viewModel.selectUser(it) },
            )
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    results: List<AdminUserSummaryDto>,
    isSearching: Boolean,
    searchError: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onUserSelected: (java.util.UUID) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp.actScaled()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Username or email") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentYellow,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp.actScaled()))
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching,
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
            ) {
                Text("Search", color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(16.dp.actScaled()))

        when {
            isSearching -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            searchError != null -> Text(text = searchError, color = DangerRed, fontSize = 13.sp)
            results.isEmpty() -> Text(
                text = "Search by username or email to find a user.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { user ->
                    UserResultRow(user = user, onClick = { onUserSelected(user.id) })
                    Spacer(modifier = Modifier.height(8.dp.actScaled()))
                }
            }
        }
    }
}

@Composable
private fun UserResultRow(user: AdminUserSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .clickable(onClick = onClick)
            .padding(16.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.username, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = user.email, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        if (user.banState.isBanned) {
            Text(text = "BANNED", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UserDetailContent(
    uiState: AdminUserUiState,
    onBan: (Int?, Boolean, String?) -> Unit,
    onUnban: () -> Unit,
    onRevoke: (java.util.UUID) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.isLoadingDetail && uiState.detail == null -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        uiState.detailError != null && uiState.detail == null -> Box(modifier = Modifier.fillMaxSize()) {
            StateMessage(
                title = "Couldn't load this user",
                subtitle = uiState.detailError,
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        uiState.detail != null -> UserDetailBody(
            detail = uiState.detail,
            isMutating = uiState.isMutating,
            mutationError = uiState.mutationError,
            onBan = onBan,
            onUnban = onUnban,
            onRevoke = onRevoke,
        )
    }
}

@Composable
private fun UserDetailBody(
    detail: UserModerationResponseDto,
    isMutating: Boolean,
    mutationError: String?,
    onBan: (Int?, Boolean, String?) -> Unit,
    onUnban: () -> Unit,
    onRevoke: (java.util.UUID) -> Unit,
) {
    var banReason by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 13.dp.actScaled(), bottom = 40.dp.actScaled()),
    ) {
        item {
            Text(text = detail.user.username, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(text = detail.user.email, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp.actScaled()))

            val needsReviewColor = if (detail.needsReview) WarningOrange else Color.White
            Text(
                text = "Active violations: ${detail.activeViolationCount}" + if (detail.needsReview) " — needs review" else "",
                color = needsReviewColor,
                fontWeight = if (detail.needsReview) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(12.dp.actScaled()))

            if (detail.user.banState.isBanned) {
                val until = detail.user.banState.bannedUntil
                val text = when {
                    detail.user.banState.permanent -> "Banned permanently"
                    until != null -> "Banned until $until"
                    else -> "Banned"
                }
                Text(text = text, color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Button(
                    onClick = onUnban,
                    enabled = !isMutating,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                ) {
                    Text("Unban", color = Color.Black)
                }
            } else {
                OutlinedTextField(
                    value = banReason,
                    onValueChange = { banReason = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ban reason (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentYellow,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp.actScaled())) {
                    listOf(3, 7, 30).forEach { days ->
                        OutlinedButton(
                            onClick = { onBan(days, false, banReason.ifBlank { null }) },
                            enabled = !isMutating,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) {
                            Text("$days d")
                        }
                    }
                    Button(
                        onClick = { onBan(null, true, banReason.ifBlank { null }) },
                        enabled = !isMutating,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    ) {
                        Text("Permanent", color = Color.White)
                    }
                }
            }

            if (mutationError != null) {
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Text(text = mutationError, color = DangerRed, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp.actScaled()))
            Text(text = "Violations", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
        }

        if (detail.violations.isEmpty()) {
            item {
                Text(
                    text = "No violations recorded.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
        } else {
            items(detail.violations, key = { it.id }) { violation ->
                ViolationRow(violation = violation, isMutating = isMutating, onRevoke = { onRevoke(violation.id) })
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
            }
        }
    }
}

@Composable
private fun ViolationRow(
    violation: ModerationViolationDto,
    isMutating: Boolean,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .padding(16.dp.actScaled()),
    ) {
        Text(text = violation.reason.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (!violation.reasonDetails.isNullOrBlank()) {
            Text(text = violation.reasonDetails, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(text = violation.createdAt.toRelativeTime(), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)

        if (violation.revokedAt != null) {
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(text = "Revoked", color = Color(0xFF9D9D9D), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        } else {
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            OutlinedButton(
                onClick = onRevoke,
                enabled = !isMutating,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellow),
            ) {
                Text("Revoke")
            }
        }
    }
}
