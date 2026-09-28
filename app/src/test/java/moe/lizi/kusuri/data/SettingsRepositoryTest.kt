package moe.lizi.kusuri.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import moe.lizi.kusuri.domain.model.ReminderLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repository() = SettingsRepository(context)

    private val prefs
        get() = context.getSharedPreferences("kusuri_settings", Context.MODE_PRIVATE)

    @Test
    fun `defaults are an audible reminder, two-hour grace and no onboarding`() {
        val settings = repository()

        assertEquals(ReminderLevel.DEFAULT, settings.reminderLevel.value)
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
    fun `the reminder level is persisted in the observed state`() {
        val settings = repository()

        settings.setReminderLevel(ReminderLevel.VIBRATE)
        settings.setOnboardingDone(true)

        assertEquals(ReminderLevel.VIBRATE, repository().reminderLevel.value)
        assertTrue(repository().onboardingDone.value)
    }

    @Test
    fun `the old on-off switch migrates to sound on and silent off`() {
        // 旧版存的是布尔值;开源→响亮,关→静默,并且不再留旧键。
        prefs.edit().putBoolean("reminder_sound", false).commit()
        assertEquals(ReminderLevel.SILENT, repository().reminderLevel.value)
        assertFalse(prefs.contains("reminder_sound"))
        assertEquals(ReminderLevel.SILENT, repository().reminderLevel.value)

        prefs.edit().clear().putBoolean("reminder_sound", true).commit()
        assertEquals(ReminderLevel.DEFAULT, repository().reminderLevel.value)
        assertFalse(prefs.contains("reminder_sound"))
    }

    @Test
    fun `the stored level wins over the legacy switch`() {
        prefs.edit()
            .putString("reminder_level", ReminderLevel.BANNER.name)
            .putBoolean("reminder_sound", false)
            .commit()

        assertEquals(ReminderLevel.BANNER, repository().reminderLevel.value)
    }
}
