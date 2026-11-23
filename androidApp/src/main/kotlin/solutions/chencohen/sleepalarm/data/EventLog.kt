package solutions.chencohen.sleepalarm.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Two append-only files in the app's private storage:
 *  - events.log: human-readable timeline (polls, alarm changes, errors)
 *  - polls.csv: one row per overnight poll, for the sync-lag research
 */
class EventLog(context: Context) {

    private val dir = context.applicationContext.filesDir
    val eventsFile = File(dir, "events.log")
    val pollsFile = File(dir, "polls.csv")

    @Synchronized
    fun event(message: String) {
        eventsFile.appendText("${fmt(System.currentTimeMillis())}  $message\n")
    }

    @Synchronized
    fun poll(
        pollMs: Long,
        mode: SessionMode,
        records: Int,
        samples: Int,
        newestSampleMs: Long?,
        newestWriteMs: Long?,
        detection: String,
    ) {
        if (!pollsFile.exists()) {
            pollsFile.writeText(
                "poll_time,mode,hr_records,hr_samples,newest_sample_time,newest_write_time,staleness_min,detection\n",
            )
        }
        val staleMin = newestSampleMs?.let { (pollMs - it) / 60_000 }?.toString() ?: ""
        pollsFile.appendText(
            listOf(
                iso(pollMs), mode.name, records.toString(), samples.toString(),
                newestSampleMs?.let(::iso) ?: "", newestWriteMs?.let(::iso) ?: "", staleMin,
                "\"" + detection.replace("\"", "'") + "\"",
            ).joinToString(",") + "\n",
        )
    }

    fun recentEvents(maxLines: Int = 60): List<String> =
        if (!eventsFile.exists()) emptyList() else eventsFile.readLines().takeLast(maxLines).reversed()

    companion object {
        private val LOCAL = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        fun fmt(ms: Long): String = LOCAL.format(Instant.ofEpochMilli(ms))
        fun iso(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
    }
}
