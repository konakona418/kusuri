package moe.lizi.kusuri.alarm

import android.content.Intent
import android.net.Uri

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

/**
 * 通知动作的身份写在 Intent 的 **data** 里(而不是只靠 extras)。
 *
 * 实测小米 HyperOS:后台广播的 extras 会被系统剥成空(`Bundle[{STRIPPED=1}]`),
 * 只认 extras 的接收器会静默失效——闹钟响了、通知却没出来。动作按钮由 SystemUI 触发,
 * 同样按这个口径走;闹钟本身仍用请求码认人(它还要能取消,data 一变就取消不掉)。
 */
object ReminderUris {
    private const val SCHEME = "kusuri"
    private const val HOST_DOSE = "dose"
    private const val HOST_REMINDER = "reminder"

    /** `kusuri://dose/<药 id>/<计划时刻 millis>` */
    fun dose(medicationId: Long, scheduledMillis: Long): Uri =
        Uri.parse("$SCHEME://$HOST_DOSE/$medicationId/$scheduledMillis")

    /** `kusuri://reminder/<提醒 id>[/<发生时刻 millis>]`(动作只需要 id) */
    fun reminder(reminderId: Long, occurrenceMillis: Long? = null): Uri =
        if (occurrenceMillis == null) {
            Uri.parse("$SCHEME://$HOST_REMINDER/$reminderId")
        } else {
            Uri.parse("$SCHEME://$HOST_REMINDER/$reminderId/$occurrenceMillis")
        }

    /** 解析出 (id, 时刻 millis?);不是我们的 URI 就返回 null。 */
    fun parse(uri: Uri?): Pair<Long, Long?>? {
        if (uri == null || uri.scheme != SCHEME) return null
        if (uri.host != HOST_DOSE && uri.host != HOST_REMINDER) return null
        val id = uri.pathSegments.getOrNull(0)?.toLongOrNull() ?: return null
        return id to uri.pathSegments.getOrNull(1)?.toLongOrNull()
    }
}

/** 服药相关广播的身份:优先 data,退回 extras(闹钟一直走 extras)。 */
internal fun Intent.doseTarget(): Pair<Long, Long>? {
    ReminderUris.parse(data)?.let { (id, millis) ->
        if (id > 0L && millis != null && millis > 0L) return id to millis
    }
    val id = getLongExtra(ReminderExtras.MEDICATION_ID, -1L)
    val millis = getLongExtra(ReminderExtras.SCHEDULED_AT, -1L)
    return if (id > 0L && millis > 0L) id to millis else null
}

/** 提醒相关广播的身份:优先 data,退回 extras。 */
internal fun Intent.reminderTarget(): Pair<Long, Long?>? {
    ReminderUris.parse(data)?.let { return it }
    val id = getLongExtra(ReminderExtras.REMINDER_ID, -1L)
    if (id <= 0L) return null
    val millis = getLongExtra(ReminderExtras.OCCURRENCE_AT, -1L)
    return id to millis.takeIf { it > 0L }
}
