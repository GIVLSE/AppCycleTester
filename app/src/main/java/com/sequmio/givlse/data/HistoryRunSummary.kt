package com.sequmio.givlse.data

import com.sequmio.givlse.model.AppPoolOutcome
import com.sequmio.givlse.model.RunEvent
import org.json.JSONObject

/** A compact read-only view of a saved run. Never use a session verdict as an app verdict.
 * Only read events from that run, not the mixed global device event history.
 */
internal data class HistoryAppSummary(val packageName: String, val label: String, val outcome: AppPoolOutcome)

internal data class HistoryRunSummary(
    val id: String,
    val status: String,
    val startedAt: Long,
    val durationSeconds: Long,
    val completedCycles: Int,
    val reason: String,
    val apps: List<HistoryAppSummary>,
    val events: List<RunEvent>
) {
    val summaryLabel: String
        get() = buildList {
            add("${apps.size} " + if (apps.size == 1) "app" else "apps")
            listOf(AppPoolOutcome.PASSED to "Passed", AppPoolOutcome.FAILED to "Failed",
                AppPoolOutcome.ERROR to "Error", AppPoolOutcome.NOT_EXECUTED to "Not run")
                .forEach { (status, label) ->
                    val count = apps.count { it.outcome == status }
                    if (count > 0) add("$count $label")
                }
        }.joinToString(" · ")

    companion object {
        fun fromReport(report: RunReport): HistoryRunSummary {
            val json = runCatching { JSONObject(report.rawJson) }.getOrNull()
            val targets = json?.optJSONArray("targets")
            val apps = buildList {
                if (targets != null) for (i in 0 until targets.length()) {
                    val target = targets.optJSONObject(i) ?: continue
                    val pkg = target.optString("packageName")
                    add(HistoryAppSummary(pkg, target.optString("label").ifBlank {
                        pkg.ifBlank { "Unknown app" }
                    }, ReportDetails.appOutcome(target)))
                }
            }
            val rows = json?.optJSONArray("events")
            val events = buildList {
                if (rows != null) for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val message = row.optString("message")
                    if (message.isNotBlank()) add(RunEvent(row.optLong("at"), message))
                }
            }
            return HistoryRunSummary(report.id, report.displayStatus, report.startedAt,
                report.durationSeconds, report.completedCycles, report.reason, apps, events)
        }
    }
}
