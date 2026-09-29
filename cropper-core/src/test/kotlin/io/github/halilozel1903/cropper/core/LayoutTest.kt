package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LayoutTest {

    private val bounds = CropRect(0f, 0f, 1000f, 1000f)
    private val landscape = CropSize(2000f, 1000f)

    @Test
    fun freeLayoutFitsTheWholeImage() {
        val layout = CropMath.layoutForRegion(bounds, landscape)
        assertEquals(ImageTransform(0.5f, 0f, 250f), layout.transform)
        assertRect(CropRect(0f, 250f, 1000f, 750f), layout.cropRect)
        assertTrue(CropMath.covers(layout.transform, layout.cropRect, landscape))
    }

    @Test
    fun ratioLayoutCentersTheLargestFrameOnTheImage() {
        val layout = CropMath.layoutForRegion(bounds, landscape, aspectRatio = 1f)
        assertRect(CropRect(250f, 250f, 750f, 750f), layout.cropRect)
        assertTrue(CropMath.covers(layout.transform, layout.cropRect, landscape))
    }

    @Test
    fun regionLayoutKeepsTheImageFittedAndFramesTheRegion() {
        val layout = CropMath.layoutForRegion(bounds, landscape, CropRect(0.5f, 0f, 1f, 1f))
        assertRect(CropRect(500f, 250f, 1000f, 750f), layout.cropRect)
        assertEquals(0.5f, layout.transform.scale)
    }

    @Test
    fun filledLayoutZoomsTheRegionIntoTheBounds() {
        val region = CropRect(0.25f, 0.5f, 0.5f, 1f) // 500 x 500 px of the image
        val layout = CropMath.layoutForRegion(bounds, landscape, region, fill = true)
        assertRect(bounds, layout.cropRect)
        assertClose(2f, layout.transform.scale)
        assertRect(region, CropMath.regionOf(layout.cropRect, layout.transform, landscape))
    }

    @Test
    fun filledLayoutAppliesTheRatioInsideTheRegion() {
        val layout = CropMath.layoutForRegion(bounds, landscape, CropRect.Full, aspectRatio = 16f / 9f, fill = true)
        assertClose(16f / 9f, layout.cropRect.aspectRatio)
        assertClose(1000f, layout.cropRect.width)
        assertTrue(CropMath.covers(layout.transform, layout.cropRect, landscape))
    }

    @Test
    fun emptyOrOutsideRegionsFallBackToTheWholeImage() {
        val layout = CropMath.layoutForRegion(bounds, landscape, CropRect(2f, 2f, 3f, 3f))
        assertRect(CropRect(0f, 250f, 1000f, 750f), layout.cropRect)
        assertFailsWith<IllegalArgumentException> { CropMath.layoutForRegion(CropRect(0f, 0f, 0f, 0f), landscape) }
    }

    @Test
    fun regionRoundTrips() {
        val transform = ImageTransform(1.5f, -120f, 40f)
        val region = CropRect(0.1f, 0.2f, 0.6f, 0.7f)
        val view = CropMath.regionToView(region, transform, landscape)
        assertRect(region, CropMath.regionOf(view, transform, landscape))
    }

    @Test
    fun rotatingARegionFourTimesGivesItBack() {
        val region = CropRect(0.1f, 0.2f, 0.4f, 0.9f)
        var clockwise = region
        var counter = region
        repeat(4) {
            clockwise = CropMath.rotateRegion(clockwise, clockwise = true)
            counter = CropMath.rotateRegion(counter, clockwise = false)
        }
        assertRect(region, clockwise)
        assertRect(region, counter)
        val once = CropMath.rotateRegion(region, clockwise = true)
        assertRect(region, CropMath.rotateRegion(once, clockwise = false))
    }

    @Test
    fun rotatingARegionFollowsTheImage() {
        // The top left quarter of the image ends up in the top right after a clockwise turn.
        val topLeft = CropRect(0f, 0f, 0.5f, 0.5f)
        assertRect(CropRect(0.5f, 0f, 1f, 0.5f), CropMath.rotateRegion(topLeft, clockwise = true))
        assertRect(CropRect(0f, 0.5f, 0.5f, 1f), CropMath.rotateRegion(topLeft, clockwise = false))
    }

    @Test
    fun rotatedRegionMatchesRotatingTheSourcePixels() {
        val source = CropSize(400f, 300f)
        val pixels = CropRect(40f, 30f, 200f, 90f)
        val region = CropRect(pixels.left / 400f, pixels.top / 300f, pixels.right / 400f, pixels.bottom / 300f)
        val rotated = CropRotation.Rotate90.toRotated(pixels, source) // in a 300 x 400 image
        val expected = CropRect(rotated.left / 300f, rotated.top / 400f, rotated.right / 300f, rotated.bottom / 400f)
        assertRect(expected, CropMath.rotateRegion(region, clockwise = true))
    }
}
