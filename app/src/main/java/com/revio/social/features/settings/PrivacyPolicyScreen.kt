package com.revio.social.features.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.revio.social.core.ui.components.AppScreenBackground
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins

private val PolicyCardBackground = Color(0x292B3156)
private val PolicyBodyColor = Color(0xFFD2D3DD)
private val PolicyMutedColor = Color(0xFF9FA1B1)

@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    AppScreenBackground {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp.actScaled()),
            ) {
                Spacer(Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = navController::popBackStack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp.actScaled()),
                        )
                    }
                    Spacer(Modifier.width(4.dp.actScaled()))
                    Text(
                        text = "Privacy Policy",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                Spacer(Modifier.height(18.dp.actScaled()))
                Text(
                    text = "Effective date: 21 August 2026",
                    color = PolicyMutedColor,
                    fontFamily = Poppins,
                    fontSize = 12.sp.actScaledText(),
                    lineHeight = 19.sp.actScaledText(),
                )
                Spacer(Modifier.height(14.dp.actScaled()))
                PolicyIntroCard()
                Spacer(Modifier.height(20.dp.actScaled()))

                privacyPolicySections.forEach { section ->
                    PolicySection(section)
                    Spacer(Modifier.height(20.dp.actScaled()))
                }
                Spacer(Modifier.height(24.dp.actScaled()))
            }
        }
    }
}

@Composable
private fun PolicyIntroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp.actScaled()))
            .background(PolicyCardBackground)
            .padding(18.dp.actScaled()),
    ) {
        Text("Privacy Policy for Revio", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 19.sp.actScaledText())
        Spacer(Modifier.height(10.dp.actScaled()))
        PolicyBody("Revio is a car-spotting community where users can create profiles, share car photos and posts, discover public posts, and interact through comments and likes. This Privacy Policy explains how Andrei R., the developer of Revio (\"Revio\", \"we\", \"us\", or \"our\"), collects, uses, shares, stores, and protects personal data when you use the Revio mobile application and related services (collectively, the \"Services\").")
        Spacer(Modifier.height(14.dp.actScaled()))
        Text("Developer and data controller: Andrei R.", color = PolicyBodyColor, fontFamily = Poppins, fontSize = 13.sp.actScaledText(), lineHeight = 19.sp.actScaledText())
        Text("Contact: threvioapp@gmail.com", color = PolicyBodyColor, fontFamily = Poppins, fontSize = 13.sp.actScaledText())
        Spacer(Modifier.height(14.dp.actScaled()))
        PolicyBody("This Policy applies to the Revio Android app, its backend, and the related waitlist and support services operated by Andrei R.")
    }
}

@Composable
private fun PolicySection(section: PolicySectionContent) {
    Column {
        Text(section.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp.actScaledText())
        section.subtitle?.let {
            Spacer(Modifier.height(6.dp.actScaled()))
            Text(it, color = PolicyMutedColor, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp.actScaledText())
        }
        Spacer(Modifier.height(9.dp.actScaled()))
        section.paragraphs.forEachIndexed { index, paragraph ->
            if (paragraph.startsWith("• ")) {
                Row(modifier = Modifier.padding(start = 4.dp.actScaled())) {
                    Text("•", color = Color.White, fontSize = 16.sp.actScaledText())
                    Spacer(Modifier.width(8.dp.actScaled()))
                    PolicyBody(paragraph.removePrefix("• "), Modifier.weight(1f))
                }
            } else {
                PolicyBody(paragraph)
            }
            if (index != section.paragraphs.lastIndex) Spacer(Modifier.height(9.dp.actScaled()))
        }
    }
}

@Composable
private fun PolicyBody(text: String, modifier: Modifier = Modifier, color: Color = PolicyBodyColor) {
    Text(text, modifier = modifier, color = color, fontFamily = Poppins, fontSize = 13.sp.actScaledText(), lineHeight = 21.sp.actScaledText())
}

private data class PolicySectionContent(val title: String, val subtitle: String? = null, val paragraphs: List<String>)

private val privacyPolicySections = listOf(
    PolicySectionContent("1. Eligibility", paragraphs = listOf("Revio is intended for people aged 13 or older. We collect the date of birth entered during profile setup, but it is not displayed publicly. If you believe a child under 13 has provided personal data, contact threvioapp@gmail.com so we can investigate and delete it where appropriate.")),
    PolicySectionContent("2. Data we collect", "Account and profile", listOf(
        "We collect the information needed to create and operate your account: email address, sign-in method, account identifiers, full name, username, date of birth, country, optional phone number, profile photo, and optional details and photo of your own car. For password sign-in, the server stores a one-way password hash, not your plain-text password.",
        "We also process account status and activity statistics such as SpotScore, streaks, challenge progress, leaderboard position, badges, post count, Early Spotter status, and moderation status.",
        "Security and session records can include device identifier and name, IP address, user-agent, sign-in provider, session and hashed refresh-token records, and timestamps.",
        "Waitlist data you previously submitted — such as email, username, platform, country, and signup timestamps — may be matched by email to prefill your profile and assign Early Spotter status.",
        "Content and activity",
        "We collect content and actions you provide: car photos, captions, car make and model, posts, comments, likes, challenge participation, reports, notifications, and related timestamps. We also process moderation and safety records needed to investigate reports and enforce our rules.",
        "Location and photos",
        "Location is optional and requested only when you create a post. If permitted, we may collect and store the precise latitude and longitude attached to that post and derive its town and country. Revio does not collect location in the background. Uploaded photos are re-encoded before upload, so EXIF metadata such as GPS tags is not copied to the stored image.",
        "Feedback and support",
        "If you send feedback or request support, we collect your messages, category, ratings, and related context. If you choose to include diagnostics, we may also receive app version, Android version, device model, connection type, and recent technical error information. An optional account-deletion reason may be retained without your user ID for product analysis.",
        "Analytics and crash diagnostics",
        "Firebase Analytics and Firebase Crashlytics collection are enabled by default to help us understand app performance and improve features. You can turn this off at any time via “Help improve Revio” in Settings. While enabled, Google may process app interactions, feature-performance events, app and device information, identifiers, crash reports, non-fatal errors, and technical breadcrumbs. We design Revio's own analytics event parameters to avoid names, email, phone number, birth date, captions, comments, exact location, image URLs, passwords, tokens, and internal user or post IDs. Turning collection off stops future analytics collection immediately; crash diagnostics fully stop the next time you open the app, and any crash report already captured before you turned it off may still be sent. We do not use this data for advertising.",
        "On-device data",
        "The app stores sign-in tokens in protected device storage and may cache feed data, images, settings, and consent choices locally so it can work reliably. You can remove local app data through Android settings or by uninstalling the app.",
    )),
    PolicySectionContent("3. How we use data", paragraphs = listOf(
        "We use data to provide and secure accounts; operate profiles and social features; upload and display images; calculate scores, streaks, challenges and rankings; provide optional post location; send in-app activity updates; handle feedback; investigate abuse; maintain and improve the Services; and comply with legal obligations.",
        "We process optional location based on your choice, and Firebase analytics/crash data unless you turn it off in Settings. Other processing is necessary to provide the Services, keep them secure, or comply with law.",
    )),
    PolicySectionContent("4. Visibility and sharing", paragraphs = listOf(
        "Other Revio users may see your full name, username, country, profile photo, optional car details/photo, SpotScore, streak, rankings, badges, posts, comments, and likes. Public content can be viewed, copied, or shared by others, so do not post confidential or sensitive information.",
        "A location-enabled post shows town and country in the standard app interface. At present, the stored coordinates attached to that post can also be included in post data delivered to authenticated Revio clients. Do not attach location if you do not want precise coordinates associated with a post.",
        "We use Cloudflare R2 for uploaded images; Hetzner Online GmbH for backend and database hosting; Google for Google Sign-In, Firebase Analytics, and Firebase Crashlytics; and Supabase for waitlist data. These providers process data only to supply their services to Revio and under their applicable terms.",
        "We may disclose data when required by law, to protect users or the Services, or as part of a business transfer subject to appropriate safeguards. We do not sell personal data or share it for behavioural advertising.",
    )),
    PolicySectionContent("5. International processing", paragraphs = listOf("Our providers may process data in countries other than your own. Where required, we use appropriate contractual or legal safeguards for international transfers.")),
    PolicySectionContent("6. Retention and deletion", paragraphs = listOf(
        "We keep account data while your account is active and only as long as needed for the purposes described above. Security records, legal records, and backups may be retained for a limited period when necessary to prevent abuse, meet legal obligations, or resolve disputes.",
        "Delete your account in Settings → Account → Delete account, or follow the instructions at https://www.joinrevio.app/delete-account. Confirmed deletion removes the account and associated profile, posts, comments, likes, reports, sessions, and other database records. Revio then deletes associated image objects; failed image removals are queued for retry.",
        "Limited de-identified deletion feedback, aggregated statistics, and security/audit records may remain where they no longer directly identify you or retention is required for security or law. Data previously sent to Firebase is subject to Google's retention and deletion controls. Account deletion is permanent.",
    )),
    PolicySectionContent("7. Your choices and rights", paragraphs = listOf(
        "You can edit profile information, deny or revoke Android location permission, turn Firebase analytics/crash collection off (or back on) at any time in Settings, omit optional feedback diagnostics, and delete your account.",
        "Depending on where you live, you may request access, correction, deletion, restriction, objection, or portability, and withdraw consent where processing relies on consent. Email threvioapp@gmail.com from the address associated with your account. We may verify your identity. You may also complain to your local data-protection authority.",
    )),
    PolicySectionContent("8. Security", paragraphs = listOf("We use measures designed to protect data, including HTTPS for production network traffic, one-way password hashing, protected token storage on Android, access controls, and limited provider access. No system is completely secure, so absolute security cannot be guaranteed.")),
    PolicySectionContent("9. Policy changes", paragraphs = listOf("We may update this Policy when the Services or legal requirements change. We will update the effective date and provide additional notice in the app when a change is material.")),
    PolicySectionContent("10. Contact", paragraphs = listOf("Developer and data controller: Andrei R.", "Privacy, deletion, and safety requests: threvioapp@gmail.com")),
)
