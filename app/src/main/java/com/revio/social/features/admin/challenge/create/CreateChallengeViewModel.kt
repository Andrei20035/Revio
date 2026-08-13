package com.revio.social.features.admin.challenge.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.ChallengeAdminStatus
import com.revio.social.data.repository.AdminCarFamilyRepository
import com.revio.social.data.repository.AdminChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val GOAL_STEP_FIELDS = setOf(
    CreateChallengeField.TITLE,
    CreateChallengeField.REQUIRED_POSTS,
    CreateChallengeField.REWARD_POINTS,
)

/** The fields a create/update request needs, resolved from [CreateChallengeFormState] at the
 * moment of submit (so `Start now` reads the clock then, not when it was toggled). */
private data class SubmitFields(
    val title: String,
    val description: String?,
    val targetFamilyId: UUID,
    val requiredPosts: Int,
    val rewardPoints: Int,
    val startsAtLocal: String,
    val endsAtLocal: String,
    val timezone: String,
)

/** Owns the create-challenge wizard: form state across all three steps, car-family/model
 * loading, validation, and save-draft/publish orchestration. No UI — see the plan's Etapa 4/§8. */
@HiltViewModel
class CreateChallengeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val adminChallengeRepository: AdminChallengeRepository,
    private val adminCarFamilyRepository: AdminCarFamilyRepository,
) : ViewModel() {

    private val editChallengeId: UUID? =
        savedStateHandle.get<String>(Screen.AdminChallengeCreate.ARG_CHALLENGE_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val mode: CreateChallengeMode =
        editChallengeId?.let { CreateChallengeMode.Edit(it) } ?: CreateChallengeMode.Create

    private val _uiState = MutableStateFlow(CreateChallengeUiState(mode = mode))
    val uiState: StateFlow<CreateChallengeUiState> = _uiState.asStateFlow()

    init {
        loadFamilies()
        editChallengeId?.let { loadForEdit(it) }
    }

    fun onAction(action: CreateChallengeAction) {
        when (action) {
            is CreateChallengeAction.SelectBrand -> selectBrand(action.brand)
            is CreateChallengeAction.SelectFamily -> selectFamily(action.familyId)
            CreateChallengeAction.RetryLoadFamilies -> loadFamilies()
            CreateChallengeAction.RetryLoadModels -> retryLoadModels()
            is CreateChallengeAction.UpdateTitle -> updateTitle(action.value)
            is CreateChallengeAction.UpdateDescription -> updateDescription(action.value)
            is CreateChallengeAction.UpdateRequiredPosts -> updateRequiredPosts(action.value)
            is CreateChallengeAction.UpdateRewardPoints -> updateRewardPoints(action.value)
            is CreateChallengeAction.SetStartNow -> setStartNow(action.startNow)
            is CreateChallengeAction.UpdateStartsAt -> updateStartsAt(action.value)
            is CreateChallengeAction.UpdateEndsAt -> updateEndsAt(action.value)
            CreateChallengeAction.NextStep -> nextStep()
            CreateChallengeAction.PreviousStep -> previousStep()
            CreateChallengeAction.SaveDraft -> saveDraft()
            CreateChallengeAction.RequestPublish -> requestPublish()
            CreateChallengeAction.ConfirmPublish -> confirmPublish()
            CreateChallengeAction.DismissPublishSheet -> dismissPublishSheet()
            CreateChallengeAction.RetryPublish -> retryPublish()
            CreateChallengeAction.KeepAsDraft -> keepAsDraft()
            CreateChallengeAction.RequestClose -> requestClose()
            CreateChallengeAction.ConfirmDiscard -> confirmDiscard()
            CreateChallengeAction.DismissDiscardSheet -> dismissDiscardSheet()
        }
    }

    /** Resets the one-shot navigation effect once the screen has acted on it. */
    fun consumeNavigateToDetail() {
        _uiState.update { it.copy(navigateToDetailChallengeId = null) }
    }

    /** Resets the one-shot close effect once the screen has popped the back stack. */
    fun consumeClose() {
        _uiState.update { it.copy(closeRequested = false) }
    }

    // ---------- Step 1 — Vehicle ----------

    private fun loadFamilies() {
        _uiState.update { it.copy(familiesState = FamiliesState.Loading) }
        viewModelScope.launch {
            when (val result = adminCarFamilyRepository.listFamilies()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            familiesState = if (result.data.isEmpty()) {
                                FamiliesState.Empty
                            } else {
                                FamiliesState.Content(result.data)
                            },
                        )
                    }
                    resolveBrandIfNeeded()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(familiesState = FamiliesState.Error(result.message, result.isNetworkError))
                }
            }
        }
    }

    /** Loads an existing DRAFT for editing — [CreateChallengeMode.Edit]'s counterpart to
     * [loadFamilies]. Runs independently of it (either may finish first), so the family's *brand*
     * — not carried on [AdminChallenge] — is resolved separately via [resolveBrandIfNeeded] once
     * both the challenge and the family list are available. */
    private fun loadForEdit(challengeId: UUID) {
        _uiState.update { it.copy(initialLoadState = InitialLoadState.Loading) }
        viewModelScope.launch {
            when (val result = adminChallengeRepository.getChallenge(challengeId)) {
                is ApiResult.Success -> {
                    val challenge = result.data
                    if (challenge.status != ChallengeAdminStatus.DRAFT) {
                        _uiState.update { it.copy(initialLoadState = InitialLoadState.NotDraft) }
                        return@launch
                    }

                    // The window is converted with the challenge's own adminTimezone, not the
                    // device's — otherwise the prefilled fields would show different values than
                    // what was actually saved (plan §5).
                    val zone = ZoneId.of(challenge.adminTimezone)
                    val prefilledForm = CreateChallengeFormState(
                        selectedFamilyId = challenge.targetFamilyId,
                        title = challenge.title,
                        description = challenge.description.orEmpty(),
                        requiredPostsInput = challenge.requiredPosts.toString(),
                        rewardPointsInput = challenge.rewardPoints.toString(),
                        startNow = false,
                        startsAtLocal = LocalDateTime.ofInstant(challenge.startsAt, zone),
                        endsAtLocal = LocalDateTime.ofInstant(challenge.endsAt, zone),
                    )
                    _uiState.update {
                        it.copy(
                            form = prefilledForm,
                            initialForm = prefilledForm,
                            createdDraftId = challenge.id,
                            lastSubmittedForm = prefilledForm,
                            initialLoadState = InitialLoadState.Ready,
                        )
                    }
                    loadModelsForFamily(challenge.targetFamilyId)
                    resolveBrandIfNeeded()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(initialLoadState = InitialLoadState.Error(result.message))
                }
            }
        }
    }

    /** [AdminChallenge]/[loadForEdit] only carries `targetFamilyId`, not its brand — once the
     * family list has loaded, look the brand up so the Vehicle step's Brand selector shows the
     * right value. Safe to call from either [loadFamilies] or [loadForEdit], in either order. */
    private fun resolveBrandIfNeeded() {
        val state = _uiState.value
        val families = (state.familiesState as? FamiliesState.Content)?.families ?: return
        val familyId = state.form.selectedFamilyId ?: return
        if (state.form.selectedBrand != null) return
        val brand = families.firstOrNull { it.id == familyId }?.brand ?: return

        _uiState.update {
            it.copy(
                form = it.form.copy(selectedBrand = brand),
                initialForm = it.initialForm.copy(selectedBrand = brand),
            )
        }
    }

    private fun selectBrand(brand: String) {
        _uiState.update {
            it.copy(
                form = it.form.copy(selectedBrand = brand, selectedFamilyId = null),
                modelsState = ModelsState.Idle,
            )
        }
    }

    private fun selectFamily(familyId: UUID) {
        _uiState.update { it.copy(form = it.form.copy(selectedFamilyId = familyId)) }
        loadModelsForFamily(familyId)
    }

    private fun retryLoadModels() {
        _uiState.value.form.selectedFamilyId?.let { loadModelsForFamily(it) }
    }

    private fun loadModelsForFamily(familyId: UUID) {
        _uiState.update { it.copy(modelsState = ModelsState.Loading) }
        viewModelScope.launch {
            when (val result = adminCarFamilyRepository.listModelsForFamily(familyId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        modelsState = if (result.data.isEmpty()) {
                            ModelsState.EmptyForFamily
                        } else {
                            ModelsState.Content(result.data)
                        },
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(modelsState = ModelsState.Error(result.message))
                }
            }
        }
    }

    // ---------- Step 2 — Goal and reward ----------

    private fun updateTitle(value: String) {
        _uiState.update {
            it.copy(form = it.form.copy(title = value), fieldErrors = it.fieldErrors - CreateChallengeField.TITLE)
        }
    }

    private fun updateDescription(value: String) {
        _uiState.update { it.copy(form = it.form.copy(description = value)) }
    }

    private fun updateRequiredPosts(value: String) {
        _uiState.update {
            it.copy(
                form = it.form.copy(requiredPostsInput = value),
                fieldErrors = it.fieldErrors - CreateChallengeField.REQUIRED_POSTS,
            )
        }
    }

    private fun updateRewardPoints(value: String) {
        _uiState.update {
            it.copy(
                form = it.form.copy(rewardPointsInput = value),
                fieldErrors = it.fieldErrors - CreateChallengeField.REWARD_POINTS,
            )
        }
    }

    // ---------- Step 3 — Schedule and review ----------

    private fun setStartNow(startNow: Boolean) {
        _uiState.update {
            it.copy(
                form = it.form.copy(startNow = startNow),
                fieldErrors = it.fieldErrors - CreateChallengeField.SCHEDULE,
            )
        }
    }

    private fun updateStartsAt(value: LocalDateTime) {
        _uiState.update {
            it.copy(
                form = it.form.copy(startsAtLocal = value),
                fieldErrors = it.fieldErrors - CreateChallengeField.SCHEDULE,
            )
        }
    }

    private fun updateEndsAt(value: LocalDateTime) {
        _uiState.update {
            it.copy(
                form = it.form.copy(endsAtLocal = value),
                fieldErrors = it.fieldErrors - CreateChallengeField.SCHEDULE,
            )
        }
    }

    // ---------- Step navigation ----------

    private fun nextStep() {
        val state = _uiState.value
        when (state.step) {
            WizardStep.Vehicle -> if (validateVehicleStep(state)) {
                _uiState.update { it.copy(step = WizardStep.GoalReward) }
            }

            WizardStep.GoalReward -> {
                val errors = validateGoalStep(state.form)
                if (errors.isEmpty()) {
                    _uiState.update {
                        it.copy(step = WizardStep.ScheduleReview, fieldErrors = it.fieldErrors - GOAL_STEP_FIELDS)
                    }
                } else {
                    _uiState.update { it.copy(fieldErrors = it.fieldErrors + errors) }
                }
            }

            // Schedule and review is the last step — it has no `Next`, only Save draft/Publish.
            WizardStep.ScheduleReview -> Unit
        }
    }

    private fun previousStep() {
        _uiState.update {
            when (it.step) {
                WizardStep.GoalReward -> it.copy(step = WizardStep.Vehicle)
                WizardStep.ScheduleReview -> it.copy(step = WizardStep.GoalReward)
                WizardStep.Vehicle -> it
            }
        }
    }

    // ---------- Back / discard ----------

    private fun requestClose() {
        _uiState.update {
            if (it.isDirty) it.copy(showDiscardSheet = true) else it.copy(closeRequested = true)
        }
    }

    private fun confirmDiscard() {
        _uiState.update { it.copy(showDiscardSheet = false, closeRequested = true) }
    }

    private fun dismissDiscardSheet() {
        _uiState.update { it.copy(showDiscardSheet = false) }
    }

    // ---------- Submit orchestration ----------

    /** Full re-validation across all three steps — always run fresh at submit time so a window
     * that was valid when entered but has since slipped into the past is still caught
     * (see the plan's §5, "re-validare la submit"). */
    private fun validateForSubmit(state: CreateChallengeUiState, now: LocalDateTime): Map<CreateChallengeField, String> {
        val errors = validateGoalStep(state.form).toMutableMap()
        validateScheduleStep(state.form, now)?.let { errors[CreateChallengeField.SCHEDULE] = it }
        return errors
    }

    private fun buildSubmitFields(form: CreateChallengeFormState, now: LocalDateTime): SubmitFields {
        val startsAt = if (form.startNow) now else requireNotNull(form.startsAtLocal)
        return SubmitFields(
            title = form.title.trim(),
            description = form.description.trim().ifBlank { null },
            targetFamilyId = requireNotNull(form.selectedFamilyId),
            requiredPosts = form.requiredPostsInput.trim().toInt(),
            rewardPoints = form.rewardPointsInput.trim().toInt(),
            startsAtLocal = startsAt.toChallengeLocalIsoString(),
            endsAtLocal = requireNotNull(form.endsAtLocal).toChallengeLocalIsoString(),
            timezone = ZoneId.systemDefault().id,
        )
    }

    /** `createChallenge` only on the very first submit (no [CreateChallengeUiState.createdDraftId]
     * yet); every submit after that is `updateChallenge` against the same id. */
    private suspend fun createOrUpdateDraft(fields: SubmitFields): ApiResult<AdminChallenge> {
        val draftId = _uiState.value.createdDraftId
        return if (draftId == null) {
            adminChallengeRepository.createChallenge(
                title = fields.title,
                description = fields.description,
                targetFamilyId = fields.targetFamilyId,
                requiredPosts = fields.requiredPosts,
                rewardPoints = fields.rewardPoints,
                startsAtLocal = fields.startsAtLocal,
                endsAtLocal = fields.endsAtLocal,
                timezone = fields.timezone,
            )
        } else {
            adminChallengeRepository.updateChallenge(
                challengeId = draftId,
                title = fields.title,
                description = fields.description,
                targetFamilyId = fields.targetFamilyId,
                requiredPosts = fields.requiredPosts,
                rewardPoints = fields.rewardPoints,
                startsAtLocal = fields.startsAtLocal,
                endsAtLocal = fields.endsAtLocal,
                timezone = fields.timezone,
            )
        }
    }

    /** A 409 window-overlap conflict is remapped to friendlier copy — same rule and text as
     * `AdminChallengeDetailViewModel.mapPublishError` (`AdminChallengeDetailScreen.kt:149-154`). */
    private fun mapPublishError(message: String): String =
        if (message.contains("overlaps another", ignoreCase = true)) {
            "This window overlaps another scheduled challenge."
        } else {
            message
        }

    /** In edit mode, `updateChallenge` 409s with `ChallengeNotEditableException` if the draft was
     * published (or otherwise left DRAFT) by someone else between load and submit
     * (`ChallengeService.kt:291-293`). [ScheduleReviewStep] recognizes this exact message and
     * swaps the usual Save/Publish buttons for a single "Open details" — retrying the same submit
     * can't succeed, since the challenge genuinely isn't a DRAFT anymore (plan §5). */
    private fun mapUpdateConflictError(message: String): String =
        if (message.contains("is not editable", ignoreCase = true)) {
            "This challenge was already published."
        } else {
            message
        }

    private fun saveDraft() {
        val state = _uiState.value
        if (state.submitState == SubmitState.Submitting) return
        if (!validateVehicleStep(state)) return

        val now = LocalDateTime.now(ZoneId.systemDefault())
        val errors = validateForSubmit(state, now)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = it.fieldErrors + errors) }
            return
        }

        val fields = buildSubmitFields(state.form, now)
        _uiState.update { it.copy(submitState = SubmitState.Submitting) }
        viewModelScope.launch {
            when (val result = createOrUpdateDraft(fields)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        submitState = SubmitState.Idle,
                        createdDraftId = result.data.id,
                        lastSubmittedForm = state.form,
                        navigateToDetailChallengeId = result.data.id,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(submitState = SubmitState.Failed(mapUpdateConflictError(result.message)))
                }
            }
        }
    }

    /** Validates and opens the publish confirmation sheet; the actual create+publish calls happen
     * in [confirmPublish] once the admin confirms. */
    private fun requestPublish() {
        val state = _uiState.value
        if (state.submitState == SubmitState.Submitting) return
        if (!validateVehicleStep(state)) return

        val now = LocalDateTime.now(ZoneId.systemDefault())
        val errors = validateForSubmit(state, now)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = it.fieldErrors + errors) }
            return
        }

        _uiState.update { it.copy(showPublishSheet = true) }
    }

    private fun dismissPublishSheet() {
        _uiState.update { it.copy(showPublishSheet = false) }
    }

    private fun confirmPublish() {
        val state = _uiState.value
        if (state.submitState == SubmitState.Submitting) return

        val now = LocalDateTime.now(ZoneId.systemDefault())
        val fields = buildSubmitFields(state.form, now)
        _uiState.update { it.copy(submitState = SubmitState.Submitting, showPublishSheet = false) }

        viewModelScope.launch {
            when (val createResult = createOrUpdateDraft(fields)) {
                is ApiResult.Error -> _uiState.update {
                    it.copy(submitState = SubmitState.Failed(mapUpdateConflictError(createResult.message)))
                }
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(createdDraftId = createResult.data.id, lastSubmittedForm = state.form)
                    }
                    when (val publishResult = adminChallengeRepository.publishChallenge(createResult.data.id)) {
                        is ApiResult.Success -> _uiState.update {
                            it.copy(submitState = SubmitState.Idle, navigateToDetailChallengeId = publishResult.data.id)
                        }
                        is ApiResult.Error -> _uiState.update {
                            it.copy(submitState = SubmitState.PartialSuccess(mapPublishError(publishResult.message)))
                        }
                    }
                }
            }
        }
    }

    private fun retryPublish() {
        val state = _uiState.value
        val draftId = state.createdDraftId ?: return
        if (state.submitState == SubmitState.Submitting) return

        _uiState.update { it.copy(submitState = SubmitState.Submitting) }
        viewModelScope.launch {
            val currentForm = _uiState.value.form
            if (currentForm != state.lastSubmittedForm) {
                val now = LocalDateTime.now(ZoneId.systemDefault())
                val fields = buildSubmitFields(currentForm, now)
                when (
                    val updateResult = adminChallengeRepository.updateChallenge(
                        challengeId = draftId,
                        title = fields.title,
                        description = fields.description,
                        targetFamilyId = fields.targetFamilyId,
                        requiredPosts = fields.requiredPosts,
                        rewardPoints = fields.rewardPoints,
                        startsAtLocal = fields.startsAtLocal,
                        endsAtLocal = fields.endsAtLocal,
                        timezone = fields.timezone,
                    )
                ) {
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(submitState = SubmitState.Failed(mapUpdateConflictError(updateResult.message)))
                        }
                        return@launch
                    }
                    is ApiResult.Success -> _uiState.update { it.copy(lastSubmittedForm = currentForm) }
                }
            }

            when (val publishResult = adminChallengeRepository.publishChallenge(draftId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(submitState = SubmitState.Idle, navigateToDetailChallengeId = publishResult.data.id)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(submitState = SubmitState.PartialSuccess(mapPublishError(publishResult.message)))
                }
            }
        }
    }

    private fun keepAsDraft() {
        val draftId = _uiState.value.createdDraftId ?: return
        _uiState.update { it.copy(submitState = SubmitState.Idle, navigateToDetailChallengeId = draftId) }
    }
}
