package io.github.halilozel1903.cropper.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The math behind the cropper, free of any UI framework.
 *
 * Coordinates: the image is first rotated by a [CropRotation] (its size then is the "rotated
 * size"), then scaled and moved into the view by an [ImageTransform]. The crop frame is a
 * [CropRect] in view pixels. The image must always cover the crop frame, so every crop pixel
 * comes from the image.
 */
public object CropMath {

    /** Rounding slack, in view pixels, used when checking that the image covers the frame. */
    public const val Tolerance: Float = 0.01f

    /** The largest rectangle with [aspectRatio] (width / height) that fits [bounds], centered in it. */
    public fun fitRect(bounds: CropRect, aspectRatio: Float): CropRect {
        require(aspectRatio > 0f) { "aspectRatio must be positive, was $aspectRatio" }
        if (bounds.isEmpty) return CropRect.centered(bounds.centerX, bounds.centerY, 0f, 0f)
        return if (bounds.aspectRatio > aspectRatio) {
            CropRect.centered(bounds.centerX, bounds.centerY, bounds.height * aspectRatio, bounds.height)
        } else {
            CropRect.centered(bounds.centerX, bounds.centerY, bounds.width, bounds.width / aspectRatio)
        }
    }

    /** The scale at which an image of [imageSize] fits inside [bounds] completely. */
    public fun containScale(bounds: CropRect, imageSize: CropSize): Float {
        requireImage(imageSize)
        return min(bounds.width / imageSize.width, bounds.height / imageSize.height)
    }

    /** The smallest scale at which an image of [imageSize] can cover [cropRect]. */
    public fun minScale(cropRect: CropRect, imageSize: CropSize): Float {
        requireImage(imageSize)
        return max(cropRect.width / imageSize.width, cropRect.height / imageSize.height)
    }

    /** `true` when the image drawn with [transform] covers [cropRect]. */
    public fun covers(transform: ImageTransform, cropRect: CropRect, imageSize: CropSize): Boolean =
        transform.imageRect(imageSize).contains(cropRect, Tolerance)

    /**
     * Brings [transform] back into the allowed range: the scale between the scale that covers
     * [cropRect] and [maxScale] (zooming about the frame's center), then the offset so the image
     * covers the frame on every side.
     */
    public fun clampTransform(
        transform: ImageTransform,
        cropRect: CropRect,
        imageSize: CropSize,
        maxScale: Float = Float.MAX_VALUE,
    ): ImageTransform {
        val lowest = minScale(cropRect, imageSize)
        val scale = transform.scale.coerceIn(lowest, max(lowest, maxScale))
        var t = transform
        if (scale != transform.scale) {
            t = zoom(t, scale / transform.scale, cropRect.centerX, cropRect.centerY)
            t = ImageTransform(scale, t.offsetX, t.offsetY)
        }
        val offsetX = clampOffset(t.offsetX, cropRect.left, cropRect.right, imageSize.width * scale)
        val offsetY = clampOffset(t.offsetY, cropRect.top, cropRect.bottom, imageSize.height * scale)
        return ImageTransform(scale, offsetX, offsetY)
    }

    /** [transform] zoomed by [factor] about the view point ([focusX], [focusY]), not clamped. */
    public fun zoom(transform: ImageTransform, factor: Float, focusX: Float, focusY: Float): ImageTransform {
        require(factor > 0f) { "factor must be positive, was $factor" }
        return ImageTransform(
            scale = transform.scale * factor,
            offsetX = focusX - (focusX - transform.offsetX) * factor,
            offsetY = focusY - (focusY - transform.offsetY) * factor,
        )
    }

    /** [transform] moved by ([dx], [dy]) view pixels, not clamped. */
    public fun pan(transform: ImageTransform, dx: Float, dy: Float): ImageTransform =
        ImageTransform(transform.scale, transform.offsetX + dx, transform.offsetY + dy)

    /**
     * One step of a pinch or pan gesture: zoom by [zoom] about ([focusX], [focusY]), move by
     * ([panX], [panY]), then clamp so the image still covers [cropRect].
     */
    public fun gesture(
        transform: ImageTransform,
        zoom: Float,
        panX: Float,
        panY: Float,
        focusX: Float,
        focusY: Float,
        cropRect: CropRect,
        imageSize: CropSize,
        maxScale: Float = Float.MAX_VALUE,
    ): ImageTransform {
        val zoomed = zoom(transform, if (zoom > 0f) zoom else 1f, focusX, focusY)
        return clampTransform(pan(zoomed, panX, panY), cropRect, imageSize, maxScale)
    }

    /**
     * Where a double tap at ([x], [y]) should take the image: zoomed in by [zoomStep] around the
     * tapped point when the image is at (or near) its smallest scale, back to the smallest scale
     * otherwise.
     */
    public fun doubleTapTransform(
        transform: ImageTransform,
        x: Float,
        y: Float,
        cropRect: CropRect,
        imageSize: CropSize,
        maxScale: Float = Float.MAX_VALUE,
        zoomStep: Float = 2.5f,
    ): ImageTransform {
        val lowest = minScale(cropRect, imageSize)
        val zoomedIn = transform.scale > lowest * 1.05f
        val target = if (zoomedIn) lowest else min(lowest * zoomStep, max(lowest, maxScale))
        val focusX = if (zoomedIn) cropRect.centerX else x
        val focusY = if (zoomedIn) cropRect.centerY else y
        return clampTransform(
            zoom(transform, target / transform.scale, focusX, focusY), cropRect, imageSize, maxScale,
        )
    }

    /**
     * Resizes [rect] by dragging [handle] by ([dx], [dy]) from where it started.
     *
     * The result stays inside [bounds] (pass the overlap of the view and the image so the image
     * keeps covering the frame), keeps both sides at least [minSize] when the bounds allow it and,
     * when [aspectRatio] (width / height) is given, keeps that ratio: corners resize from the
     * opposite corner, edges resize around the frame's center line.
     */
    public fun resize(
        rect: CropRect,
        handle: CropHandle,
        dx: Float,
        dy: Float,
        bounds: CropRect,
        minSize: Float = 0f,
        aspectRatio: Float? = null,
    ): CropRect {
        val minSide = max(0f, min(minSize, min(bounds.width, bounds.height)))
        if (aspectRatio == null) return resizeFree(rect, handle, dx, dy, bounds, minSide)
        require(aspectRatio > 0f) { "aspectRatio must be positive, was $aspectRatio" }
        // Both sides must be at least minSide: width >= minSide and width / ratio >= minSide.
        val minWidth = max(minSide, minSide * aspectRatio)
        return when {
            handle.isCorner -> resizeCorner(rect, handle, dx, dy, bounds, minWidth, aspectRatio)
            handle.movesLeft || handle.movesRight -> {
                val anchor = if (handle.movesLeft) rect.right else rect.left
                val room = if (handle.movesLeft) anchor - bounds.left else bounds.right - anchor
                val verticalRoom = 2f * min(rect.centerY - bounds.top, bounds.bottom - rect.centerY)
                val maxWidth = min(room, verticalRoom * aspectRatio)
                val width = clamp(rect.width + if (handle.movesLeft) -dx else dx, minWidth, maxWidth)
                val left = if (handle.movesLeft) anchor - width else anchor
                CropRect.fromSize(left, rect.centerY - width / aspectRatio / 2f, width, width / aspectRatio)
            }
            else -> {
                val anchor = if (handle.movesTop) rect.bottom else rect.top
                val room = if (handle.movesTop) anchor - bounds.top else bounds.bottom - anchor
                val horizontalRoom = 2f * min(rect.centerX - bounds.left, bounds.right - rect.centerX)
                val maxHeight = min(room, horizontalRoom / aspectRatio)
                val height = clamp(rect.height + if (handle.movesTop) -dy else dy, minWidth / aspectRatio, maxHeight)
                val top = if (handle.movesTop) anchor - height else anchor
                CropRect.fromSize(rect.centerX - height * aspectRatio / 2f, top, height * aspectRatio, height)
            }
        }
    }

    /**
     * The handle under the point ([x], [y]), or `null`. A point within [touchRadius] of a corner
     * picks the nearest corner; otherwise a point within [touchRadius] of an edge (between the
     * corners) picks that edge.
     */
    public fun handleAt(rect: CropRect, x: Float, y: Float, touchRadius: Float): CropHandle? {
        val corners = listOf<Pair<CropHandle, Float>>(
            CropHandle.TopLeft to hypot(x - rect.left, y - rect.top),
            CropHandle.TopRight to hypot(x - rect.right, y - rect.top),
            CropHandle.BottomRight to hypot(x - rect.right, y - rect.bottom),
            CropHandle.BottomLeft to hypot(x - rect.left, y - rect.bottom),
        )
        val corner = corners.filter { it.second <= touchRadius }.minByOrNull { it.second }
        if (corner != null) return corner.first
        val withinX = x > rect.left && x < rect.right
        val withinY = y > rect.top && y < rect.bottom
        val edges = listOfNotNull<Pair<CropHandle, Float>>(
            (CropHandle.Top to abs(y - rect.top)).takeIf { withinX },
            (CropHandle.Bottom to abs(y - rect.bottom)).takeIf { withinX },
            (CropHandle.Left to abs(x - rect.left)).takeIf { withinY },
            (CropHandle.Right to abs(x - rect.right)).takeIf { withinY },
        )
        return edges.filter { it.second <= touchRadius }.minByOrNull { it.second }?.first
    }

    /**
     * Lays out a crop of [region] (normalized to the rotated image of [imageSize]) in [bounds].
     *
     * With [fill] `false` the whole image fits [bounds] and the frame sits on the region, so the
     * rest of the image is visible around it. With [fill] `true` the region is zoomed so the
     * frame is as large as [bounds] allow. When [aspectRatio] is given, the frame is the largest
     * rectangle of that ratio inside the region, centered on it.
     */
    public fun layoutForRegion(
        bounds: CropRect,
        imageSize: CropSize,
        region: CropRect = CropRect.Full,
        aspectRatio: Float? = null,
        fill: Boolean = false,
    ): CropLayout {
        requireImage(imageSize)
        require(!bounds.isEmpty) { "bounds must not be empty, was $bounds" }
        val normalized = region.intersect(CropRect.Full).takeUnless { it.isEmpty } ?: CropRect.Full
        return if (!fill) {
            val scale = containScale(bounds, imageSize)
            val transform = ImageTransform(
                scale = scale,
                offsetX = bounds.centerX - imageSize.width * scale / 2f,
                offsetY = bounds.centerY - imageSize.height * scale / 2f,
            )
            var crop = regionToView(normalized, transform, imageSize)
            if (aspectRatio != null) crop = fitRect(crop, aspectRatio)
            CropLayout(crop, transform)
        } else {
            var pixels = CropRect(
                normalized.left * imageSize.width,
                normalized.top * imageSize.height,
                normalized.right * imageSize.width,
                normalized.bottom * imageSize.height,
            )
            if (aspectRatio != null) pixels = fitRect(pixels, aspectRatio)
            val crop = fitRect(bounds, pixels.aspectRatio)
            val scale = crop.width / pixels.width
            val transform = ImageTransform(scale, crop.left - pixels.left * scale, crop.top - pixels.top * scale)
            CropLayout(crop, clampTransform(transform, crop, imageSize))
        }
    }

    /** The part of the rotated image under [cropRect], normalized to `0..1`. */
    public fun regionOf(cropRect: CropRect, transform: ImageTransform, imageSize: CropSize): CropRect {
        requireImage(imageSize)
        val w = imageSize.width * transform.scale
        val h = imageSize.height * transform.scale
        return CropRect(
            (cropRect.left - transform.offsetX) / w,
            (cropRect.top - transform.offsetY) / h,
            (cropRect.right - transform.offsetX) / w,
            (cropRect.bottom - transform.offsetY) / h,
        ).intersect(CropRect.Full)
    }

    /** The view rectangle where the normalized [region] of the rotated image is drawn. */
    public fun regionToView(region: CropRect, transform: ImageTransform, imageSize: CropSize): CropRect {
        val w = imageSize.width * transform.scale
        val h = imageSize.height * transform.scale
        return CropRect(
            transform.offsetX + region.left * w,
            transform.offsetY + region.top * h,
            transform.offsetX + region.right * w,
            transform.offsetY + region.bottom * h,
        )
    }

    /**
     * A normalized region of the rotated image, turned along with the image by a further 90
     * degrees clockwise (or counterclockwise).
     */
    public fun rotateRegion(region: CropRect, clockwise: Boolean): CropRect = if (clockwise) {
        CropRect(1f - region.bottom, region.left, 1f - region.top, region.right)
    } else {
        CropRect(region.top, 1f - region.right, region.bottom, 1f - region.left)
    }

    /**
     * The pixels of the unrotated source bitmap of [sourceSize] under [cropRect], when the image is
     * rotated by [rotation] and drawn with [transform]. Always at least one pixel, and inside the
     * bitmap.
     */
    public fun sourceRect(
        cropRect: CropRect,
        transform: ImageTransform,
        sourceSize: PixelSize,
        rotation: CropRotation = CropRotation.Rotate0,
    ): PixelRect {
        require(sourceSize.width > 0 && sourceSize.height > 0) { "sourceSize must not be empty, was $sourceSize" }
        val rotatedSize = rotation.rotate(sourceSize.toCropSize())
        val inRotated = CropRect(
            (cropRect.left - transform.offsetX) / transform.scale,
            (cropRect.top - transform.offsetY) / transform.scale,
            (cropRect.right - transform.offsetX) / transform.scale,
            (cropRect.bottom - transform.offsetY) / transform.scale,
        )
        val inSource = rotation.toSource(inRotated, rotatedSize)
        val left = inSource.left.roundToInt().coerceIn(0, sourceSize.width - 1)
        val top = inSource.top.roundToInt().coerceIn(0, sourceSize.height - 1)
        val right = inSource.right.roundToInt().coerceIn(left + 1, sourceSize.width)
        val bottom = inSource.bottom.roundToInt().coerceIn(top + 1, sourceSize.height)
        return PixelRect(left, top, right - left, bottom - top)
    }

    /**
     * The size of the cropped bitmap: the source rectangle's size, turned by [rotation] and scaled
     * down (keeping its ratio) so the longer side is at most [maxSize]. `maxSize` 0 means no limit.
     */
    public fun outputSize(sourceRect: PixelRect, rotation: CropRotation = CropRotation.Rotate0, maxSize: Int = 0): PixelSize {
        require(maxSize >= 0) { "maxSize must not be negative, was $maxSize" }
        val size = rotation.rotate(PixelSize(sourceRect.width, sourceRect.height))
        val longest = max(size.width, size.height)
        if (maxSize == 0 || longest <= maxSize) return size
        val factor = maxSize.toFloat() / longest
        return PixelSize(
            (size.width * factor).roundToInt().coerceIn(1, maxSize),
            (size.height * factor).roundToInt().coerceIn(1, maxSize),
        )
    }

    private fun resizeFree(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, bounds: CropRect, minSide: Float): CropRect {
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        if (handle.movesLeft) left = clampLow(left + dx, bounds.left, right - minSide)
        if (handle.movesRight) right = clampHigh(right + dx, left + minSide, bounds.right)
        if (handle.movesTop) top = clampLow(top + dy, bounds.top, bottom - minSide)
        if (handle.movesBottom) bottom = clampHigh(bottom + dy, top + minSide, bounds.bottom)
        return CropRect(left, top, right, bottom)
    }

    private fun resizeCorner(
        rect: CropRect,
        handle: CropHandle,
        dx: Float,
        dy: Float,
        bounds: CropRect,
        minWidth: Float,
        aspectRatio: Float,
    ): CropRect {
        val anchorX = if (handle.movesLeft) rect.right else rect.left
        val anchorY = if (handle.movesTop) rect.bottom else rect.top
        val widthChange = if (handle.movesLeft) -dx else dx
        // The vertical drag expressed as a width change; the larger of the two wins.
        val heightChange = (if (handle.movesTop) -dy else dy) * aspectRatio
        val proposed = rect.width + if (abs(widthChange) >= abs(heightChange)) widthChange else heightChange
        val roomX = if (handle.movesLeft) anchorX - bounds.left else bounds.right - anchorX
        val roomY = if (handle.movesTop) anchorY - bounds.top else bounds.bottom - anchorY
        val width = clamp(proposed, minWidth, min(roomX, roomY * aspectRatio))
        val height = width / aspectRatio
        return CropRect.fromSize(
            if (handle.movesLeft) anchorX - width else anchorX,
            if (handle.movesTop) anchorY - height else anchorY,
            width,
            height,
        )
    }

    // When the range is empty the upper limit (the room left) wins over the minimum size.
    private fun clamp(value: Float, low: Float, high: Float): Float = if (low > high) high else value.coerceIn(low, high)

    // For a left/top edge: `low` is the bounds, `high` keeps the minimum size. Bounds win.
    private fun clampLow(value: Float, low: Float, high: Float): Float = if (low > high) low else value.coerceIn(low, high)

    // For a right/bottom edge: `low` keeps the minimum size, `high` is the bounds. Bounds win.
    private fun clampHigh(value: Float, low: Float, high: Float): Float = if (low > high) high else value.coerceIn(low, high)

    private fun clampOffset(offset: Float, cropStart: Float, cropEnd: Float, imageExtent: Float): Float {
        val low = cropEnd - imageExtent
        val high = cropStart
        return if (low > high) (low + high) / 2f else offset.coerceIn(low, high)
    }

    private fun requireImage(imageSize: CropSize) {
        require(!imageSize.isEmpty) { "imageSize must not be empty, was $imageSize" }
    }
}
