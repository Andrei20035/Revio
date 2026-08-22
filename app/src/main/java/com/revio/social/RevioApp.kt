package com.revio.social

import android.app.Application
import android.content.Context
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
import com.revio.social.core.auth.SessionManager
import com.revio.social.core.navigation.Screen
import com.revio.social.core.tour.TourHostViewModel
import com.revio.social.core.tour.TourStep
import javax.inject.Inject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.revio.social.core.navigation.RevioNavigation
import com.revio.social.core.navigation.StartDestinationViewModel
import com.revio.social.features.notifications.ModerationNoticeHost
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.data.local.preferences.UserPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Applies an opt-in consent decision (docs/consent-decision.md) to both Firebase SDKs. Extracted
 * from [RevioApp.onCreate] so both branches stay unit-testable without an Application instance.
 */
internal fun applyAnalyticsConsent(context: Context, granted: Boolean) {
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(granted)
    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(granted)
}

@HiltAndroidApp
class RevioApp : Application() {

    @Inject lateinit var userPreferences: UserPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Opt-out consent (docs/consent-decision.md). A fresh install collects immediately: the
        // manifest's firebase_*_collection_enabled=true meta-data applies before this runs.
        //
        // This used to hardcode false, which re-revoked consent on every cold start — a user who
        // opted in was silently collected-from only for the session in which they touched the
        // toggle. Firebase does persist the enabled flag across launches on its own — and that
        // persisted flag takes priority over the manifest default after the first launch — so
        // for a user who hasn't changed the default this read is usually a no-op reassertion; it
        // is done anyway because DataStore holds the value the Settings screen displays, and
        // reasserting it here keeps the displayed choice and the SDKs' actual state from
        // drifting apart. It's also what turns a user who revoked consent under the old opt-in
        // regime back on: DataStore has no key for them, so this read resolves to true.
        //
        // Read asynchronously (DataStore is disk-backed — a blocking read here would risk an ANR
        // on startup). The window before it lands is covered by whichever value Firebase already
        // persisted, so a user who previously revoked consent stays opted out throughout it.
        appScope.launch {
            applyAnalyticsConsent(this@RevioApp, userPreferences.analyticsConsentGranted.first())
        }
    }
}

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

    ModerationNoticeHost(navController = navController)

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
