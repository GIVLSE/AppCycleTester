package com.sequmio.givlse.runner

import com.sequmio.givlse.model.RepeatMode

/** Pure cycle navigation; Android-specific operations are handled by the service. */
object RunPolicy {
    data class Next(val index: Int?, val completedCycles: Int)

    fun next(
        currentIndex: Int, itemCount: Int, completedCycles: Int,
        mode: RepeatMode, requestedCycles: Int
    ): Next {
        require(itemCount > 0)
        require(currentIndex in 0 until itemCount)
        require(completedCycles >= 0)
        require(requestedCycles > 0)

        if (currentIndex < itemCount - 1) return Next(currentIndex + 1, completedCycles)
        val finished = completedCycles + 1
        val stop = when (mode) {
            RepeatMode.ONCE -> true
            RepeatMode.COUNT -> finished >= requestedCycles
            RepeatMode.ENDLESS -> false
        }
        return Next(if (stop) null else 0, finished)
    }
}
