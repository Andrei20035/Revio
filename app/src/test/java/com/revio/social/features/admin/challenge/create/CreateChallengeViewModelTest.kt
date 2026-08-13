package com.revio.social.features.admin.challenge.create

import androidx.lifecycle.SavedStateHandle
import com.revio.social.MainDispatcherRule
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.AdminCarFamily
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.ChallengeAdminStatus
import com.revio.social.data.remote.dto.car_model.CarModelOption
import com.revio.social.data.repository.AdminCarFamilyRepository
import com.revio.social.data.repository.AdminChallengeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreateChallengeViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var challengeRepository: AdminChallengeRepository
    private lateinit var carFamilyRepository: AdminCarFamilyRepository

    private val family = AdminCarFamily(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
        brand = "Volkswagen",
        name = "Golf",
    )
    private val models = listOf(
        CarModelOption(id = UUID.fromString("00000000-0000-0000-0000-0000000000e1"), model = "Golf"),
        CarModelOption(id = UUID.fromString("00000000-0000-0000-0000-0000000000e2"), model = "Golf Plus"),
    )

    @Before
    fun setup() {
        challengeRepository = mockk()
        carFamilyRepository = mockk()
    }

    private fun adminChallenge(id: UUID, status: ChallengeAdminStatus = ChallengeAdminStatus.DRAFT) = AdminChallenge(
        id = id,
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyId = family.id,
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        adminTimezone = "Europe/Bucharest",
        status = status,
        createdBy = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        publishedAt = null,
        cancelledAt = null,
        finalizedAt = null,
    )

    /** [adminChallenge] with a window safely in the future (year 2030) — edit-mode `loadForEdit`
     * always sets `startNow = false`, so submit-path tests need a window that won't itself trip
     * "Start time is in the past." regardless of when the test suite actually runs. */
    private fun editableDraftChallenge(id: UUID) = adminChallenge(id).copy(
        startsAt = Instant.parse("2030-08-07T06:00:00Z"),
        endsAt = Instant.parse("2030-08-09T06:00:00Z"),
    )

    private fun createEditViewModel(
        challenge: AdminChallenge,
        familiesResult: ApiResult<List<AdminCarFamily>> = ApiResult.Success(listOf(family)),
        modelsResult: ApiResult<List<CarModelOption>> = ApiResult.Success(models),
    ): CreateChallengeViewModel {
        coEvery { challengeRepository.getChallenge(challenge.id) } returns ApiResult.Success(challenge)
        coEvery { carFamilyRepository.listFamilies() } returns familiesResult
        coEvery { carFamilyRepository.listModelsForFamily(any()) } returns modelsResult
        return CreateChallengeViewModel(savedStateHandle(challenge.id), challengeRepository, carFamilyRepository)
    }

    // ---------- mock helpers (matchers must be re-declared inline on every call — MockK resolves
    // any()/eq() against a thread-local stack consumed by the very next mocked invocation) ----------

    private fun stubCreate(result: ApiResult<AdminChallenge>) {
        coEvery {
            challengeRepository.createChallenge(any(), any(), any(), any(), any(), any(), any(), any())
        } returns result
    }

    private fun verifyCreateCalled(times: Int) {
        coVerify(exactly = times) {
            challengeRepository.createChallenge(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    private fun stubUpdate(challengeId: UUID, result: ApiResult<AdminChallenge>) {
        coEvery {
            challengeRepository.updateChallenge(eq(challengeId), any(), any(), any(), any(), any(), any(), any(), any())
        } returns result
    }

    private fun verifyUpdateCalled(challengeId: UUID, times: Int) {
        coVerify(exactly = times) {
            challengeRepository.updateChallenge(eq(challengeId), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    private fun verifyUpdateNeverCalled() {
        coVerify(exactly = 0) {
            challengeRepository.updateChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    private fun stubPublish(challengeId: UUID, result: ApiResult<AdminChallenge>) {
        coEvery { challengeRepository.publishChallenge(challengeId) } returns result
    }

    private fun savedStateHandle(editChallengeId: UUID? = null): SavedStateHandle =
        if (editChallengeId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf(Screen.AdminChallengeCreate.ARG_CHALLENGE_ID to editChallengeId.toString()))
        }

    private fun createViewModel(
        families: List<AdminCarFamily> = listOf(family),
        familiesResult: ApiResult<List<AdminCarFamily>> = ApiResult.Success(families),
        modelsResult: ApiResult<List<CarModelOption>> = ApiResult.Success(models),
        editChallengeId: UUID? = null,
    ): CreateChallengeViewModel {
        coEvery { carFamilyRepository.listFamilies() } returns familiesResult
        coEvery { carFamilyRepository.listModelsForFamily(any()) } returns modelsResult
        return CreateChallengeViewModel(savedStateHandle(editChallengeId), challengeRepository, carFamilyRepository)
    }

    private fun CreateChallengeViewModel.advanceToGoalReward() {
        onAction(CreateChallengeAction.SelectBrand(family.brand))
        onAction(CreateChallengeAction.SelectFamily(family.id))
        onAction(CreateChallengeAction.NextStep)
    }

    private fun CreateChallengeViewModel.fillValidGoalStep(
        title: String = "Weekend Golf Hunt",
        requiredPosts: String = "5",
        rewardPoints: String = "300",
    ) {
        onAction(CreateChallengeAction.UpdateTitle(title))
        onAction(CreateChallengeAction.UpdateRequiredPosts(requiredPosts))
        onAction(CreateChallengeAction.UpdateRewardPoints(rewardPoints))
    }

    private fun CreateChallengeViewModel.advanceToScheduleReview() {
        fillValidGoalStep()
        onAction(CreateChallengeAction.NextStep)
    }

    /** Positions the VM at ScheduleReview with a fully valid form — brand/family/models chosen,
     * a valid title/counts, `Start now` (default) and a future end date. */
    private fun readyToSubmitViewModel(): CreateChallengeViewModel {
        val vm = createViewModel()
        vm.advanceToGoalReward()
        vm.advanceToScheduleReview()
        vm.onAction(CreateChallengeAction.UpdateEndsAt(LocalDateTime.now().plusDays(2)))
        return vm
    }

    // ---------- Familii (Step 1 data loading) ----------

    @Test
    fun `init incarca familiile si populeaza familiesState Content`() {
        val vm = createViewModel(families = listOf(family))

        val state = vm.uiState.value.familiesState
        assertTrue(state is FamiliesState.Content)
        assertEquals(listOf(family), (state as FamiliesState.Content).families)
    }

    @Test
    fun `familii goale seteaza familiesState Empty`() {
        val vm = createViewModel(familiesResult = ApiResult.Success(emptyList()))

        assertEquals(FamiliesState.Empty, vm.uiState.value.familiesState)
    }

    @Test
    fun `eroare la incarcarea familiilor seteaza familiesState Error`() {
        val vm = createViewModel(familiesResult = ApiResult.Error("Server error"))

        val state = vm.uiState.value.familiesState
        assertTrue(state is FamiliesState.Error)
        assertEquals("Server error", (state as FamiliesState.Error).message)
        assertFalse(state.isOffline)
    }

    @Test
    fun `eroare de retea la familii seteaza isOffline`() {
        val vm = createViewModel(familiesResult = ApiResult.Error("Network error", code = "network_unavailable"))

        val state = vm.uiState.value.familiesState as FamiliesState.Error
        assertTrue(state.isOffline)
    }

    @Test
    fun `RetryLoadFamilies reincearca incarcarea`() {
        coEvery { carFamilyRepository.listFamilies() } returns ApiResult.Error("Server error")
        coEvery { carFamilyRepository.listModelsForFamily(any()) } returns ApiResult.Success(models)
        val vm = CreateChallengeViewModel(savedStateHandle(), challengeRepository, carFamilyRepository)
        assertTrue(vm.uiState.value.familiesState is FamiliesState.Error)

        coEvery { carFamilyRepository.listFamilies() } returns ApiResult.Success(listOf(family))
        vm.onAction(CreateChallengeAction.RetryLoadFamilies)

        assertTrue(vm.uiState.value.familiesState is FamiliesState.Content)
    }

    @Test
    fun `brandsFromFamilies deriva branduri distincte si sortate`() {
        val families = listOf(
            AdminCarFamily(UUID.randomUUID(), "Volkswagen", "Golf"),
            AdminCarFamily(UUID.randomUUID(), "Toyota", "Corolla"),
            AdminCarFamily(UUID.randomUUID(), "Volkswagen", "Polo"),
        )

        assertEquals(listOf("Toyota", "Volkswagen"), brandsFromFamilies(families))
    }

    @Test
    fun `familiesForBrand filtreaza familiile la brandul cerut`() {
        val volkswagenGolf = AdminCarFamily(UUID.randomUUID(), "Volkswagen", "Golf")
        val toyotaCorolla = AdminCarFamily(UUID.randomUUID(), "Toyota", "Corolla")

        assertEquals(listOf(volkswagenGolf), familiesForBrand(listOf(volkswagenGolf, toyotaCorolla), "Volkswagen"))
    }

    // ---------- Vehicle step selection ----------

    @Test
    fun `selectBrand actualizeaza brandul selectat`() {
        val vm = createViewModel()

        vm.onAction(CreateChallengeAction.SelectBrand("Volkswagen"))

        assertEquals("Volkswagen", vm.uiState.value.form.selectedBrand)
    }

    @Test
    fun `schimbarea brandului dupa selectarea unei familii reseteaza familia si modelele`() {
        val vm = createViewModel()
        vm.onAction(CreateChallengeAction.SelectBrand(family.brand))
        vm.onAction(CreateChallengeAction.SelectFamily(family.id))
        assertTrue(vm.uiState.value.modelsState is ModelsState.Content)

        vm.onAction(CreateChallengeAction.SelectBrand("Toyota"))

        assertNull(vm.uiState.value.form.selectedFamilyId)
        assertEquals(ModelsState.Idle, vm.uiState.value.modelsState)
    }

    @Test
    fun `selectFamily declanseaza incarcarea modelelor si seteaza modelsState Content`() {
        val vm = createViewModel()

        vm.onAction(CreateChallengeAction.SelectFamily(family.id))

        val state = vm.uiState.value.modelsState
        assertTrue(state is ModelsState.Content)
        assertEquals(models, (state as ModelsState.Content).models)
    }

    @Test
    fun `familie fara modele seteaza modelsState EmptyForFamily si blocheaza Next`() {
        val vm = createViewModel(modelsResult = ApiResult.Success(emptyList()))
        vm.onAction(CreateChallengeAction.SelectBrand(family.brand))
        vm.onAction(CreateChallengeAction.SelectFamily(family.id))

        assertEquals(ModelsState.EmptyForFamily, vm.uiState.value.modelsState)
        assertFalse(validateVehicleStep(vm.uiState.value))

        vm.onAction(CreateChallengeAction.NextStep)
        assertEquals(WizardStep.Vehicle, vm.uiState.value.step)
    }

    @Test
    fun `eroare la incarcarea modelelor seteaza modelsState Error si blocheaza Next`() {
        val vm = createViewModel(modelsResult = ApiResult.Error("Server error"))
        vm.onAction(CreateChallengeAction.SelectBrand(family.brand))
        vm.onAction(CreateChallengeAction.SelectFamily(family.id))

        val state = vm.uiState.value.modelsState
        assertTrue(state is ModelsState.Error)
        assertFalse(validateVehicleStep(vm.uiState.value))

        vm.onAction(CreateChallengeAction.NextStep)
        assertEquals(WizardStep.Vehicle, vm.uiState.value.step)
    }

    @Test
    fun `Vehicle valid avanseaza la GoalReward la Next`() {
        val vm = createViewModel()
        vm.advanceToGoalReward()

        assertEquals(WizardStep.GoalReward, vm.uiState.value.step)
    }

    // ---------- Goal step ----------

    @Test
    fun `Next cu titlu gol seteaza fieldErrors TITLE Required si nu avanseaza`() {
        val vm = createViewModel()
        vm.advanceToGoalReward()

        vm.onAction(CreateChallengeAction.UpdateRequiredPosts("5"))
        vm.onAction(CreateChallengeAction.UpdateRewardPoints("300"))
        vm.onAction(CreateChallengeAction.NextStep)

        assertEquals(WizardStep.GoalReward, vm.uiState.value.step)
        assertEquals("Required.", vm.uiState.value.fieldErrors[CreateChallengeField.TITLE])
    }

    @Test
    fun `requiredPosts 0 seteaza mesajul Must be greater than 0`() {
        val vm = createViewModel()
        vm.advanceToGoalReward()

        vm.onAction(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"))
        vm.onAction(CreateChallengeAction.UpdateRequiredPosts("0"))
        vm.onAction(CreateChallengeAction.UpdateRewardPoints("300"))
        vm.onAction(CreateChallengeAction.NextStep)

        assertEquals("Must be greater than 0.", vm.uiState.value.fieldErrors[CreateChallengeField.REQUIRED_POSTS])
    }

    @Test
    fun `requiredPosts abc seteaza mesajul Enter a whole number`() {
        val vm = createViewModel()
        vm.advanceToGoalReward()

        vm.onAction(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"))
        vm.onAction(CreateChallengeAction.UpdateRequiredPosts("abc"))
        vm.onAction(CreateChallengeAction.UpdateRewardPoints("300"))
        vm.onAction(CreateChallengeAction.NextStep)

        assertEquals("Enter a whole number.", vm.uiState.value.fieldErrors[CreateChallengeField.REQUIRED_POSTS])
    }

    @Test
    fun `titlu de 151 de caractere seteaza eroare de lungime fara apel de retea`() {
        val vm = createViewModel()
        vm.advanceToGoalReward()
        stubCreate(ApiResult.Success(adminChallenge(UUID.randomUUID())))

        vm.onAction(CreateChallengeAction.UpdateTitle("A".repeat(151)))
        vm.onAction(CreateChallengeAction.UpdateRequiredPosts("5"))
        vm.onAction(CreateChallengeAction.UpdateRewardPoints("300"))
        vm.onAction(CreateChallengeAction.NextStep)

        assertEquals("Keep it under 150 characters.", vm.uiState.value.fieldErrors[CreateChallengeField.TITLE])
        verifyCreateCalled(0)
    }

    @Test
    fun `tastare dupa eroare curata eroarea acelui camp`() {
        val vm = createViewModel()
        vm.advanceToGoalReward()
        vm.onAction(CreateChallengeAction.UpdateRequiredPosts("5"))
        vm.onAction(CreateChallengeAction.UpdateRewardPoints("300"))
        vm.onAction(CreateChallengeAction.NextStep)
        assertTrue(vm.uiState.value.fieldErrors.containsKey(CreateChallengeField.TITLE))

        vm.onAction(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"))

        assertFalse(vm.uiState.value.fieldErrors.containsKey(CreateChallengeField.TITLE))
    }

    // ---------- Step navigation ----------

    @Test
    fun `PreviousStep din ScheduleReview revine la GoalReward pastrand datele`() {
        val vm = readyToSubmitViewModel()
        val formBeforeBack = vm.uiState.value.form

        vm.onAction(CreateChallengeAction.PreviousStep)

        assertEquals(WizardStep.GoalReward, vm.uiState.value.step)
        assertEquals(formBeforeBack, vm.uiState.value.form)
    }

    // ---------- Schedule validation (pure functions) ----------

    @Test
    fun `validateScheduleStep detecteaza endsAt inainte de startsAt`() {
        val now = LocalDateTime.parse("2026-08-12T09:00:00")
        val form = CreateChallengeFormState(
            startNow = false,
            startsAtLocal = now.plusDays(2),
            endsAtLocal = now.plusDays(1),
        )

        assertEquals("End must be after start.", validateScheduleStep(form, now))
    }

    @Test
    fun `validateScheduleStep detecteaza start programat in trecut`() {
        val now = LocalDateTime.parse("2026-08-12T09:00:00")
        val form = CreateChallengeFormState(
            startNow = false,
            startsAtLocal = now.minusDays(1),
            endsAtLocal = now.plusDays(1),
        )

        assertEquals("Start time is in the past.", validateScheduleStep(form, now))
    }

    @Test
    fun `submit revalideaza schedule chiar daca fereastra a devenit invalida dupa ce pasul a fost atins`() {
        val vm = readyToSubmitViewModel()
        stubCreate(ApiResult.Success(adminChallenge(UUID.randomUUID())))

        vm.onAction(CreateChallengeAction.SetStartNow(false))
        vm.onAction(CreateChallengeAction.UpdateStartsAt(LocalDateTime.now().minusDays(2)))
        vm.onAction(CreateChallengeAction.UpdateEndsAt(LocalDateTime.now().minusDays(1)))

        vm.onAction(CreateChallengeAction.SaveDraft)

        assertEquals("Start time is in the past.", vm.uiState.value.fieldErrors[CreateChallengeField.SCHEDULE])
        verifyCreateCalled(0)
    }

    // ---------- Save draft ----------

    @Test
    fun `Start now true calculeaza startsAtLocal la momentul submitului`() {
        val vm = readyToSubmitViewModel()
        val startsAtSlot = slot<String>()
        coEvery {
            challengeRepository.createChallenge(any(), any(), any(), any(), any(), capture(startsAtSlot), any(), any())
        } returns ApiResult.Success(adminChallenge(UUID.randomUUID()))

        val before = LocalDateTime.now()
        vm.onAction(CreateChallengeAction.SaveDraft)
        val after = LocalDateTime.now()

        val captured = LocalDateTime.parse(startsAtSlot.captured)
        assertFalse(captured.isBefore(before.minusSeconds(5)))
        assertFalse(captured.isAfter(after.plusSeconds(5)))
    }

    @Test
    fun `submit trimite timezone-ul dispozitivului`() {
        val vm = readyToSubmitViewModel()
        val timezoneSlot = slot<String>()
        coEvery {
            challengeRepository.createChallenge(any(), any(), any(), any(), any(), any(), any(), capture(timezoneSlot))
        } returns ApiResult.Success(adminChallenge(UUID.randomUUID()))

        vm.onAction(CreateChallengeAction.SaveDraft)

        assertEquals(ZoneId.systemDefault().id, timezoneSlot.captured)
    }

    @Test
    fun `Save draft succes apeleaza createChallenge o singura data si seteaza efectul de navigare`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))

        vm.onAction(CreateChallengeAction.SaveDraft)

        verifyCreateCalled(1)
        assertEquals(created.id, vm.uiState.value.createdDraftId)
        assertEquals(created.id, vm.uiState.value.navigateToDetailChallengeId)
        assertEquals(SubmitState.Idle, vm.uiState.value.submitState)
    }

    @Test
    fun `Save draft eroare seteaza SubmitState Failed`() {
        val vm = readyToSubmitViewModel()
        stubCreate(ApiResult.Error("Server error"))

        vm.onAction(CreateChallengeAction.SaveDraft)

        assertEquals(SubmitState.Failed("Server error"), vm.uiState.value.submitState)
        assertNull(vm.uiState.value.navigateToDetailChallengeId)
    }

    @Test
    fun `double submit in timp ce isSubmitting nu produce un al doilea apel`() {
        val vm = readyToSubmitViewModel()
        val pending = CompletableDeferred<ApiResult<AdminChallenge>>()
        coEvery {
            challengeRepository.createChallenge(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers { pending.await() }

        vm.onAction(CreateChallengeAction.SaveDraft)
        assertEquals(SubmitState.Submitting, vm.uiState.value.submitState)

        vm.onAction(CreateChallengeAction.SaveDraft)

        verifyCreateCalled(1)
        pending.complete(ApiResult.Success(adminChallenge(UUID.randomUUID())))
    }

    // ---------- Publish ----------

    @Test
    fun `Publish succes apeleaza createChallenge apoi publishChallenge in ordine`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Success(created))

        vm.onAction(CreateChallengeAction.RequestPublish)
        assertTrue(vm.uiState.value.showPublishSheet)
        vm.onAction(CreateChallengeAction.ConfirmPublish)

        coVerifyOrder {
            challengeRepository.createChallenge(any(), any(), any(), any(), any(), any(), any(), any())
            challengeRepository.publishChallenge(created.id)
        }
        assertEquals(created.id, vm.uiState.value.navigateToDetailChallengeId)
        assertEquals(SubmitState.Idle, vm.uiState.value.submitState)
        assertFalse(vm.uiState.value.showPublishSheet)
    }

    @Test
    fun `create reusit dar publish esuat seteaza PartialSuccess si pastreaza createdDraftId fara navigare`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Error("Server error"))

        vm.onAction(CreateChallengeAction.RequestPublish)
        vm.onAction(CreateChallengeAction.ConfirmPublish)

        assertEquals(created.id, vm.uiState.value.createdDraftId)
        assertEquals(SubmitState.PartialSuccess("Server error"), vm.uiState.value.submitState)
        assertNull(vm.uiState.value.navigateToDetailChallengeId)
    }

    @Test
    fun `retry publish dupa succes partial nu recreaza draftul`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Error("Server error"))
        vm.onAction(CreateChallengeAction.RequestPublish)
        vm.onAction(CreateChallengeAction.ConfirmPublish)
        assertTrue(vm.uiState.value.submitState is SubmitState.PartialSuccess)

        stubPublish(created.id, ApiResult.Success(created))
        vm.onAction(CreateChallengeAction.RetryPublish)

        verifyCreateCalled(1)
        verifyUpdateNeverCalled()
        assertEquals(created.id, vm.uiState.value.navigateToDetailChallengeId)
    }

    @Test
    fun `editare dupa succes partial apoi retry apeleaza updateChallenge apoi publishChallenge fara create`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Error("Server error"))
        vm.onAction(CreateChallengeAction.RequestPublish)
        vm.onAction(CreateChallengeAction.ConfirmPublish)
        assertTrue(vm.uiState.value.submitState is SubmitState.PartialSuccess)

        vm.onAction(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt (revised)"))
        stubUpdate(created.id, ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Success(created))

        vm.onAction(CreateChallengeAction.RetryPublish)

        verifyCreateCalled(1)
        verifyUpdateCalled(created.id, 1)
        coVerifyOrder {
            challengeRepository.updateChallenge(eq(created.id), any(), any(), any(), any(), any(), any(), any(), any())
            challengeRepository.publishChallenge(created.id)
        }
        assertEquals(created.id, vm.uiState.value.navigateToDetailChallengeId)
    }

    @Test
    fun `publish 409 overlap primeste mesaj prietenos`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Error("Challenge overlaps another scheduled challenge"))

        vm.onAction(CreateChallengeAction.RequestPublish)
        vm.onAction(CreateChallengeAction.ConfirmPublish)

        assertEquals(
            SubmitState.PartialSuccess("This window overlaps another scheduled challenge."),
            vm.uiState.value.submitState,
        )
    }

    @Test
    fun `KeepAsDraft navigheaza la draftul deja creat fara alt apel de retea`() {
        val vm = readyToSubmitViewModel()
        val created = adminChallenge(UUID.randomUUID())
        stubCreate(ApiResult.Success(created))
        stubPublish(created.id, ApiResult.Error("Server error"))
        vm.onAction(CreateChallengeAction.RequestPublish)
        vm.onAction(CreateChallengeAction.ConfirmPublish)

        vm.onAction(CreateChallengeAction.KeepAsDraft)

        assertEquals(created.id, vm.uiState.value.navigateToDetailChallengeId)
        verifyCreateCalled(1)
    }

    // ---------- Discard / close ----------

    @Test
    fun `isDirty fals la intrare nu cere confirmare la close`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.isDirty)

        vm.onAction(CreateChallengeAction.RequestClose)

        assertFalse(vm.uiState.value.showDiscardSheet)
        assertTrue(vm.uiState.value.closeRequested)
    }

    @Test
    fun `isDirty adevarat dupa o tastare cere confirmare la close`() {
        val vm = createViewModel()
        vm.onAction(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"))
        assertTrue(vm.uiState.value.isDirty)

        vm.onAction(CreateChallengeAction.RequestClose)

        assertTrue(vm.uiState.value.showDiscardSheet)
        assertFalse(vm.uiState.value.closeRequested)
    }

    @Test
    fun `ConfirmDiscard inchide sheet-ul si cere navigarea inapoi`() {
        val vm = createViewModel()
        vm.onAction(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"))
        vm.onAction(CreateChallengeAction.RequestClose)

        vm.onAction(CreateChallengeAction.ConfirmDiscard)

        assertFalse(vm.uiState.value.showDiscardSheet)
        assertTrue(vm.uiState.value.closeRequested)
    }

    @Test
    fun `consumeClose reseteaza efectul one-shot`() {
        val vm = createViewModel()
        vm.onAction(CreateChallengeAction.RequestClose)
        assertTrue(vm.uiState.value.closeRequested)

        vm.consumeClose()

        assertFalse(vm.uiState.value.closeRequested)
    }

    // ---------- Mod edit (Etapa 10) ----------

    @Test
    fun `mod edit incarca draftul si prefill-uieste toate campurile, familia si brandul deduse din targetFamilyId`() {
        val challenge = editableDraftChallenge(UUID.fromString("00000000-0000-0000-0000-0000000000d1"))
        val vm = createEditViewModel(challenge)

        val state = vm.uiState.value
        assertEquals(CreateChallengeMode.Edit(challenge.id), state.mode)
        assertEquals(InitialLoadState.Ready, state.initialLoadState)
        assertEquals(challenge.id, state.createdDraftId)

        assertEquals(challenge.title, state.form.title)
        assertEquals("", state.form.description)
        assertEquals("5", state.form.requiredPostsInput)
        assertEquals("300", state.form.rewardPointsInput)
        assertEquals(family.id, state.form.selectedFamilyId)
        assertEquals(family.brand, state.form.selectedBrand)
        assertFalse(state.form.startNow)
        // adminTimezone is "Europe/Bucharest" (UTC+3 in August) — 06:00Z becomes 09:00 local, not
        // the device zone's conversion (plan §5's whole point of this test).
        assertEquals(LocalDateTime.of(2030, 8, 7, 9, 0), state.form.startsAtLocal)
        assertEquals(LocalDateTime.of(2030, 8, 9, 9, 0), state.form.endsAtLocal)

        // initialForm mirrors the loaded state, not an empty form — isDirty must be false here.
        assertEquals(state.form, state.initialForm)
        assertFalse(state.isDirty)
    }

    @Test
    fun `mod edit challenge care nu mai e DRAFT la load seteaza NotDraft si nu populeaza formularul`() {
        val challenge = adminChallenge(
            UUID.fromString("00000000-0000-0000-0000-0000000000d2"),
            status = ChallengeAdminStatus.SCHEDULED,
        )
        val vm = createEditViewModel(challenge)

        val state = vm.uiState.value
        assertEquals(InitialLoadState.NotDraft, state.initialLoadState)
        assertEquals(CreateChallengeFormState(), state.form)
        assertNull(state.createdDraftId)
    }

    @Test
    fun `mod edit 409 la submit seteaza mesajul de conflict fara a recrea draftul`() {
        val challenge = editableDraftChallenge(UUID.fromString("00000000-0000-0000-0000-0000000000d3"))
        val vm = createEditViewModel(challenge)
        coEvery {
            challengeRepository.updateChallenge(eq(challenge.id), any(), any(), any(), any(), any(), any(), any(), any())
        } returns ApiResult.Error("Challenge ${challenge.id} is not editable: challenge is not DRAFT (status=SCHEDULED)")

        vm.onAction(CreateChallengeAction.SaveDraft)

        assertEquals(
            SubmitState.Failed("This challenge was already published."),
            vm.uiState.value.submitState,
        )
        verifyCreateCalled(0)
    }

    @Test
    fun `publish din mod edit apeleaza updateChallenge apoi publishChallenge, niciodata createChallenge`() {
        val challenge = editableDraftChallenge(UUID.fromString("00000000-0000-0000-0000-0000000000d4"))
        val vm = createEditViewModel(challenge)
        stubUpdate(challenge.id, ApiResult.Success(challenge))
        stubPublish(challenge.id, ApiResult.Success(challenge))

        vm.onAction(CreateChallengeAction.RequestPublish)
        assertTrue(vm.uiState.value.showPublishSheet)
        vm.onAction(CreateChallengeAction.ConfirmPublish)

        verifyCreateCalled(0)
        coVerifyOrder {
            challengeRepository.updateChallenge(
                eq(challenge.id), any(), any(), any(), any(), any(), any(), any(), any(),
            )
            challengeRepository.publishChallenge(challenge.id)
        }
        assertEquals(challenge.id, vm.uiState.value.navigateToDetailChallengeId)
        assertEquals(SubmitState.Idle, vm.uiState.value.submitState)
    }
}
