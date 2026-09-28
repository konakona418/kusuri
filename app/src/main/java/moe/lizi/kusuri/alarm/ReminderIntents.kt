package moe.lizi.kusuri.alarm

object ReminderActions {
    /** 到点提醒(排程闹钟)。 */
    const val ACTION_DOSE_REMINDER = "moe.lizi.kusuri.action.DOSE_REMINDER"

    /** 稍后提醒的再次通知。 */
    const val ACTION_DOSE_SNOOZE = "moe.lizi.kusuri.action.DOSE_SNOOZE"

    const val ACTION_DOSE_TAKEN = "moe.lizi.kusuri.action.DOSE_TAKEN"
    const val ACTION_DOSE_SKIP = "moe.lizi.kusuri.action.DOSE_SKIP"

    /** 通知上的"稍后"按钮。 */
    const val ACTION_DOSE_SNOOZE_REQUEST = "moe.lizi.kusuri.action.DOSE_SNOOZE_REQUEST"

    const val SNOOZE_MILLIS = 15 * 60 * 1000L
}

object ReminderExtras {
    const val MEDICATION_ID = "medicationId"
    const val SCHEDULED_AT = "scheduledAt"
}

/** 同一药物的提醒/稍后闹钟共用请求码;Intent action 保证两者相互区分。 */
internal fun reminderRequestCode(medicationId: Long): Int =
    (medicationId xor (medicationId ushr 32)).toInt()
