package com.revio.app.features.upload

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.revio.app.R
import com.revio.app.core.ui.components.CustomSnackbar
import com.revio.app.features.profile.components.DropdownOverlay
import com.revio.app.features.profile.components.EditableImageContainer
import kotlinx.coroutines.delay

private val ScreenTop = Color.Black
private val ScreenBottom = Color(0xFF05071B)
private val CardPlaceholder = Color(0xFF11162E)
private val CardShape = RoundedCornerShape(22.8.dp)
private val FieldShape = RoundedCornerShape(12.dp)
private val FieldValueColor = Color(0xFF1B1B1B)
private val FieldPlaceholderColor = Color(0xFF434343)
private val DescriptionPlaceholder = Color(0xFF4E4E4E)
// Figma Post button gradient (purple → red).
private val PostGradient = Brush.linearGradient(listOf(Color(0xFFA470BE), Color(0xFFD96570)))

@Composable
fun ImageUploadScreen(
    navController: NavController,
    viewModel: ImageUploadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // On a successful post, signal the launching screen and return.
    LaunchedEffect(uiState.postSuccess) {
        if (uiState.postSuccess) {
            navController.previousBackStackEntry?.savedStateHandle?.set("post_created", true)
            navController.popBackStack()
        }
    }

    // Optional location: request foreground permission once on entry, then resolve best-effort.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) {
            viewModel.onLocationPermissionResult(true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(0f to ScreenTop, 0.4f to ScreenBottom, 1f to ScreenBottom),
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Top bar ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { navController.popBackStack() },
                        ),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Upload photo",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                LocationStatusChip(
                    status = uiState.locationStatus,
                    town = uiState.town,
                    country = uiState.country,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Image preview card (crop preview: pinch-zoom + pan, clipped) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(375f / 468f)
                    .shadow(elevation = 12.dp, shape = CardShape, clip = false)
                    .clip(CardShape)
                    .background(CardPlaceholder),
            ) {
                uiState.imageUri?.let { uri ->
                    EditableImageContainer(
                        model = uri,
                        contentDescription = "Selected photo",
                        shape = CardShape,
                        modifier = Modifier.fillMaxSize(),
                        onTransformChanged = viewModel::onTransformChanged,
                    )

                    // One-shot pinch hint: shown once per new image, auto-dismissed after 1.5s.
                    var showHint by remember(uri) { mutableStateOf(true) }
                    LaunchedEffect(uri) {
                        delay(1500)
                        showHint = false
                    }
                    PinchHintOverlay(visible = showHint)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Brand + Model dropdowns ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UploadDropdownField(
                    modifier = Modifier.weight(1f),
                    placeholder = "Brand",
                    value = uiState.selectedBrand,
                    enabled = true,
                    loading = uiState.isLoadingBrands,
                    onClick = viewModel::onBrandFieldClick,
                )
                UploadDropdownField(
                    modifier = Modifier.weight(1f),
                    placeholder = "Model",
                    value = uiState.selectedModel?.model,
                    enabled = uiState.isModelDropdownEnabled,
                    loading = uiState.isLoadingModels,
                    onClick = viewModel::onModelFieldClick,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Description ----
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                placeholder = {
                    Text("Tell them more about your find…", color = DescriptionPlaceholder, fontSize = 14.sp)
                },
                shape = FieldShape,
                textStyle = TextStyle(fontSize = 14.sp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = FieldPlaceholderColor,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Post button ----
            PostButton(
                enabled = uiState.canPost,
                loading = uiState.isPosting,
                onClick = viewModel::post,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---- Dropdown overlays (reused design-system component) ----
        DropdownOverlay(
            visible = uiState.brandDropdownOpen,
            items = uiState.brands,
            onItemSelected = viewModel::onBrandSelected,
            onDismiss = viewModel::dismissBrandDropdown,
        )
        DropdownOverlay(
            visible = uiState.modelDropdownOpen,
            items = uiState.modelNames,
            onItemSelected = viewModel::onModelSelected,
            onDismiss = viewModel::dismissModelDropdown,
        )

        // ---- One-shot error feedback ----
        uiState.userMessage?.let { message ->
            LaunchedEffect(message) {
                delay(3000)
                viewModel.consumeUserMessage()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                CustomSnackbar(message = message)
            }
        }
    }
}

/**
 * Non-interactive overlay that pulses two finger-circles toward each other to hint pinch-to-zoom.
 * Rendered above the preview image but captures no gestures, so the real pinch works underneath.
 * Fades in immediately and fades out when [visible] turns false.
 */
@Composable
private fun PinchHintOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit  = fadeOut(animationSpec = tween(400)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Semi-transparent scrim so the circles read against any image.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )

            val infiniteTransition = rememberInfiniteTransition(label = "pinch_hint")
            // Oscillates 0→1→0: at 0 the fingers are apart, at 1 they are close together.
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pinch_progress",
            )

            // Finger circles spread from ±40dp at progress=0 to ±10dp at progress=1.
            val spreadDp = androidx.compose.ui.unit.lerp(40.dp, 10.dp, progress)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left finger circle.
                    Box(
                        modifier = Modifier
                            .offset(x = -spreadDp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f)),
                    )
                    // Right finger circle.
                    Box(
                        modifier = Modifier
                            .offset(x = spreadDp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f)),
                    )
                }
                Text(
                    text = "Pinch to zoom",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Subtle, non-blocking location indicator shown in the top bar — never obstructs posting.
 * Hidden while [LocationStatus.Idle].
 */
@Composable
private fun LocationStatusChip(
    status: LocationStatus,
    town: String?,
    country: String?,
) {
    if (status == LocationStatus.Idle) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            LocationStatus.Resolving -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                LocationStatusText("Getting location…")
            }

            LocationStatus.Added -> {
                Image(
                    painter = painterResource(R.drawable.ic_gps),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                val place = listOfNotNull(town, country).joinToString(", ")
                LocationStatusText(place.ifBlank { "Location added" })
            }

            LocationStatus.Unavailable -> LocationStatusText("Posting without location")

            LocationStatus.Idle -> Unit
        }
    }
}

@Composable
private fun LocationStatusText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** White pill dropdown field with the [R.drawable.arrow_square_down] icon, matching Figma. */
@Composable
private fun UploadDropdownField(
    placeholder: String,
    value: String?,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(FieldShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.45f))
            .clickable(
                enabled = enabled && !loading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value ?: placeholder,
            color = if (value != null) FieldValueColor else FieldPlaceholderColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = FieldPlaceholderColor,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.arrow_square_down),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun PostButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(212.dp)
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(PostGradient)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text(text = "Post", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
