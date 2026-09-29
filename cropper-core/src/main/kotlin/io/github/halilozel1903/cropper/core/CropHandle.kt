package io.github.halilozel1903.cropper.core

/** A draggable part of the crop frame: its four corners and four edges. */
public enum class CropHandle(
    /** Dragging moves the left edge. */
    public val movesLeft: Boolean,
    /** Dragging moves the top edge. */
    public val movesTop: Boolean,
    /** Dragging moves the right edge. */
    public val movesRight: Boolean,
    /** Dragging moves the bottom edge. */
    public val movesBottom: Boolean,
) {
    TopLeft(true, true, false, false),
    Top(false, true, false, false),
    TopRight(false, true, true, false),
    Right(false, false, true, false),
    BottomRight(false, false, true, true),
    Bottom(false, false, false, true),
    BottomLeft(true, false, false, true),
    Left(true, false, false, false),
    ;

    /** `true` for the four corners. */
    public val isCorner: Boolean get() = (movesLeft || movesRight) && (movesTop || movesBottom)

    /** `true` for the four edges. */
    public val isEdge: Boolean get() = !isCorner
}
