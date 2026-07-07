package com.revio.app.features.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.revio.app.core.ui.components.GradientText
import com.revio.app.core.navigation.Screen
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.revio.app.R

@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit = {
        navController.navigate(Screen.Auth.route) {
            popUpTo(Screen.Onboarding.route) {
                inclusive = true
            }
        }
    }
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Welcome to",
                subtitle = "Revio!",
                imageRes = R.drawable.app_presentation_1,
                titleColor = Color.White,
                titleFontWeight = FontWeight.Normal,
                subtitleFontSize = 48.sp,
                subtitleFontWeight = FontWeight.Bold
            ),
            OnboardingPage(
                title = "Spot the Unseen",
                subtitle = "Uncover hidden gems on the streets, from vintage classics to the latest supercars.",
                imageRes = R.drawable.app_presentation_2,
            ),
            OnboardingPage(
                title = "Capture the Moment",
                subtitle = "Snap and share your automotive encounters. Get recognized by a community of enthusiasts.",
                imageRes = R.drawable.app_presentation_3,
            ),
            OnboardingPage(
                title = "Become a Top Spotter 🌟",
                subtitle = "Engage in challenges, earn badges, and climb the leaderboard.",
                imageRes = R.drawable.app_presentation_4,

            )
        )
    }

    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState(initial = false)

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isOnboardingCompleted) {
        if(isOnboardingCompleted) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                coroutineScope.launch {
                    if (pagerState.currentPage < pages.lastIndex) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    } else {
                        viewModel.completeOnboarding()
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPageContent(pageData = pages[page], isFirstPage = pagerState.currentPage == 0)
        }

        val pageCount = pages.size
        val currentPage = pagerState.currentPage
        val currentPageOffset = pagerState.currentPageOffsetFraction

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 60.dp)
                .navigationBarsPadding()
                .padding(48.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(pageCount) { index ->

                val distance = abs(index - (currentPage + currentPageOffset))
                val width = lerp(
                    start = 16.dp,
                    stop = 32.dp,
                    fraction = (1f - distance).coerceIn(0f, 1f)
                )

                val color = if (distance < 0.5f) Color.White else Color.White.copy(alpha = 0.3f)

                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        // Tap instruction
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Text(
                text = if (pagerState.currentPage < pages.lastIndex)
                    "Tap anywhere to continue"
                else
                    "Tap to get started",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OnboardingPageContent(pageData: OnboardingPage, isFirstPage: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = pageData.imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 1f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = pageData.title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = pageData.titleColor,
                    fontSize = pageData.titleFontSize,
                    fontWeight = pageData.titleFontWeight,
                    lineHeight = 50.sp,
                    textAlign = TextAlign.Center,
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isFirstPage) {
                GradientText(
                    text = pageData.subtitle,
                    fontWeight = pageData.subtitleFontWeight,
                    fontSize = pageData.subtitleFontSize,
                    lineHeight = pageData.lineHeight
                )
            } else {
                Text(
                    text = pageData.subtitle,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = pageData.subtitleColor,
                        fontSize = pageData.subtitleFontSize,
                        fontWeight = pageData.subtitleFontWeight,
                        lineHeight = pageData.lineHeight,
                        textAlign = TextAlign.Center
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

