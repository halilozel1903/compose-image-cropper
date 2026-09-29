package io.github.halilozel1903.cropper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResizeTest {

    private val bounds = CropRect(0f, 0f, 1000f, 1000f)
    private val rect = CropRect(200f, 200f, 600f, 500f)

    @Test
    fun freeCornerMovesTwoEdges() {
        val result = CropMath.resize(rect, CropHandle.TopLeft, -50f, 30f, bounds)
        assertEquals(CropRect(150f, 230f, 600f, 500f), result)
    }

    @Test
    fun freeEdgeMovesOneEdge() {
        assertEquals(CropRect(200f, 200f, 700f, 500f), CropMath.resize(rect, CropHandle.Right, 100f, 999f, bounds))
        assertEquals(CropRect(200f, 200f, 600f, 450f), CropMath.resize(rect, CropHandle.Bottom, 999f, -50f, bounds))
    }

    @Test
    fun freeResizeStaysInBounds() {
        val result = CropMath.resize(rect, CropHandle.BottomRight, 5000f, 5000f, bounds)
        assertEquals(CropRect(200f, 200f, 1000f, 1000f), result)
        val topLeft = CropMath.resize(rect, CropHandle.TopLeft, -5000f, -5000f, bounds)
        assertEquals(CropRect(0f, 0f, 600f, 500f), topLeft)
    }

    @Test
    fun freeResizeKeepsTheMinimumSize() {
        val result = CropMath.resize(rect, CropHandle.TopLeft, 1000f, 1000f, bounds, minSize = 80f)
        assertEquals(CropRect(520f, 420f, 600f, 500f), result)
        val left = CropMath.resize(rect, CropHandle.Left, 1000f, 0f, bounds, minSize = 80f)
        assertEquals(520f, left.left)
    }

    @Test
    fun minimumSizeLargerThanTheBoundsIsCapped() {
        val small = CropRect(0f, 0f, 50f, 50f)
        val result = CropMath.resize(small, CropHandle.BottomRight, -40f, -40f, small, minSize = 500f)
        assertEquals(small, result)
    }

    @Test
    fun lockedCornerKeepsTheRatioAndTheOppositeCorner() {
        val square = CropRect(200f, 200f, 500f, 500f)
        val result = CropMath.resize(square, CropHandle.BottomRight, 100f, 20f, bounds, aspectRatio = 1f)
        assertRect(CropRect(200f, 200f, 600f, 600f), result)
        val topLeft = CropMath.resize(square, CropHandle.TopLeft, 10f, -80f, bounds, aspectRatio = 1f)
        assertRect(CropRect(120f, 120f, 500f, 500f), topLeft)
    }

    @Test
    fun lockedCornerStopsAtTheNearestBound() {
        val wide = CropRect(100f, 700f, 260f, 790f) // 16:9
        val ratio = 16f / 9f
        val result = CropMath.resize(wide, CropHandle.BottomRight, 2000f, 0f, bounds, aspectRatio = ratio)
        // Only 300 px below the top edge: the height stops at 300, the width at 533.33.
        assertClose(1000f, result.bottom)
        assertClose(ratio, result.aspectRatio)
        assertClose(100f, result.left)
        assertClose(700f, result.top)
        assertTrue(bounds.contains(result, 0.01f))
    }

    @Test
    fun lockedCornerRespectsTheMinimumOnBothSides() {
        val wide = CropRect(100f, 100f, 420f, 280f) // 16:9
        val result = CropMath.resize(wide, CropHandle.BottomRight, -1000f, -1000f, bounds, minSize = 90f, aspectRatio = 16f / 9f)
        assertClose(90f, result.height)
        assertClose(160f, result.width)
    }

    @Test
    fun lockedEdgeGrowsAroundTheCenterLine() {
        val square = CropRect(400f, 400f, 600f, 600f)
        val result = CropMath.resize(square, CropHandle.Right, 100f, 0f, bounds, aspectRatio = 1f)
        assertRect(CropRect(400f, 350f, 700f, 650f), result)
        val top = CropMath.resize(square, CropHandle.Top, 0f, -100f, bounds, aspectRatio = 1f)
        assertRect(CropRect(350f, 300f, 650f, 600f), top)
    }

    @Test
    fun lockedEdgeIsLimitedByTheCrossAxisRoom() {
        val square = CropRect(400f, 850f, 500f, 950f) // centered 100 px above the bottom
        val result = CropMath.resize(square, CropHandle.Right, 500f, 0f, bounds, aspectRatio = 1f)
        // Growing around y = 900 can't pass 1000, so the height (and the width) stop at 200.
        assertRect(CropRect(400f, 800f, 600f, 1000f), result)
        val left = CropMath.resize(square, CropHandle.Left, -300f, 0f, bounds, aspectRatio = 1f)
        assertRect(CropRect(300f, 800f, 500f, 1000f), left)
    }

    @Test
    fun resultsAlwaysStayInsideTheBounds() {
        val ratios = listOf<Float?>(null, 1f, 16f / 9f, 9f / 16f)
        val deltas = listOf(-3000f, -120f, -7f, 0f, 9f, 160f, 3000f)
        val start = CropRect(300f, 250f, 700f, 600f)
        for (ratio in ratios) {
            val begin = if (ratio == null) start else CropMath.fitRect(start, ratio)
            for (handle in CropHandle.entries) for (dx in deltas) for (dy in deltas) {
                val result = CropMath.resize(begin, handle, dx, dy, bounds, minSize = 40f, aspectRatio = ratio)
                assertTrue(bounds.contains(result, 0.01f), "$handle $dx $dy $ratio -> $result")
                assertTrue(result.width >= 39.99f && result.height >= 39.99f, "$handle $dx $dy $ratio -> $result")
                if (ratio != null) assertClose(ratio, result.aspectRatio, 0.001f)
            }
        }
    }

    @Test
    fun handleHitTesting() {
        val r = CropRect(100f, 100f, 300f, 300f)
        assertEquals(CropHandle.TopLeft, CropMath.handleAt(r, 90f, 95f, 24f))
        assertEquals(CropHandle.BottomRight, CropMath.handleAt(r, 310f, 305f, 24f))
        assertEquals(CropHandle.Top, CropMath.handleAt(r, 200f, 110f, 24f))
        assertEquals(CropHandle.Left, CropMath.handleAt(r, 85f, 200f, 24f))
        assertEquals(CropHandle.Right, CropMath.handleAt(r, 299f, 200f, 24f))
        assertEquals(CropHandle.Bottom, CropMath.handleAt(r, 200f, 320f, 24f))
        assertNull(CropMath.handleAt(r, 200f, 200f, 24f))
        assertNull(CropMath.handleAt(r, 500f, 500f, 24f))
    }

    @Test
    fun smallFramesPickTheNearestCorner() {
        val r = CropRect(100f, 100f, 120f, 120f)
        assertEquals(CropHandle.TopLeft, CropMath.handleAt(r, 104f, 104f, 24f))
        assertEquals(CropHandle.BottomRight, CropMath.handleAt(r, 117f, 118f, 24f))
    }

    @Test
    fun handleFlags() {
        assertEquals(4, CropHandle.entries.count { it.isCorner })
        assertEquals(4, CropHandle.entries.count { it.isEdge })
        assertTrue(CropHandle.BottomLeft.movesLeft && CropHandle.BottomLeft.movesBottom)
    }
}
