package io.github.halilozel1903.cropper.sample

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.halilozel1903.cropper.core.CropRect

/**
 * The sample's photos, drawn with Compose drawing commands into bitmaps so the app needs no image
 * assets and no network.
 */
object DemoImages {

    /** A 1600 x 1200 sunset over mountains and a lake, with hot air balloons. */
    fun landscape(): ImageBitmap = render(1600, 1200) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f

        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF241A5C),
                0.35f to Color(0xFF7A2E82),
                0.7f to Color(0xFFF0625A),
                1f to Color(0xFFFFC46B),
                startY = 0f,
                endY = horizon,
            ),
            size = Size(w, horizon),
        )
        // Stars in the dark part of the sky.
        val stars = listOf<Offset>(
            Offset(0.08f, 0.06f), Offset(0.21f, 0.12f), Offset(0.33f, 0.04f), Offset(0.47f, 0.10f),
            Offset(0.58f, 0.03f), Offset(0.74f, 0.08f), Offset(0.88f, 0.05f), Offset(0.95f, 0.14f),
            Offset(0.14f, 0.19f), Offset(0.66f, 0.16f),
        )
        for (star in stars) drawCircle(Color.White.copy(alpha = 0.8f), radius = 3.5f, center = Offset(star.x * w, star.y * h))

        // Sun with a glow.
        // High enough to sit above the far ridge, setting behind it.
        val sun = Offset(w * 0.66f, h * 0.35f)
        drawCircle(
            Brush.radialGradient(
                listOf<Color>(Color(0x88FFE3A0), Color(0x00FFE3A0)),
                center = sun,
                radius = w * 0.26f,
            ),
            radius = w * 0.26f,
            center = sun,
        )
        drawCircle(Color(0xFFFFE9A8), radius = w * 0.075f, center = sun)

        // Soft cloud bands.
        drawRoundRect(Color(0x55FFB3C7), Offset(w * 0.05f, h * 0.30f), Size(w * 0.42f, h * 0.035f), CornerRadius(40f))
        drawRoundRect(Color(0x44FFD0A0), Offset(w * 0.55f, h * 0.24f), Size(w * 0.36f, h * 0.03f), CornerRadius(40f))
        drawRoundRect(Color(0x33FFFFFF), Offset(w * 0.18f, h * 0.36f), Size(w * 0.28f, h * 0.022f), CornerRadius(40f))

        // Mountain layers, far to near.
        drawPath(
            ridge(w, horizon, listOf(0f to 0.47f, 0.12f to 0.36f, 0.24f to 0.44f, 0.38f to 0.30f, 0.52f to 0.42f, 0.66f to 0.40f, 0.8f to 0.33f, 0.92f to 0.41f, 1f to 0.38f), h),
            Color(0xFF8C4A9E),
        )
        drawPath(
            ridge(w, horizon, listOf(0f to 0.52f, 0.1f to 0.46f, 0.2f to 0.54f, 0.34f to 0.45f, 0.48f to 0.56f, 0.6f to 0.5f, 0.74f to 0.57f, 0.88f to 0.47f, 1f to 0.53f), h),
            Color(0xFF5C3483),
        )
        drawPath(
            ridge(w, horizon, listOf(0f to 0.58f, 0.15f to 0.55f, 0.3f to 0.6f, 0.45f to 0.57f, 0.6f to 0.61f, 0.8f to 0.56f, 1f to 0.6f), h),
            Color(0xFF3A2466),
        )

        // Lake with the sun's reflection.
        drawRect(
            Brush.verticalGradient(
                listOf<Color>(Color(0xFF4A2F78), Color(0xFF1B1542)),
                startY = horizon,
                endY = h,
            ),
            topLeft = Offset(0f, horizon),
            size = Size(w, h - horizon),
        )
        for (i in 0 until 9) {
            val y = horizon + h * 0.025f + i * h * 0.035f
            val half = w * (0.1f - i * 0.009f)
            drawLine(
                Color(0xFFFFC46B).copy(alpha = 0.75f - i * 0.07f),
                Offset(sun.x - half, y),
                Offset(sun.x + half, y),
                strokeWidth = h * 0.009f,
                cap = StrokeCap.Round,
            )
        }

        // Hot air balloons.
        balloon(Offset(w * 0.2f, h * 0.2f), w * 0.06f, listOf<Color>(Color(0xFFFF5A5F), Color(0xFFFFD166), Color(0xFF06D6A0)))
        balloon(Offset(w * 0.42f, h * 0.33f), w * 0.04f, listOf<Color>(Color(0xFF4CC9F0), Color(0xFFF72585), Color(0xFFFFFFFF)))
        balloon(Offset(w * 0.86f, h * 0.23f), w * 0.05f, listOf<Color>(Color(0xFFFFD166), Color(0xFF7209B7), Color(0xFFFF9F1C)))

        // Pines on the near shore.
        val shore = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.86f)
            quadraticTo(w * 0.16f, h * 0.82f, w * 0.32f, h * 0.9f)
            quadraticTo(w * 0.4f, h * 0.95f, w * 0.44f, h)
            close()
        }
        drawPath(shore, Color(0xFF120C2C))
        for ((x, height) in listOf(0.03f to 0.2f, 0.09f to 0.26f, 0.15f to 0.18f, 0.22f to 0.22f, 0.28f to 0.14f)) {
            pine(Offset(w * x, h * 0.9f), h * height, Color(0xFF120C2C))
        }
    }

    /** A 1200 x 1400 illustrated portrait, for the circle avatar crop. */
    fun avatar(): ImageBitmap = render(1200, 1400) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(listOf<Color>(Color(0xFF3DDBC3), Color(0xFF4E6CF2)), Offset.Zero, Offset(w, h)))
        drawCircle(Color(0x33FFFFFF), radius = w * 0.3f, center = Offset(w * 0.12f, h * 0.12f))
        drawCircle(Color(0x26FFFFFF), radius = w * 0.22f, center = Offset(w * 0.92f, h * 0.3f))
        drawCircle(Color(0x1FFFFFFF), radius = w * 0.35f, center = Offset(w * 0.85f, h * 0.95f))

        val face = Offset(w * 0.5f, h * 0.42f)
        val faceRadius = w * 0.19f
        val skin = Color(0xFFF2C29B)

        // Hair behind the head.
        drawCircle(Color(0xFF3B2416), radius = faceRadius * 1.22f, center = face - Offset(0f, faceRadius * 0.12f))
        // Shoulders and sweater.
        val sweater = Path().apply {
            moveTo(w * 0.1f, h)
            cubicTo(w * 0.12f, h * 0.76f, w * 0.3f, h * 0.7f, w * 0.5f, h * 0.7f)
            cubicTo(w * 0.7f, h * 0.7f, w * 0.88f, h * 0.76f, w * 0.9f, h)
            close()
        }
        drawRect(skin, Offset(face.x - faceRadius * 0.38f, face.y + faceRadius * 0.6f), Size(faceRadius * 0.76f, h * 0.14f))
        drawPath(sweater, Color(0xFFF4A340))
        drawArc(
            Color(0xFFE08A1E),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(face.x - faceRadius * 0.55f, h * 0.66f),
            size = Size(faceRadius * 1.1f, h * 0.08f),
            style = Stroke(w * 0.022f),
        )
        // Ears and head.
        drawCircle(skin, radius = faceRadius * 0.2f, center = Offset(face.x - faceRadius * 0.98f, face.y + faceRadius * 0.1f))
        drawCircle(skin, radius = faceRadius * 0.2f, center = Offset(face.x + faceRadius * 0.98f, face.y + faceRadius * 0.1f))
        drawOval(skin, Offset(face.x - faceRadius, face.y - faceRadius * 1.05f), Size(faceRadius * 2f, faceRadius * 2.2f))
        // Fringe.
        val fringe = Path().apply {
            moveTo(face.x - faceRadius * 1.02f, face.y - faceRadius * 0.1f)
            cubicTo(
                face.x - faceRadius * 1.05f, face.y - faceRadius * 1.3f,
                face.x + faceRadius * 0.9f, face.y - faceRadius * 1.45f,
                face.x + faceRadius * 1.02f, face.y - faceRadius * 0.2f,
            )
            quadraticTo(face.x + faceRadius * 0.2f, face.y - faceRadius * 0.95f, face.x - faceRadius * 1.02f, face.y - faceRadius * 0.1f)
            close()
        }
        drawPath(fringe, Color(0xFF3B2416))
        // Eyes, brows, cheeks and smile.
        val eyeY = face.y + faceRadius * 0.08f
        for (side in listOf(-1f, 1f)) {
            val eyeX = face.x + side * faceRadius * 0.38f
            drawCircle(Color(0xFF2B1B12), radius = faceRadius * 0.1f, center = Offset(eyeX, eyeY))
            drawCircle(Color.White, radius = faceRadius * 0.035f, center = Offset(eyeX + faceRadius * 0.03f, eyeY - faceRadius * 0.03f))
            drawLine(
                Color(0xFF3B2416),
                Offset(eyeX - faceRadius * 0.14f, eyeY - faceRadius * 0.24f),
                Offset(eyeX + faceRadius * 0.14f, eyeY - faceRadius * 0.28f),
                strokeWidth = faceRadius * 0.06f,
                cap = StrokeCap.Round,
            )
            drawCircle(Color(0x55FF6F91), radius = faceRadius * 0.13f, center = Offset(eyeX + side * faceRadius * 0.08f, eyeY + faceRadius * 0.3f))
        }
        drawArc(
            Color(0xFFB5534A),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(face.x - faceRadius * 0.3f, face.y + faceRadius * 0.2f),
            size = Size(faceRadius * 0.6f, faceRadius * 0.45f),
            style = Stroke(faceRadius * 0.07f, cap = StrokeCap.Round),
        )
    }

    /** The part of [avatar] around the face, normalized, for the circle scene. */
    val AvatarFace: CropRect = CropRect(0.2f, 0.19f, 0.8f, 0.7f)

    private fun render(width: Int, height: Int, block: DrawScope.() -> Unit): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            block()
        }
        return bitmap
    }

    private fun ridge(width: Float, base: Float, points: List<Pair<Float, Float>>, height: Float): Path = Path().apply {
        moveTo(0f, base)
        for ((x, y) in points) lineTo(x * width, y * height)
        lineTo(width, base)
        close()
    }

    private fun DrawScope.balloon(center: Offset, radius: Float, colors: List<Color>) {
        // Envelope in vertical stripes.
        val stripes = colors.size * 2
        for (i in 0 until stripes) {
            drawArc(
                colors[i % colors.size],
                startAngle = 180f + i * 180f / stripes,
                sweepAngle = 180f / stripes + 0.5f,
                useCenter = true,
                topLeft = center - Offset(radius, radius),
                size = Size(radius * 2f, radius * 2f),
            )
        }
        val bottom = Path().apply {
            moveTo(center.x - radius, center.y)
            quadraticTo(center.x - radius * 0.8f, center.y + radius * 0.9f, center.x - radius * 0.25f, center.y + radius * 1.25f)
            lineTo(center.x + radius * 0.25f, center.y + radius * 1.25f)
            quadraticTo(center.x + radius * 0.8f, center.y + radius * 0.9f, center.x + radius, center.y)
            close()
        }
        drawPath(bottom, colors[0])
        drawLine(Color(0xFF3A2466), Offset(center.x - radius * 0.22f, center.y + radius * 1.25f), Offset(center.x - radius * 0.16f, center.y + radius * 1.6f), radius * 0.04f)
        drawLine(Color(0xFF3A2466), Offset(center.x + radius * 0.22f, center.y + radius * 1.25f), Offset(center.x + radius * 0.16f, center.y + radius * 1.6f), radius * 0.04f)
        drawRect(
            Color(0xFF8B5A2B),
            topLeft = Offset(center.x - radius * 0.2f, center.y + radius * 1.6f),
            size = Size(radius * 0.4f, radius * 0.3f),
        )
    }

    private fun DrawScope.pine(base: Offset, height: Float, color: Color) {
        val tree = Path().apply {
            moveTo(base.x, base.y - height)
            lineTo(base.x + height * 0.28f, base.y)
            lineTo(base.x - height * 0.28f, base.y)
            close()
        }
        drawPath(tree, color)
    }
}
