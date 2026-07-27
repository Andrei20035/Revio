package com.revio.app

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Empty Hilt-aware host Activity for Compose instrumented tests that need a real
 * `hiltViewModel()`-backed composition.
 *
 * This lives in the debug source set so ActivityScenario launches it in the target app process,
 * while release builds do not package a test-only Activity.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
