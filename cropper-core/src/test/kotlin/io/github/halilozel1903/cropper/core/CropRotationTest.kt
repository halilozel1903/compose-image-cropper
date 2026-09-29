package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CropRotationTest {

    private val source = CropSize(400f, 300f)

    @Test
    fun turnsWrapAround() {
        assertEquals(CropRotation.Rotate90, CropRotation.Rotate0.rotatedClockwise())
        assertEquals(CropRotation.Rotate0, CropRotation.Rotate270.rotatedClockwise())
        assertEquals(CropRotation.Rotate270, CropRotation.Rotate0.rotatedCounterClockwise())
        var rotation = CropRotation.Rotate0
        repeat(4) { rotation = rotation.rotatedClockwise() }
        assertEquals(CropRotation.Rotate0, rotation)
    }

    @Test
    fun fromDegreesNormalizes() {
        assertEquals(CropRotation.Rotate270, CropRotation.fromDegrees(-90))
        assertEquals(CropRotation.Rotate90, CropRotation.fromDegrees(450))
        assertEquals(CropRotation.Rotate0, CropRotation.fromDegrees(720))
        assertFailsWith<IllegalArgumentException> { CropRotation.fromDegrees(45) }
    }

    @Test
    fun sidewaysRotationsSwapTheSize() {
        assertTrue(CropRotation.Rotate90.isSideways)
        assertFalse(CropRotation.Rotate180.isSideways)
        assertEquals(CropSize(300f, 400f), CropRotation.Rotate90.rotate(source))
        assertEquals(source, CropRotation.Rotate180.rotate(source))
        assertEquals(PixelSize(3, 4), CropRotation.Rotate270.rotate(PixelSize(4, 3)))
    }

    @Test
    fun clockwiseQuarterTurnMovesTheTopLeftCornerToTheTopRight() {
        // A 10x10 block at the source's top left corner.
        val block = CropRect(0f, 0f, 10f, 10f)
        val rotated = CropRotation.Rotate90.toRotated(block, source)
        // The rotated image is 300 wide; the block now sits at its top right.
        assertEquals(CropRect(290f, 0f, 300f, 10f), rotated)
    }

    @Test
    fun counterClockwiseQuarterTurnMovesTheTopLeftCornerToTheBottomLeft() {
        val block = CropRect(0f, 0f, 10f, 10f)
        assertEquals(CropRect(0f, 390f, 10f, 400f), CropRotation.Rotate270.toRotated(block, source))
        assertEquals(CropRect(390f, 290f, 400f, 300f), CropRotation.Rotate180.toRotated(block, source))
    }

    @Test
    fun toSourceUndoesToRotatedForEveryRotation() {
        val rect = CropRect(30f, 40f, 170f, 90f)
        for (rotation in CropRotation.entries) {
            val rotated = rotation.toRotated(rect, source)
            assertEquals(rect, rotation.toSource(rotated, rotation.rotate(source)), "for $rotation")
        }
    }
}
