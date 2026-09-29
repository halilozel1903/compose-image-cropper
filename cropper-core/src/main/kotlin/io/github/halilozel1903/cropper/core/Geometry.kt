package io.github.halilozel1903.cropper.core

import kotlin.math.max
import kotlin.math.min

/**
 * An axis aligned rectangle in floating point coordinates. Used for view-space rectangles (in
 * pixels) and for normalized regions of an image, where `0..1` spans the whole image.
 */
public data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** `right - left`. */
    public val width: Float get() = right - left

    /** `bottom - top`. */
    public val height: Float get() = bottom - top

    /** Horizontal center. */
    public val centerX: Float get() = (left + right) / 2f

    /** Vertical center. */
    public val centerY: Float get() = (top + bottom) / 2f

    /** `true` when the rectangle has no area. */
    public val isEmpty: Boolean get() = width <= 0f || height <= 0f

    /** `width / height`, or `0` when the rectangle has no height. */
    public val aspectRatio: Float get() = if (height > 0f) width / height else 0f

    /** This rectangle moved by [dx] and [dy]. */
    public fun translate(dx: Float, dy: Float): CropRect = CropRect(left + dx, top + dy, right + dx, bottom + dy)

    /** This rectangle shrunk by [dx] on the left and right and by [dy] on the top and bottom. */
    public fun inset(dx: Float, dy: Float = dx): CropRect = CropRect(left + dx, top + dy, right - dx, bottom - dy)

    /** `true` when the point is inside or on the edge. */
    public fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** `true` when [other] lies inside this rectangle, allowing [tolerance] of rounding error. */
    public fun contains(other: CropRect, tolerance: Float = 0f): Boolean =
        other.left >= left - tolerance && other.top >= top - tolerance &&
            other.right <= right + tolerance && other.bottom <= bottom + tolerance

    /** The overlap of both rectangles; an empty rectangle when they do not overlap. */
    public fun intersect(other: CropRect): CropRect {
        val l = max(left, other.left)
        val t = max(top, other.top)
        val r = max(l, min(right, other.right))
        val b = max(t, min(bottom, other.bottom))
        return CropRect(l, t, r, b)
    }

    public companion object {
        /** The whole image as a normalized region. */
        public val Full: CropRect = CropRect(0f, 0f, 1f, 1f)

        /** A rectangle of the given size whose center is at ([centerX], [centerY]). */
        public fun centered(centerX: Float, centerY: Float, width: Float, height: Float): CropRect =
            CropRect(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)

        /** A rectangle from its top left corner and size. */
        public fun fromSize(left: Float, top: Float, width: Float, height: Float): CropRect =
            CropRect(left, top, left + width, top + height)
    }
}

/** A width and height in floating point, for example an image's size in pixels. */
public data class CropSize(val width: Float, val height: Float) {
    init {
        require(width >= 0f && height >= 0f) { "Size must not be negative, was ${width}x$height" }
    }

    /** `width / height`, or `0` when the height is zero. */
    public val aspectRatio: Float get() = if (height > 0f) width / height else 0f

    /** `true` when either side is zero. */
    public val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/** A rectangle of whole pixels inside a bitmap. */
public data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    /** `left + width`, exclusive. */
    public val right: Int get() = left + width

    /** `top + height`, exclusive. */
    public val bottom: Int get() = top + height
}

/** A size in whole pixels. */
public data class PixelSize(val width: Int, val height: Int) {
    init {
        require(width >= 0 && height >= 0) { "Size must not be negative, was ${width}x$height" }
    }

    /** The same size as floats. */
    public fun toCropSize(): CropSize = CropSize(width.toFloat(), height.toFloat())
}

/**
 * Where the (rotated) image is drawn in the view: it is scaled by [scale] view pixels per image
 * pixel and its top left corner is at ([offsetX], [offsetY]).
 */
public data class ImageTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    init {
        require(scale > 0f) { "scale must be positive, was $scale" }
    }

    /** The view-space rectangle the image of [imageSize] covers with this transform. */
    public fun imageRect(imageSize: CropSize): CropRect =
        CropRect.fromSize(offsetX, offsetY, imageSize.width * scale, imageSize.height * scale)

    /** Linear interpolation towards [target]; `fraction` 0 is this transform, 1 is [target]. */
    public fun lerp(target: ImageTransform, fraction: Float): ImageTransform = ImageTransform(
        scale = scale + (target.scale - scale) * fraction,
        offsetX = offsetX + (target.offsetX - offsetX) * fraction,
        offsetY = offsetY + (target.offsetY - offsetY) * fraction,
    )
}

/** A crop frame in view space and the image transform under it. */
public data class CropLayout(val cropRect: CropRect, val transform: ImageTransform)
