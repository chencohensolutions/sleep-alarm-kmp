package solutions.chencohen.sleepalarm.data

import android.content.Context
import androidx.core.content.edit

enum class SessionMode {
    /** Polls overnight and logs what arrived when. No alarm. */
    PROBE,
    /** Polls, detects sleep onset and moves the alarm to onset + target. */
    ALARM,
}

/** One night. Only one can be active at a time. */
data class Session(
    val mode: SessionMode,
    val bedtimeMs: Long,
    val targetSleepMs: Long,
    /** ALARM: never ring later than this. PROBE: stop polling at this time. */
    val latestWakeMs: Long,
    val onsetMs: Long? = null,
    val plannedAlarmMs: Long? = null,
    val planReason: String? = null,
    val lastPollMs: Long? = null,
    val newestSampleMs: Long? = null,
    val lastStatus: String? = null,
)

/** SharedPreferences-backed, so the receivers and the worker can read it without the UI. */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val mode = prefs.getString(K_MODE, null)?.let { runCatching { SessionMode.valueOf(it) }.getOrNull() }
            ?: return null
        return Session(
            mode = mode,
            bedtimeMs = prefs.getLong(K_BEDTIME, 0),
            targetSleepMs = prefs.getLong(K_TARGET, 0),
            latestWakeMs = prefs.getLong(K_LATEST, 0),
            onsetMs = prefs.longOrNull(K_ONSET),
            plannedAlarmMs = prefs.longOrNull(K_PLANNED),
            planReason = prefs.getString(K_REASON, null),
            lastPollMs = prefs.longOrNull(K_LAST_POLL),
            newestSampleMs = prefs.longOrNull(K_NEWEST),
            lastStatus = prefs.getString(K_STATUS, null),
        )
    }

    fun save(s: Session) = prefs.edit(commit = true) {
        putString(K_MODE, s.mode.name)
        putLong(K_BEDTIME, s.bedtimeMs)
        putLong(K_TARGET, s.targetSleepMs)
        putLong(K_LATEST, s.latestWakeMs)
        putLongOrRemove(K_ONSET, s.onsetMs)
        putLongOrRemove(K_PLANNED, s.plannedAlarmMs)
        putString(K_REASON, s.planReason)
        putLongOrRemove(K_LAST_POLL, s.lastPollMs)
        putLongOrRemove(K_NEWEST, s.newestSampleMs)
        putString(K_STATUS, s.lastStatus)
    }

    fun clear() = prefs.edit(commit = true) { clear() }

    private fun android.content.SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0) else null

    private fun android.content.SharedPreferences.Editor.putLongOrRemove(key: String, v: Long?) {
        if (v == null) remove(key) else putLong(key, v)
    }

    private companion object {
        const val K_MODE = "mode"
        const val K_BEDTIME = "bedtime"
        const val K_TARGET = "target"
        const val K_LATEST = "latest"
        const val K_ONSET = "onset"
        const val K_PLANNED = "planned"
        const val K_REASON = "reason"
        const val K_LAST_POLL = "lastPoll"
        const val K_NEWEST = "newest"
        const val K_STATUS = "status"
    }
}

/** Last-used settings for the alarm screen. */
class AlarmSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var targetSleepMinutes: Int
        get() = prefs.getInt("targetMin", 7 * 60)
        set(v) = prefs.edit { putInt("targetMin", v) }

    /** Minutes after midnight. */
    var latestWakeMinuteOfDay: Int
        get() = prefs.getInt("latestWake", 7 * 60 + 30)
        set(v) = prefs.edit { putInt("latestWake", v) }

    /** Minutes after midnight; night window used by the analysis screen. */
    var nightStartMinuteOfDay: Int
        get() = prefs.getInt("nightStart", 23 * 60)
        set(v) = prefs.edit { putInt("nightStart", v) }

    var nightEndMinuteOfDay: Int
        get() = prefs.getInt("nightEnd", 7 * 60)
        set(v) = prefs.edit { putInt("nightEnd", v) }
}
