package solutions.chencohen.sleepalarm.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import solutions.chencohen.sleepalarm.core.AlarmPlanner
import solutions.chencohen.sleepalarm.core.Detection
import solutions.chencohen.sleepalarm.core.HOUR_MS
import solutions.chencohen.sleepalarm.core.SleepOnsetDetector
import solutions.chencohen.sleepalarm.data.EventLog
import solutions.chencohen.sleepalarm.data.SessionMode
import solutions.chencohen.sleepalarm.data.SessionStore
import solutions.chencohen.sleepalarm.health.HealthData
import solutions.chencohen.sleepalarm.health.toIntervals
import solutions.chencohen.sleepalarm.health.toSamples
import java.time.Instant
import kotlin.math.abs

/**
 * One overnight poll: read what Samsung Health has synced so far, log it (sync-lag research),
 * and in ALARM mode re-run onset detection and move the wake alarm if the plan changed.
 * Always schedules the next tick while the session is active.
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SessionStore(ctx)
        val log = EventLog(ctx)
        val scheduler = AlarmScheduler(ctx)

        val session = store.load() ?: return Result.success()
        val now = System.currentTimeMillis()

        if (session.mode == SessionMode.PROBE && now >= session.latestWakeMs) {
            log.event("Probe finished")
            NightSession.stop(ctx, reason = "probe window ended")
            return Result.success()
        }

        var updated = session.copy(lastPollMs = now)
        try {
            val hd = HealthData(ctx)
            val from = Instant.ofEpochMilli(session.bedtimeMs - 2 * HOUR_MS)
            val to = Instant.ofEpochMilli(now + 60_000)
            val hr = hd.heartRateRecords(from, to)
            val steps = hd.stepRecords(from, to)

            val samples = hr.toSamples()
            val newest = samples.lastOrNull()?.timeMs
            val newestWrite = hr.maxOfOrNull { it.metadata.lastModifiedTime.toEpochMilli() }
            val detection = SleepOnsetDetector.detect(session.bedtimeMs, samples, steps.toIntervals())
            val detectionText = when (detection) {
                is Detection.Found ->
                    "Asleep since ${EventLog.fmt(detection.onsetMs)} (baseline ${detection.baselineBpm.toInt()} bpm, now ~${detection.windowMedianBpm.toInt()})"
                is Detection.NotYet -> detection.reason
            }
            log.poll(now, session.mode, hr.size, samples.size, newest, newestWrite, detectionText)
            updated = updated.copy(newestSampleMs = newest, lastStatus = detectionText)

            if (session.mode == SessionMode.ALARM) {
                val onset = (detection as? Detection.Found)?.onsetMs
                val plan = AlarmPlanner.plan(now, onset, session.targetSleepMs, session.latestWakeMs)
                val previous = session.plannedAlarmMs
                val changed = previous == null || abs(plan.alarmAtMs - previous) > 30_000
                if (changed) {
                    scheduler.setWakeAlarm(plan.alarmAtMs)
                    log.event("Alarm set for ${EventLog.fmt(plan.alarmAtMs)} (${plan.reason})")
                }
                updated = updated.copy(
                    onsetMs = onset,
                    plannedAlarmMs = if (changed) plan.alarmAtMs else previous,
                    planReason = if (changed) plan.reason.name else session.planReason,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            updated = updated.copy(lastStatus = "Health Connect permission missing (is background read allowed?)")
            log.event("Poll failed, permission: ${e.message}")
        } catch (e: IllegalStateException) {
            updated = updated.copy(lastStatus = "Health Connect unavailable or rate-limited")
            log.event("Poll failed: ${e.message}")
        } catch (e: Exception) {
            updated = updated.copy(lastStatus = "Poll error: ${e.javaClass.simpleName}")
            log.event("Poll failed: $e")
        }

        // The session may have been stopped (or the alarm fired) while we were reading: don't revive it.
        if (store.load() != null) {
            store.save(updated)
            scheduler.schedulePoll(now + AlarmScheduler.POLL_INTERVAL_MS)
            Notifications.showStatus(ctx, updated)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "night-poll"
    }
}
