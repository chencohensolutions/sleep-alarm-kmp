package solutions.chencohen.sleepalarm.core

enum class PlanReason {
    /** Sleep onset found: alarm = onset + target sleep. */
    SLEEP_ONSET,
    /** No onset yet (or onset + target is later than your latest wake time): ring at the deadline. */
    LATEST_WAKE,
    /** The planned time has already passed (data arrived late): ring right away. */
    OVERDUE,
}

data class AlarmPlan(val alarmAtMs: Long, val reason: PlanReason)

object AlarmPlanner {

    /**
     * The alarm never rings later than [latestWakeMs]: if the band never syncs, you still wake up.
     */
    fun plan(
        nowMs: Long,
        onsetMs: Long?,
        targetSleepMs: Long,
        latestWakeMs: Long,
        minLeadMs: Long = 5_000,
    ): AlarmPlan {
        val (at, reason) = if (onsetMs != null && onsetMs + targetSleepMs <= latestWakeMs) {
            onsetMs + targetSleepMs to PlanReason.SLEEP_ONSET
        } else {
            latestWakeMs to PlanReason.LATEST_WAKE
        }
        return if (at < nowMs + minLeadMs) AlarmPlan(nowMs + minLeadMs, PlanReason.OVERDUE)
        else AlarmPlan(at, reason)
    }
}
