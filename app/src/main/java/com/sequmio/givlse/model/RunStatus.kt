package com.sequmio.givlse.model

/** Execution orchestration has no product verdict. */
enum class RunStatus {
    FINISHED, BLOCKED, CANCELLED;
    companion object {
        fun fromStored(status: String, reason: String): String = when (status) {
            "RUNNING", "PAUSED" -> status
            "FINISHED", "BLOCKED", "CANCELLED" ->
                if (reason == "Completed with app failures") FINISHED.name else status
            "PASS" -> FINISHED.name
            "STOPPED" -> CANCELLED.name
            "FAIL", "TIMEOUT" ->
                if (reason == "Completed with app failures" || reason == "All cycles completed")
                    FINISHED.name else BLOCKED.name
            else -> BLOCKED.name
        }
    }
}

/** Evidence precedence: a confirmed failed assertion must not be hidden by a
 * later pass or execution error. Detailed case reasons remain in the report. */
enum class AppPoolOutcome {
    NOT_EXECUTED, PASSED, ERROR, FAILED;
    companion object {
        fun fromExecution(status: String): AppPoolOutcome = when (status) {
            "PASS", "PASSED" -> PASSED
            "FAIL", "FAILED" -> FAILED
            "BLOCKED", "TIMEOUT", "ERROR", "STOPPED", "CANCELLED", "INTERRUPTED", "INCOMPLETE", "RUNNING" -> ERROR
            else -> NOT_EXECUTED
        }
    }
}
