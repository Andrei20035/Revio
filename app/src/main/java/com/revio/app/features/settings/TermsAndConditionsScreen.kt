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

private val TermsCardBackground = Color(0x292B3156)
private val TermsBodyColor = Color(0xFFD2D3DD)
private val TermsMutedColor = Color(0xFF9FA1B1)
private val TermsWarningColor = Color(0xFFFFC766)

@Composable
fun TermsAndConditionsScreen(navController: NavController) {
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
                        text = "Terms & Conditions",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                Spacer(Modifier.height(18.dp.actScaled()))
                Text(
                    text = "Effective date: [REQUIRED BEFORE LAUNCH: DD Month YYYY]",
                    color = TermsWarningColor,
                    fontFamily = Poppins,
                    fontSize = 12.sp.actScaledText(),
                    lineHeight = 19.sp.actScaledText(),
                )
                Spacer(Modifier.height(14.dp.actScaled()))
                TermsIntroCard()
                Spacer(Modifier.height(20.dp.actScaled()))

                termsAndConditionsSections.forEach { section ->
                    TermsSection(section)
                    Spacer(Modifier.height(20.dp.actScaled()))
                }
                Spacer(Modifier.height(24.dp.actScaled()))
            }
        }
    }
}

@Composable
private fun TermsIntroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp.actScaled()))
            .background(TermsCardBackground)
            .padding(18.dp.actScaled()),
    ) {
        Text("Terms of Use and Community Guidelines", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 19.sp.actScaledText())
        Spacer(Modifier.height(10.dp.actScaled()))
        Text("Status: Draft — review with qualified legal counsel before launch.", color = TermsWarningColor, fontFamily = Poppins, fontSize = 12.sp.actScaledText(), lineHeight = 19.sp.actScaledText())
        Text("Owner: Revio Team", color = TermsBodyColor, fontFamily = Poppins, fontSize = 13.sp.actScaledText())
        Text("Contact: threvioapp@gmail.com", color = TermsBodyColor, fontFamily = Poppins, fontSize = 13.sp.actScaledText())
        Spacer(Modifier.height(14.dp.actScaled()))
        TermsBody("Revio is a community for sharing and discovering real car-spotting photography. By creating an account, uploading content, or using Revio, you agree to these Terms of Use and Community Guidelines (together, the \"Terms\"). If you do not agree, do not use Revio.")
    }
}

@Composable
private fun TermsSection(section: TermsSectionContent) {
    Column {
        Text(section.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp.actScaledText())
        section.subtitle?.let {
            Spacer(Modifier.height(6.dp.actScaled()))
            Text(it, color = TermsMutedColor, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp.actScaledText())
        }
        Spacer(Modifier.height(9.dp.actScaled()))
        section.paragraphs.forEachIndexed { index, paragraph ->
            if (paragraph.startsWith("• ")) {
                Row(modifier = Modifier.padding(start = 4.dp.actScaled())) {
                    Text("•", color = Color.White, fontSize = 16.sp.actScaledText())
                    Spacer(Modifier.width(8.dp.actScaled()))
                    TermsBody(paragraph.removePrefix("• "), Modifier.weight(1f))
                }
            } else {
                TermsBody(
                    paragraph,
                    color = if (paragraph.contains("[REQUIRED BEFORE LAUNCH:") || paragraph.contains("[LEGAL REVIEW:")) TermsWarningColor else TermsBodyColor,
                )
            }
            if (index != section.paragraphs.lastIndex) Spacer(Modifier.height(9.dp.actScaled()))
        }
    }
}

@Composable
private fun TermsBody(text: String, modifier: Modifier = Modifier, color: Color = TermsBodyColor) {
    Text(text, modifier = modifier, color = color, fontFamily = Poppins, fontSize = 13.sp.actScaledText(), lineHeight = 21.sp.actScaledText())
}

private data class TermsSectionContent(val title: String, val subtitle: String? = null, val paragraphs: List<String>)

private val termsAndConditionsSections = listOf(
    TermsSectionContent("1. Eligibility and account responsibility", paragraphs = listOf(
        "Revio is intended for people aged 16 or older. You must provide accurate information, keep your account credentials secure, and not create an account for another person without their permission.",
        "You are responsible for activity on your account and for the content you upload. You must use Revio lawfully and in accordance with these Terms.",
        "[REQUIRED BEFORE LAUNCH: implement a date-of-birth age gate (16+) and the required Terms/Privacy Policy acceptance before account creation.]",
        "[LEGAL REVIEW: confirm the final age-rating and child-safety obligations for each distribution platform and country, and whether the 16+ floor satisfies COPPA, UK Age Appropriate Design Code, and GDPR digital-consent-age requirements in target markets.]",
    )),
    TermsSectionContent("2. What Revio is for", paragraphs = listOf(
        "Revio is a car-spotting community. Posts must have a real vehicle, or real automotive content, as their primary focus. The purpose of the service is to help users share original, real-world car-spotting content and enjoy a respectful automotive community.",
        "People, number plates, buildings, and other elements may appear incidentally in a photograph, including in public places. They must not become the primary subject of the post. Revio may remove content where the main focus is not a vehicle or relevant automotive subject.",
    )),
    TermsSectionContent("3. Content requirements", paragraphs = listOf(
        "You may upload only content that you created or have the necessary rights and permissions to use. You must not upload content that violates copyright, privacy, publicity, trademark, or other rights.",
        "The following content is not permitted:",
        "• AI-generated, synthetic, or manipulated imagery presented as real car spotting;",
        "• images from video games, simulations, or other non-real-world sources;",
        "• reposted content that you do not have permission to share;",
        "• content whose primary focus is a person rather than a vehicle or automotive subject;",
        "• illegal, fraudulent, deceptive, or unsafe content;",
        "• threats, harassment, bullying, hate speech, doxxing, stalking, or encouragement of violence or self-harm;",
        "• explicit sexual content, nudity, graphic violence, or otherwise age-inappropriate content;",
        "• any content that sexualises, exploits, grooms, endangers, or depicts the abuse of a child;",
        "• content that reveals sensitive personal information or a person's precise location without their permission;",
        "• spam, scams, malware, unauthorised commercial solicitation, or attempts to gain unauthorised access to accounts or systems; and",
        "• content or conduct intended to interfere with reporting, moderation, security controls, or the normal operation of Revio.",
    )),
    TermsSectionContent("4. Number plates and people in photographs", paragraphs = listOf(
        "Number plates may be visible in car-spotting posts. Visible plates are an accepted part of real-world car-spotting photography on Revio. Users should still act responsibly and respect applicable law when taking and publishing photographs.",
        "People may appear incidentally in a post, particularly in public places, provided the vehicle or automotive subject remains the main focus. Do not use Revio to target, harass, identify, shame, or expose a person. Revio may remove content that is focused on a person, infringes privacy or other rights, or creates a safety concern.",
    )),
    TermsSectionContent("5. Your content and Revio's licence", paragraphs = listOf(
        "You keep ownership of the content you upload. By posting content to Revio, you grant Revio a non-exclusive, worldwide, royalty-free, transferable and sublicensable licence to host, store, reproduce, adapt for technical purposes, display, distribute, and otherwise use that content to operate, improve, and promote Revio.",
        "Revio may use content you have posted in promotional materials for the app, including app-store materials, social-media posts, websites, and marketing campaigns. If you delete a post, Revio will permanently delete the original post and its associated content from Revio's servers. Revio will not use that deleted post in new promotional materials.",
        "If Revio already created or published promotional material using your post before you deleted it, that promotional material may remain published indefinitely. Deleting the post does not require Revio to remove or update promotional material that already exists.",
        "[REQUIRED BEFORE LAUNCH: confirm the exact storage-deletion behaviour, including deletion of Cloudflare R2 objects, so the Terms and Privacy Policy accurately describe the product.]",
        "[LEGAL REVIEW: confirm the scope of the worldwide, transferable, sublicensable licence above is the intended breadth for this clause.]",
    )),
    TermsSectionContent("6. Public content and safety", paragraphs = listOf(
        "Posts, comments, likes, profile details, and certain account statistics may be visible to other Revio users as described in the Privacy Policy. Do not include personal, confidential, or sensitive information in public posts or comments.",
        "You understand that public content may be viewed, copied, or shared by others. Revio cannot guarantee that other users will not copy or use content that you make public.",
    )),
    TermsSectionContent("7. Reporting, moderation, and enforcement", paragraphs = listOf(
        "Revio may review content and user conduct to enforce these Terms and protect the community. We may remove posts, comments, likes, or other associated content where appropriate. Removing a post may also remove its associated comments and likes.",
        "For ordinary violations, Revio follows these steps in order:",
        "• Removal with reason. We remove the non-compliant content and explain why it was removed.",
        "• Formal warning. If the violation continues or repeats, we notify the user that further violations may lead to suspension or a ban.",
        "• Suspension or ban. If the violation continues after a warning, we may temporarily suspend or permanently terminate the account.",
        "Revio does not skip a step in this order for ordinary violations. The only exception is the serious-violation category below, which can result in immediate suspension or termination without going through steps 1–2.",
        "For serious violations — including child sexual abuse material, exploitation of children, credible threats, serious harassment, fraud, doxxing, unlawful activity, or security abuse — Revio may remove content and suspend or ban an account immediately, without prior warning. This list gives examples and is not exhaustive; Revio uses reasonable judgment to identify comparably serious violations.",
        "[REQUIRED BEFORE LAUNCH: implement reporting for comments and user accounts. The MVP currently supports reporting posts only. Add corresponding user interface, API routes, moderation reasons, and an internal review/audit workflow.]",
        "[REQUIRED BEFORE LAUNCH: implement user blocking before release. A public user-generated-content feed needs a way for a user to stop seeing another user's posts and comments.]",
    )),
    TermsSectionContent("8. Account deletion", paragraphs = listOf(
        "You may delete your account through the account-deletion feature in Revio once it is available. When deletion is completed, Revio will permanently delete the account and the associated posts, comments, likes, and other Revio-controlled content from its servers, subject only to any retention required by applicable law.",
        "Deletion is permanent and cannot be undone.",
        "[REQUIRED BEFORE LAUNCH: add the in-app account-deletion entry point, verify end-to-end deletion of stored image objects, and publish the required public account-deletion request webpage.]",
    )),
    TermsSectionContent("9. Changes, suspension, and termination", paragraphs = listOf(
        "We may update these Terms when Revio, our community rules, or legal obligations change. We will provide notice in the app or by another appropriate method when material changes are made. Continued use after the effective date of an update means you accept the updated Terms to the extent permitted by law.",
        "We may limit, suspend, or terminate access to Revio when necessary to enforce these Terms, protect users, preserve the integrity of the service, or comply with legal obligations.",
    )),
    TermsSectionContent("10. Disclaimers, liability, and governing law", paragraphs = listOf(
        "[LEGAL REVIEW: draft disclaimer of warranties, limitation of liability, indemnification, and governing law / dispute resolution clauses appropriate for Revio's launch jurisdiction(s). Not drafted here — pending counsel input.]",
        "[REQUIRED BEFORE LAUNCH: determine target jurisdiction(s) for governing law and dispute resolution.]",
    )),
    TermsSectionContent("11. Contact", paragraphs = listOf(
        "For questions, safety concerns, or reports about these Terms, contact:",
        "Revio Team",
        "Email: threvioapp@gmail.com",
    )),
)
