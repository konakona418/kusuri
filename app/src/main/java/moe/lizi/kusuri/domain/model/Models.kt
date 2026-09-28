package moe.lizi.kusuri.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 餐时标签:纯展示标签,不参与调度计算。 */
enum class MealTag { NONE, BEFORE, AFTER, WITH }

enum class MedicationStatus { ACTIVE, COMPLETED, ARCHIVED }

enum class IntervalUnit { HOURS, DAYS }

enum class StockEventType { INITIAL, REFILL, ADJUST }

/** 调度模式。间隔制为固定锚点循环,不随实际服药滚动(docs/plan.md §3)。 */
sealed interface Schedule {
    data class DailyTimes(val times: List<LocalTime>) : Schedule

    data class Interval(
        val every: Int,
        val unit: IntervalUnit,
        val anchor: LocalDateTime,
    ) : Schedule

    data class Prn(
        val minIntervalMinutes: Int?,
        val maxPerDay: Int?,
    ) : Schedule
}

data class Medication(
    val id: Long = 0L,
    val name: String,
    val unit: String,
    val defaultDose: Double,
    val mealTag: MealTag,
    val notes: String?,
    val status: MedicationStatus,
    val createdAt: Instant,
    val courseStart: LocalDate,
    val courseEnd: LocalDate?,
    val schedule: Schedule,
    val lowStockThreshold: Double,
    val stockAlertArmed: Boolean,
    /** 由库存事件与服药记录派生,只读。 */
    val remainingStock: Double,
)
