package moe.lizi.kusuri.data.backup

import java.time.Duration
import java.time.Instant

/** CSV 导出的时间区间(docs/plan.md §8:CSV 按时间区间,给医生看)。 */
enum class CsvRange { LAST_30_DAYS, LAST_90_DAYS, ALL }

/**
 * 区间 → 绝对时间窗。
 *
 * SAF 导出与局域网导出共用这一份口径,免得同一个"近 30 天"在两处算出不同结果。
 */
fun CsvRange.window(now: Instant): Pair<Instant, Instant> {
    val from = when (this) {
        CsvRange.LAST_30_DAYS -> now.minus(Duration.ofDays(30))
        CsvRange.LAST_90_DAYS -> now.minus(Duration.ofDays(90))
        CsvRange.ALL -> Instant.EPOCH
    }
    return from to now
}
