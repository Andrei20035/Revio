package com.revio.app.features.settings.personalinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.revio.app.R
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.scaling.LocalActivityScale
import com.revio.app.core.ui.scaling.LocalProfileScale
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.scaling.profileScaledV
import com.revio.app.core.ui.scaling.rememberActivityScale
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.core.ui.theme.ProfileFieldErrorColor
import com.revio.app.core.ui.theme.ProfileFieldHintColor
import com.revio.app.features.profile.components.BirthDateField
import com.revio.app.features.profile.components.CountryDropdown
import com.revio.app.features.profile.components.EditableImageContainer
import com.revio.app.features.profile.components.ImageTransformState
import com.revio.app.features.profile.components.LabeledTextField
import com.revio.app.features.profile.components.PinchHintOverlay
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PersonalInfoScreen(
    navController: NavController,
    isNavTransitionRunning: Boolean = false,
    viewModel: PersonalInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var lockedDialogField by remember { mutableStateOf<PersonalInfoField?>(null) }
    var infoDialogField by remember { mutableStateOf<PersonalInfoField?>(null) }
    var showPermanentConfirmDialog by remember { mutableStateOf(false) }

    // Avoid composing the (heavier) form while the nav slide-in animation is still
    // running, so the first composition of its fields doesn't compete with the
    // animation's frames. This does not delay the loading spinner itself.
    var navTransitionFinished by remember { mutableStateOf(!isNavTransitionRunning) }
    LaunchedEffect(isNavTransitionRunning) {
        if (!isNavTransitionRunning) navTransitionFinished = true
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            navController.popBackStack()
        }
    }

    AppScreenBackground(showBottomScrim = false) {
        val activityScale = rememberActivityScale()
        CompositionLocalProvider(
            LocalActivityScale provides activityScale,
            // The shared profile fields use profileScaled() for their height. Keep that
            // scale in sync with the settings screens, where fields use actScaled().
            LocalProfileScale provides activityScale,
        ) {
            when {
                uiState.isLoading || !navTransitionFinished -> PersonalInfoLoadingContent(
                    onBack = { navController.popBackStack() },
                )

                uiState.user == null -> PersonalInfoLoadErrorContent(
                    message = uiState.generalError ?: "Couldn't load your personal information.",
                    onBack = { navController.popBackStack() },
                    onRetry = viewModel::retryLoadCurrentUser,
                )

                else -> PersonalInfoForm(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onImagePicked = viewModel::onImagePicked,
                    onFullNameChanged = viewModel::onFullNameChanged,
                    onUsernameChanged = viewModel::onUsernameChanged,
                    onCountryChanged = viewModel::onCountryChanged,
                    onBirthDateChanged = viewModel::onBirthDateChanged,
                    onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                    onSave = viewModel::onSave,
                    onLockedClick = { lockedDialogField = it },
                    onInfoClick = { infoDialogField = it },
                    onPermanentChangesRequested = { showPermanentConfirmDialog = true },
                )
            }

            if (!uiState.isLoading && uiState.user != null && uiState.isPositioningImage) {
                ImagePositioningOverlay(
                    uiState = uiState,
                    onTransformChanged = viewModel::onTransformChanged,
                    onDoneImage = viewModel::onDoneImage,
                )
            }
        }
    }

    lockedDialogField?.let { field ->
        LockedFieldDialog(
            field = field,
            unlockAt = uiState.unlockAt[field],
            onDismiss = { lockedDialogField = null },
        )
    }

    infoDialogField?.let { field ->
        FieldRuleInfoDialog(
            field = field,
            onDismiss = { infoDialogField = null },
        )
    }

    if (showPermanentConfirmDialog) {
        PermanentChangeConfirmDialog(
            fields = uiState.pendingPermanentFields,
            onConfirm = {
                showPermanentConfirmDialog = false
                viewModel.onSave()
            },
            onDismiss = { showPermanentConfirmDialog = false },
        )
    }
}

@Composable
private fun PersonalInfoForm(
    uiState: PersonalInfoUiState,
    onBack: () -> Unit,
    onImagePicked: (android.net.Uri) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onCountryChanged: (String) -> Unit,
    onBirthDateChanged: (java.time.LocalDate) -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    onSave: () -> Unit,
    onLockedClick: (PersonalInfoField) -> Unit,
    onInfoClick: (PersonalInfoField) -> Unit,
    onPermanentChangesRequested: () -> Unit,
) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
                // ── Top bar ──────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp.actScaled()),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp.actScaled()))
                    Text(
                        text = "Personal info",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                // ── Avatar ───────────────────────────────────────────────
                Spacer(modifier = Modifier.height(24.dp.actScaled()))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileAvatar(
                        uiState = uiState,
                        onImagePicked = onImagePicked,
                    )
                }

                // ── Fields ───────────────────────────────────────────────
                Spacer(modifier = Modifier.height(28.dp.actScaled()))

                val fullNameLocked = uiState.canChange[PersonalInfoField.FULL_NAME] == false
                LabeledTextField(
                    label = "Full name",
                    value = uiState.fullName,
                    onValueChange = onFullNameChanged,
                    placeholderText = "Full name",
                    enabled = !fullNameLocked,
                    isError = uiState.fieldErrors[PersonalInfoField.FULL_NAME] != null,
                    accentFocus = true,
                    onLockedClick = { onLockedClick(PersonalInfoField.FULL_NAME) },
                    trailingContent = {
                        FieldTrailingIcon(
                            locked = fullNameLocked,
                            onInfoClick = { onInfoClick(PersonalInfoField.FULL_NAME) },
                        )
                    },
                )
                uiState.fieldErrors[PersonalInfoField.FULL_NAME]?.let {
                    FieldWarning(text = it, isError = true)
                }
                Spacer(Modifier.height(23.dp.profileScaledV()))

                val usernameLocked = uiState.canChange[PersonalInfoField.USERNAME] == false
                LabeledTextField(
                    label = "Username",
                    value = uiState.username,
                    onValueChange = onUsernameChanged,
                    placeholderText = "Username",
                    enabled = !usernameLocked,
                    isError = uiState.fieldErrors[PersonalInfoField.USERNAME] != null ||
                        uiState.usernameCheck is UsernameCheckState.Taken ||
                        uiState.usernameCheck is UsernameCheckState.Invalid,
                    accentFocus = true,
                    onLockedClick = { onLockedClick(PersonalInfoField.USERNAME) },
                    trailingContent = {
                        FieldTrailingIcon(
                            locked = usernameLocked,
                            onInfoClick = { onInfoClick(PersonalInfoField.USERNAME) },
                        )
                    },
                )
                usernameStatusText(uiState)?.let { (text, isErr) ->
                    FieldWarning(text = text, isError = isErr)
                } ?: run {
                    Spacer(Modifier.height(23.dp.profileScaledV()))
                }


                val countryLocked = uiState.canChange[PersonalInfoField.COUNTRY] == false
                CountryDropdown(
                    selectedCountry = uiState.country,
                    onCountrySelected = { onCountryChanged(it.name) },
                    enabled = !countryLocked,
                    isError = uiState.fieldErrors[PersonalInfoField.COUNTRY] != null,
                    accentFocus = true,
                    onLockedClick = { onLockedClick(PersonalInfoField.COUNTRY) },
                    trailingContent = {
                        FieldTrailingIcon(
                            locked = countryLocked,
                            onInfoClick = { onInfoClick(PersonalInfoField.COUNTRY) },
                        )
                    },
                )
                uiState.fieldErrors[PersonalInfoField.COUNTRY]?.let {
                    FieldWarning(text = it, isError = true)
                } ?: run {
                    Spacer(Modifier.height(23.dp.profileScaledV()))
                }

                Spacer(modifier = Modifier.height(4.dp.actScaled()))
                val birthDateLocked = uiState.canChange[PersonalInfoField.BIRTH_DATE] == false
                BirthDateField(
                    birthDate = uiState.birthDate,
                    onBirthDateChanged = onBirthDateChanged,
                    enabled = !birthDateLocked,
                    isError = uiState.fieldErrors[PersonalInfoField.BIRTH_DATE] != null,
                    accentFocus = true,
                    onLockedClick = { onLockedClick(PersonalInfoField.BIRTH_DATE) },
                    trailingContent = {
                        FieldTrailingIcon(
                            locked = birthDateLocked,
                            onInfoClick = { onInfoClick(PersonalInfoField.BIRTH_DATE) },
                        )
                    },
                )
                uiState.fieldErrors[PersonalInfoField.BIRTH_DATE]?.let {
                    FieldWarning(text = it, isError = true)
                } ?: run {
                    Spacer(Modifier.height(23.dp.profileScaledV()))
                }

                val phoneLocked = uiState.canChange[PersonalInfoField.PHONE_NUMBER] == false
                LabeledTextField(
                    label = "Phone number",
                    value = uiState.phoneNumber,
                    onValueChange = onPhoneNumberChanged,
                    placeholderText = "Phone number",
                    enabled = !phoneLocked,
                    isError = uiState.fieldErrors[PersonalInfoField.PHONE_NUMBER] != null,
                    accentFocus = true,
                    onLockedClick = { onLockedClick(PersonalInfoField.PHONE_NUMBER) },
                    trailingContent = {
                        FieldTrailingIcon(
                            locked = phoneLocked,
                            onInfoClick = { onInfoClick(PersonalInfoField.PHONE_NUMBER) },
                        )
                    },
                )
                uiState.fieldErrors[PersonalInfoField.PHONE_NUMBER]?.let {
                    FieldWarning(text = it, isError = true)
                } ?: run {
                    Spacer(Modifier.height(23.dp.profileScaledV()))
                }

                uiState.generalError?.let {
                    Text(
                        text = it,
                        color = ProfileFieldErrorColor,
                        fontSize = 13.sp.actScaledText(),
                        modifier = Modifier.padding(bottom = 12.dp.actScaled()),
                    )
                }

                // ── Save ─────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Button(
                    onClick = {
                        if (uiState.pendingPermanentFields.isNotEmpty()) {
                            onPermanentChangesRequested()
                        } else {
                            onSave()
                        }
                    },
                    enabled = !uiState.isSaveBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp.actScaled()),
                    shape = RoundedCornerShape(33.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF0AB25),
                        disabledContainerColor = Color(0xFFF0AB25).copy(alpha = 0.7f),
                    ),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(22.dp.actScaled()),
                        )
                    } else {
                        Text(
                            text = "Save",
                            color = Color.Black,
                            fontSize = 18.sp.actScaledText(),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp.actScaled()))
            }

}

@Composable
private fun PersonalInfoLoadingContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 10.dp.actScaled()),
    ) {
        PersonalInfoTopBar(onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun PersonalInfoLoadErrorContent(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 10.dp.actScaled()),
    ) {
        PersonalInfoTopBar(onBack = onBack)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = message,
                color = ProfileFieldErrorColor,
                fontSize = 14.sp.actScaledText(),
                modifier = Modifier.padding(horizontal = 24.dp.actScaled()),
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 20.dp.actScaled()),
                shape = RoundedCornerShape(33.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0AB25)),
            ) {
                Text(text = "Retry", color = Color.Black, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PersonalInfoTopBar(onBack: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp.actScaled()))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp.actScaled()),
            )
        }
        Spacer(modifier = Modifier.width(4.dp.actScaled()))
        Text(
            text = "Personal info",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 25.sp.actScaledText(),
        )
    }
}

private fun usernameStatusText(uiState: PersonalInfoUiState): Pair<String, Boolean>? {
    uiState.fieldErrors[PersonalInfoField.USERNAME]?.let { return it to true }
    return when (val check = uiState.usernameCheck) {
        is UsernameCheckState.Invalid -> check.reason to true
        UsernameCheckState.Checking -> "Checking availability…" to false
        UsernameCheckState.Available -> "Username is available" to false
        UsernameCheckState.Taken -> "Username is already taken" to true
        UsernameCheckState.NetworkError -> "Couldn't verify availability — will check again on save" to false
        UsernameCheckState.Idle, UsernameCheckState.Unchanged -> null
    }
}

private fun fieldLowerName(field: PersonalInfoField): String = when (field) {
    PersonalInfoField.FULL_NAME -> "full name"
    PersonalInfoField.COUNTRY -> "country"
    PersonalInfoField.BIRTH_DATE -> "date of birth"
    PersonalInfoField.USERNAME -> "username"
    PersonalInfoField.PHONE_NUMBER -> "phone number"
}

private fun fieldTitleName(field: PersonalInfoField): String = when (field) {
    PersonalInfoField.FULL_NAME -> "Full name"
    PersonalInfoField.COUNTRY -> "Country"
    PersonalInfoField.BIRTH_DATE -> "Date of birth"
    PersonalInfoField.USERNAME -> "Username"
    PersonalInfoField.PHONE_NUMBER -> "Phone number"
}

private fun isCooldownField(field: PersonalInfoField): Boolean =
    field == PersonalInfoField.USERNAME || field == PersonalInfoField.PHONE_NUMBER

@Composable
private fun FieldTrailingIcon(locked: Boolean, onInfoClick: (() -> Unit)?) {
    if (locked) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked",
            tint = ProfileFieldHintColor,
            modifier = Modifier.size(18.dp.actScaled()),
        )
    } else if (onInfoClick != null) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Change rules",
            tint = ProfileFieldHintColor,
            modifier = Modifier
                .size(18.dp.actScaled())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onInfoClick,
                ),
        )
    }
}

@Composable
private fun LockedFieldDialog(
    field: PersonalInfoField,
    unlockAt: Instant?,
    onDismiss: () -> Unit,
) {
    val message = if (isCooldownField(field) && unlockAt != null) {
        val date = unlockAt.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH))
        "You can change your ${fieldLowerName(field)} again on $date."
    } else {
        "You've already changed your ${fieldLowerName(field)}. This can only be done once."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Field locked") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun FieldRuleInfoDialog(
    field: PersonalInfoField,
    onDismiss: () -> Unit,
) {
    val message = if (isCooldownField(field)) {
        "Your ${fieldLowerName(field)} can be changed once every 30 days."
    } else {
        "Your ${fieldLowerName(field)} can only be changed once."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change rules") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun PermanentChangeConfirmDialog(
    fields: Set<PersonalInfoField>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val names = fields.sortedBy { it.ordinal }.joinToString(", ") { fieldTitleName(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permanent changes") },
        text = { Text("These changes are permanent and can't be undone: $names.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImagePositioningOverlay(
    uiState: PersonalInfoUiState,
    onTransformChanged: (ImageTransformState) -> Unit,
    onDoneImage: () -> Unit,
) {
    val pendingImage = uiState.pendingImage ?: return
    var showHint by remember { mutableStateOf(true) }
    LaunchedEffect(pendingImage) {
        showHint = true
        delay(1500)
        showHint = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp.actScaled()),
        ) {
            Text(
                text = "Position your photo",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp.actScaledText(),
                modifier = Modifier.padding(bottom = 24.dp.actScaled()),
            )

            Box(
                modifier = Modifier.size(240.dp.actScaled()),
                contentAlignment = Alignment.Center,
            ) {
                EditableImageContainer(
                    model = pendingImage.uri,
                    contentDescription = "Position profile picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    shape = CircleShape,
                    onTransformChanged = onTransformChanged,
                )
                PinchHintOverlay(visible = showHint)
            }

            Button(
                onClick = onDoneImage,
                enabled = !uiState.isUploadingImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp.actScaled())
                    .height(54.dp.actScaled()),
                shape = RoundedCornerShape(33.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF0AB25),
                    disabledContainerColor = Color(0xFFF0AB25).copy(alpha = 0.7f),
                ),
            ) {
                if (uiState.isUploadingImage) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(22.dp.actScaled()),
                    )
                } else {
                    Text(
                        text = "Done",
                        color = Color.Black,
                        fontSize = 18.sp.actScaledText(),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    uiState: PersonalInfoUiState,
    onImagePicked: (android.net.Uri) -> Unit,
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? -> uri?.let(onImagePicked) }

    Box(
        modifier = Modifier.size(100.dp.actScaled()),
        contentAlignment = Alignment.Center,
    ) {
        val avatarModifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9))

        val avatarUrl = uiState.user?.profilePicturePath
        if (avatarUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.profile_picture),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
            )
        } else {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
                placeholder = painterResource(R.drawable.profile_picture),
                fallback = painterResource(R.drawable.profile_picture),
                error = painterResource(R.drawable.profile_picture),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp.actScaled())
                .clip(CircleShape)
                .background(Color.Black)
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.pencil_svgrepo_com),
                contentDescription = "Edit profile picture",
                modifier = Modifier.size(14.dp.actScaled()),
            )
        }
    }
}

@Composable
private fun FieldWarning(text: String, isError: Boolean) {
    Text(
        text = text,
        color = if (isError) ProfileFieldErrorColor else ProfileFieldHintColor,
        fontSize = 12.sp.actScaledText(),
        modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
    )
}
