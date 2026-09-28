package moe.lizi.kusuri.domain

/** 一次性清空全部业务数据(药物/记录/日志/库存)。设置等偏好不在其列。 */
interface DataWiper {
    suspend fun wipeAll()
}
