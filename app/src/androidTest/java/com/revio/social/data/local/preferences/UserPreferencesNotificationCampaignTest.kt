package com.revio.social.data.local.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [UserPreferences.notificationCampaignV1Done] / [UserPreferences.setNotificationCampaignV1Done]
 * (step 1.1) are backed by DataStore, which needs a real Android [android.content.Context] —
 * hence instrumented, not a JVM unit test (same reasoning as
 * [UserPreferencesAnalyticsConsentTest]). Unlike that device-wide key, this one is keyed per
 * [UUID], so a freshly random id in each test reliably observes the key in its untouched
 * ("absent") state even while sharing a DataStore file with other instrumented tests.
 */
@RunWith(AndroidJUnit4::class)
class UserPreferencesNotificationCampaignTest {

    private val userPreferences = UserPreferences(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        json = Json,
    )

    @Test
    fun absentByDefault() = runBlocking {
        val userId = UUID.randomUUID()

        assertFalse(userPreferences.notificationCampaignV1Done(userId).first())
    }

    @Test
    fun trueAfterBeingMarkedDone() = runBlocking {
        val userId = UUID.randomUUID()

        userPreferences.setNotificationCampaignV1Done(userId)

        assertTrue(userPreferences.notificationCampaignV1Done(userId).first())
    }

    @Test
    fun isolatedPerUser() = runBlocking {
        val markedUserId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()

        userPreferences.setNotificationCampaignV1Done(markedUserId)

        assertTrue(userPreferences.notificationCampaignV1Done(markedUserId).first())
        assertFalse(userPreferences.notificationCampaignV1Done(otherUserId).first())
    }
}
