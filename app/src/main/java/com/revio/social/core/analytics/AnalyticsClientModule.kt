package com.revio.social.core.analytics

import com.revio.social.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [NoOpAnalyticsClient] on debug builds (isolated from the real Firebase project, see
 * `docs/firebase-environments.md`), [FirebaseAnalyticsClient] otherwise. Same `BuildConfig.DEBUG`
 * pattern already used at `di/NetworkModule.kt` for the OkHttp logging level.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsClientModule {
    @Provides
    @Singleton
    fun provideAnalyticsClient(
        firebaseAnalyticsClient: FirebaseAnalyticsClient,
        noOpAnalyticsClient: NoOpAnalyticsClient,
    ): AnalyticsClient = if (BuildConfig.DEBUG) noOpAnalyticsClient else firebaseAnalyticsClient
}
