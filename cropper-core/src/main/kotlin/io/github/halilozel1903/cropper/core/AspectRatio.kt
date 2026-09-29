package io.github.halilozel1903.cropper.core

/**
 * A crop aspect ratio such as 16:9. Free cropping is represented by `null` wherever an
 * `AspectRatio?` is accepted.
 */
public data class AspectRatio(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "Aspect ratio sides must be positive, was $width:$height" }
    }

    /** Convenience constructor, `AspectRatio(16, 9)`. */
    public constructor(width: Int, height: Int) : this(width.toFloat(), height.toFloat())

    /** `width / height`. */
    public val value: Float get() = width / height

    /** The same ratio turned by 90 degrees, 16:9 becomes 9:16. */
    public fun inverted(): AspectRatio = AspectRatio(height, width)

    /** A label like `16:9`. */
    override fun toString(): String = "${format(width)}:${format(height)}"

    public companion object {
        /** 1:1, also used by circle crops. */
        public val Square: AspectRatio = AspectRatio(1, 1)

        /** 4:3 landscape. */
        public val FourThree: AspectRatio = AspectRatio(4, 3)

        /** 3:4 portrait. */
        public val ThreeFour: AspectRatio = AspectRatio(3, 4)

        /** 3:2 landscape, the classic 35 mm photo. */
        public val ThreeTwo: AspectRatio = AspectRatio(3, 2)

        /** 2:3 portrait. */
        public val TwoThree: AspectRatio = AspectRatio(2, 3)

        /** 16:9 landscape, video and banners. */
        public val SixteenNine: AspectRatio = AspectRatio(16, 9)

        /** 9:16 portrait, stories. */
        public val NineSixteen: AspectRatio = AspectRatio(9, 16)

        /** 4:5 portrait, feed posts. */
        public val FourFive: AspectRatio = AspectRatio(4, 5)

        /** The common presets, `null` (free) first. */
        public val Presets: List<AspectRatio?> = listOf<AspectRatio?>(
            null, Square, FourThree, ThreeFour, SixteenNine, NineSixteen, ThreeTwo, FourFive,
        )

        private fun format(value: Float): String {
            val whole = value.toInt()
            return if (whole.toFloat() == value) whole.toString() else value.toString()
        }
    }
}
