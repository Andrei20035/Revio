package com.revio.social

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.revio.social.core.notifications.PendingDeepLink
import com.revio.social.core.ui.theme.RevioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pendingDeepLink: PendingDeepLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch { pendingDeepLink.capture(intent) }

        enableEdgeToEdge()
        setContent {
            RevioTheme {
                RevioAppUI()
            }
        }
    }

    /** `singleTop` launch mode delivers a push tap here instead of a new [onCreate] while the app is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { pendingDeepLink.capture(intent) }
    }
}
