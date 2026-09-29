package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AspectRatioTest {

    @Test
    fun valueAndInverted() {
        assertClose(16f / 9f, AspectRatio.SixteenNine.value)
        assertEquals(AspectRatio.NineSixteen, AspectRatio.SixteenNine.inverted())
        assertEquals(1f, AspectRatio.Square.value)
    }

    @Test
    fun labels() {
        assertEquals("16:9", AspectRatio.SixteenNine.toString())
        assertEquals("1:1", AspectRatio.Square.toString())
        assertEquals("1.91:1", AspectRatio(1.91f, 1f).toString())
    }

    @Test
    fun presetsStartWithFree() {
        assertNull(AspectRatio.Presets.first())
        assertEquals(AspectRatio.Presets.size, AspectRatio.Presets.toSet().size)
    }

    @Test
    fun rejectsNonPositiveSides() {
        assertFailsWith<IllegalArgumentException> { AspectRatio(0, 1) }
        assertFailsWith<IllegalArgumentException> { AspectRatio(1f, -2f) }
    }
}
