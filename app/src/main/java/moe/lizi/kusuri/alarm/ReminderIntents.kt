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

    /** 通用提醒到点(排程闹钟)。 */
    const val ACTION_REMINDER_ALARM = "moe.lizi.kusuri.action.REMINDER_ALARM"

    /** 通用提醒"稍后"的再次通知。 */
    const val ACTION_REMINDER_SNOOZE = "moe.lizi.kusuri.action.REMINDER_SNOOZE"

    /** 通用提醒通知上的"知道了"。 */
    const val ACTION_REMINDER_ACK = "moe.lizi.kusuri.action.REMINDER_ACK"

    /** 通用提醒通知上的"稍后 15 分钟"。 */
    const val ACTION_REMINDER_SNOOZE_REQUEST = "moe.lizi.kusuri.action.REMINDER_SNOOZE_REQUEST"

    const val SNOOZE_MILLIS = 15 * 60 * 1000L
}

object ReminderExtras {
    const val MEDICATION_ID = "medicationId"
    const val SCHEDULED_AT = "scheduledAt"
    const val REMINDER_ID = "reminderId"
    const val OCCURRENCE_AT = "occurrenceAt"
}

/** 同一药物的提醒/稍后闹钟共用请求码;Intent action 保证两者相互区分。 */
internal fun reminderRequestCode(medicationId: Long): Int =
    (medicationId xor (medicationId ushr 32)).toInt()

/** 通用提醒的请求码另立命名空间,避免与药物 id 撞号(药物 id 也是 Long)。 */
internal fun reminderAlarmRequestCode(reminderId: Long): Int = "reminder:$reminderId".hashCode()
