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

private val TermsCardBackground = Color(0x292B3156)
private val TermsBodyColor = Color(0xFFD2D3DD)
private val TermsMutedColor = Color(0xFF9FA1B1)

@Composable
fun TermsAndConditionsScreen(navController: NavController) {
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
                        text = "Terms & Conditions",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                Spacer(Modifier.height(18.dp.actScaled()))
                Text(
                    text = "Effective date: 21 August 2026",
                    color = TermsMutedColor,
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
        Text("Developer: Andrei R.", color = TermsBodyColor, fontFamily = Poppins, fontSize = 13.sp.actScaledText())
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
                TermsBody(paragraph)
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
        "You must be at least 13 years old to use Revio. If you are under the age of legal majority where you live, your parent or legal guardian must review these Terms and permit you to use Revio where applicable law requires it.",
        "You must provide accurate information, keep your credentials secure, and not create an account for another person without permission. You are responsible for activity on your account and for the content you upload.",
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
        "Number plates may be visible in car-spotting posts. Visible plates are an accepted part of real-world car-spotting photography on Revio. Users must still act responsibly and respect applicable law when taking and publishing photographs.",
        "People may appear incidentally in a post, particularly in public places, provided the vehicle or automotive subject remains the main focus. Do not use Revio to target, harass, identify, shame, or expose a person. Revio may remove content that is focused on a person, infringes privacy or other rights, or creates a safety concern.",
    )),
    TermsSectionContent("5. Your content and Revio's licence", paragraphs = listOf(
        "You keep ownership of content you upload. You confirm that you have the rights and permissions needed to post it.",
        "By posting content, you grant Revio a non-exclusive, worldwide, royalty-free licence to host, store, reproduce, resize, re-encode, display, distribute, and otherwise process that content as needed to operate, secure, improve, and promote Revio. This licence allows Revio to use service providers for those purposes.",
        "The licence ends when your content is deleted, except where a limited copy must remain temporarily in backups, for legal or security reasons, or in promotional material that was created and published before deletion. Revio will not use deleted content in new promotional material.",
    )),
    TermsSectionContent("6. Public content and safety", paragraphs = listOf(
        "Posts, comments, likes, profile details, location information attached to posts, and certain account statistics may be visible to other Revio users as described in the Privacy Policy. Do not include personal, confidential, or sensitive information in public posts or comments.",
        "Public content may be viewed, copied, or shared by others. Revio cannot guarantee that other users will not copy or use content you make public.",
    )),
    TermsSectionContent("7. Reporting, moderation, and enforcement", paragraphs = listOf(
        "You can report a post using the in-app reporting tool. To report another user, a comment, a child-safety concern, or an urgent issue, contact threvioapp@gmail.com and include enough information for us to locate the content. Do not send passwords or unnecessary personal data.",
        "Revio may review content, reports, and user conduct to enforce these Terms and protect the community. Depending on the circumstances, we may remove content, restrict features, warn a user, temporarily suspend access, or permanently terminate an account.",
        "We may act without prior notice for serious violations, including child sexual abuse or exploitation material, credible threats, doxxing, fraud, unlawful conduct, repeat abuse, or attempts to compromise the Services. We may preserve and disclose relevant information where required by law or necessary to protect people.",
    )),
    TermsSectionContent("8. Account deletion", paragraphs = listOf(
        "You may permanently delete your account through Settings → Account → Delete account. If you cannot access the app, follow the instructions at https://www.joinrevio.app/delete-account.",
        "Confirmed deletion removes the account and associated Revio-controlled content as described in the Privacy Policy. Limited records may be retained only where necessary for security, fraud prevention, legal compliance, or dispute resolution. Deletion is permanent and cannot be undone.",
    )),
    TermsSectionContent("9. Changes, suspension, and termination", paragraphs = listOf(
        "We may update these Terms when Revio, our community rules, or legal obligations change. We will provide notice in the app or by another appropriate method when material changes are made. Continued use after the effective date of an update means you accept the updated Terms to the extent permitted by law.",
        "We may limit, suspend, or terminate access to Revio when necessary to enforce these Terms, protect users, preserve the integrity of the service, or comply with legal obligations.",
    )),
    TermsSectionContent("10. Disclaimers, liability, and applicable law", paragraphs = listOf(
        "Revio is provided on an “as is” and “as available” basis. To the extent permitted by law, we do not guarantee uninterrupted availability, that all content is accurate, or that every error will be corrected.",
        "To the maximum extent permitted by applicable law, the developer of Revio is not liable for indirect, incidental, special, consequential, or punitive loss arising from your use of the Services or from content posted by other users. Nothing in these Terms excludes liability that cannot lawfully be excluded or limits mandatory consumer rights.",
        "These Terms are governed by applicable law. Any dispute will be handled by the courts or authorities that have jurisdiction under mandatory law. If any provision is unenforceable, the remaining provisions continue to apply.",
    )),
    TermsSectionContent("11. Contact", paragraphs = listOf(
        "For questions, safety concerns, or reports about these Terms, contact:",
        "Andrei R.",
        "Email: threvioapp@gmail.com",
    )),
)
