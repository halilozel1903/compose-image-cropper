package io.github.halilozel1903.cropper

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.halilozel1903.cropper.core.AspectRatio
import io.github.halilozel1903.cropper.core.CropHandle
import io.github.halilozel1903.cropper.core.CropLayout
import io.github.halilozel1903.cropper.core.CropMath
import io.github.halilozel1903.cropper.core.CropRect
import io.github.halilozel1903.cropper.core.CropRotation
import io.github.halilozel1903.cropper.core.CropSize
import io.github.halilozel1903.cropper.core.ImageTransform
import io.github.halilozel1903.cropper.core.PixelRect
import io.github.halilozel1903.cropper.core.PixelSize

/** The shape of the crop frame. */
public enum class CropShape {
    /** A rectangle, free or locked to an [AspectRatio]. */
    Rectangle,

    /** A circle, for avatars. Always 1:1; the corners of the cropped bitmap are transparent. */
    Circle,
}

/**
 * Creates and remembers a [CropState]. The frame, zoom, rotation, aspect ratio and shape survive
 * configuration changes and process death.
 *
 * The arguments are only the initial values; change [CropState.aspectRatio] and
 * [CropState.shape] on the state afterwards.
 *
 * @param aspectRatio Initial aspect ratio of the frame, `null` for a free crop.
 * @param shape Initial shape of the frame.
 * @param maxZoom How far the image can be zoomed in, relative to fitting the whole image in view.
 */
@Composable
public fun rememberCropState(
    aspectRatio: AspectRatio? = null,
    shape: CropShape = CropShape.Rectangle,
    maxZoom: Float = CropState.DefaultMaxZoom,
): CropState = rememberSaveable(saver = CropState.Saver) {
    CropState(aspectRatio, shape, maxZoom)
}

/**
 * State of an [ImageCropper]: where the crop frame is, how the image is zoomed, panned and rotated,
 * and the frame's aspect ratio and shape. Call [crop] to get the cropped bitmap.
 *
 * Everything is backed by snapshot state, so reading the properties in composition recomposes
 * when they change. The state is laid out once an [ImageCropper] using it is measured; until
 * then [isReady] is `false`, and calls such as [setCropRegion] or [rotateClockwise] are applied as
 * soon as it is.
 *
 * @param aspectRatio Initial aspect ratio of the frame, `null` for a free crop.
 * @param shape Initial shape of the frame.
 * @param maxZoom How far the image can be zoomed in, relative to fitting the whole image in view.
 */
@Stable
public class CropState(
    aspectRatio: AspectRatio? = null,
    shape: CropShape = CropShape.Rectangle,
    maxZoom: Float = DefaultMaxZoom,
) {
    init {
        require(maxZoom >= 1f) { "maxZoom must be at least 1, was $maxZoom" }
    }

    /** How far the image can be zoomed in, relative to fitting the whole image in view. */
    public val maxZoom: Float = maxZoom

    private var aspectRatioState: AspectRatio? by mutableStateOf<AspectRatio?>(aspectRatio)
    private var shapeState: CropShape by mutableStateOf<CropShape>(shape)
    private var rotationState: CropRotation by mutableStateOf<CropRotation>(CropRotation.Rotate0)
    private var layout: CropLayout? by mutableStateOf<CropLayout?>(null)
    private var bounds: CropRect? by mutableStateOf<CropRect?>(null)
    private var sourceSize: PixelSize? by mutableStateOf<PixelSize?>(null)

    // Applied at the next layout: set before the cropper is measured, or restored after process death.
    private var pendingRegion: CropRect? = null
    private var pendingFill: Boolean = false

    private var viewSize: IntSize = IntSize.Zero
    private var padding: Float = 0f

    internal var bitmap: ImageBitmap? = null
        private set

    /**
     * The aspect ratio of the frame, `null` for a free crop. Setting it lays out a new frame of
     * that ratio, as large as possible and centered on the image. Ignored while [shape] is
     * [CropShape.Circle], which is always 1:1.
     */
    public var aspectRatio: AspectRatio?
        get() = aspectRatioState
        set(value) {
            if (value == aspectRatioState) return
            val before = effectiveAspectRatio
            aspectRatioState = value
            if (effectiveAspectRatio != before) relayout(CropRect.Full, fill = false)
        }

    /** The shape of the frame. Switching to [CropShape.Circle] lays out a new 1:1 frame. */
    public var shape: CropShape
        get() = shapeState
        set(value) {
            if (value == shapeState) return
            val before = effectiveAspectRatio
            shapeState = value
            if (effectiveAspectRatio != before) relayout(CropRect.Full, fill = false)
        }

    /** The ratio the frame keeps: 1:1 for circles, otherwise [aspectRatio]. */
    public val effectiveAspectRatio: AspectRatio?
        get() = if (shapeState == CropShape.Circle) AspectRatio.Square else aspectRatioState

    /** The rotation of the image, in quarter turns. */
    public val rotation: CropRotation get() = rotationState

    /** `true` once the image and the cropper's size are known and the frame is laid out. */
    public val isReady: Boolean get() = layout != null

    /** The crop frame in the cropper's coordinates (pixels), or `null` before layout. */
    public val cropRect: Rect? get() = layout?.cropRect?.toRect()

    /** Where the rotated image is drawn in the cropper's coordinates, or `null` before layout. */
    public val imageRect: Rect?
        get() {
            val l = layout ?: return null
            return l.transform.imageRect(rotatedImageSize() ?: return null).toRect()
        }

    /** The image's scale relative to fitting the whole image in view: `1` fits, `2` is zoomed in 2x. */
    public val zoom: Float
        get() {
            val l = layout ?: return 1f
            val b = bounds ?: return 1f
            val image = rotatedImageSize() ?: return 1f
            return l.transform.scale / CropMath.containScale(b, image)
        }

    /**
     * The part of the (rotated) image inside the frame, normalized so `0..1` spans the image, or
     * `null` before layout.
     */
    public val cropRegion: CropRect?
        get() {
            val l = layout ?: return null
            return CropMath.regionOf(l.cropRect, l.transform, rotatedImageSize() ?: return null)
        }

    /**
     * The pixels of the original, unrotated bitmap under the frame, or `null` before layout. Use it
     * to crop a larger version of the image yourself, scaling it by the size difference.
     */
    public val sourceRect: PixelRect?
        get() {
            val l = layout ?: return null
            return CropMath.sourceRect(l.cropRect, l.transform, sourceSize ?: return null, rotationState)
        }

    /**
     * Shows the whole image with the frame on [region], a rectangle of the (rotated) image
     * normalized so `0..1` spans it. With an aspect ratio, the frame is the largest rectangle of
     * that ratio inside the region.
     */
    public fun setCropRegion(region: CropRect) {
        relayout(region, fill = false)
    }

    /** Turns the image 90 degrees clockwise. The frame stays on the same part of the image. */
    public fun rotateClockwise() {
        rotate(clockwise = true)
    }

    /** Turns the image 90 degrees counterclockwise. The frame stays on the same part of the image. */
    public fun rotateCounterClockwise() {
        rotate(clockwise = false)
    }

    /** Resets the rotation, the zoom and the frame. The aspect ratio and shape are kept. */
    public fun reset() {
        rotationState = CropRotation.Rotate0
        relayout(CropRect.Full, fill = false)
    }

    /** Zooms the image by [factor] about the center of the frame, within the allowed range. */
    public fun zoomBy(factor: Float) {
        val l = layout ?: return
        if (factor <= 0f) return
        transformBy(factor, Offset.Zero, Offset(l.cropRect.centerX, l.cropRect.centerY))
    }

    /**
     * Crops the image shown by the [ImageCropper] using this state.
     *
     * The result has the resolution of the original bitmap (scaled down so its longer side is at
     * most [maxSize] when that is greater than 0), is rotated like the preview, and has transparent
     * corners for [CropShape.Circle].
     *
     * This draws on the calling thread; for very large bitmaps call it from a background
     * dispatcher. The bitmap must be drawable in software, so decode it with hardware bitmaps
     * disabled (for example `ImageDecoder`'s `ALLOCATOR_SOFTWARE`).
     *
     * @throws IllegalStateException when no [ImageCropper] has laid this state out yet.
     */
    public fun crop(maxSize: Int = 0): ImageBitmap {
        val source = checkNotNull(bitmap) { "CropState is not attached to an ImageCropper yet" }
        val region = checkNotNull(sourceRect) { "CropState is not laid out yet" }
        return cropBitmap(source, region, rotationState, shapeState, maxSize)
    }

    // region Internal API used by ImageCropper

    internal fun onImage(image: ImageBitmap) {
        val previous = sourceSize
        val size = PixelSize(image.width, image.height)
        bitmap = image
        if (previous == size) return
        if (previous != null) {
            // A different image: start over.
            rotationState = CropRotation.Rotate0
            layout = null
            pendingRegion = null
            pendingFill = false
        }
        sourceSize = size
        relayout(pendingRegion ?: CropRect.Full, pendingFill)
    }

    internal fun onViewport(size: IntSize) {
        if (size == viewSize) return
        viewSize = size
        relayoutKeepingRegion()
    }

    internal fun onPadding(paddingPx: Float) {
        if (paddingPx == padding) return
        padding = paddingPx
        relayoutKeepingRegion()
    }

    internal fun handleAt(position: Offset, touchRadius: Float): CropHandle? {
        val l = layout ?: return null
        return CropMath.handleAt(l.cropRect, position.x, position.y, touchRadius)
    }

    internal fun currentCropRect(): CropRect? = layout?.cropRect

    /** Resizes the frame from [start] by the total drag ([dx], [dy]) of [handle]. */
    internal fun resize(start: CropRect, handle: CropHandle, dx: Float, dy: Float, minSize: Float) {
        val l = layout ?: return
        val b = bounds ?: return
        val image = rotatedImageSize() ?: return
        // The frame must stay in view and on the image, so the image keeps covering it.
        val limits = b.intersect(l.transform.imageRect(image))
        val rect = CropMath.resize(start, handle, dx, dy, limits, minSize, effectiveAspectRatio?.value)
        layout = CropLayout(rect, CropMath.clampTransform(l.transform, rect, image, maxScale(b, image)))
    }

    internal fun transformBy(zoom: Float, pan: Offset, centroid: Offset) {
        val l = layout ?: return
        val b = bounds ?: return
        val image = rotatedImageSize() ?: return
        val transform = CropMath.gesture(
            l.transform, zoom, pan.x, pan.y, centroid.x, centroid.y, l.cropRect, image, maxScale(b, image),
        )
        layout = l.copy(transform = transform)
    }

    internal suspend fun animateDoubleTap(position: Offset, animationSpec: AnimationSpec<Float> = tween<Float>(DoubleTapMillis)) {
        val l = layout ?: return
        val b = bounds ?: return
        val image = rotatedImageSize() ?: return
        val start = l.transform
        val target = CropMath.doubleTapTransform(start, position.x, position.y, l.cropRect, image, maxScale(b, image))
        animate(0f, 1f, animationSpec = animationSpec) { value, _ ->
            val current = layout
            // Stop following when the frame changed meanwhile (for example a new layout).
            if (current != null && current.cropRect == l.cropRect) {
                layout = current.copy(transform = start.lerp(target, value))
            }
        }
    }

    internal fun imageTransform(): ImageTransform? = layout?.transform

    // endregion

    private fun relayoutKeepingRegion() {
        val region = cropRegion
        if (region != null) {
            // Keep what the user framed: zoomed in stays zoomed on the region, otherwise the whole
            // image stays in view with the frame on the region.
            relayout(region, fill = isZoomedIn())
        } else {
            relayout(pendingRegion ?: CropRect.Full, pendingFill)
        }
    }

    private fun rotate(clockwise: Boolean) {
        val region = cropRegion ?: pendingRegion ?: CropRect.Full
        rotationState = if (clockwise) rotationState.rotatedClockwise() else rotationState.rotatedCounterClockwise()
        relayout(CropMath.rotateRegion(region, clockwise), fill = false)
    }

    private fun relayout(region: CropRect, fill: Boolean) {
        val b = bounds()
        val image = rotatedImageSize()
        if (b == null || image == null) {
            pendingRegion = region
            pendingFill = fill
            layout = null
            bounds = b
            return
        }
        bounds = b
        val next = CropMath.layoutForRegion(b, image, region, effectiveAspectRatio?.value, fill)
        layout = CropLayout(next.cropRect, CropMath.clampTransform(next.transform, next.cropRect, image, maxScale(b, image)))
        pendingRegion = null
        pendingFill = false
    }

    private fun bounds(): CropRect? {
        val rect = CropRect(padding, padding, viewSize.width - padding, viewSize.height - padding)
        return rect.takeUnless { it.isEmpty }
    }

    private fun rotatedImageSize(): CropSize? {
        val size = sourceSize ?: return null
        if (size.width == 0 || size.height == 0) return null
        return rotationState.rotate(size.toCropSize())
    }

    private fun isZoomedIn(): Boolean = zoom > 1.01f

    private fun maxScale(bounds: CropRect, image: CropSize): Float = CropMath.containScale(bounds, image) * maxZoom

    public companion object {
        /** The default [maxZoom]: 8x the scale that fits the whole image. */
        public const val DefaultMaxZoom: Float = 8f

        private const val DoubleTapMillis = 260

        /** Saves the aspect ratio, shape, rotation, zoom and the framed region. */
        public val Saver: Saver<CropState, Any> = listSaver<CropState, Any>(
            save = { state ->
                val ratio = state.aspectRatio
                val region = state.cropRegion ?: state.pendingRegion ?: CropRect.Full
                val fill = if (state.cropRegion != null) state.isZoomedIn() else state.pendingFill
                listOf<Any>(
                    ratio?.width ?: -1f,
                    ratio?.height ?: -1f,
                    state.shape.name,
                    state.rotation.name,
                    state.maxZoom,
                    region.left,
                    region.top,
                    region.right,
                    region.bottom,
                    fill,
                )
            },
            restore = { values ->
                val width = values[0] as Float
                val height = values[1] as Float
                CropState(
                    aspectRatio = if (width > 0f && height > 0f) AspectRatio(width, height) else null,
                    shape = CropShape.valueOf(values[2] as String),
                    maxZoom = values[4] as Float,
                ).apply {
                    rotationState = CropRotation.valueOf(values[3] as String)
                    pendingRegion = CropRect(values[5] as Float, values[6] as Float, values[7] as Float, values[8] as Float)
                    pendingFill = values[9] as Boolean
                }
            },
        )
    }
}

internal fun CropRect.toRect(): Rect = Rect(left, top, right, bottom)

/** Draws [region] of [source], rotated and scaled to its output size, into a new bitmap. */
internal fun cropBitmap(
    source: ImageBitmap,
    region: PixelRect,
    rotation: CropRotation,
    shape: CropShape,
    maxSize: Int,
): ImageBitmap {
    val out = CropMath.outputSize(region, rotation, maxSize)
    val result = ImageBitmap(out.width, out.height, ImageBitmapConfig.Argb8888, hasAlpha = true)
    val size = Size(out.width.toFloat(), out.height.toFloat())
    // Before rotating, the drawn image has the unrotated proportions.
    val drawWidth = if (rotation.isSideways) out.height else out.width
    val drawHeight = if (rotation.isSideways) out.width else out.height
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(result), size) {
        rotate(rotation.degrees.toFloat(), pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawImage(
                image = source,
                srcOffset = IntOffset(region.left, region.top),
                srcSize = IntSize(region.width, region.height),
                dstOffset = IntOffset((out.width - drawWidth) / 2, (out.height - drawHeight) / 2),
                dstSize = IntSize(drawWidth, drawHeight),
                filterQuality = FilterQuality.High,
            )
        }
        if (shape == CropShape.Circle) {
            // Clear everything outside the circle (anti-aliased, unlike clipPath).
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addOval(Rect(Offset.Zero, size))
            }
            drawPath(outside, Color.Black, blendMode = BlendMode.Clear)
        }
    }
    return result
}
