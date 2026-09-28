package moe.lizi.kusuri.domain

import kotlinx.coroutines.flow.first

/**
 * 删除全部数据(危险操作,调用方必须做双重确认):
 * 先逐味药撤掉提醒与通知,再清空数据库,避免留下"幽灵闹钟"。
 */
class WipeAllDataUseCase(
    private val medicationRepository: MedicationRepository,
    private val reminderControl: DoseReminderControl,
    private val dataWiper: DataWiper,
) {

    suspend fun wipe() {
        medicationRepository.observeMedications().first().forEach { medication ->
            reminderControl.cancelAllFor(medication.id)
        }
        dataWiper.wipeAll()
    }
}
