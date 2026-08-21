package com.revio.social.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Firebase's documented Crashlytics custom-log budget — see docs/telemetry-naming-and-forbidden-data.md. */
internal const val BREADCRUMB_BUDGET_BYTES = 64 * 1024

/**
 * Crash custom keys + breadcrumbs — context attached to Crashlytics for a *future* crash
 * report, not a metric or an event on its own. Keys are the fixed, closed set below (never a
 * caller-chosen key, so no identifier can slip in through a typo); values must still never be
 * an identifier — that discipline is the caller's, per docs/telemetry-naming-and-forbidden-data.md.
 *
 * Not wired into any flow yet — instrumenting auth/onboarding/post/feed with this is a later,
 * separate step.
 */
object CrashContext {

    /** The complete, closed set of custom keys this object will ever set. */
    val CUSTOM_KEYS: Set<String> = setOf("flow", "stage", "last_api_code", "is_offline", "build_type")

    private var breadcrumbBytesUsed = 0

    fun setFlow(value: String) = FirebaseCrashlytics.getInstance().setCustomKey("flow", value)

    fun setStage(value: String) = FirebaseCrashlytics.getInstance().setCustomKey("stage", value)

    fun setLastApiCode(value: String) = FirebaseCrashlytics.getInstance().setCustomKey("last_api_code", value)

    fun setOffline(value: Boolean) = FirebaseCrashlytics.getInstance().setCustomKey("is_offline", value)

    fun setBuildType(value: String) = FirebaseCrashlytics.getInstance().setCustomKey("build_type", value)

    /**
     * Drops the breadcrumb once [BREADCRUMB_BUDGET_BYTES] is exceeded for this process, rather
     * than silently relying on Crashlytics' own internal buffer to evict older entries.
     */
    fun breadcrumb(message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8).size
        if (breadcrumbBytesUsed + bytes > BREADCRUMB_BUDGET_BYTES) return
        breadcrumbBytesUsed += bytes
        FirebaseCrashlytics.getInstance().log(message)
    }

    /** Test-only: resets the breadcrumb budget counter between test cases. */
    internal fun resetBudgetForTests() {
        breadcrumbBytesUsed = 0
    }
}
