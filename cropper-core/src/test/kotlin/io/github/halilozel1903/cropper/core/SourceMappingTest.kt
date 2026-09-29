package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceMappingTest {

    private val source = PixelSize(400, 300)

    @Test
    fun unrotatedMappingDividesByTheScale() {
        // Image drawn at 2x with its top left at (10, 20).
        val transform = ImageTransform(2f, 10f, 20f)
        val crop = CropRect(110f, 220f, 310f, 420f)
        assertEquals(PixelRect(50, 100, 100, 100), CropMath.sourceRect(crop, transform, source))
    }

    @Test
    fun clockwiseRotationMapsBackToTheSource() {
        // Rotated 90 degrees the image is 300 x 400. Its top right 100 x 50 block ...
        val transform = ImageTransform(1f, 0f, 0f)
        val crop = CropRect(200f, 0f, 300f, 50f)
        // ... is the source's left 50 columns, top 100 rows.
        assertEquals(PixelRect(0, 0, 50, 100), CropMath.sourceRect(crop, transform, source, CropRotation.Rotate90))
    }

    @Test
    fun halfTurnMapsTheTopLeftToTheBottomRight() {
        val transform = ImageTransform(1f, 0f, 0f)
        val crop = CropRect(0f, 0f, 40f, 30f)
        assertEquals(PixelRect(360, 270, 40, 30), CropMath.sourceRect(crop, transform, source, CropRotation.Rotate180))
    }

    @Test
    fun counterClockwiseRotationMapsBackToTheSource() {
        // Rotated 270 degrees (300 x 400) the top left 100 x 50 block is the source's right 50 columns, top 100 rows.
        val transform = ImageTransform(1f, 0f, 0f)
        val crop = CropRect(0f, 0f, 100f, 50f)
        assertEquals(PixelRect(350, 0, 50, 100), CropMath.sourceRect(crop, transform, source, CropRotation.Rotate270))
    }

    @Test
    fun everyRotationAgreesWithToRotated() {
        val pixels = CropRect(40f, 60f, 240f, 160f)
        for (rotation in CropRotation.entries) {
            val inRotated = rotation.toRotated(pixels, source.toCropSize())
            val transform = ImageTransform(0.5f, 33f, -12f)
            val crop = CropMath.regionToView(
                CropRect(
                    inRotated.left / rotation.rotate(source).width,
                    inRotated.top / rotation.rotate(source).height,
                    inRotated.right / rotation.rotate(source).width,
                    inRotated.bottom / rotation.rotate(source).height,
                ),
                transform,
                rotation.rotate(source.toCropSize()),
            )
            assertEquals(PixelRect(40, 60, 200, 100), CropMath.sourceRect(crop, transform, source, rotation), "$rotation")
        }
    }

    @Test
    fun resultStaysInsideTheBitmapAndIsNeverEmpty() {
        val transform = ImageTransform(1f, 0f, 0f)
        val outside = CropMath.sourceRect(CropRect(-50f, -50f, 1000f, 1000f), transform, source)
        assertEquals(PixelRect(0, 0, 400, 300), outside)
        val tiny = CropMath.sourceRect(CropRect(10f, 10f, 10.1f, 10.1f), transform, source)
        assertTrue(tiny.width >= 1 && tiny.height >= 1)
        val edge = CropMath.sourceRect(CropRect(399.9f, 299.9f, 400f, 300f), transform, source)
        assertTrue(edge.right <= 400 && edge.bottom <= 300 && edge.width >= 1)
        assertFailsWith<IllegalArgumentException> { CropMath.sourceRect(CropRect.Full, transform, PixelSize(0, 10)) }
    }

    @Test
    fun fittedLayoutMapsToTheWholeBitmap() {
        for (rotation in CropRotation.entries) {
            val size = rotation.rotate(source.toCropSize())
            val layout = CropMath.layoutForRegion(CropRect(16f, 16f, 1064f, 1904f), size)
            assertEquals(PixelRect(0, 0, 400, 300), CropMath.sourceRect(layout.cropRect, layout.transform, source, rotation), "$rotation")
        }
    }

    @Test
    fun outputSizeTurnsAndScalesDown() {
        val rect = PixelRect(0, 0, 400, 300)
        assertEquals(PixelSize(400, 300), CropMath.outputSize(rect))
        assertEquals(PixelSize(300, 400), CropMath.outputSize(rect, CropRotation.Rotate90))
        assertEquals(PixelSize(200, 150), CropMath.outputSize(rect, maxSize = 200))
        assertEquals(PixelSize(150, 200), CropMath.outputSize(rect, CropRotation.Rotate270, maxSize = 200))
        assertEquals(PixelSize(400, 300), CropMath.outputSize(rect, maxSize = 1000))
        assertEquals(PixelSize(1, 100), CropMath.outputSize(PixelRect(0, 0, 1, 1000), maxSize = 100))
        assertFailsWith<IllegalArgumentException> { CropMath.outputSize(rect, maxSize = -1) }
    }
}
