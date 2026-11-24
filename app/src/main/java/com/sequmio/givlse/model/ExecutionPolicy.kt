package com.sequmio.givlse.model

object ExecutionPolicy {
    fun timeout(estimated: Int, override: Int): Int =
        if (override > 0) override.coerceIn(1, 14430)
        else estimated.coerceIn(5, 7200) * 2 + 30

    fun repeat(mode: ExecutionMode, elapsedMs: Long, duration: Int): Boolean =
        mode == ExecutionMode.LOOP_TEST_CASES && elapsedMs < duration * 1000L

    fun combine(a: String, b: String): String {
        val order = listOf("PASS", "BLOCKED", "TIMEOUT", "FAIL")
        return if (order.indexOf(a) >= order.indexOf(b)) a else b
    }
}
