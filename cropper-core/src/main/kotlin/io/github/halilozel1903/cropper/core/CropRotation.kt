package io.github.halilozel1903.cropper.core

/** Rotation of the image in quarter turns, clockwise. */
public enum class CropRotation(public val degrees: Int) {
    Rotate0(0),
    Rotate90(90),
    Rotate180(180),
    Rotate270(270),
    ;

    /** `true` for 90 and 270 degrees, where the image's width and height swap. */
    public val isSideways: Boolean get() = this == Rotate90 || this == Rotate270

    /** This rotation turned a further 90 degrees clockwise. */
    public fun rotatedClockwise(): CropRotation = entries[(ordinal + 1) % 4]

    /** This rotation turned 90 degrees counterclockwise. */
    public fun rotatedCounterClockwise(): CropRotation = entries[(ordinal + 3) % 4]

    /** The size of an image of [size] after this rotation. */
    public fun rotate(size: CropSize): CropSize = if (isSideways) CropSize(size.height, size.width) else size

    /** The size of an image of [size] after this rotation. */
    public fun rotate(size: PixelSize): PixelSize = if (isSideways) PixelSize(size.height, size.width) else size

    /**
     * Maps a rectangle in the rotated image (pixels, rotated image of [rotatedSize]) back to the
     * unrotated source image.
     */
    public fun toSource(rect: CropRect, rotatedSize: CropSize): CropRect {
        val w = rotatedSize.width
        val h = rotatedSize.height
        return when (this) {
            Rotate0 -> rect
            // Source (x, y) is drawn at (sourceHeight - y, x), and sourceHeight == w.
            Rotate90 -> CropRect(rect.top, w - rect.right, rect.bottom, w - rect.left)
            Rotate180 -> CropRect(w - rect.right, h - rect.bottom, w - rect.left, h - rect.top)
            // Source (x, y) is drawn at (y, sourceWidth - x), and sourceWidth == h.
            Rotate270 -> CropRect(h - rect.bottom, rect.left, h - rect.top, rect.right)
        }
    }

    /** Maps a rectangle in the unrotated source image of [sourceSize] into the rotated image. */
    public fun toRotated(rect: CropRect, sourceSize: CropSize): CropRect {
        val w = sourceSize.width
        val h = sourceSize.height
        return when (this) {
            Rotate0 -> rect
            Rotate90 -> CropRect(h - rect.bottom, rect.left, h - rect.top, rect.right)
            Rotate180 -> CropRect(w - rect.right, h - rect.bottom, w - rect.left, h - rect.top)
            Rotate270 -> CropRect(rect.top, w - rect.right, rect.bottom, w - rect.left)
        }
    }

    public companion object {
        /** The rotation for any multiple of 90 degrees, negative values included (-90 is 270). */
        public fun fromDegrees(degrees: Int): CropRotation {
            require(degrees % 90 == 0) { "Rotation must be a multiple of 90 degrees, was $degrees" }
            val normalized = ((degrees % 360) + 360) % 360
            return entries.first { it.degrees == normalized }
        }
    }
}
