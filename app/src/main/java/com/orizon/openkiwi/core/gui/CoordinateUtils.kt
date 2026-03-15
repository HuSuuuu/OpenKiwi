package com.orizon.openkiwi.core.gui

object CoordinateUtils {
    const val NORMALIZED_MAX = 1000

    fun normalizedToAbsolute(normalizedX: Int, normalizedY: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        return Pair((normalizedX * screenWidth) / NORMALIZED_MAX, (normalizedY * screenHeight) / NORMALIZED_MAX)
    }

    fun absoluteToNormalized(absoluteX: Int, absoluteY: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        return Pair((absoluteX * NORMALIZED_MAX) / screenWidth, (absoluteY * NORMALIZED_MAX) / screenHeight)
    }

    fun isValidNormalized(x: Int, y: Int): Boolean = x in 0..NORMALIZED_MAX && y in 0..NORMALIZED_MAX

    fun clampNormalized(x: Int, y: Int): Pair<Int, Int> = Pair(x.coerceIn(0, NORMALIZED_MAX), y.coerceIn(0, NORMALIZED_MAX))

    fun calculateSwipeDuration(startX: Int, startY: Int, endX: Int, endY: Int, screenWidth: Int, screenHeight: Int, minMs: Long = 300, maxMs: Long = 1500): Long {
        val (sx, sy) = normalizedToAbsolute(startX, startY, screenWidth, screenHeight)
        val (ex, ey) = normalizedToAbsolute(endX, endY, screenWidth, screenHeight)
        val distSq = (sx - ex).toLong() * (sx - ex) + (sy - ey).toLong() * (sy - ey)
        return (distSq / 1000).coerceIn(minMs, maxMs)
    }
}
