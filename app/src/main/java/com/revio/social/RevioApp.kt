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
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.notifications.DeepLinkDestination
import com.revio.social.core.notifications.PendingDeepLink
import com.revio.social.core.notifications.PushTokenRegistrar
import com.revio.social.core.navigation.Screen
import com.revio.social.core.tour.TourHostViewModel
import com.revio.social.core.tour.TourStep
import javax.inject.Inject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.revio.social.core.navigation.RevioNavigation
import com.revio.social.core.navigation.StartDestinationViewModel
import com.revio.social.core.notifications.createNotificationChannels
import com.revio.social.core.notifications.NotificationPrepromptHost
import com.revio.social.core.notifications.NotificationPrepromptViewModel
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
import java.util.UUID

/**
 * Applies an opt-in consent decision (docs/consent-decision.md) to both Firebase SDKs. Extracted
 * from [RevioApp.onCreate] so both branches stay unit-testable without an Application instance.
 */
internal fun applyAnalyticsConsent(context: Context, granted: Boolean) {
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(granted)
    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(granted)
}

/**
 * Navigates to a challenge's public detail screen for a CHALLENGE deep link
 * (push-notifications plan, "challenge is live" work) — same `popUpTo`/`launchSingleTop` shape as
 * the existing FEED deep-link navigation and the tour's own cross-screen navigation just below,
 * so a challenge landed on this way sits directly on top of Feed in the back stack and a repeat
 * tap never pushes a second copy of the destination.
 */
private fun NavHostController.navigateToChallengeDetail(challengeId: UUID) {
    navigate(Screen.ChallengeDetail.createRoute(challengeId)) {
        popUpTo(Screen.Feed.route) { saveState = true }
        launchSingleTop = true
    }
}

@HiltAndroidApp
class RevioApp : Application() {

    @Inject lateinit var userPreferences: UserPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels(this)

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
    // Defaulted (not required, unlike RevioNavigation's own navController param) so the single
    // production call site (MainActivity) is unaffected — tests inject a TestNavHostController
    // here to assert on nav state directly, the same reason RevioNavigation takes one.
    navController: NavHostController = rememberNavController(),
    sessionManager: SessionManager = androidx.hilt.navigation.compose.hiltViewModel<SessionHostViewModel>().sessionManager,
) {
    val context = LocalContext.current
    val startVm: StartDestinationViewModel = hiltViewModel()
    val start by startVm.startDestination.collectAsState()
    val tourHostViewModel: TourHostViewModel = hiltViewModel()
    val tourStep by tourHostViewModel.tourController.step.collectAsState()
    val pushTokenRegistrar = hiltViewModel<PushTokenHostViewModel>().pushTokenRegistrar
    val pendingDeepLink = hiltViewModel<PendingDeepLinkHostViewModel>().pendingDeepLink
    val notificationPrepromptViewModel: NotificationPrepromptViewModel = hiltViewModel()
    val connectivity = hiltViewModel<ConnectivityHostViewModel>().networkConnectivityManager

    // Corrects a connectivity value left stale by a missed/delayed system callback while the
    // process was backgrounded (pas 1, docs/plans/avem-un-bug-android-mutable-sky.md) — without
    // this, NetworkConnectivityInterceptor and every onReconnected()/onValidatedReconnect()
    // consumer could keep acting on a "false" that no longer reflects reality. Kept as its own
    // effect, ordered first, so the foreground requests below always see a freshly-checked value
    // rather than racing the check. refresh() itself logs the pas-0 breadcrumb (source="resume"),
    // and its underlying MutableStateFlows don't re-emit on an unchanged value, so a resume with
    // no real change triggers no extra retries downstream.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        connectivity.refresh(source = "resume")
    }

    // Re-sends the FCM token on every foreground so timezone/locale/appVersion stay current on
    // the server row (push-notifications plan, step 2.4) — an upsert, so this never duplicates
    // the device row created at login.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pushTokenRegistrar.registerCurrentToken()
    }

    // A trip to Android Settings can grant the notifications permission out-of-band — without
    // this, a visible pre-prompt card would sit there until the user notices and dismisses it
    // themselves (step 3.4). Kept as its own effect, separate from the one above, so it doesn't
    // interfere with the FCM token resend.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationPrepromptViewModel.onResumed()
    }

    // Consumes a push tap buffered before the graph existed (cold start) or before login
    // completed (delogat). For a FEED target there's nothing to navigate — Feed is already the
    // start destination or is where the post-login flow lands anyway, so this only clears the
    // buffer once that destination is actually reached (push-notifications plan, step 2.5). A
    // CHALLENGE target is looked at and navigated *before* it could otherwise be silently
    // discarded by this same buffer-clearing — landing on Feed at cold start must never eat a
    // buffered challenge deep link (push-notifications plan, "challenge is live" work).
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(start, currentBackStackEntry, pendingDeepLink) {
        if (start == null) return@LaunchedEffect
        if (currentBackStackEntry?.destination?.route == Screen.Feed.route) {
            val target = pendingDeepLink.consume()
            if (target?.destination == DeepLinkDestination.CHALLENGE) {
                target.challengeId?.let { challengeId ->
                    navController.navigateToChallengeDetail(challengeId)
                    pendingDeepLink.logDestinationReached(DeepLinkDestination.CHALLENGE.value, outcome = "ok")
                }
            }
        }
    }

    // A push tapped while the app is already running elsewhere (singleTop -> onNewIntent), or
    // the same buffered target this effect's own subscription picks up right at cold start
    // (PendingDeepLink.signal buffers one pending emission for a not-yet-collecting flow). The
    // graph already exists by the time `start` is non-null, so this navigates explicitly.
    // Guarded against firing before the graph exists and against jumping anywhere while
    // unauthenticated.
    LaunchedEffect(pendingDeepLink, navController) {
        pendingDeepLink.signal.collect {
            if (start == null) return@collect
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == Screen.Auth.route || currentRoute == Screen.Onboarding.route) return@collect
            when (val target = pendingDeepLink.consume()) {
                null -> Unit
                else -> when (target.destination) {
                    DeepLinkDestination.FEED -> if (currentRoute != Screen.Feed.route) {
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Feed.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    DeepLinkDestination.CHALLENGE -> target.challengeId?.let { challengeId ->
                        navController.navigateToChallengeDetail(challengeId)
                        pendingDeepLink.logDestinationReached(DeepLinkDestination.CHALLENGE.value, outcome = "ok")
                    }
                    DeepLinkDestination.LIKE, DeepLinkDestination.COMMENT -> Unit
                }
            }
        }
    }

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

            NotificationPrepromptHost(viewModel = notificationPrepromptViewModel)
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class SessionHostViewModel @Inject constructor(
    val sessionManager: SessionManager,
) : androidx.lifecycle.ViewModel()

@dagger.hilt.android.lifecycle.HiltViewModel
class ConnectivityHostViewModel @Inject constructor(
    val networkConnectivityManager: NetworkConnectivityManager,
) : androidx.lifecycle.ViewModel()

@dagger.hilt.android.lifecycle.HiltViewModel
class PushTokenHostViewModel @Inject constructor(
    val pushTokenRegistrar: PushTokenRegistrar,
) : androidx.lifecycle.ViewModel()

@dagger.hilt.android.lifecycle.HiltViewModel
class PendingDeepLinkHostViewModel @Inject constructor(
    val pendingDeepLink: PendingDeepLink,
) : androidx.lifecycle.ViewModel()
