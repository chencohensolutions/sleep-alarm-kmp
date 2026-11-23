package solutions.chencohen.sleepalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import solutions.chencohen.sleepalarm.MainActivity
import solutions.chencohen.sleepalarm.data.EventLog

/**
 * Two kinds of system alarms:
 *  - the wake alarm: AlarmManager.setAlarmClock, the most reliable kind (shown in the status bar,
 *    exempt from Doze);
 *  - poll ticks every [POLL_INTERVAL_MS]: setExactAndAllowWhileIdle, each one runs [PollWorker].
 *    Exact ticks are used instead of periodic WorkManager because periodic work can be
 *    deferred for hours in deep Doze.
 */
class AlarmScheduler(private val context: Context) {

    private val am = context.getSystemService(AlarmManager::class.java)
    private val log = EventLog(context)

    fun canScheduleExact(): Boolean = am.canScheduleExactAlarms()

    fun setWakeAlarm(atMs: Long) {
        val show = PendingIntent.getActivity(
            context, REQ_SHOW, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), wakePendingIntent())
        } catch (e: SecurityException) {
            // Exact alarms not allowed (Android 12 without the user's OK). Fall back so we still ring.
            log.event("Exact alarm not permitted, using inexact fallback: ${e.message}")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, wakePendingIntent())
        }
    }

    fun cancelWakeAlarm() = am.cancel(wakePendingIntent())

    fun schedulePoll(atMs: Long) {
        val pi = pollPendingIntent()
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    fun cancelPolls() {
        am.cancel(pollPendingIntent())
        WorkManager.getInstance(context).cancelUniqueWork(PollWorker.UNIQUE_NAME)
    }

    /** Run a poll now (also used by the poll tick receiver). */
    fun pollNow() {
        val request = OneTimeWorkRequestBuilder<PollWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(PollWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun wakePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_WAKE, Intent(context, WakeAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pollPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_POLL, Intent(context, PollTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val POLL_INTERVAL_MS: Long = 15 * 60_000L
        private const val REQ_WAKE = 1
        private const val REQ_POLL = 2
        private const val REQ_SHOW = 3
    }
}
