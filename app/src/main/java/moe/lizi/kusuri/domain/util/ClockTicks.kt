package moe.lizi.kusuri.domain.util

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 周期性发出当前时间,让界面中的派生状态(到时间了/错过)随时间刷新。 */
fun clockTicks(clock: Clock, intervalMillis: Long): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(intervalMillis)
    }
}
