package io.github.halilozel1903.cropper

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.cropper.core.CropHandle
import io.github.halilozel1903.cropper.core.CropRect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Colors of an [ImageCropper].
 *
 * @param background Behind the image, where the image does not reach.
 * @param scrim Over the image outside the crop frame.
 * @param frame The frame's outline.
 * @param handles The corner and edge handles.
 * @param grid The rule of thirds grid inside the frame.
 */
@Immutable
public class CropperColors(
    public val background: Color,
    public val scrim: Color,
    public val frame: Color,
    public val handles: Color,
    public val grid: Color,
) {
    override fun equals(other: Any?): Boolean = other is CropperColors &&
        background == other.background && scrim == other.scrim && frame == other.frame &&
        handles == other.handles && grid == other.grid

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + scrim.hashCode()
        result = 31 * result + frame.hashCode()
        result = 31 * result + handles.hashCode()
        result = 31 * result + grid.hashCode()
        return result
    }
}

/** Defaults for [ImageCropper]. */
public object CropperDefaults {
    /** Space between the cropper's edges and the largest possible frame, so handles can be grabbed. */
    public val ContentPadding: Dp = 24.dp

    /** How far from a handle a touch still grabs it. */
    public val TouchRadius: Dp = 28.dp

    /** The smallest frame the handles can make. */
    public val MinCropSize: Dp = 48.dp

    /** The default colors: a dark scrim with a white frame, handles and grid. */
    public fun colors(
        background: Color = Color(0xFF0B0B0C),
        scrim: Color = Color.Black.copy(alpha = 0.6f),
        frame: Color = Color.White.copy(alpha = 0.9f),
        handles: Color = Color.White,
        grid: Color = Color.White.copy(alpha = 0.45f),
    ): CropperColors = CropperColors(background, scrim, frame, handles, grid)
}

/**
 * Shows [bitmap] with a crop frame over it.
 *
 * - Pinch to zoom and drag to pan the image; it always covers the frame.
 * - Double tap to zoom in around the tapped point, and again to zoom back out.
 * - Drag the frame's corners and edges to resize it, keeping [CropState.aspectRatio] if one is set.
 *
 * Call [CropState.crop] to get the cropped bitmap. For a complete screen with aspect ratio chips
 * and a rotate button, use [CropScreen].
 *
 * @param state The crop state, see [rememberCropState].
 * @param bitmap The image to crop. It must be drawable in software for [CropState.crop].
 * @param modifier Size the cropper with this; it fills the space it gets.
 * @param colors Colors of the background, scrim, frame, handles and grid.
 * @param showGrid Draws a rule of thirds grid inside the frame; it gets brighter while you drag.
 * @param contentPadding Space between the edges and the largest frame, so handles can be grabbed.
 * @param contentDescription Describes the image for accessibility services.
 */
@Composable
public fun ImageCropper(
    state: CropState,
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    colors: CropperColors = CropperDefaults.colors(),
    showGrid: Boolean = true,
    contentPadding: Dp = CropperDefaults.ContentPadding,
    contentDescription: String? = null,
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { contentPadding.toPx() }
    val touchRadius = with(density) { CropperDefaults.TouchRadius.toPx() }
    val minCropSize = with(density) { CropperDefaults.MinCropSize.toPx() }
    val scope = rememberCoroutineScope()
    val animation = remember { AnimationHolder() }
    var interacting by remember { mutableStateOf<Boolean>(false) }
    val gridAlpha by animateFloatAsState(if (interacting) 1f else 0.7f, label = "gridAlpha")

    SideEffect {
        state.onPadding(paddingPx)
        state.onImage(bitmap)
    }

    Canvas(
        modifier
            .clipToBounds()
            .background(colors.background)
            .onSizeChanged { state.onViewport(it) }
            .pointerInput(state, touchRadius, minCropSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    animation.job?.cancel()
                    val handle = state.handleAt(down.position, touchRadius)
                    val start = state.currentCropRect()
                    try {
                        if (handle != null && start != null) {
                            interacting = true
                            dragHandle(down.id, handle, start, down.position, state, minCropSize)
                        } else {
                            transformImage(state, viewConfiguration.touchSlop) { interacting = true }
                        }
                    } finally {
                        interacting = false
                    }
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        animation.job?.cancel()
                        animation.job = scope.launch { state.animateDoubleTap(position) }
                    },
                )
            }
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        drawImageLayer(state, bitmap)
        val crop = state.currentCropRect() ?: return@Canvas
        drawOverlay(crop.toRect(), state.shape, colors, showGrid, gridAlpha)
    }
}

private class AnimationHolder {
    var job: Job? = null
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragHandle(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    handle: CropHandle,
    start: CropRect,
    origin: Offset,
    state: CropState,
    minCropSize: Float,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
        if (!change.pressed) break
        if (change.positionChange() != Offset.Zero) {
            val total = change.position - origin
            state.resize(start, handle, total.x, total.y, minCropSize)
            change.consume()
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.transformImage(
    state: CropState,
    touchSlop: Float,
    onStart: () -> Unit,
) {
    var zoom = 1f
    var pan = Offset.Zero
    var pastSlop = false
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed }) break
        val zoomChange = event.calculateZoom()
        val panChange = event.calculatePan()
        if (!pastSlop) {
            zoom *= zoomChange
            pan += panChange
            val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
            if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) {
                pastSlop = true
                onStart()
            }
        }
        if (pastSlop) {
            val centroid = event.calculateCentroid(useCurrent = false)
            if (zoomChange != 1f || panChange != Offset.Zero) {
                state.transformBy(zoomChange, panChange, centroid)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        if (event.changes.none { it.pressed }) break
    }
}

private fun DrawScope.drawImageLayer(state: CropState, bitmap: ImageBitmap) {
    val transform = state.imageTransform() ?: return
    val sideways = state.rotation.isSideways
    val rotatedWidth = if (sideways) bitmap.height else bitmap.width
    val rotatedHeight = if (sideways) bitmap.width else bitmap.height
    val centerX = transform.offsetX + transform.scale * rotatedWidth / 2f
    val centerY = transform.offsetY + transform.scale * rotatedHeight / 2f
    withTransform({
        translate(centerX, centerY)
        rotate(state.rotation.degrees.toFloat(), pivot = Offset.Zero)
        scale(transform.scale, transform.scale, pivot = Offset.Zero)
        translate(-bitmap.width / 2f, -bitmap.height / 2f)
    }) {
        drawImage(bitmap)
    }
}

private fun DrawScope.drawOverlay(
    crop: Rect,
    shape: CropShape,
    colors: CropperColors,
    showGrid: Boolean,
    gridAlpha: Float,
) {
    val circle = shape == CropShape.Circle
    val hole = Path().apply { if (circle) addOval(crop) else addRect(crop) }
    val scrim = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        if (circle) addOval(crop) else addRect(crop)
    }
    drawPath(scrim, colors.scrim)

    val hairline = 1.dp.toPx()
    if (showGrid) {
        clipPath(hole) {
            for (i in 1..2) {
                val x = crop.left + crop.width * i / 3f
                val y = crop.top + crop.height * i / 3f
                drawLine(colors.grid, Offset(x, crop.top), Offset(x, crop.bottom), hairline, alpha = gridAlpha)
                drawLine(colors.grid, Offset(crop.left, y), Offset(crop.right, y), hairline, alpha = gridAlpha)
            }
        }
    }

    if (circle) {
        drawOval(colors.frame, crop.topLeft, crop.size, style = Stroke(1.5.dp.toPx()))
        // The bounding square shows where the handles are.
        drawRect(colors.frame.copy(alpha = colors.frame.alpha * 0.35f), crop.topLeft, crop.size, style = Stroke(hairline))
    } else {
        drawRect(colors.frame, crop.topLeft, crop.size, style = Stroke(hairline))
    }
    drawHandles(crop, colors.handles, edges = !circle)
}

private fun DrawScope.drawHandles(crop: Rect, color: Color, edges: Boolean) {
    val stroke = 3.dp.toPx()
    val length = min(20.dp.toPx(), min(crop.width, crop.height) / 3f)
    // Drawn half a stroke outside the frame so the frame line stays visible.
    val o = stroke / 2f
    val l = crop.left - o
    val t = crop.top - o
    val r = crop.right + o
    val b = crop.bottom + o
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke, cap = StrokeCap.Square)

    line(l, t, l + length, t); line(l, t, l, t + length)
    line(r, t, r - length, t); line(r, t, r, t + length)
    line(l, b, l + length, b); line(l, b, l, b - length)
    line(r, b, r - length, b); line(r, b, r, b - length)
    if (edges) {
        val cx = crop.center.x
        val cy = crop.center.y
        line(cx - length / 2f, t, cx + length / 2f, t)
        line(cx - length / 2f, b, cx + length / 2f, b)
        line(l, cy - length / 2f, l, cy + length / 2f)
        line(r, cy - length / 2f, r, cy + length / 2f)
    }
}
