package com.revio.app.features.settings

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
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.scaling.LocalActivityScale
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.scaling.rememberActivityScale
import com.revio.app.core.ui.theme.Poppins

private val PolicyCardBackground = Color(0x292B3156)
private val PolicyBodyColor = Color(0xFFD2D3DD)
private val PolicyMutedColor = Color(0xFF9FA1B1)
private val PolicyWarningColor = Color(0xFFFFC766)

@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    AppScreenBackground(showBottomScrim = false) {
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
                    text = "Effective date: [REQUIRED BEFORE LAUNCH: DD Month YYYY]",
                    color = PolicyWarningColor,
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
        PolicyBody("Revio is a car-spotting community where users can create profiles, share car photos and posts, discover public posts, and interact through comments and likes. This Privacy Policy explains how Revio Team (\"Revio\", \"we\", \"us\", or \"our\") collects, uses, shares, stores, and protects personal data when you use the Revio mobile application and related services (collectively, the \"Services\").")
        Spacer(Modifier.height(14.dp.actScaled()))
        Text("Data controller: [REQUIRED BEFORE LAUNCH: legal entity/individual name, registered or business address, country].", color = PolicyWarningColor, fontFamily = Poppins, fontSize = 12.sp.actScaledText(), lineHeight = 19.sp.actScaledText())
        Text("Contact: threvioapp@gmail.com", color = PolicyBodyColor, fontFamily = Poppins, fontSize = 13.sp.actScaledText())
        Spacer(Modifier.height(14.dp.actScaled()))
        PolicyBody("By creating an account or using the Services, you acknowledge the practices described in this Privacy Policy. If you do not agree, please do not use the Services.")
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
                PolicyBody(paragraph, color = if (paragraph.contains("[REQUIRED BEFORE LAUNCH:")) PolicyWarningColor else PolicyBodyColor)
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
    PolicySectionContent("1. Who may use Revio", paragraphs = listOf("Revio is intended for users aged 16 or older. We do not knowingly allow people under 16 to create an account. If you believe that a person under 16 has provided us with personal data, please contact us at threvioapp@gmail.com so we can investigate and delete the account where appropriate.")),
    PolicySectionContent("2. Data we collect", "A. Account and profile data", listOf("When you create or use an account, we may collect:", "• email address;", "• account identifier and authentication information;", "• full name, username, date of birth, country, and profile photograph;", "• account statistics, including SpotScore and streak information; and", "• account and security information, such as login sessions, device identifier, device name, IP address, user-agent information, and timestamps.", "Your date of birth is used to apply the age requirement. It is not displayed publicly. [REQUIRED BEFORE LAUNCH: not yet implemented — no date-of-birth field or age check exists in either repo today. Implement the age gate or revise this paragraph before publishing.] [REQUIRED BEFORE LAUNCH: phone number is collected end-to-end in the app today but is not listed above — add it here, or remove phone collection before launch (see Accuracy blockers).]", "B. Content and social activity", "We collect the content you choose to provide, including car photographs, post captions, car information, comments, likes, reports, and related timestamps. Posts, comments, likes, account statistics, and profile information are part of the social functionality of Revio and may be visible to other users as described below.", "C. Location data", "Revio requests location permission only when you choose to create a post. Location is optional: you can publish a post without granting location permission.", "If you grant permission and choose to use the feature, we may collect and store the latitude and longitude connected with that post, and derive or store the associated town and country. Other users are shown no more than the town and country; Revio does not display your precise coordinates to other users. Revio does not collect location in the background.", "Uploaded photographs are reprocessed on-device before upload: the image is re-encoded from pixel data, so EXIF metadata — including GPS location, timestamp, and device model — is not transmitted and never reaches storage. Correct visual orientation is preserved by applying rotation directly to the pixels.", "D. Analytics and technical data", "We use Firebase Analytics to understand how the Services are used, measure feature performance, and improve reliability and user experience. This may involve usage data, app interactions, device and app information, identifiers, and diagnostic information collected by Firebase/Google according to its applicable terms and privacy practices. [REQUIRED BEFORE LAUNCH: not yet implemented — no Firebase Analytics SDK exists in revio-android today. Integrate it or remove this section and the analytics consent screen in §5 before publishing.]", "We do not use analytics data for targeted advertising and do not sell personal data.")),
    PolicySectionContent("3. How we use data", paragraphs = listOf("We use personal data to:", "• create and secure accounts, authenticate users, and prevent abuse or fraud;", "• operate profiles, posts, comments, likes, scores, streaks, and related social features;", "• show the town and country associated with a post when location is provided;", "• store, deliver, and display user-uploaded images;", "• investigate reports, enforce our Terms of Use and Community Guidelines, and protect users and the Services;", "• maintain, troubleshoot, analyse, and improve the Services; and", "• comply with legal obligations and respond to lawful requests.", "Where required by applicable law, we rely on your consent for optional data processing, such as permission-based location access and analytics where consent is required. We otherwise process data as necessary to provide the Services, protect the Services and users, or meet legal obligations.")),
    PolicySectionContent("4. What is public", paragraphs = listOf("The following information may be visible to other Revio users: your full name, username, profile photograph, country, SpotScore, streak, posts, comments, and likes. A post may show its town and country if you provided location permission for that post.", "Do not include personal, confidential, or sensitive information in public posts or comments. Content you make public can be viewed, copied, or shared by other users.")),
    PolicySectionContent("5. How we share data", paragraphs = listOf("We share data only as needed to operate the Services, including with:", "• Cloudflare R2, which stores user-uploaded images;", "• Google, for Google Sign-In authentication ([REQUIRED BEFORE LAUNCH: remove \"Firebase Analytics\" from this line unless the SDK is actually integrated before launch — see Accuracy blockers]);", "• [REQUIRED BEFORE LAUNCH: API/database hosting provider and region], which hosts and processes the Revio backend and database;", "• service providers that help us operate, secure, or support the Services, under appropriate contractual or legal safeguards; and", "• authorities or other parties when required by law, necessary to protect rights and safety, investigate abuse, or enforce our policies.", "We do not sell personal data and do not share personal data for cross-context behavioural advertising.")),
    PolicySectionContent("6. International transfers", paragraphs = listOf("Revio is available globally. Your data may be processed in countries other than the country where you live, including countries where our providers operate. We use appropriate safeguards required by applicable law for international data transfers.")),
    PolicySectionContent("7. Retention and account deletion", paragraphs = listOf("We retain personal data while your account remains active and only for as long as needed to provide the Services, resolve disputes, enforce our agreements, protect users, or meet legal obligations.", "You can initiate account deletion in the app through Settings → Account → Delete account. You can also submit a deletion request at [REQUIRED BEFORE LAUNCH: public account-deletion webpage URL].", "When an account-deletion request is completed, we delete or irreversibly de-identify personal data associated with the account, including profile data, posts, comments, likes, reports, sessions, and Revio-controlled image objects, unless we must retain limited information to comply with law, resolve a dispute, or protect the security and integrity of the Services. De-identified, aggregated analytics that cannot reasonably identify you may be retained.", "Deletion is permanent and cannot be undone. We will state the deletion timeline in the app and on the deletion webpage before launch.")),
    PolicySectionContent("8. Your privacy rights", paragraphs = listOf("Depending on where you live, you may have rights to request access to, correction of, deletion of, restriction of, or portability of your personal data, and to object to certain processing. You may also withdraw consent where we rely on consent.", "To make a request, email threvioapp@gmail.com from the email address associated with your account. We may need to verify your identity before completing a request. You may also have the right to complain to your local data-protection authority.")),
    PolicySectionContent("9. Security", paragraphs = listOf("We use reasonable technical and organisational measures designed to protect data. No method of transmission or storage is completely secure, so we cannot guarantee absolute security.")),
    PolicySectionContent("10. Third-party services", paragraphs = listOf("Google and Firebase services are governed by their own privacy terms and policies. Cloudflare R2 processes stored images as a service provider for Revio. We are not responsible for the independent privacy practices of third parties outside the Services.")),
    PolicySectionContent("11. Changes to this Privacy Policy", paragraphs = listOf("We may update this Privacy Policy when our Services or legal obligations change. If we make material changes, we will provide notice in the app or by another appropriate method. The “Effective date” above shows when this Policy was last updated.")),
    PolicySectionContent("12. Contact", paragraphs = listOf("Questions, privacy requests, and safety concerns can be sent to:", "Revio Team", "Email: threvioapp@gmail.com")),
)
