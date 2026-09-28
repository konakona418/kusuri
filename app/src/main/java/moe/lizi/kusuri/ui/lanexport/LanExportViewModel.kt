package moe.lizi.kusuri.ui.lanexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.lizi.kusuri.data.backup.BackupService
import moe.lizi.kusuri.data.backup.CsvLabels
import moe.lizi.kusuri.data.backup.CsvRange
import moe.lizi.kusuri.data.backup.window
import moe.lizi.kusuri.data.lan.LanExportClient
import moe.lizi.kusuri.data.lan.LanExportFailure
import moe.lizi.kusuri.data.lan.LanExportKind
import moe.lizi.kusuri.data.lan.LanExportParse
import moe.lizi.kusuri.data.lan.LanExportResult
import moe.lizi.kusuri.data.lan.LanExportTarget
import moe.lizi.kusuri.data.lan.lanExportBackupFileName
import moe.lizi.kusuri.data.lan.lanExportRecordsFileName
import moe.lizi.kusuri.data.lan.localIpv4Address
import moe.lizi.kusuri.data.lan.parseLanExportTarget

/** 局域网导出页的状态机(docs/lan-export.md §5)。 */
sealed interface LanExportPhase {
    /** 扫码中(相机不可用时,这里是"等待手输")。 */
    data object Scanning : LanExportPhase

    /** 扫到的东西不是我们的码,或手输格式不对。 */
    data class Unparsed(val reason: LanExportParse) : LanExportPhase

    /** 目标已确认,等用户点发送——绝不自动发送。 */
    data class Ready(val target: LanExportTarget) : LanExportPhase

    data class Sending(val target: LanExportTarget, val kind: LanExportKind) : LanExportPhase

    data class Sent(
        val target: LanExportTarget,
        val kind: LanExportKind,
        val savedPath: String,
        val bytes: Long,
        val sha256: String,
    ) : LanExportPhase

    data class Failed(
        val target: LanExportTarget?,
        val kind: LanExportKind?,
        val failure: LanExportFailure,
    ) : LanExportPhase
}

data class LanExportUiState(
    val phase: LanExportPhase = LanExportPhase.Scanning,
    val manualEntryVisible: Boolean = false,
    val manualInput: String = "",
    /** 手输解析失败的原因:留在输入框下说明,不把已输入的内容赶走。 */
    val manualError: LanExportParse? = null,
)

/**
 * 局域网导出的状态与动作。
 *
 * 刻意**不留任何持久状态**:脚本每次重启都换口令,"记住上次目标"没有意义
 * (docs/lan-export.md §5)。
 */
class LanExportViewModel(
    private val backupService: BackupService,
    private val client: LanExportClient,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(LanExportUiState())
    val state: StateFlow<LanExportUiState> = _state.asStateFlow()

    /** 只活在这一次页面里:重试时沿用用户原本选的 CSV 区间,不悄悄退回默认值。 */
    private var lastCsvRange: CsvRange = CsvRange.LAST_30_DAYS

    /** 扫码回调:相机解出一个码就调一次(相机随后释放)。 */
    fun onScanned(text: String) {
        applyParse(parsed = parseLanExportTarget(text, localIpv4Address()), fromManualEntry = false)
    }

    /** 手输的方式:相机不可用、或权限被拒时的兜底(与扫码共用同一个解析器)。 */
    fun showManualEntry() {
        _state.update { current ->
            when (current.phase) {
                is LanExportPhase.Scanning, is LanExportPhase.Unparsed ->
                    current.copy(phase = LanExportPhase.Scanning, manualEntryVisible = true)

                else -> current
            }
        }
    }

    fun hideManualEntry() {
        _state.update { it.copy(manualEntryVisible = false, manualError = null) }
    }

    fun onManualInputChange(value: String) {
        _state.update { it.copy(manualInput = value, manualError = null) }
    }

    /** 手输提交:成功就进"已确认目标",失败就在输入框下说明为什么。 */
    fun submitManualInput() {
        applyParse(
            parsed = parseLanExportTarget(_state.value.manualInput, localIpv4Address()),
            fromManualEntry = true,
        )
    }

    /** 回到扫码状态。也用于"重新扫码"(口令可能已经变了)。 */
    fun rescan() {
        _state.value = LanExportUiState()
    }

    fun send(kind: LanExportKind, labels: CsvLabels, range: CsvRange = CsvRange.LAST_30_DAYS) {
        val target = currentTarget() ?: return
        lastCsvRange = range
        _state.update { it.copy(phase = LanExportPhase.Sending(target, kind)) }
        viewModelScope.launch {
            val payload = runCatching { buildPayload(kind, labels, range) }
            val (fileName, content) = payload.getOrElse { error ->
                _state.update {
                    it.copy(
                        phase = LanExportPhase.Failed(
                            target = target,
                            kind = kind,
                            failure = LanExportFailure.LocalError(
                                error.message ?: (error::class.simpleName ?: "unknown"),
                            ),
                        ),
                    )
                }
                return@launch
            }

            val result = client.send(target, kind, fileName, content)
            _state.update { current ->
                current.copy(
                    phase = when (result) {
                        is LanExportResult.Delivered -> LanExportPhase.Sent(
                            target = target,
                            kind = kind,
                            savedPath = result.savedPath,
                            bytes = result.bytes,
                            sha256 = result.sha256,
                        )

                        is LanExportResult.Failed -> LanExportPhase.Failed(target, kind, result.failure)
                    },
                )
            }
        }
    }

    /** 失败后重试:沿用同一个目标与内容类型(口令没变就还能成功)。 */
    fun retry(labels: CsvLabels) {
        val phase = _state.value.phase as? LanExportPhase.Failed ?: return
        val target = phase.target ?: return
        val kind = phase.kind ?: return
        _state.update { it.copy(phase = LanExportPhase.Ready(target)) }
        send(kind, labels, lastCsvRange)
    }

    private fun applyParse(parsed: LanExportParse, fromManualEntry: Boolean) {
        when (parsed) {
            is LanExportParse.Parsed -> _state.update {
                it.copy(
                    phase = LanExportPhase.Ready(parsed.target),
                    manualEntryVisible = false,
                    manualError = null,
                )
            }

            else -> _state.update {
                if (fromManualEntry) {
                    it.copy(manualError = parsed)
                } else {
                    it.copy(phase = LanExportPhase.Unparsed(parsed))
                }
            }
        }
    }

    private fun currentTarget(): LanExportTarget? = when (val phase = _state.value.phase) {
        is LanExportPhase.Ready -> phase.target
        is LanExportPhase.Sent -> phase.target
        else -> null
    }

    /** 字节与 SAF 导出完全一致:同一个 `BackupService`,不做第二套序列化(契约 §3.1)。 */
    private suspend fun buildPayload(
        kind: LanExportKind,
        labels: CsvLabels,
        range: CsvRange,
    ): Pair<String, ByteArray> {
        val now = clock.instant()
        val zone = clock.zone
        return when (kind) {
            LanExportKind.JSON -> lanExportBackupFileName(now, zone) to
                backupService.exportJson().toByteArray(Charsets.UTF_8)

            LanExportKind.RECORDS -> {
                val (from, to) = range.window(now)
                lanExportRecordsFileName(from, to, zone) to
                    backupService.exportCsv(from, to, labels).toByteArray(Charsets.UTF_8)
            }
        }
    }
}
