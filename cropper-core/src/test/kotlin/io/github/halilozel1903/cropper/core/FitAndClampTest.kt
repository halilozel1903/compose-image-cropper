package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FitAndClampTest {

    private val image = CropSize(400f, 300f)
    private val crop = CropRect(100f, 100f, 300f, 300f)

    @Test
    fun fitRectUsesTheLimitingSide() {
        val bounds = CropRect(0f, 0f, 400f, 200f)
        assertRect(CropRect(100f, 0f, 300f, 200f), CropMath.fitRect(bounds, 1f))
        assertRect(CropRect(0f, 43.75f, 400f, 156.25f), CropMath.fitRect(bounds, 16f / 4.5f))
        assertFailsWith<IllegalArgumentException> { CropMath.fitRect(bounds, 0f) }
    }

    @Test
    fun containAndMinScale() {
        val bounds = CropRect(0f, 0f, 800f, 900f)
        assertEquals(2f, CropMath.containScale(bounds, image))
        // A 200x200 frame needs the 300 px high image at 2/3.
        assertClose(2f / 3f, CropMath.minScale(crop, image))
        assertFailsWith<IllegalArgumentException> { CropMath.minScale(crop, CropSize(0f, 10f)) }
    }

    @Test
    fun clampRaisesTheScaleUntilTheImageCovers() {
        val clamped = CropMath.clampTransform(ImageTransform(0.1f, 0f, 0f), crop, image)
        assertClose(2f / 3f, clamped.scale)
        assertTrue(CropMath.covers(clamped, crop, image))
    }

    @Test
    fun clampLimitsTheScale() {
        val clamped = CropMath.clampTransform(ImageTransform(10f, -1000f, -1000f), crop, image, maxScale = 4f)
        assertEquals(4f, clamped.scale)
        assertTrue(CropMath.covers(clamped, crop, image))
    }

    @Test
    fun maxScaleBelowTheCoverScaleIsIgnored() {
        val clamped = CropMath.clampTransform(ImageTransform(1f, 50f, 50f), crop, image, maxScale = 0.1f)
        assertTrue(CropMath.covers(clamped, crop, image))
    }

    @Test
    fun clampPullsTheImageBackOverTheFrame() {
        // Scale 1: the image is 400x300, the frame 200x200 at (100, 100).
        val tooFarRight = CropMath.clampTransform(ImageTransform(1f, 150f, 0f), crop, image)
        assertEquals(100f, tooFarRight.offsetX)
        val tooFarUp = CropMath.clampTransform(ImageTransform(1f, 0f, -250f), crop, image)
        assertEquals(0f, tooFarUp.offsetY) // bottom edge at 300 == frame bottom
        val inside = ImageTransform(1f, -50f, 50f)
        assertEquals(inside, CropMath.clampTransform(inside, crop, image))
    }

    @Test
    fun zoomKeepsTheFocusPointFixed() {
        val transform = ImageTransform(1f, 0f, 0f)
        val zoomed = CropMath.zoom(transform, 2f, 100f, 50f)
        // The image point under (100, 50) was (100, 50) and still is.
        assertEquals(2f, zoomed.scale)
        assertEquals(100f, zoomed.offsetX + 100f * zoomed.scale)
        assertEquals(ImageTransform(2f, -100f, -50f), zoomed)
        assertFailsWith<IllegalArgumentException> { CropMath.zoom(transform, 0f, 0f, 0f) }
    }

    @Test
    fun panMoves() {
        assertEquals(ImageTransform(1f, 5f, -3f), CropMath.pan(ImageTransform(1f, 0f, 0f), 5f, -3f))
    }

    @Test
    fun gestureZoomsPansAndClamps() {
        val start = ImageTransform(1f, 0f, 0f)
        val result = CropMath.gesture(start, 0.1f, 1000f, 1000f, 200f, 200f, crop, image, maxScale = 3f)
        assertTrue(CropMath.covers(result, crop, image))
        assertClose(2f / 3f, result.scale)
        val zoomed = CropMath.gesture(start, 10f, 0f, 0f, 200f, 200f, crop, image, maxScale = 3f)
        assertEquals(3f, zoomed.scale)
        assertTrue(CropMath.covers(zoomed, crop, image))
    }

    @Test
    fun doubleTapZoomsInAroundTheTapAndBackOut() {
        val min = CropMath.clampTransform(ImageTransform(0.01f, 0f, 0f), crop, image)
        val zoomedIn = CropMath.doubleTapTransform(min, 150f, 150f, crop, image, maxScale = 10f)
        assertClose(min.scale * 2.5f, zoomedIn.scale)
        assertTrue(CropMath.covers(zoomedIn, crop, image))
        val back = CropMath.doubleTapTransform(zoomedIn, 150f, 150f, crop, image, maxScale = 10f)
        assertClose(min.scale, back.scale)
        assertTrue(CropMath.covers(back, crop, image))
    }

    @Test
    fun doubleTapRespectsMaxScale() {
        val min = CropMath.clampTransform(ImageTransform(0.01f, 0f, 0f), crop, image)
        val zoomedIn = CropMath.doubleTapTransform(min, 150f, 150f, crop, image, maxScale = 1f)
        assertClose(1f, zoomedIn.scale)
    }

    @Test
    fun lerpBetweenCoveringTransformsKeepsCovering() {
        val a = CropMath.clampTransform(ImageTransform(0.01f, 0f, 0f), crop, image)
        val b = CropMath.doubleTapTransform(a, 290f, 110f, crop, image, maxScale = 10f)
        for (step in 0..10) {
            assertTrue(CropMath.covers(a.lerp(b, step / 10f), crop, image), "step $step")
        }
    }
}
