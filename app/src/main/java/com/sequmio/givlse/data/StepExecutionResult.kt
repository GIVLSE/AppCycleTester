package com.sequmio.givlse.data

/** Metadata-only result: never persist typed text, screen contents or credentials. */
data class StepExecutionResult(
    val scenarioName: String,
    val suite: String,
    val scenarioVersion: Int,
    val stepIndex: Int,
    val action: String,
    val status: String,
    val durationMs: Long,
    val message: String,
    val at: Long,
    val expectedResult: String = "",
    val actualResult: String = "",
    val errorCode: String = ""
)
