package moe.lizi.kusuri.di

import android.content.Context
import java.time.Clock
import moe.lizi.kusuri.data.RoomMedicationRepository
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.domain.MedicationRepository

/** 手动依赖容器:单模块小应用不引入 Hilt(docs/plan.md §8)。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val clock: Clock = Clock.systemDefaultZone()

    private val database: KusuriDatabase by lazy { KusuriDatabase.build(appContext) }

    val medicationRepository: MedicationRepository by lazy {
        RoomMedicationRepository(database, clock)
    }
}
