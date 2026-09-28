package moe.lizi.kusuri.ui.lanexport

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 自绘取景框:压暗层中央掏一个方孔 + 四角标;识别成功时整框闪一下绿([decoded])。
 *
 * 孔只是引导,解码用的是整帧(见 [QrAnalyzer]),所以码放在框边上也能认。
 */
@Composable
fun ScanFrameOverlay(decoded: Boolean, modifier: Modifier = Modifier) {
    val accent = if (decoded) SuccessFlash else MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = if (decoded) 0.35f else 0.55f)

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height) * FRAME_FRACTION
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val hole = Rect(left = left, top = top, right = left + side, bottom = top + side)
        val corner = side * CORNER_FRACTION
        val stroke = 4.dp.toPx()

        val scrimPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(hole, CornerRadius(20.dp.toPx())))
        }
        drawPath(scrimPath, scrim)

        drawCorner(hole.left, hole.top, 1f, 1f, corner, stroke, accent)
        drawCorner(hole.right, hole.top, -1f, 1f, corner, stroke, accent)
        drawCorner(hole.left, hole.bottom, 1f, -1f, corner, stroke, accent)
        drawCorner(hole.right, hole.bottom, -1f, -1f, corner, stroke, accent)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCorner(
    x: Float,
    y: Float,
    horizontal: Float,
    vertical: Float,
    corner: Float,
    stroke: Float,
    color: Color,
) {
    val path = Path().apply {
        moveTo(x + horizontal * corner, y)
        lineTo(x, y)
        lineTo(x, y + vertical * corner)
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

private const val FRAME_FRACTION = 0.72f
private const val CORNER_FRACTION = 0.22f

/** 取景框"对上了"的一瞬用绿闪——相机类界面的通行信号,不进主题色板。 */
private val SuccessFlash = Color(0xFF35C759)
