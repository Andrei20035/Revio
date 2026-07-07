package com.revio.app.features.feed.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.revio.app.core.navigation.Screen
import java.io.File

@Composable
fun rememberPostCreationLauncher(navController: NavController): () -> Unit {
    val context = LocalContext.current
    var showPostDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            navController.navigate(Screen.ImageUpload.createRoute(uri.toString(), source = "GALLERY"))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            navController.navigate(Screen.ImageUpload.createRoute(uri.toString(), source = "CAMERA"))
        }
    }

    if (showPostDialog) {
        PostYourFindOverlay(
            onCamera = {
                showPostDialog = false
                val uri = createCameraImageUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            onGallery = {
                showPostDialog = false
                galleryLauncher.launch("image/*")
            },
            onDismiss = { showPostDialog = false },
        )
    }

    return { showPostDialog = true }
}

fun createCameraImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
