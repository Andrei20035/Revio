package com.revio.app.features.profile.components

import android.app.DatePickerDialog
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.revio.app.core.ui.scaling.profileScaled
import com.revio.app.core.ui.scaling.profileScaledText
import com.revio.app.core.ui.scaling.profileScaledV
import kotlinx.coroutines.delay
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import coil3.compose.rememberAsyncImagePainter
import com.revio.app.data.model.CountryItem
import com.revio.app.features.profile.customization.ImageSource
import com.revio.app.features.profile.customization.ProfileCustomizationAction
import com.revio.app.features.profile.customization.ProfileStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.revio.app.R


/**
 * Produces a deterministic TextStyle for ProfileCustomization input fields.
 * - lineHeight is 1.3× fontSize so descenderii nu ies din line box pe niciun font de sistem
 * - includeFontPadding = false elimină padding-ul legacy care diferă între device-uri
 * - LineHeightStyle.Alignment.Center centrează glifa identic pe toate device-urile
 *
 * Folosit atât pentru textStyle cât și pentru placeholder astfel încât metricile
 * verticale să fie identice și clipping-ul să fie eliminat (vezi planul de fix).
 */
@Composable
private fun profileFieldTextStyle(
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color = Color(0xFF434343),
): TextStyle = TextStyle(
    color = color,
    fontSize = fontSize,
    lineHeight = fontSize * 1.3f,
    fontWeight = FontWeight.Medium,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

@Composable
fun PictureContainer(
    modifier: Modifier = Modifier,
    currentStep: ProfileStep,
    picture: ImageSource?,
    text: String,
    onImageSelected: (Uri) -> Unit,
    onBackPress: ((ProfileCustomizationAction) -> Unit)? = null,
    onTransformChanged: ((ImageTransformState) -> Unit)? = null,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(onImageSelected)
    }
    val imageShape = if (currentStep == ProfileStep.Car) {
        RoundedCornerShape(12.dp)
    } else {
        CircleShape
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (currentStep == ProfileStep.Car) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = Color.White,
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(36.dp.profileScaled())
                            .clickable {
                                onBackPress?.invoke(ProfileCustomizationAction.PreviousStep)
                            }
                    )
                }
                Text(
                    text = text,
                    color = Color(0xFFDBB8B8),
                    fontSize = 22.sp.profileScaledText(),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                    )
            }
        }

        val containerModifier = when (currentStep) {
                ProfileStep.Personal -> Modifier
                    .size(167.dp.profileScaled())
                    .clip(imageShape)

                ProfileStep.Car -> Modifier
                    .fillMaxWidth()
                    .aspectRatio(17f / 10f)
                    .clip(imageShape)
            }
                .background(Color(0xFFD9D9D9))

        var showHint by remember(picture) { mutableStateOf(picture != null) }
        LaunchedEffect(picture) {
            if (picture != null) {
                delay(1500)
                showHint = false
            }
        }

        Box(
            modifier = if (picture == null) {
                containerModifier.clickable { launcher.launch("image/*") }
            } else {
                containerModifier
            },
            contentAlignment = Alignment.Center
        ) {
            when (picture) {
                is ImageSource.Local -> {
                    EditableImageContainer(
                        model = picture.uri,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxSize(),
                        shape = imageShape,
                        onClick = { launcher.launch("image/*") },
                        onTransformChanged = onTransformChanged,
                    )
                    PinchHintOverlay(visible = showHint)
                }

                is ImageSource.Remote -> {
                    EditableImageContainer(
                        model = picture.url,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxSize(),
                        shape = imageShape,
                        onClick = { launcher.launch("image/*") },
                        onTransformChanged = onTransformChanged,
                    )
                    PinchHintOverlay(visible = showHint)
                }

                null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add image",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp.profileScaled())
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add an image",
                            color = Color.Gray,
                            fontSize = 12.sp.profileScaledText()
                        )
                    }
                }
            }
        }
    }
}

/**
 * One-shot pinch hint overlay: two animated finger circles moving toward each other,
 * with a "Pinch to zoom" label. Shown for 1.5 s after the user picks an image.
 * Captures no gestures — the real pinch/pan from EditableImageContainer works underneath.
 */
@Composable
internal fun PinchHintOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit  = fadeOut(animationSpec = tween(400)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )

            val infiniteTransition = rememberInfiniteTransition(label = "pinch_hint")
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pinch_progress",
            )

            val spreadDp = androidx.compose.ui.unit.lerp(40.dp, 10.dp, progress)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = -spreadDp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f)),
                    )
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

data class ImageTransformState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    /** Preview container size in raw pixels (filled once the layout is measured). */
    val containerSize: IntSize = IntSize.Zero,
    /** Oriented image size in pixels as decoded by Coil (filled once the image loads). */
    val imageSize: Size = Size.Unspecified,
)

@Composable
fun EditableImageContainer(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape,
    contentDescription: String?,
    minScale: Float = 1f,
    maxScale: Float = 3f,
    onClick: (() -> Unit)? = null,
    onTransformChanged: ((ImageTransformState) -> Unit)? = null,
) {
    val density = LocalDensity.current

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(model) { mutableStateOf(Size.Unspecified) }

    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }

    val painter = rememberAsyncImagePainter(
        model = model,
        onSuccess = { state ->
            imageSize = Size(
                width = state.result.image.width.toFloat(),
                height = state.result.image.height.toFloat()
            )
        }
    )

    val drawnImageSize = remember(containerSize, imageSize) {
        val containerWidth = containerSize.width.toFloat()
        val containerHeight = containerSize.height.toFloat()

        if (
            containerWidth > 0f &&
            containerHeight > 0f &&
            imageSize.isSpecified &&
            imageSize.width > 0f &&
            imageSize.height > 0f
        ) {
            val scaleToCover = maxOf(
                containerWidth / imageSize.width,
                containerHeight / imageSize.height
            )
            val drawnWidth = imageSize.width * scaleToCover
            val drawnHeight = imageSize.height * scaleToCover

            Size(drawnWidth, drawnHeight)
        } else {
            Size(
                width = containerSize.width.coerceAtLeast(1).toFloat(),
                height = containerSize.height.coerceAtLeast(1).toFloat()
            )
        }
    }

    fun clampOffset(rawOffset: Offset, currentScale: Float): Offset {
        val scaledWidth = drawnImageSize.width * currentScale
        val scaledHeight = drawnImageSize.height * currentScale

        val maxOffsetX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)

        return Offset(
            x = rawOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = rawOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    fun updateTransform(nextScale: Float, rawOffset: Offset) {
        val clampedScale = nextScale.coerceIn(minScale, maxScale)
        val clampedOffset = clampOffset(rawOffset, clampedScale)

        scale = clampedScale
        offset = clampedOffset
        onTransformChanged?.invoke(
            ImageTransformState(
                scale = clampedScale,
                offset = clampedOffset,
                containerSize = containerSize,
                imageSize = imageSize,
            )
        )
    }

    LaunchedEffect(drawnImageSize, scale) {
        updateTransform(scale, offset)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .onSizeChanged { containerSize = it }
            .pointerInput(model, drawnImageSize, containerSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    updateTransform(
                        nextScale = scale * zoom,
                        rawOffset = offset + pan
                    )
                }
            }
            .pointerInput(model) {
                detectTapGestures(
                    onTap = { onClick?.invoke() },
                    onDoubleTap = {
                        updateTransform(minScale, Offset.Zero)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .requiredSize(
                    width = with(density) { drawnImageSize.width.toDp() },
                    height = with(density) { drawnImageSize.height.toDp() }
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin.Center
                }
        )
    }
}

@Composable
fun DropdownFieldWithoutOverlay(
    selectedItem: String,
    label: String,
    onDropdownToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clickInteractionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFFDFA3A3),
            fontSize = 13.73.sp.profileScaledText(),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, start = 8.dp)
        )

        Box(modifier = Modifier.height(51.dp.profileScaled())) {
            OutlinedTextField(
                value = selectedItem,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFD9D9D9),
                    unfocusedContainerColor = Color(0xFFD9D9D9)
                ),
                trailingIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.arrow_square_down),
                        contentDescription = "Drop down",
                        modifier = Modifier.size(33.dp.profileScaled())
                    )
                },
                singleLine = true,
                textStyle = profileFieldTextStyle(fontSize = 13.73.sp.profileScaledText())
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = clickInteractionSource,
                        indication = null,
                        onClick = onDropdownToggle
                    )
            )
        }
    }
}

@Composable
fun DropdownOverlay(
    visible: Boolean,
    items: List<String>,
    onItemSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val itemHeight = 56.dp
    val maxVisibleItems = 10
    val listHeight = if (items.size > maxVisibleItems) {
        itemHeight * maxVisibleItems
    } else {
        itemHeight * items.size
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
                scaleIn(
                    initialScale = 0.8f,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(200)) +
                scaleOut(
                    targetScale = 0.8f,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec = tween(200, easing = FastOutLinearInEasing)
                ),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight)
                    .align(Alignment.Center),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .heightIn(max = itemHeight * maxVisibleItems)
                        .padding(horizontal = 8.dp)
                ) {
                    items(items) { item ->
                        DropdownItem(item, onItemSelected)
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownItem(item: String, onItemSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemSelected(item) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item,
            fontSize = 16.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
    Divider(color = Color.Gray.copy(alpha = 0.1f), thickness = 0.5.dp)
}


@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        color = Color(0xFFDFA3A3),
        fontSize = 14.5.sp.profileScaledText(),
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 8.dp)
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholderText,
                style = profileFieldTextStyle(
                    fontSize = 15.sp.profileScaledText(),
                    color = Color(0xFF434343).copy(alpha = 0.5f),
                ),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 23.dp.profileScaledV())
            .height(54.dp.profileScaled()),
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color(0xFFD9D9D9),
            unfocusedContainerColor = Color(0xFFD9D9D9)
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        textStyle = profileFieldTextStyle(fontSize = 15.sp.profileScaledText())
    )
}


@Composable
fun BirthDateField(
    birthDate: LocalDate?,
    onBirthDateChanged: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }


    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onBirthDateChanged(LocalDate.of(year, month + 1, dayOfMonth))
            },
            birthDate?.year ?: 2000,
            birthDate?.monthValue?.minus(1) ?: 0,
            birthDate?.dayOfMonth ?: 1
        )
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    datePickerDialog.show()
                }
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Birthdate",
            color = Color(0xFFDFA3A3),
            fontSize = 14.5.sp.profileScaledText(),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, start = 8.dp)
        )

        OutlinedTextField(
            value = birthDate?.format(
                DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy",
                    Locale.ENGLISH
                )
            ) ?: "",
            placeholder = {
                Text(
                    text = "01/12/2002",
                    style = profileFieldTextStyle(
                        fontSize = 15.5.sp.profileScaledText(),
                        color = Color(0xFF434343).copy(alpha = 0.5f),
                    ),
                )
            },
            onValueChange = {},
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 23.dp.profileScaledV())
                .height(54.dp.profileScaled()),
            readOnly = true,
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFFD9D9D9),
                unfocusedContainerColor = Color(0xFFD9D9D9),
                disabledContainerColor = Color(0xFFD9D9D9)
            ),
            singleLine = true,
            textStyle = profileFieldTextStyle(fontSize = 15.5.sp.profileScaledText()),
        )
    }
}

@Composable
fun CountryDropdown(
    selectedCountry: String,
    onCountrySelected: (CountryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var countries by remember { mutableStateOf<List<CountryItem>?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }


    LaunchedEffect(Unit) {
        if (countries == null && !isLoading) {
            isLoading = true
            val countryList = withContext(Dispatchers.IO) {
                Locale.getISOCountries().map { code ->
                    val flag = getFlagEmoji(code)
                    val name = Locale("", code).getDisplayCountry(Locale.getDefault())
                    CountryItem(name = name, code = code, flag = flag)
                }.sortedBy { it.name }
            }
            countries = countryList
            isLoading = false
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    Log.d("CountryDropdown", "TextField clicked, expanded: $expanded")
                    expanded = !expanded
                }
            }
        }
    }


    Column(modifier = modifier) {
        Text(
            text = "Country",
            color = Color(0xFFDFA3A3),
            fontSize = 14.5.sp.profileScaledText(),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, start = 8.dp)
        )

        Box {
            val countryCode = getCountryCodeByName(selectedCountry)
            val flag = countryCode?.let { getFlagEmoji(it) } ?: ""

            OutlinedTextField(
                value = if (selectedCountry.isBlank()) "" else "$flag  $selectedCountry",
                onValueChange = {},
                readOnly = true,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp.profileScaled()),
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFD9D9D9),
                    unfocusedContainerColor = Color(0xFFD9D9D9)
                ),
                trailingIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.arrow_square_down),
                        contentDescription = "Drop down",
                        modifier = Modifier.size(33.dp.profileScaled())
                    )
                },
                singleLine = true,
                textStyle = profileFieldTextStyle(fontSize = 15.5.sp.profileScaledText()),
            )

        }
    }
    if (expanded) {
        Popup(
            onDismissRequest = { expanded = false },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        expanded = false
                    }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                        .align(Alignment.Center)
                        .height(400.dp)
                        .zIndex(1000f)
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    countries?.let { countryList ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                        ) {
                            items(countryList) { country ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCountrySelected(country)
                                            expanded = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = country.flag,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = country.name,
                                        fontSize = 16.sp,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                HorizontalDivider(
                                    color = Color.Gray.copy(alpha = 0.1f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    } ?: run {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun NextStepButton(
    text: String,
    onClick: (ProfileCustomizationAction) -> Unit,
    buttonHeight: Dp = 54.dp,
) {
    Button(
        onClick = {
            onClick(ProfileCustomizationAction.NextStep)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight.profileScaled()),
        shape = RoundedCornerShape(33.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF0AB25),
            disabledContainerColor = Color(0xFFF0AB25).copy(alpha = 0.7f),
            disabledContentColor = Color.Black.copy(alpha = 0.7f)
        ),
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 19.sp.profileScaledText(),
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun SkipCarInfoText(
    onClick: (ProfileCustomizationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Skip this step",
        color = Color(0xFF878787),
        fontSize = 17.sp.profileScaledText(),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                onClick(ProfileCustomizationAction.Complete)
            }),
        textAlign = TextAlign.Center
    )
}

private fun getFlagEmoji(countryCode: String): String {
    return countryCode.uppercase().map {
        val base = 0x1F1E6
        val offset = it.code - 'A'.code
        Character.toChars(base + offset)
    }.joinToString("") { it.concatToString() }
}

private fun getCountryCodeByName(name: String): String? {
    return Locale.getISOCountries().firstOrNull { code ->
        Locale("", code).getDisplayCountry(Locale.getDefault()) == name
    }
}
