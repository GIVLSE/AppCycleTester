package com.sequmio.givlse.data

data class QaPreviewCase(val testerUid: String, val runId: String, val status: String)

data class QaTodaySummary(val passed: Int, val failed: Int, val errors: Int) {
    val total: Int get() = passed + failed + errors
    companion object {
        fun fromCases(cases: List<QaPreviewCase>): QaTodaySummary {
            var passed = 0
            var failed = 0
            var errors = 0
            cases.groupBy { it.testerUid to it.runId }.values.forEach { run ->
                when {
                    run.any { it.status == "FAIL" || it.status == "FAILED" } -> failed++
                    run.all { it.status == "PASS" || it.status == "PASSED" } -> passed++
                    else -> errors++
                }
            }
            return QaTodaySummary(passed, failed, errors)
        }
    }
}
