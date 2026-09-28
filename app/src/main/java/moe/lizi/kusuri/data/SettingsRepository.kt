package moe.lizi.kusuri.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.lizi.kusuri.domain.model.DOSE_GRACE_PERIOD

/** 应用设置的本地存储(SharedPreferences);宽限窗口与首次向导状态需要被界面实时观察。 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _gracePeriodHours = MutableStateFlow(
        prefs.getInt(KEY_GRACE_HOURS, DEFAULT_GRACE_HOURS),
    )
    val gracePeriodHours: StateFlow<Int> = _gracePeriodHours.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _reminderSoundEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER_SOUND, false))
    val reminderSoundEnabled: StateFlow<Boolean> = _reminderSoundEnabled.asStateFlow()

    fun setGracePeriodHours(hours: Int) {
        val clamped = hours.coerceIn(MIN_GRACE_HOURS, MAX_GRACE_HOURS)
        prefs.edit().putInt(KEY_GRACE_HOURS, clamped).apply()
        _gracePeriodHours.value = clamped
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
        _onboardingDone.value = done
    }

    /** 提醒声音与震动:默认关闭,只显示通知(docs/plan.md §4.1)。 */
    fun setReminderSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_SOUND, enabled).apply()
        _reminderSoundEnabled.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "kusuri_settings"
        private const val KEY_GRACE_HOURS = "grace_period_hours"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_REMINDER_SOUND = "reminder_sound"

        val DEFAULT_GRACE_HOURS: Int = DOSE_GRACE_PERIOD.toHours().toInt()
        const val MIN_GRACE_HOURS = 1
        const val MAX_GRACE_HOURS = 12
    }
}
