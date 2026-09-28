package moe.lizi.kusuri.domain.history

import moe.lizi.kusuri.domain.model.DoseStatus

data class AdherenceSummary(val taken: Int, val resolved: Int) {
    /** 按已判定的剂量(服用/跳过/错过)计算;暂无判定时为 null。 */
    val rate: Double? get() = if (resolved == 0) null else taken.toDouble() / resolved
}

/**
 * 遵守率:分子为已服用(含补记),分母为已判定剂量。
 * 尚未到判定时点的剂量(待服用/到时间了)不计入,避免白天把当天拉低。
 */
fun adherenceRate(statuses: List<DoseStatus>): AdherenceSummary {
    var taken = 0
    var resolved = 0
    statuses.forEach { status ->
        when (status) {
            is DoseStatus.Taken -> {
                taken++
                resolved++
            }

            DoseStatus.Skipped, DoseStatus.Missed -> resolved++
            DoseStatus.Pending, DoseStatus.Overdue -> Unit
        }
    }
    return AdherenceSummary(taken = taken, resolved = resolved)
}
