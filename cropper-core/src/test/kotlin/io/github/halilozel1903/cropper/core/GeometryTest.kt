package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {

    @Test
    fun rectBasics() {
        val rect = CropRect(10f, 20f, 110f, 70f)
        assertEquals(100f, rect.width)
        assertEquals(50f, rect.height)
        assertEquals(60f, rect.centerX)
        assertEquals(45f, rect.centerY)
        assertEquals(2f, rect.aspectRatio)
        assertFalse(rect.isEmpty)
        assertTrue(CropRect(5f, 5f, 5f, 10f).isEmpty)
    }

    @Test
    fun translateInsetAndCentered() {
        val rect = CropRect(0f, 0f, 100f, 50f)
        assertEquals(CropRect(10f, -5f, 110f, 45f), rect.translate(10f, -5f))
        assertEquals(CropRect(10f, 5f, 90f, 45f), rect.inset(10f, 5f))
        assertEquals(CropRect(40f, 45f, 60f, 55f), CropRect.centered(50f, 50f, 20f, 10f))
        assertEquals(CropRect(1f, 2f, 4f, 6f), CropRect.fromSize(1f, 2f, 3f, 4f))
    }

    @Test
    fun containsPointAndRect() {
        val rect = CropRect(0f, 0f, 100f, 100f)
        assertTrue(rect.contains(0f, 100f))
        assertFalse(rect.contains(-1f, 50f))
        assertTrue(rect.contains(CropRect(10f, 10f, 90f, 90f)))
        assertFalse(rect.contains(CropRect(-0.5f, 10f, 90f, 90f)))
        assertTrue(rect.contains(CropRect(-0.5f, 10f, 90f, 90f), tolerance = 1f))
    }

    @Test
    fun intersect() {
        val a = CropRect(0f, 0f, 100f, 100f)
        assertEquals(CropRect(50f, 20f, 100f, 100f), a.intersect(CropRect(50f, 20f, 200f, 300f)))
        assertTrue(a.intersect(CropRect(200f, 200f, 300f, 300f)).isEmpty)
    }

    @Test
    fun sizesRejectNegativeValues() {
        assertFailsWith<IllegalArgumentException> { CropSize(-1f, 1f) }
        assertFailsWith<IllegalArgumentException> { PixelSize(1, -1) }
        assertEquals(CropSize(4f, 3f), PixelSize(4, 3).toCropSize())
        assertEquals(PixelRect(2, 3, 10, 20).right, 12)
        assertEquals(PixelRect(2, 3, 10, 20).bottom, 23)
    }

    @Test
    fun transformImageRectAndLerp() {
        val transform = ImageTransform(2f, 10f, 20f)
        assertEquals(CropRect(10f, 20f, 210f, 120f), transform.imageRect(CropSize(100f, 50f)))
        val target = ImageTransform(4f, 30f, 0f)
        assertEquals(transform, transform.lerp(target, 0f))
        assertEquals(target, transform.lerp(target, 1f))
        assertEquals(ImageTransform(3f, 20f, 10f), transform.lerp(target, 0.5f))
        assertFailsWith<IllegalArgumentException> { ImageTransform(0f, 0f, 0f) }
    }
}
