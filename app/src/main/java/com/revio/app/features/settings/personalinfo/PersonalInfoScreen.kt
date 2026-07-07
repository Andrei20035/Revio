package com.revio.app.features.settings.personalinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateListOf
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
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.scaling.rememberActivityScale
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.features.profile.components.BirthDateField
import com.revio.app.features.profile.components.CountryDropdown
import com.revio.app.features.profile.components.EditableImageContainer
import com.revio.app.features.profile.components.ImageTransformState
import com.revio.app.features.profile.components.LabeledTextField
import com.revio.app.features.profile.components.PinchHintOverlay
import kotlinx.coroutines.delay

@Composable
fun PersonalInfoScreen(
    navController: NavController,
    viewModel: PersonalInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val confirmedOneTimeFields = remember { mutableStateListOf<PersonalInfoField>() }
    var confirmDialogField by remember { mutableStateOf<PersonalInfoField?>(null) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            navController.popBackStack()
        }
    }

    AppScreenBackground {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp.actScaled()),
            ) {
                // ── Top bar ──────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                        fontSize = 29.sp.actScaledText(),
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
                        onImagePicked = viewModel::onImagePicked,
                    )
                }

                // ── Fields ───────────────────────────────────────────────
                Spacer(modifier = Modifier.height(28.dp.actScaled()))

                OneTimeFieldGuard(
                    isConfirmed = PersonalInfoField.FULL_NAME in confirmedOneTimeFields,
                    onRequestConfirm = { confirmDialogField = PersonalInfoField.FULL_NAME },
                ) {
                    LabeledTextField(
                        label = "Full name",
                        value = uiState.fullName,
                        onValueChange = viewModel::onFullNameChanged,
                        placeholderText = "Full name",
                    )
                }
                FieldWarning(
                    text = "Can be changed only once",
                    error = uiState.fieldErrors[PersonalInfoField.FULL_NAME],
                )

                LabeledTextField(
                    label = "Username",
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChanged,
                    placeholderText = "Username",
                )
                FieldWarning(
                    text = "Can be changed once a month",
                    error = uiState.fieldErrors[PersonalInfoField.USERNAME],
                )

                OneTimeFieldGuard(
                    isConfirmed = PersonalInfoField.COUNTRY in confirmedOneTimeFields,
                    onRequestConfirm = { confirmDialogField = PersonalInfoField.COUNTRY },
                ) {
                    CountryDropdown(
                        selectedCountry = uiState.country,
                        onCountrySelected = { viewModel.onCountryChanged(it.name) },
                    )
                }
                FieldWarning(
                    text = "Can be changed only once",
                    error = uiState.fieldErrors[PersonalInfoField.COUNTRY],
                )

                Spacer(modifier = Modifier.height(4.dp.actScaled()))
                OneTimeFieldGuard(
                    isConfirmed = PersonalInfoField.BIRTH_DATE in confirmedOneTimeFields,
                    onRequestConfirm = { confirmDialogField = PersonalInfoField.BIRTH_DATE },
                ) {
                    BirthDateField(
                        birthDate = uiState.birthDate,
                        onBirthDateChanged = viewModel::onBirthDateChanged,
                    )
                }
                FieldWarning(
                    text = "Can be changed only once",
                    error = uiState.fieldErrors[PersonalInfoField.BIRTH_DATE],
                )

                LabeledTextField(
                    label = "Phone number",
                    value = uiState.phoneNumber,
                    onValueChange = viewModel::onPhoneNumberChanged,
                    placeholderText = "Phone number",
                )
                FieldWarning(
                    text = "Can be changed once a month",
                    error = uiState.fieldErrors[PersonalInfoField.PHONE_NUMBER],
                )

                uiState.generalError?.let {
                    Text(
                        text = it,
                        color = Color(0xFFF93939),
                        fontSize = 13.sp.actScaledText(),
                        modifier = Modifier.padding(bottom = 12.dp.actScaled()),
                    )
                }

                // ── Save ─────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Button(
                    onClick = viewModel::onSave,
                    enabled = !uiState.isSaving,
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

            if (uiState.isPositioningImage) {
                ImagePositioningOverlay(
                    uiState = uiState,
                    onTransformChanged = viewModel::onTransformChanged,
                    onDoneImage = viewModel::onDoneImage,
                )
            }
        }
    }

    confirmDialogField?.let { field ->
        OneTimeChangeConfirmDialog(
            field = field,
            onConfirm = {
                confirmedOneTimeFields.add(field)
                confirmDialogField = null
            },
            onDismiss = { confirmDialogField = null },
        )
    }
}

@Composable
private fun OneTimeFieldGuard(
    isConfirmed: Boolean,
    onRequestConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
        if (!isConfirmed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onRequestConfirm,
                    ),
            )
        }
    }
}

@Composable
private fun OneTimeChangeConfirmDialog(
    field: PersonalInfoField,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fieldName = when (field) {
        PersonalInfoField.FULL_NAME -> "full name"
        PersonalInfoField.COUNTRY -> "country"
        PersonalInfoField.BIRTH_DATE -> "date of birth"
        PersonalInfoField.USERNAME -> "username"
        PersonalInfoField.PHONE_NUMBER -> "phone number"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permanent change") },
        text = {
            Text("You can only change your $fieldName once. This action cannot be undone. Continue?")
        },
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
private fun FieldWarning(text: String, error: String?) {
    Text(
        text = error ?: text,
        color = if (error != null) Color(0xFFF93939) else Color(0xFF8D8D8D),
        fontSize = 12.5.sp.actScaledText(),
        modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
    )
}
