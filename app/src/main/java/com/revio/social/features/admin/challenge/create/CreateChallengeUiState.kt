package com.revio.social.features.admin.challenge.create

import com.revio.social.data.model.AdminCarFamily
import com.revio.social.data.remote.dto.car_model.CarModelOption
import java.time.LocalDateTime
import java.util.UUID

private const val MAX_TITLE_LENGTH = 150

/** Which destination the create-challenge route was opened in. [Edit]'s prefill/guard behavior
 * lands in a later step; here the mode only shapes the state (see the plan's §8). */
sealed interface CreateChallengeMode {
    data object Create : CreateChallengeMode
    data class Edit(val challengeId: UUID) : CreateChallengeMode
}

enum class WizardStep {
    Vehicle,
    GoalReward,
    ScheduleReview,
}

/** Keys for [CreateChallengeUiState.fieldErrors]. [SCHEDULE] covers the whole Schedule step
 * (`endsAt`/`startsAt` window) since it isn't a single text field. */
enum class CreateChallengeField {
    TITLE,
    REQUIRED_POSTS,
    REWARD_POINTS,
    SCHEDULE,
}

sealed interface FamiliesState {
    data object Loading : FamiliesState
    data class Content(val families: List<AdminCarFamily>) : FamiliesState
    data object Empty : FamiliesState
    data class Error(val message: String, val isOffline: Boolean) : FamiliesState
}

sealed interface ModelsState {
    data object Idle : ModelsState
    data object Loading : ModelsState
    data class Content(val models: List<CarModelOption>) : ModelsState
    data object EmptyForFamily : ModelsState
    data class Error(val message: String) : ModelsState
}

/** Only meaningful in [CreateChallengeMode.Edit] — the initial `getChallenge` load. A later step
 * wires this up; in create mode it stays [Ready]. */
sealed interface InitialLoadState {
    data object Loading : InitialLoadState
    data object Ready : InitialLoadState
    data object NotDraft : InitialLoadState
    data class Error(val message: String) : InitialLoadState
}

sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState

    /** The draft was created/updated but `publish` failed — [createdDraftId] on the outer state
     * is not lost, and [CreateChallengeAction.RetryPublish] never recreates it. */
    data class PartialSuccess(val publishErrorMessage: String) : SubmitState
    data class Failed(val message: String) : SubmitState
}

/** The three steps' fields, held as one object so `isDirty` is a single comparison against
 * [CreateChallengeUiState.initialForm]. Numeric inputs stay [String] so "empty", "not a number",
 * and "zero" are distinguishable — see [validateGoalStep]. */
data class CreateChallengeFormState(
    // Step 1 — Vehicle
    val selectedBrand: String? = null,
    val selectedFamilyId: UUID? = null,
    // Step 2 — Goal and reward
    val title: String = "",
    val description: String = "",
    val requiredPostsInput: String = "",
    val rewardPointsInput: String = "",
    // Step 3 — Schedule and review
    val startNow: Boolean = true,
    val startsAtLocal: LocalDateTime? = null,
    val endsAtLocal: LocalDateTime? = null,
)

data class CreateChallengeUiState(
    val mode: CreateChallengeMode = CreateChallengeMode.Create,
    val step: WizardStep = WizardStep.Vehicle,
    val form: CreateChallengeFormState = CreateChallengeFormState(),
    val initialForm: CreateChallengeFormState = CreateChallengeFormState(),
    val familiesState: FamiliesState = FamiliesState.Loading,
    val modelsState: ModelsState = ModelsState.Idle,
    val initialLoadState: InitialLoadState = InitialLoadState.Ready,
    val fieldErrors: Map<CreateChallengeField, String> = emptyMap(),
    val submitState: SubmitState = SubmitState.Idle,
    /** `null` until the first successful create. Once set, every later save/publish/retry goes
     * through `updateChallenge`/`publishChallenge` for this id — `createChallenge` is never
     * called again (the server has no create idempotency; this is the client-side guarantee). */
    val createdDraftId: UUID? = null,
    /** Snapshot of [form] as of the last successful create/update call. [RetryPublish] only
     * re-sends an update when [form] has since diverged from this. */
    val lastSubmittedForm: CreateChallengeFormState? = null,
    val showDiscardSheet: Boolean = false,
    val showPublishSheet: Boolean = false,
    /** One-shot navigation effect; the screen calls `consumeNavigateToDetail()` after acting on it. */
    val navigateToDetailChallengeId: UUID? = null,
    /** One-shot close effect; the screen calls `consumeClose()` after popping the back stack. */
    val closeRequested: Boolean = false,
) {
    val isDirty: Boolean get() = form != initialForm
}

/** Distinct brands, derived client-side — the server has no `/admin/car-families/brands` endpoint. */
fun brandsFromFamilies(families: List<AdminCarFamily>): List<String> =
    families.map { it.brand }.distinct().sorted()

fun familiesForBrand(families: List<AdminCarFamily>, brand: String): List<AdminCarFamily> =
    families.filter { it.brand == brand }

/** `Next` from Vehicle requires a brand, a family, and that family's models to have loaded
 * non-empty — a family with zero models is blocked rather than silently accepted (the server only
 * checks the family exists, `ChallengeService.kt:235`). */
fun validateVehicleStep(state: CreateChallengeUiState): Boolean {
    val modelsState = state.modelsState
    return state.form.selectedBrand != null &&
        state.form.selectedFamilyId != null &&
        modelsState is ModelsState.Content &&
        modelsState.models.isNotEmpty()
}

/** Mirrors `ChallengeService.kt:232-234` (non-blank title, positive counts) plus a client-only
 * 150-char cap — the server has no length check and the title column is `VARCHAR(150)`
 * (`V24__challenges.sql:18`), so an over-length title would otherwise surface as a bare 500. */
fun validateGoalStep(form: CreateChallengeFormState): Map<CreateChallengeField, String> {
    val errors = mutableMapOf<CreateChallengeField, String>()

    val trimmedTitle = form.title.trim()
    when {
        trimmedTitle.isEmpty() -> errors[CreateChallengeField.TITLE] = "Required."
        trimmedTitle.length > MAX_TITLE_LENGTH -> errors[CreateChallengeField.TITLE] = "Keep it under 150 characters."
    }

    validatePositiveWholeNumber(form.requiredPostsInput)?.let { errors[CreateChallengeField.REQUIRED_POSTS] = it }
    validatePositiveWholeNumber(form.rewardPointsInput)?.let { errors[CreateChallengeField.REWARD_POINTS] = it }

    return errors
}

private fun validatePositiveWholeNumber(input: String): String? {
    if (input.isBlank()) return "Required."
    val value = input.trim().toIntOrNull() ?: return "Enter a whole number."
    if (value <= 0) return "Must be greater than 0."
    return null
}

/** Mirrors `ChallengeService.kt:240` (`endsAt` after `startsAt`) plus a client-only "not in the
 * past" rule the server doesn't enforce. [now] is passed in rather than read from the system
 * clock so the check can be re-run fresh at submit time, not just at the moment fields were
 * entered — see the plan's §5. */
fun validateScheduleStep(form: CreateChallengeFormState, now: LocalDateTime): String? {
    if (!form.startNow && form.startsAtLocal == null) return "Pick a start date and time."
    if (form.endsAtLocal == null) return "Pick an end date and time."

    val startsAt = if (form.startNow) now else form.startsAtLocal
    if (!form.startNow && startsAt != null && startsAt.isBefore(now)) return "Start time is in the past."
    if (startsAt != null && !form.endsAtLocal.isAfter(startsAt)) return "End must be after start."

    return null
}
