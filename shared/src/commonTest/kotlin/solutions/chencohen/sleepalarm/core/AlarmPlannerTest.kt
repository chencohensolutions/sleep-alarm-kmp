package solutions.chencohen.sleepalarm.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmPlannerTest {

    private val sevenHours = 7 * HOUR_MS
    private val latestWake = clock(7, 0)

    @Test
    fun onsetPlusTarget() {
        val p = AlarmPlanner.plan(clock(1, 0), clock(23, 30), sevenHours, latestWake)
        assertEquals(AlarmPlan(clock(6, 30), PlanReason.SLEEP_ONSET), p)
    }

    @Test
    fun neverLaterThanLatestWake() {
        val p = AlarmPlanner.plan(clock(1, 0), clock(0, 30), sevenHours, latestWake)
        assertEquals(AlarmPlan(latestWake, PlanReason.LATEST_WAKE), p)
    }

    @Test
    fun noOnsetFallsBackToLatestWake() {
        val p = AlarmPlanner.plan(clock(1, 0), null, sevenHours, latestWake)
        assertEquals(AlarmPlan(latestWake, PlanReason.LATEST_WAKE), p)
    }

    @Test
    fun lateDataRingsRightAway() {
        val now = clock(6, 45)
        val p = AlarmPlanner.plan(now, clock(23, 30), sevenHours, latestWake)
        assertEquals(AlarmPlan(now + 5_000, PlanReason.OVERDUE), p)
    }
}
