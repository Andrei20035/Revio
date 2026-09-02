package com.revio.social.features.settings

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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.revio.social.BuildConfig
import com.revio.social.R
import com.revio.social.core.navigation.Screen
import com.revio.social.core.ui.components.AppScreenBackground
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins

private val CardBg = Color(0x3DD9D9D9)          // rgba(217,217,217,0.24)
private val SectionLabelColor = Color(0xFF8D8D8D)
private val ItemTextColor = Color.White
private val LogoutRed = Color.Red

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.logoutCompleted) {
        if (uiState.logoutCompleted) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    AppScreenBackground {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 10.dp.actScaled()),
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
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
                    text = "Settings",
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 25.sp.actScaledText(),
                )
            }

            // ── Profile card ─────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp.actScaled()))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(122.dp.actScaled())
                    .clip(RoundedCornerShape(21.dp))
                    .background(CardBg)
                    .padding(horizontal = 16.dp.actScaled()),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    val avatarUrl = uiState.user?.profilePicturePath
                    if (avatarUrl.isNullOrBlank()) {
                        Image(
                            painter = painterResource(R.drawable.profile_picture),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp.actScaled())
                                .clip(CircleShape),
                        )
                    } else {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp.actScaled())
                                .clip(CircleShape),
                            placeholder = painterResource(R.drawable.profile_picture),
                            fallback = painterResource(R.drawable.profile_picture),
                            error = painterResource(R.drawable.profile_picture),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp.actScaled()))

                    Column {
                        Text(
                            text = uiState.user?.fullName ?: "",
                            color = Color.White,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp.actScaledText(),
                        )
                        Spacer(modifier = Modifier.height(10.dp.actScaled()))
                        Text(
                            text = uiState.user?.username?.let { "@$it" } ?: "",
                            color = Color(0xFFA9A9A9),
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp.actScaledText(),
                        )
                    }
                }
            }

            // ── Account section ──────────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp.actScaled()))
            SectionLabel("Account")
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            SettingsRow(
                iconRes = R.drawable.user_icon,
                label = "Personal info",
                topRound = true,
                bottomRound = false,
                onClick = { navController.navigate(Screen.PersonalInfo.route) },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            SettingsRow(
                iconRes = R.drawable.user_icon,
                label = "Delete account",
                topRound = false,
                bottomRound = true,
                onClick = { navController.navigate(Screen.DeleteAccount.route) },
                labelColor = LogoutRed,
            )

            // ── Security section ─────────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp.actScaled()))
            SectionLabel("Security")
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            SettingsRow(
                iconRes = R.drawable.change_password,
                label = "Change password",
                topRound = true,
                bottomRound = true,
                onClick = { navController.navigate(Screen.ChangePassword.route) },
            )

            // ── Notifications section ────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp.actScaled()))
            SectionLabel("Notifications")
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            SettingsRow(
                iconRes = R.drawable.notification_bell,
                label = "Notifications",
                topRound = true,
                bottomRound = true,
                onClick = { navController.navigate(Screen.NotificationSettings.route) },
            )

            // ── Help us improve section ─────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp.actScaled()))
            SectionLabel("Help us improve")
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            SettingsRow(
                iconRes = R.drawable.feedback_icon,
                label = "Feedback & ideas",
                topRound = true,
                bottomRound = true,
                onClick = { navController.navigate(Screen.Feedback.createRoute()) },
            )

            // ── Privacy section ──────────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp.actScaled()))
            SectionLabel("Privacy")
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            AnalyticsConsentRow(
                granted = uiState.analyticsConsentGranted,
                enabled = uiState.analyticsConsentLoaded,
                onGrantedChange = viewModel::setAnalyticsConsentGranted,
            )

            // ── Admin section — hidden unless the current user is an admin ────
            if (uiState.user?.isAdmin == true) {
                Spacer(modifier = Modifier.height(24.dp.actScaled()))
                SectionLabel("Admin")
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                SettingsRow(
                    iconRes = R.drawable.user_icon,
                    label = "Admin panel",
                    topRound = true,
                    bottomRound = true,
                    onClick = { navController.navigate(Screen.AdminHome.route) },
                )
            }

            // ── Developer section — pas 1.10, doar debug ────────────────────────
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(24.dp.actScaled()))
                SectionLabel("Developer")
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg)
                        .clickable { navController.navigate(Screen.DevTools.route) }
                        .padding(horizontal = 16.dp.actScaled(), vertical = 14.dp.actScaled()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Dev Tools",
                        color = ItemTextColor,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp.actScaledText(),
                    )
                }
            }

            // ── Others section ───────────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp.actScaled()))
            SectionLabel("Others")
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            SettingsRow(
                iconRes = R.drawable.privacy_policy,
                label = "Privacy Policy",
                topRound = true,
                bottomRound = false,
                onClick = { navController.navigate(Screen.PrivacyPolicy.route) },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            SettingsRow(
                iconRes = R.drawable.terms_conditions,
                label = "Terms & Conditions",
                topRound = false,
                bottomRound = true,
                onClick = { navController.navigate(Screen.TermsConditions.route) },
            )

            // ── Log out ──────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(28.dp.actScaled()))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = viewModel::logout,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.logout),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp.actScaled()),
                )
                Spacer(modifier = Modifier.width(8.dp.actScaled()))
                Text(
                    text = if (uiState.isLoggingOut) "Logging out…" else "Log out",
                    color = LogoutRed,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp.actScaledText(),
                )
            }

            Spacer(modifier = Modifier.height(32.dp.actScaled()))
        }
        }
    }
}


@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.padding(start = 10.dp),
        color = SectionLabelColor,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp.actScaledText(),
    )
}

@Composable
private fun SettingsRow(
    iconRes: Int,
    label: String,
    topRound: Boolean,
    bottomRound: Boolean,
    onClick: () -> Unit,
    labelColor: Color = ItemTextColor,
) {
    val topRadius = if (topRound) 20.dp else 0.dp
    val bottomRadius = if (bottomRound) 20.dp else 0.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp.actScaled())
            .clip(shape)
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(30.dp.actScaled()),
        )
        Spacer(modifier = Modifier.width(16.dp.actScaled()))
        Text(
            text = label,
            color = labelColor,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp.actScaledText(),
        )
    }
}

/**
 * Opt-out consent toggle — always visible in Settings so the choice can be changed anytime, per
 * docs/consent-decision.md. Copy adapted from REVIO_LAUNCH_COPY.md's "Help improve Revio" draft.
 * [enabled] is false until the persisted choice has loaded, so the switch can't be toggled to a
 * position that would then be overwritten by the real value once it arrives.
 */
@Composable
private fun AnalyticsConsentRow(
    granted: Boolean,
    enabled: Boolean,
    onGrantedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp.actScaled(), vertical = 14.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Usage & crash analytics",
                color = ItemTextColor,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = "Share optional diagnostics to help improve Revio. Never used for advertising.",
                color = SectionLabelColor,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp.actScaledText(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp.actScaled()))
        Switch(checked = granted, enabled = enabled, onCheckedChange = onGrantedChange)
    }
}
