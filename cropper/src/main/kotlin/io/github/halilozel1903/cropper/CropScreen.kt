package io.github.halilozel1903.cropper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.cropper.core.AspectRatio
import kotlin.math.cos
import kotlin.math.sin

/**
 * Texts of a [CropScreen], for localization.
 *
 * @param title Shown at the top.
 * @param cancel The button that leaves without cropping.
 * @param done The button that crops.
 * @param free The chip for a free (unlocked) aspect ratio.
 * @param rotate The rotate button.
 * @param reset The reset button.
 * @param imageDescription Describes the image for accessibility services.
 */
@Immutable
public class CropScreenLabels(
    public val title: String = "Crop",
    public val cancel: String = "Cancel",
    public val done: String = "Done",
    public val free: String = "Free",
    public val rotate: String = "Rotate",
    public val reset: String = "Reset",
    public val imageDescription: String = "Image to crop",
)

/** Defaults for [CropScreen]. */
public object CropScreenDefaults {
    /** Free, 1:1, 4:3, 3:4, 16:9, 9:16, 3:2 and 4:5. */
    public val AspectRatios: List<AspectRatio?> = AspectRatio.Presets
}

/**
 * A complete Material 3 crop screen: a top bar with Cancel and Done, the [ImageCropper], aspect
 * ratio chips and a rotate button. It pads itself out of the system bars.
 *
 * ```kotlin
 * CropScreen(
 *     bitmap = photo,
 *     onCrop = { cropped -> viewModel.saveAvatar(cropped) },
 *     onCancel = { navController.popBackStack() },
 *     state = rememberCropState(shape = CropShape.Circle),
 * )
 * ```
 *
 * @param bitmap The image to crop.
 * @param onCrop Called with the cropped bitmap when Done is pressed.
 * @param onCancel Called when Cancel is pressed.
 * @param modifier Modifier for the screen, which fills the space it gets.
 * @param state The crop state. Its shape decides whether the ratio chips are shown: a circle is always 1:1.
 * @param aspectRatios The ratio chips, `null` for free. Pass an empty list to hide them.
 * @param maxOutputSize Longest side of the cropped bitmap, 0 for the original resolution.
 * @param labels Texts for localization.
 * @param colors Colors of the cropper area.
 * @param showGrid Draws a rule of thirds grid inside the frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CropScreen(
    bitmap: ImageBitmap,
    onCrop: (ImageBitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    state: CropState = rememberCropState(),
    aspectRatios: List<AspectRatio?> = CropScreenDefaults.AspectRatios,
    maxOutputSize: Int = 0,
    labels: CropScreenLabels = CropScreenLabels(),
    colors: CropperColors = CropperDefaults.colors(background = MaterialTheme.colorScheme.surfaceContainerHighest),
    showGrid: Boolean = true,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(labels.cancel) }
            Text(
                text = labels.title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = { onCrop(state.crop(maxOutputSize)) }, enabled = state.isReady) {
                Text(labels.done)
            }
        }

        ImageCropper(
            state = state,
            bitmap = bitmap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = colors,
            showGrid = showGrid,
            contentDescription = labels.imageDescription,
        )

        if (state.shape == CropShape.Rectangle && aspectRatios.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (ratio in aspectRatios) {
                    FilterChip(
                        selected = state.aspectRatio == ratio,
                        onClick = { state.aspectRatio = ratio },
                        label = { Text(ratio?.toString() ?: labels.free) },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { state.rotateClockwise() },
                contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            ) {
                RotateIcon(LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text(labels.rotate)
            }
            TextButton(onClick = { state.reset() }) { Text(labels.reset) }
        }
    }
}

/** A clockwise arrow drawn with Canvas, so the library needs no icon dependency. */
@Composable
private fun RotateIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        val inset = size.width * 0.16f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val startAngle = -10f
        val sweepAngle = 280f
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        // Arrow head at the end of the arc (the top), pointing clockwise (to the right).
        val radius = arcSize.width / 2f
        val angle = Math.toRadians((startAngle + sweepAngle).toDouble())
        val radialX = cos(angle).toFloat()
        val radialY = sin(angle).toFloat()
        val tangentX = -radialY
        val tangentY = radialX
        val tip = Offset(center.x + radius * radialX, center.y + radius * radialY)
        val head = size.width * 0.3f
        val arrow = Path().apply {
            moveTo(tip.x + (-tangentX + radialX) * head * 0.7f, tip.y + (-tangentY + radialY) * head * 0.7f)
            lineTo(tip.x, tip.y)
            lineTo(tip.x + (-tangentX - radialX) * head * 0.7f, tip.y + (-tangentY - radialY) * head * 0.7f)
        }
        drawPath(arrow, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
