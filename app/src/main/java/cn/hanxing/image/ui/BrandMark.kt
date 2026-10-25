package cn.hanxing.image.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 网页版品牌标：猫脸描边 + 右上四角星。
 * 路径与网页版 `BrandMark`（viewBox 0 0 32）逐点一致，线条跟随主题强调色，星星固定暖金。
 */
val BrandStarColor = Color(0xFFE6B04A)

private val CatFacePath: Path by lazy {
    PathParser().parsePathString(
        "M7.2 13.4 6.4 5.6c-.08-.78.8-1.28 1.42-.8l4.86 3.78a11.6 11.6 0 0 1 6.64 0l4.86-3.78" +
            "c.62-.48 1.5.02 1.42.8l-.8 7.8c.98 1.5 1.5 3.24 1.5 5.06 0 5.66-4.82 9.34-10.3 9.34" +
            "S5.7 24.12 5.7 18.46c0-1.82.52-3.56 1.5-5.06Z",
    ).toPath()
}

private val SmilePath: Path by lazy {
    PathParser().parsePathString("M14.9 21.1 16 22 17.1 21.1").toPath()
}

private val StarPath: Path by lazy {
    PathParser().parsePathString("M27.2 1.6 28.1 3.9 30.4 4.8 28.1 5.7 27.2 8 26.3 5.7 24 4.8 26.3 3.9Z")
        .toPath()
}

@Composable
fun BrandMark(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 32f
        scale(unit, unit, pivot = Offset.Zero) {
            drawPath(CatFacePath, color, style = Stroke(width = 1.9f, join = StrokeJoin.Round))
            drawCircle(color, radius = 1.25f, center = Offset(12.4f, 17.6f))
            drawCircle(color, radius = 1.25f, center = Offset(19.6f, 17.6f))
            drawPath(SmilePath, color, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(StarPath, BrandStarColor)
        }
    }
}
