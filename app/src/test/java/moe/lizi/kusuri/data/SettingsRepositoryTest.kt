package moe.lizi.kusuri.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private fun repository() = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `defaults are quiet notifications, two-hour grace and no onboarding`() {
        val settings = repository()

        assertFalse(settings.reminderSoundEnabled.value)
        assertEquals(2, settings.gracePeriodHours.value)
        assertFalse(settings.onboardingDone.value)
    }

    @Test
    fun `grace period is clamped to the allowed range`() {
        val settings = repository()

        settings.setGracePeriodHours(0)
        assertEquals(SettingsRepository.MIN_GRACE_HOURS, settings.gracePeriodHours.value)

        settings.setGracePeriodHours(99)
        assertEquals(SettingsRepository.MAX_GRACE_HOURS, settings.gracePeriodHours.value)
    }

    @Test
    fun `toggles are persisted in the observed state`() {
        val settings = repository()

        settings.setReminderSoundEnabled(true)
        settings.setOnboardingDone(true)

        assertTrue(settings.reminderSoundEnabled.value)
        assertTrue(settings.onboardingDone.value)
    }
}
