package solutions.chencohen.sleepalarm.alarm

import android.content.Context
import solutions.chencohen.sleepalarm.core.PlanReason
import solutions.chencohen.sleepalarm.data.EventLog
import solutions.chencohen.sleepalarm.data.Session
import solutions.chencohen.sleepalarm.data.SessionMode
import solutions.chencohen.sleepalarm.data.SessionStore

/** Starting, stopping and restoring a night. Used by the UI, the receivers and the worker. */
object NightSession {

    /**
     * Bedtime is now. The backup alarm at [latestWakeMs] is set immediately, before any data
     * arrives, so a sync failure can never leave you without an alarm.
     */
    fun startAlarm(context: Context, targetSleepMs: Long, latestWakeMs: Long) {
        val now = System.currentTimeMillis()
        val session = Session(
            mode = SessionMode.ALARM,
            bedtimeMs = now,
            targetSleepMs = targetSleepMs,
            latestWakeMs = latestWakeMs,
            plannedAlarmMs = latestWakeMs,
            planReason = PlanReason.LATEST_WAKE.name,
            lastStatus = "Waiting for the first poll",
        )
        start(context, session)
        AlarmScheduler(context).setWakeAlarm(latestWakeMs)
        EventLog(context).event(
            "Alarm night started: target ${targetSleepMs / 60_000} min, latest wake ${EventLog.fmt(latestWakeMs)}",
        )
    }

    fun startProbe(context: Context, untilMs: Long) {
        val session = Session(
            mode = SessionMode.PROBE,
            bedtimeMs = System.currentTimeMillis(),
            targetSleepMs = 0,
            latestWakeMs = untilMs,
            lastStatus = "Waiting for the first poll",
        )
        start(context, session)
        EventLog(context).event("Probe started, polling every 15 min until ${EventLog.fmt(untilMs)}")
    }

    private fun start(context: Context, session: Session) {
        val scheduler = AlarmScheduler(context)
        scheduler.cancelWakeAlarm()
        scheduler.cancelPolls()
        SessionStore(context).save(session)
        Notifications.showStatus(context, session)
        scheduler.pollNow()
    }

    fun stop(context: Context, reason: String) {
        val scheduler = AlarmScheduler(context)
        scheduler.cancelWakeAlarm()
        scheduler.cancelPolls()
        SessionStore(context).clear()
        Notifications.cancelStatus(context)
        EventLog(context).event("Session stopped: $reason")
    }

    /** The wake alarm went off: end the night but keep the log. */
    fun onWakeAlarmFired(context: Context) {
        val store = SessionStore(context)
        val s = store.load()
        val scheduler = AlarmScheduler(context)
        scheduler.cancelPolls()
        store.clear()
        Notifications.cancelStatus(context)
        val slept = s?.onsetMs?.let { " — asleep since ${EventLog.fmt(it)}" } ?: " — onset never detected"
        EventLog(context).event("Wake alarm rang (${s?.planReason ?: "no session"})$slept")
    }

    /** Alarms are wiped on reboot. Put them back. */
    fun restoreAfterBoot(context: Context) {
        val s = SessionStore(context).load() ?: return
        val scheduler = AlarmScheduler(context)
        val now = System.currentTimeMillis()
        if (s.mode == SessionMode.ALARM) {
            val at = s.plannedAlarmMs ?: s.latestWakeMs
            scheduler.setWakeAlarm(if (at > now) at else now + 5_000)
        }
        scheduler.schedulePoll(now + 60_000)
        EventLog(context).event("Restored after reboot")
    }
}
