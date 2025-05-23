package com.littletrickster.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.opencv.core.Mat
import org.opencv.core.Point
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PointPreview(
    modifier: Modifier = Modifier,
    imageCaptureConfig: ImageCapture
) {
    var parentSize by remember { mutableStateOf(IntSize(1, 1)) }

    var imageWidth by remember { mutableStateOf(1) }
    var imageHeight by remember { mutableStateOf(1) }

    var points by remember { mutableStateOf(emptyList<Point>()) }

    val mScale = remember(parentSize, imageWidth, imageHeight) {
        min(
            parentSize.height.toFloat() / imageHeight.toFloat(),
            parentSize.width.toFloat() / imageWidth.toFloat()
        )
    }

    val scaledWidth = remember(mScale, imageWidth) { imageWidth * mScale }

    val scaledHeight = remember(mScale, imageHeight) { imageHeight * mScale }


    val verticalOffset =
        remember(scaledHeight, parentSize) { (parentSize.height - scaledHeight) / 2 }
    val horizontalOffset =
        remember(scaledWidth, parentSize) { (parentSize.width - scaledWidth) / 2 }



    Box(modifier = modifier
        .onSizeChanged { parentSize = it }) {

        val surfaceProvider = previewView(modifier = Modifier.fillMaxSize(),
            builder = {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            })

        ImageAnalyser(
            imageAnalysis = remember {
                ImageAnalysis.Builder().setResolutionSelector(defaultResolutionSelector())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
            },
            imageCapture = imageCaptureConfig,
            preview = remember {
                val preview: Preview = Preview.Builder()
                    .setResolutionSelector(defaultResolutionSelector())
                    .build()

                preview.setSurfaceProvider(surfaceProvider)
                preview
            },
            analyze = {
                val mat = it.yuvToMat()

                val resized = Mat()
                val scale = mat.resizeMax(resized, 300.0)
                mat.release()
                val foundPoints = getPoints(resized)
                foundPoints.rotate(
                    it.imageInfo.rotationDegrees,
                    Point(resized.width() / 2.0, resized.height() / 2.0)
                )

                resized.release()

                foundPoints *= scale


                imageWidth = it.rotatedWidth()
                imageHeight = it.rotatedHeight()
                points = foundPoints
            })

//        repeat(4) {
        //原来画的四个点
//            SimpleTargetCircle(
//                getOffset = { points.getOrNull(it)?.toOffset() },
//                horizontalOffset = horizontalOffset,
//                verticalOffset = verticalOffset,
//                scale = mScale
//            )
//        }
        //修改后画一个多边形
        PolygonRenderer(points,horizontalOffset,verticalOffset,mScale)

    }
}

@Composable
fun PolygonRenderer(
    points: List<Point>,
    horizontalOffset: Float,
    verticalOffset: Float,
    scale: Float,
    strokeColor: Color = Color.Red,
    strokeWidth: Float = 8f
) {
    // 校验坐标点数量（需4个点）
    if (points.size != 4) return



    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        // 创建闭合路径
        scale(scale = 1.05f, pivot = center) {
            val path = Path().apply {
                moveTo(
                    horizontalOffset + points[0].x.toFloat() * scale,
                    verticalOffset + points[0].y.toFloat() * scale
                )
                points.subList(1, 4).forEach { point ->
                    lineTo(
                        horizontalOffset + point.x.toFloat() * scale,
                        verticalOffset + point.y.toFloat() * scale
                    )
                }
                close()
            }
            // 绘制边框
            drawPath(path, strokeColor, style = Stroke(strokeWidth))
        }

    }
}

@Composable
private fun SimpleTargetCircle(
    getOffset: () -> Offset?,
    horizontalOffset: Float,
    verticalOffset: Float,
    scale: Float,
    circleColor: Color = Color.Green,
) {

    val offset = getOffset() ?: return


    val animatedOffset by animateOffsetAsState(
        targetValue = offset,
        animationSpec = offsetAnim
    )


    Box(
        Modifier
            .offset {
                IntOffset(
                    (horizontalOffset + animatedOffset.x * scale - 20.dp.toPx()).roundToInt(),
                    (verticalOffset + animatedOffset.y * scale - 20.dp.toPx()).roundToInt()
                )
            }

            .background(Color(255, 255, 255, 40), shape = CircleShape)
            .size(15.dp)
            .border(1.dp, circleColor, shape = CircleShape))
}
