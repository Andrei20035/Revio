package com.revio.app

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.revio.app.core.auth.SessionManager
import com.revio.app.core.navigation.Screen
import com.revio.app.core.tour.TourHostViewModel
import com.revio.app.core.tour.TourStep
import javax.inject.Inject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.revio.app.core.navigation.RevioNavigation
import com.revio.app.core.navigation.StartDestinationViewModel
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RevioApp : Application()

@Composable
fun RevioAppUI(
    modifier: Modifier = Modifier,
    sessionManager: SessionManager = androidx.hilt.navigation.compose.hiltViewModel<SessionHostViewModel>().sessionManager,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startVm: StartDestinationViewModel = hiltViewModel()
    val start by startVm.startDestination.collectAsState()
    val tourHostViewModel: TourHostViewModel = hiltViewModel()
    val tourStep by tourHostViewModel.tourController.step.collectAsState()

    LaunchedEffect(sessionManager, navController) {
        sessionManager.expired.collect { message ->
            tourHostViewModel.tourController.cancelForSessionLoss()
            navController.navigate(Screen.Auth.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Drives the tour's cross-screen navigation from the one place that owns navController.
    // Each screen only ever renders TourOverlay for the step matching itself; it never
    // navigates the tour on its own. PostCta stays on the Profile Dashboard — no navigation.
    LaunchedEffect(tourStep, start, navController) {
        // Guards against navigating before RevioNavigation's graph exists; in practice tourStep
        // only leaves null once start has already resolved (both flip in the same coroutine).
        if (start == null) return@LaunchedEffect
        val route = when (tourStep) {
            TourStep.Feed -> Screen.Feed.route
            TourStep.Leaderboard -> Screen.Leaderboard.route
            TourStep.Activity -> Screen.Activity.route
            TourStep.Profile -> Screen.Profile.route
            TourStep.PostCta, null -> null
        }
        if (route != null && navController.currentDestination?.route != route) {
            navController.navigate(route) {
                popUpTo(Screen.Feed.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // The Surface stays full-bleed so the theme background fills the whole window — including
    // behind the system bars — which keeps the system-chosen status/nav icon colors legible.
    // `systemBarsPadding()` then insets the actual screen content so nothing draws under the
    // status bar or the Android navigation bar. Consuming the insets here means per-screen
    // inset modifiers (statusBarsPadding/navigationBarsPadding) read 0 downstream — no double padding.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            when (val s = start) {
                null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                else -> RevioNavigation(navController, s)
            }
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class SessionHostViewModel @Inject constructor(
    val sessionManager: SessionManager,
) : androidx.lifecycle.ViewModel()
