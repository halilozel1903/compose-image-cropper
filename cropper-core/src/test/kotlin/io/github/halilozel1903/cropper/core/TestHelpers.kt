package io.github.halilozel1903.cropper.core

import kotlin.math.abs
import kotlin.test.assertTrue

internal const val Eps = 0.001f

internal fun assertClose(expected: Float, actual: Float, eps: Float = Eps, message: String? = null) {
    assertTrue(abs(expected - actual) <= eps, (message?.let { "$it: " } ?: "") + "expected $expected but was $actual")
}

internal fun assertRect(expected: CropRect, actual: CropRect, eps: Float = Eps) {
    assertClose(expected.left, actual.left, eps, "left of $actual")
    assertClose(expected.top, actual.top, eps, "top of $actual")
    assertClose(expected.right, actual.right, eps, "right of $actual")
    assertClose(expected.bottom, actual.bottom, eps, "bottom of $actual")
}
