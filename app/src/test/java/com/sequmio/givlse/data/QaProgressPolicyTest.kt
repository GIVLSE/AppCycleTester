package com.sequmio.givlse.data
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class QaProgressPolicyTest {
    @Test fun lastFourteenDays() {
        val days=QaProgressPolicy.dates(LocalDate.parse("2026-11-27"))
        assertEquals(14,days.size);assertEquals("2026-11-14",days.first());assertEquals("2026-11-27",days.last())
    }
    @Test fun confirmedAssertionFailureCountsAsTesting() {
        val target=JSONObject().put("origin","community").put("confirmed",1)
            .put("scenarios",JSONArray().put(JSONObject().put("status","FAIL")))
        assertTrue(QaProgressPolicy.qualifies(target))
        assertFalse(QaProgressPolicy.qualifies(target.put("confirmed",0)))
    }
    @Test fun blockedOnlyDoesNotCount() {
        assertFalse(QaProgressPolicy.qualifies(JSONObject().put("origin","community").put("confirmed",1)
            .put("scenarios",JSONArray().put(JSONObject().put("status","BLOCKED")))))
    }
    @Test fun reminderOnceOnlyAndOnlyWhenDue() {
        assertTrue(QaProgressPolicy.due(1080,1080,"today","yesterday",2))
        assertFalse(QaProgressPolicy.due(1079,1080,"today","yesterday",2))
        assertFalse(QaProgressPolicy.due(1080,1080,"today","today",2))
        assertFalse(QaProgressPolicy.due(1080,1080,"today","yesterday",0))
    }
}
