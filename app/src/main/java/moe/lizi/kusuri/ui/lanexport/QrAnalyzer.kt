package moe.lizi.kusuri.ui.lanexport

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * 在相机帧里找二维码:纯 zxing,不引 Google 服务(docs/plan.md §13 M7.4)。
 *
 * 几个刻意的选择:
 *
 * - **整帧解码**:取景框只是引导,不裁剪。框小的时候"必须对准"最挫败,框外也能认反而更稳。
 * - **解出一个就停**(`paused`):界面马上切到"已识别目标"并释放相机,不持续烧电。
 * - **节流**:每帧都跑 zxing 没必要,间隔 [MIN_INTERVAL_MS] 试一次足够跟手。
 * - Y 平面就是亮度,直接喂 zxing(`rowStride` 可能大于宽,不能整块抄)。
 */
class QrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    @Volatile
    private var paused = false

    private var lastAttemptAt = 0L

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            if (paused) return
            val now = System.currentTimeMillis()
            if (now - lastAttemptAt < MIN_INTERVAL_MS) return
            lastAttemptAt = now

            val text = runCatching { decode(proxy) }.getOrNull() ?: return
            paused = true
            onDecoded(text)
        }
    }

    private fun decode(proxy: ImageProxy): String? {
        // 用 ImageProxy 自己的平面,不走实验性的 `proxy.image`:Y 平面就是亮度,够 zxing 用了。
        val plane = proxy.planes.firstOrNull() ?: return null
        val width = proxy.width
        val height = proxy.height
        if (width <= 0 || height <= 0) return null

        val luminance = plane.toLuminance(width, height)
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 120L
    }
}

private fun ImageProxy.PlaneProxy.toLuminance(width: Int, height: Int): ByteArray {
    val buffer = buffer
    val rowStride = rowStride
    val pixelStride = pixelStride
    val output = ByteArray(width * height)

    if (pixelStride == 1) {
        var index = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (column in 0 until width) {
                val source = rowStart + column
                output[index++] = if (source < buffer.limit()) buffer.get(source) else 0
            }
        }
        return output
    }

    var index = 0
    for (row in 0 until height) {
        val rowStart = row * rowStride
        for (column in 0 until width) {
            val source = rowStart + column * pixelStride
            output[index++] = if (source < buffer.limit()) buffer.get(source) else 0
        }
    }
    return output
}
