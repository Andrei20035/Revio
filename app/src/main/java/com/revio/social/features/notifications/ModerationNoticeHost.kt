package com.revio.social.features.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.revio.social.core.navigation.Screen
import com.revio.social.core.ui.overlay.DimOnlyDialogWindow
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.overlay.OverlaySurface
import com.revio.social.core.ui.theme.Poppins

/**
 * Blocking inbox for moderation notices (bans, unbans, reward revocations…): shown as a modal
 * dialog the user must acknowledge before continuing, one at a time, oldest first. Checked
 * whenever the app reaches Feed, covering both a cold start with an existing session and the
 * moment right after a successful login.
 */
@Composable
fun ModerationNoticeHost(
    navController: NavController,
    viewModel: ModerationNoticeViewModel = hiltViewModel(),
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Feed.route) viewModel.checkForNotices()
    }

    val notice by viewModel.currentNotice.collectAsState()
    notice?.let { current ->
        ModerationNoticeDialog(
            title = current.title,
            body = current.body,
            onAcknowledge = { viewModel.acknowledgeCurrent() },
        )
    }
}

@Composable
private fun ModerationNoticeDialog(
    title: String,
    body: String,
    onAcknowledge: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        DimOnlyDialogWindow()

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(OverlaySurface)
                    .border(1.dp, OverlayBorder, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0AB25)),
                ) {
                    Text(text = "OK", color = Color.Black, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
