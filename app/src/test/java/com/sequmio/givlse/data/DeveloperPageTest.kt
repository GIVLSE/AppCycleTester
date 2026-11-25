package com.sequmio.givlse.data

import org.junit.Assert.*
import org.junit.Test

class DeveloperPageTest {
    private val valid = MyAppDraft("GridHunter","com.gridhunter.vpn","VPN",
        "https://groups.google.com/g/test","https://play.google.com/apps/testing/com.gridhunter.vpn",
        "https://play.google.com/store/apps/details?id=com.gridhunter.vpn")

    @Test fun detailsSaveDoesNotRequireRecordedCases() {
        assertTrue(valid.validateDetails().isEmpty())
        assertTrue(valid.validateForPublish().isNotEmpty())
    }
    @Test fun saveListsAllMissingLinksAndDescription() {
        val errors = valid.copy(description = "", googleGroupUrl = "", optInUrl = "",
            playStoreUrl = "").validateDetails()
        assertEquals(4, errors.size)
        assertTrue(errors.any { it.contains("Google Group") })
        assertTrue(errors.any { it.contains("Become a tester") })
        assertTrue(errors.any { it.contains("Google Play") })
    }
    @Test fun wrongLinkTypeIsRejected() {
        assertTrue(valid.copy(optInUrl = valid.playStoreUrl).validateDetails().isNotEmpty())
    }
    @Test fun cloudRestoreUsesRemoteWhenNoLocalDraft() {
        assertEquals(valid, reconcileOwnedDraft(null, null, valid))
    }
    @Test fun cloudRestoreRefreshesCleanCopy() {
        val remote = valid.copy(description = "Updated on another device")
        assertEquals(remote, reconcileOwnedDraft(valid, valid, remote))
    }
    @Test fun cloudRestorePreservesLocalEdits() {
        val local = valid.copy(description = "Unpublished local changes")
        val remote = valid.copy(description = "Cloud changes")
        assertEquals(local, reconcileOwnedDraft(local, valid, remote))
        assertEquals(local, reconcileOwnedDraft(local, null, remote))
    }
    @Test fun cloudRestorePreservesRecordedCases() {
        val local = valid.copy(scenarioJson = "local case")
        assertEquals(local, reconcileOwnedDraft(local, valid, valid))
    }
    @Test fun publishedAppRestoresAllEditableFields() {
        val app = PublishedApp(valid.packageName, valid.appName, valid.description, "uid", "Owner",
            valid.googleGroupUrl, valid.optInUrl, valid.playStoreUrl, "case json", "draft")
        assertEquals(valid.copy(scenarioJson = "case json"), app.toDraft())
    }
    @Test fun sharedCaseRowsAreGroupedIntoTesterRuns() {
        val summary = QaTodaySummary.fromCases(listOf(
            QaPreviewCase("a","1","PASS"), QaPreviewCase("a","1","FAIL"),
            QaPreviewCase("b","1","PASS"), QaPreviewCase("b","1","PASS"),
            QaPreviewCase("a","2","INCOMPLETE")))
        assertEquals(QaTodaySummary(1,1,1), summary)
        assertEquals(3, summary.total)
        assertEquals(0, QaTodaySummary.fromCases(emptyList()).total)
    }
}
