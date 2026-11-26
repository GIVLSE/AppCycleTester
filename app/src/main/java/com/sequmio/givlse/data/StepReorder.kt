package com.sequmio.givlse.data

internal data class StepBounds(val top: Float, val height: Float) {
    val center: Float get() = top + height / 2f
}

internal object StepReorder {
    fun destination(from: Int, center: Float, rows: Map<Int, StepBounds>): Int {
        val original = rows[from] ?: return from
        return when {
            center > original.center -> rows.filter { (i, b) -> i > from && center >= b.center }.keys.maxOrNull() ?: from
            center < original.center -> rows.filter { (i, b) -> i < from && center <= b.center }.keys.minOrNull() ?: from
            else -> from
        }
    }
    fun neighborOffset(index: Int, from: Int, to: Int, distance: Float): Float = when {
        from < 0 || to < 0 -> 0f
        from < to && index in (from + 1)..to -> -distance
        to < from && index in to until from -> distance
        else -> 0f
    }
    fun displayIndex(index: Int, from: Int, to: Int): Int = when {
        from < 0 || to < 0 -> index
        index == from -> to
        from < to && index in (from + 1)..to -> index - 1
        to < from && index in to until from -> index + 1
        else -> index
    }
}
