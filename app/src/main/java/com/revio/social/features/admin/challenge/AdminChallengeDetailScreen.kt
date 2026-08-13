package com.revio.social.features.admin.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.revio.social.core.network.ApiResult
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.ChallengeAdminStatus
import com.revio.social.data.repository.AdminChallengeRepository
import com.revio.social.features.admin.AppScreenBackgroundWithTopBar
import com.revio.social.features.admin.components.AdminPublishChallengeSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nav-arg key for the challenge id. Wired into a route once the admin detail screen is added
 * to the navigation graph (a later step). */
const val ADMIN_CHALLENGE_DETAIL_ARG_CHALLENGE_ID = "challengeId"

/** The exact string the server sends for a 404 on `GET /admin/challenges/{id}` — see
 * `ChallengeAdminRoutes.kt`. Matching on it distinguishes "not found" from any other failure. */
private const val CHALLENGE_NOT_FOUND_MESSAGE = "Challenge not found"

/** The admin challenge detail screen's state — read-only view of `GET /admin/challenges/{id}`. */
sealed interface AdminChallengeDetailUiState {
    data object Loading : AdminChallengeDetailUiState
    data object NotFound : AdminChallengeDetailUiState
    data class Error(val message: String) : AdminChallengeDetailUiState
    data class Content(val challenge: AdminChallenge) : AdminChallengeDetailUiState
}

/** State for [com.revio.social.features.admin.components.AdminPublishChallengeSheet]. */
data class AdminPublishUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/** State for the "Finalize now" action — no confirmation sheet, error shown inline. */
data class AdminFinalizeUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/** Owns the admin challenge detail screen's read-only data. `challengeId` comes from the nav arg. */
@HiltViewModel
class AdminChallengeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val adminChallengeRepository: AdminChallengeRepository,
) : ViewModel() {

    private val challengeId: UUID? = savedStateHandle.get<String>(ADMIN_CHALLENGE_DETAIL_ARG_CHALLENGE_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val _uiState = MutableStateFlow<AdminChallengeDetailUiState>(AdminChallengeDetailUiState.Loading)
    val uiState: StateFlow<AdminChallengeDetailUiState> = _uiState.asStateFlow()

    private val _publishState = MutableStateFlow(AdminPublishUiState())
    val publishState: StateFlow<AdminPublishUiState> = _publishState.asStateFlow()

    private val _finalizeState = MutableStateFlow(AdminFinalizeUiState())
    val finalizeState: StateFlow<AdminFinalizeUiState> = _finalizeState.asStateFlow()

    init {
        if (challengeId == null) {
            _uiState.value = AdminChallengeDetailUiState.NotFound
        } else {
            refresh()
        }
    }

    fun refresh() {
        val id = challengeId ?: return
        viewModelScope.launch {
            _uiState.value = AdminChallengeDetailUiState.Loading
            when (val result = adminChallengeRepository.getChallenge(id)) {
                is ApiResult.Success -> _uiState.value = AdminChallengeDetailUiState.Content(result.data)
                is ApiResult.Error -> _uiState.value = if (result.message == CHALLENGE_NOT_FOUND_MESSAGE) {
                    AdminChallengeDetailUiState.NotFound
                } else {
                    AdminChallengeDetailUiState.Error(result.message)
                }
            }
        }
    }

    /** Fires `POST /admin/challenges/{id}/publish` — see `AdminPublishChallengeSheet`. A 409
     * window-overlap conflict is remapped from the server's raw exception text to the friendlier
     * copy the plan's §5.5 specifies. */
    fun publish() {
        val id = challengeId ?: return
        if (_publishState.value.isSubmitting) return
        viewModelScope.launch {
            _publishState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = adminChallengeRepository.publishChallenge(id)) {
                is ApiResult.Success -> {
                    _publishState.value = AdminPublishUiState()
                    _uiState.value = AdminChallengeDetailUiState.Content(result.data)
                }
                is ApiResult.Error -> _publishState.update {
                    it.copy(isSubmitting = false, errorMessage = mapPublishError(result.message))
                }
            }
        }
    }

    fun consumePublishError() {
        _publishState.update { it.copy(errorMessage = null) }
    }

    private fun mapPublishError(message: String): String =
        if (message.contains("overlaps another", ignoreCase = true)) {
            "This window overlaps another scheduled challenge."
        } else {
            message
        }

    /** Fires `POST /admin/challenges/{id}/finalize` — the same idempotent, non-destructive engine
     * the cron sweep uses, so no confirmation is needed here (plan §5.5). Success reloads the
     * detail via [refresh]; a failure is left for the caller to show inline, next to the action. */
    fun finalize() {
        val id = challengeId ?: return
        if (_finalizeState.value.isSubmitting) return
        viewModelScope.launch {
            _finalizeState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = adminChallengeRepository.finalizeChallenge(id)) {
                is ApiResult.Success -> {
                    _finalizeState.value = AdminFinalizeUiState()
                    refresh()
                }
                is ApiResult.Error -> _finalizeState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
            }
        }
    }
}

/** Read-only admin view of one challenge (GET /api/admin/challenges/{id}) — previously had no UI. */
@Composable
fun AdminChallengeDetailScreen(
    navController: NavController,
    viewModel: AdminChallengeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val publishState by viewModel.publishState.collectAsStateWithLifecycle()
    val finalizeState by viewModel.finalizeState.collectAsStateWithLifecycle()
    var showPublishSheet by remember { mutableStateOf(false) }

    AppScreenBackgroundWithTopBar(
        title = "Challenge detail",
        onBack = { navController.popBackStack() },
    ) {
        AdminChallengeDetailContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onPublishClick = { showPublishSheet = true },
            finalizeState = finalizeState,
            onFinalizeClick = viewModel::finalize,
        )
    }

    // Rendered only while the challenge is still DRAFT — publish succeeding flips the status,
    // which naturally drops the sheet from composition without extra state juggling.
    val content = uiState
    if (showPublishSheet && content is AdminChallengeDetailUiState.Content &&
        content.challenge.status == ChallengeAdminStatus.DRAFT
    ) {
        AdminPublishChallengeSheet(
            challenge = content.challenge,
            isSubmitting = publishState.isSubmitting,
            errorMessage = publishState.errorMessage,
            onConfirm = viewModel::publish,
            onDismiss = {
                showPublishSheet = false
                viewModel.consumePublishError()
            },
        )
    }
}

@Composable
fun AdminChallengeDetailContent(
    uiState: AdminChallengeDetailUiState,
    onRetry: () -> Unit,
    onPublishClick: () -> Unit = {},
    finalizeState: AdminFinalizeUiState = AdminFinalizeUiState(),
    onFinalizeClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            AdminChallengeDetailUiState.Loading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("admin_challenge_detail_loading"),
            )

            AdminChallengeDetailUiState.NotFound -> StateMessage(
                title = "Challenge not found",
                subtitle = "It may have been removed.",
                modifier = Modifier.align(Alignment.Center),
            )

            is AdminChallengeDetailUiState.Error -> StateMessage(
                title = "Couldn't load challenge",
                subtitle = uiState.message,
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )

            is AdminChallengeDetailUiState.Content -> AdminChallengeDetailFields(
                challenge = uiState.challenge,
                onPublishClick = onPublishClick,
                finalizeState = finalizeState,
                onFinalizeClick = onFinalizeClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AdminChallengeDetailFields(
    challenge: AdminChallenge,
    onPublishClick: () -> Unit,
    finalizeState: AdminFinalizeUiState,
    onFinalizeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 13.dp.actScaled(), bottom = 40.dp.actScaled()),
    ) {
        Text(
            text = challenge.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp.actScaledText(),
        )
        if (challenge.description != null) {
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = challenge.description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp.actScaledText(),
            )
        }
        Spacer(modifier = Modifier.height(20.dp.actScaled()))
        DetailRow(label = "Target family", value = challenge.targetFamilyId.toString())
        DetailRow(label = "Required posts", value = challenge.requiredPosts.toString())
        DetailRow(label = "Reward points", value = "${challenge.rewardPoints} pts")
        DetailRow(label = "Window", value = "${challenge.startsAt} → ${challenge.endsAt}")
        DetailRow(label = "Timezone", value = challenge.adminTimezone)
        DetailRow(label = "Status", value = challenge.status.name)
        DetailRow(
            label = "Finalized",
            value = challenge.finalizedAt?.toString() ?: "Not finalized",
        )

        if (challenge.status == ChallengeAdminStatus.DRAFT) {
            Spacer(modifier = Modifier.height(20.dp.actScaled()))
            PublishButton(onClick = onPublishClick)
        }

        Spacer(modifier = Modifier.height(12.dp.actScaled()))
        FinalizeButton(isSubmitting = finalizeState.isSubmitting, onClick = onFinalizeClick)
        if (finalizeState.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            Text(
                text = finalizeState.errorMessage,
                color = DangerRed,
                fontSize = 12.sp.actScaledText(),
            )
        }
    }
}

@Composable
private fun PublishButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0AB25))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp.actScaled()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Publish",
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp.actScaledText(),
        )
    }
}

private val DangerRed = Color(0xFFFF5A5F)
private val FinalizeButtonFill = Color(0xFF34D7C4)

@Composable
private fun FinalizeButton(isSubmitting: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSubmitting) FinalizeButtonFill.copy(alpha = 0.5f) else FinalizeButtonFill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isSubmitting,
                onClick = onClick,
            )
            .padding(vertical = 14.dp.actScaled()),
        contentAlignment = Alignment.Center,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp.actScaled()),
                strokeWidth = 2.dp,
                color = Color.Black,
            )
        } else {
            Text(
                text = "Finalize now",
                color = Color.Black,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp.actScaledText(),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp.actScaled()),
    ) {
        Text(
            text = label,
            color = Color(0xFF707070),
            fontSize = 11.sp.actScaledText(),
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(2.dp.actScaled()))
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp.actScaledText(),
        )
    }
}
