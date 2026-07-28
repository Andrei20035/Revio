package com.revio.social.core.ui.blur

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the navbar should use a real Haze blur, or fall back to a flat translucent fill.
 *
 * Gated at API 32, not 31 or 33: Haze's own defaults show why. Below API 31 there's no
 * `RenderEffect`, so Haze falls back to its experimental `RenderScriptBlurEffect`, which can
 * stall the main thread on its first frame and never recover on some older GPU drivers. API 31
 * has `RenderEffect` but Haze still forces a per-frame pre-draw invalidation listener there
 * (`invalidateOnHazeAreaPreDraw() = SDK_INT < 32`) to work around known RenderNode invalidation
 * issues on that exact API level. API 33 only adds a runtime-shader path Haze uses for
 * `progressive`/mask blurs, which this navbar doesn't use — so it buys nothing over 32 while
 * needlessly excluding Android 12L devices.
 *
 * Also excludes low-RAM devices, where Haze's default is already `true` but the blur is too
 * expensive to be worth it.
 */
@Composable
fun rememberNavBarBlurEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val isLowRamDevice = context.getSystemService(Context.ACTIVITY_SERVICE)
            ?.let { it as? ActivityManager }
            ?.isLowRamDevice == true
        Build.VERSION.SDK_INT >= 32 && !isLowRamDevice
    }
}
