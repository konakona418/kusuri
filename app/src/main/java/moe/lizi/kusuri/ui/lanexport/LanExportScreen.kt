package moe.lizi.kusuri.ui.lanexport

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import moe.lizi.kusuri.R
import moe.lizi.kusuri.data.backup.CsvRange
import moe.lizi.kusuri.data.lan.LanExportFailure
import moe.lizi.kusuri.data.lan.LanExportKind
import moe.lizi.kusuri.data.lan.LanExportParse
import moe.lizi.kusuri.data.lan.LanExportTarget
import moe.lizi.kusuri.data.lan.Reachability
import moe.lizi.kusuri.data.lan.SendStage
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.CsvRangeDialog
import moe.lizi.kusuri.ui.components.backupCsvLabels

/**
 * 局域网导出(docs/lan-export.md §5)。
 *
 * 两条纪律贯穿这个页面:**绝不自动发送**(扫到只是识别目标,必须点按钮),
 * 以及**屏幕无状态**(脚本每次重启换口令,记住上次目标没有意义)。
 */
@Composable
fun LanExportScreen(
    viewModel: LanExportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val labels = backupCsvLabels()
    var showCsvRangeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = LocalActivity.current
    var cameraGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    var permissionRequested by remember { mutableStateOf(false) }
    val permanentlyDenied = permissionRequested && !cameraGranted &&
        activity?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        permissionRequested = true
    }

    val sendJson = { viewModel.send(LanExportKind.JSON, labels) }
    val sendCsv = { showCsvRangeDialog = true }

    when (val phase = state.phase) {
        LanExportPhase.Scanning -> if (state.manualEntryVisible || !cameraGranted) {
            ManualEntryPanel(
                input = state.manualInput,
                error = state.manualError,
                cameraGranted = cameraGranted,
                permanentlyDenied = permanentlyDenied,
                onInputChange = viewModel::onManualInputChange,
                onSubmit = viewModel::submitManualInput,
                onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.startActivity(appDetailsIntent(context)) },
                onScan = viewModel::hideManualEntry,
            )
        } else {
            CameraPanel(
                onDecoded = viewModel::onScanned,
                onManualEntry = viewModel::showManualEntry,
            )
        }

        is LanExportPhase.Unparsed -> MessagePanel(
            title = stringResource(R.string.lan_export_unparsed_title),
            body = parseMessage(phase.reason),
            primaryLabel = stringResource(R.string.lan_export_rescan),
            onPrimary = viewModel::rescan,
            secondaryLabel = stringResource(R.string.lan_export_manual_entry),
            onSecondary = viewModel::showManualEntry,
        )

        is LanExportPhase.Ready -> TargetPanel(
            target = phase.target,
            onSendJson = sendJson,
            onSendCsv = sendCsv,
            onRescan = viewModel::rescan,
        )

        is LanExportPhase.Sending -> SendingPanel(phase)

        is LanExportPhase.Sent -> SentPanel(
            phase = phase,
            onSendJson = sendJson,
            onSendCsv = sendCsv,
            onRescan = viewModel::rescan,
        )

        is LanExportPhase.Failed -> MessagePanel(
            title = stringResource(R.string.lan_export_failed_title),
            body = failureMessage(phase.failure, phase.target),
            primaryLabel = if (phase.target != null && phase.kind != null) {
                stringResource(R.string.lan_export_retry)
            } else {
                stringResource(R.string.lan_export_rescan)
            },
            onPrimary = {
                if (phase.target != null && phase.kind != null) {
                    viewModel.retry(labels)
                } else {
                    viewModel.rescan()
                }
            },
            secondaryLabel = stringResource(R.string.lan_export_rescan),
            onSecondary = viewModel::rescan,
        )
    }

    if (showCsvRangeDialog) {
        CsvRangeDialog(
            onDismiss = { showCsvRangeDialog = false },
            onConfirm = { range ->
                showCsvRangeDialog = false
                viewModel.send(LanExportKind.RECORDS, labels, range)
            },
        )
    }
}

@Composable
private fun CameraPanel(onDecoded: (String) -> Unit, onManualEntry: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val controller = remember { LifecycleCameraController(context) }

    // 解出一个就定住:先让绿闪与震动被看见,再把目标交给状态机(相机随即释放)。
    var decoded by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(controller, lifecycleOwner) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisResolutionSelector(ANALYSIS_RESOLUTION)
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        controller.bindToLifecycle(lifecycleOwner)
        // 分析跑在相机线程,状态必须回到主线程写:直接跨线程写 Compose 状态有概率触发不了重组,
        // 表现就是"扫到了但没反应"。
        controller.setImageAnalysisAnalyzer(
            analyzerExecutor,
            QrAnalyzer { text -> mainExecutor.execute { if (decoded == null) decoded = text } },
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analyzerExecutor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        controller.cameraControl?.enableTorch(torchOn)
    }

    LaunchedEffect(decoded) {
        val text = decoded ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(DECODED_FLASH_MS)
        onDecoded(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
        )
        ScanFrameOverlay(decoded = decoded != null, modifier = Modifier.fillMaxSize())
        // 控制条自带底色:压暗层在它下面,文字用白色,不靠取景器画面提供对比度。
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.lan_export_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { torchOn = !torchOn }) {
                        Text(
                            text = stringResource(
                                if (torchOn) R.string.lan_export_torch_off else R.string.lan_export_torch_on,
                            ),
                            color = Color.White,
                        )
                    }
                    TextButton(onClick = onManualEntry) {
                        Text(
                            text = stringResource(R.string.lan_export_manual_entry),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualEntryPanel(
    input: String,
    error: LanExportParse?,
    cameraGranted: Boolean,
    permanentlyDenied: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!cameraGranted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lan_export_camera_denied_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.lan_export_camera_denied_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRequestPermission) {
                            Text(stringResource(R.string.lan_export_grant_camera))
                        }
                        if (permanentlyDenied) {
                            OutlinedButton(onClick = onOpenSettings) {
                                Text(stringResource(R.string.lan_export_open_settings))
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.lan_export_manual_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.lan_export_manual_label)) },
            placeholder = { Text(stringResource(R.string.lan_export_manual_placeholder)) },
            supportingText = error?.let { reason -> { Text(parseMessage(reason)) } },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSubmit) {
                Text(stringResource(R.string.lan_export_manual_submit))
            }
            if (cameraGranted) {
                TextButton(onClick = onScan) {
                    Text(stringResource(R.string.lan_export_rescan))
                }
            }
        }
        Text(
            text = stringResource(R.string.lan_export_qr_fallback),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetPanel(
    target: LanExportTarget,
    onSendJson: () -> Unit,
    onSendCsv: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.lan_export_target, target.display),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.lan_export_target_code, target.code),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSendJson, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lan_export_send_json))
        }
        OutlinedButton(onClick = onSendCsv, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lan_export_send_csv))
        }
        TextButton(onClick = onRescan) {
            Text(stringResource(R.string.lan_export_rescan))
        }
    }
}

@Composable
private fun SendingPanel(phase: LanExportPhase.Sending) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.lan_export_sending, phase.target.display),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(kindLabel(phase.kind)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SentPanel(
    phase: LanExportPhase.Sent,
    onSendJson: () -> Unit,
    onSendCsv: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.lan_export_sent_title, phase.target.display),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.lan_export_sent_saved, phase.savedPath),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.lan_export_sent_verified, formatBytes(phase.bytes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.lan_export_sent_sha, phase.sha256),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSendJson, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lan_export_send_json))
        }
        OutlinedButton(onClick = onSendCsv, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lan_export_send_csv))
        }
        TextButton(onClick = onRescan) {
            Text(stringResource(R.string.lan_export_rescan))
        }
    }
}

@Composable
private fun MessagePanel(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
private fun kindLabel(kind: LanExportKind): Int = when (kind) {
    LanExportKind.JSON -> R.string.lan_export_kind_json
    LanExportKind.RECORDS -> R.string.lan_export_kind_csv
}

@Composable
private fun parseMessage(reason: LanExportParse): String = when (reason) {
    LanExportParse.NotKusuri -> stringResource(R.string.lan_export_not_kusuri)
    LanExportParse.UnsupportedVersion -> stringResource(R.string.lan_export_unsupported_version)
    LanExportParse.NeedsLocalAddress -> stringResource(R.string.lan_export_needs_local_address)
    LanExportParse.Invalid -> stringResource(R.string.lan_export_invalid)
    is LanExportParse.Parsed -> ""
}

@Composable
private fun failureMessage(failure: LanExportFailure, target: LanExportTarget?): String {
    val display = target?.display ?: ""
    val port = target?.port ?: 0
    return when (failure) {
        is LanExportFailure.Unreachable -> when (failure.kind) {
            Reachability.REFUSED -> stringResource(R.string.lan_export_error_refused, display, port)
            Reachability.NO_ROUTE -> stringResource(R.string.lan_export_error_no_route, display)
            Reachability.UNKNOWN_HOST -> stringResource(R.string.lan_export_error_unknown_host)
            Reachability.DROPPED -> stringResource(R.string.lan_export_error_dropped)
            Reachability.OTHER -> stringResource(R.string.lan_export_error_other, display, failure.detail)
        }

        is LanExportFailure.Timeout -> when (failure.stage) {
            SendStage.CONNECT -> stringResource(R.string.lan_export_error_timeout_connect)
            SendStage.READ -> stringResource(R.string.lan_export_error_timeout_read)
        }

        LanExportFailure.BadCode -> stringResource(R.string.lan_export_error_bad_code)
        LanExportFailure.TooLarge -> stringResource(R.string.lan_export_error_too_large)
        is LanExportFailure.Rejected -> stringResource(
            R.string.lan_export_error_rejected,
            failure.status,
            failure.error.orEmpty(),
        )

        LanExportFailure.BadResponse -> stringResource(R.string.lan_export_error_bad_response)
        is LanExportFailure.IntegrityMismatch -> stringResource(R.string.lan_export_error_integrity)
        is LanExportFailure.LocalError -> stringResource(R.string.lan_export_error_local, failure.detail)
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun appDetailsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

/** 只用于展示的体量文案:单位本身不翻译,所以不进 strings.xml。 */
private fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/** 绿闪与震动先被看见,再切到"已识别目标"。 */
private const val DECODED_FLASH_MS = 220L

/**
 * 二维码显示在电脑屏幕上时,画面里的像素本来就不多。
 * 抬高分析分辨率,zxing 才有足够的分辨率可认(默认的 640×480 常常偏小)。
 */
private val ANALYSIS_RESOLUTION: ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            Size(1280, 960),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        ),
    )
    .build()
