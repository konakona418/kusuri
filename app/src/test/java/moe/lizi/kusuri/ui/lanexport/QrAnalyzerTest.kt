package moe.lizi.kusuri.ui.lanexport

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.nio.ByteBuffer
import moe.lizi.kusuri.data.lan.LanExportParse
import moe.lizi.kusuri.data.lan.LanExportTarget
import moe.lizi.kusuri.data.lan.parseLanExportTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 解码这一层不需要真机:用 zxing 自己生成二维码,塞进伪造的相机帧,再看 [QrAnalyzer] 认不认。
 *
 * 顺带把契约串起来验一遍:脚本打印的 URI → 二维码 → 解码 → 解析成目标(host/port/口令),
 * 中间任何一环写歪了这里都会红。
 */
class QrAnalyzerTest {

    private val uri = "kusuri://lan-export/1?h=192.168.2.110&p=47821&c=K4F9M2"

    @Test
    fun `decodes the uri the receiver prints`() {
        var decoded: String? = null
        val analyzer = QrAnalyzer { decoded = it }

        analyzer.analyze(frameOf(uri, side = 480))

        assertEquals(uri, decoded)
    }

    @Test
    fun `decoded uri parses into the printed target`() {
        var decoded: String? = null
        val analyzer = QrAnalyzer { decoded = it }

        analyzer.analyze(frameOf(uri, side = 480))

        assertEquals(
            LanExportParse.Parsed(LanExportTarget(host = "192.168.2.110", port = 47821, code = "K4F9M2")),
            parseLanExportTarget(decoded.orEmpty(), localIpv4 = null),
        )
    }

    @Test
    fun `camera frames with padded rows still decode`() {
        // 真机的 Y 平面 rowStride 通常大于宽:整块抄会错位,这一条盯住逐行拷贝。
        var decoded: String? = null
        val analyzer = QrAnalyzer { decoded = it }

        analyzer.analyze(frameOf(uri, side = 480, rowPadding = 17))

        assertEquals(uri, decoded)
    }

    @Test
    fun `a frame without a qr code reports nothing`() {
        var decoded: String? = null
        val analyzer = QrAnalyzer { decoded = it }

        analyzer.analyze(blankFrame(side = 480))

        assertNull(decoded)
    }

    @Test
    fun `reports once and then stops looking`() {
        var calls = 0
        val analyzer = QrAnalyzer { calls += 1 }
        val frame = frameOf(uri, side = 480)

        analyzer.analyze(frame)
        analyzer.analyze(frame)

        assertEquals(1, calls)
    }

    private fun frameOf(text: String, side: Int, rowPadding: Int = 0): ImageProxy {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, side, side)
        val width = matrix.width
        val height = matrix.height
        val rowStride = width + rowPadding
        val buffer = ByteBuffer.allocate(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 黑模块 = 亮度 0,白底 = 255。
                buffer.put(y * rowStride + x, if (matrix.get(x, y)) 0 else 255.toByte())
            }
        }
        return FakeImageProxy(FakePlane(buffer, rowStrideValue = rowStride, pixelStrideValue = 1), width, height)
    }

    private fun blankFrame(side: Int): ImageProxy {
        val buffer = ByteBuffer.allocate(side * side)
        for (index in 0 until buffer.capacity()) {
            buffer.put(index, 255.toByte())
        }
        return FakeImageProxy(FakePlane(buffer, rowStrideValue = side, pixelStrideValue = 1), side, side)
    }
}

private class FakePlane(
    private val pixels: ByteBuffer,
    private val rowStrideValue: Int,
    private val pixelStrideValue: Int,
) : ImageProxy.PlaneProxy {
    override fun getBuffer(): ByteBuffer = pixels

    override fun getRowStride(): Int = rowStrideValue

    override fun getPixelStride(): Int = pixelStrideValue
}

/** 只实现 [QrAnalyzer] 真正会用到的成员;其余一律当作"不该被调用"。 */
private class FakeImageProxy(
    private val plane: ImageProxy.PlaneProxy,
    private val widthValue: Int,
    private val heightValue: Int,
) : ImageProxy {
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(plane)

    override fun getWidth(): Int = widthValue

    override fun getHeight(): Int = heightValue

    override fun getFormat(): Int = ImageFormat.YUV_420_888

    override fun close() = Unit

    override fun getImageInfo(): ImageInfo = throw UnsupportedOperationException("not used")

    override fun getImage(): android.media.Image = throw UnsupportedOperationException("not used")

    override fun getCropRect(): Rect = throw UnsupportedOperationException("not used")

    override fun setCropRect(rect: Rect?) = throw UnsupportedOperationException("not used")
}
