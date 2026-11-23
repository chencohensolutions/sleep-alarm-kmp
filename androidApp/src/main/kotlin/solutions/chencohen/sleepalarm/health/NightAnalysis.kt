package solutions.chencohen.sleepalarm.health

import android.content.Context
import solutions.chencohen.sleepalarm.core.Detection
import solutions.chencohen.sleepalarm.core.HOUR_MS
import solutions.chencohen.sleepalarm.core.SleepOnsetDetector
import solutions.chencohen.sleepalarm.core.SyncLagAnalyzer
import solutions.chencohen.sleepalarm.core.SyncLagReport
import solutions.chencohen.sleepalarm.data.EventLog
import java.io.File
import java.time.Instant

data class NightResult(
    val report: SyncLagReport,
    /** This app's detector, treating the night start as bedtime. */
    val detection: Detection,
    /** What Samsung Health concluded after the fact, for comparison. */
    val samsungSleepStartMs: Long?,
    val samsungSleepEndMs: Long?,
    val stepRecords: Int,
    /** Raw export, one row per heart-rate reading. */
    val csv: File,
)

/**
 * Works on nights already stored in Health Connect: Samsung's write time
 * (metadata.lastModifiedTime) on each record shows when it arrived, so you get the
 * sync-lag answer without waiting for a new night.
 */
suspend fun analyzeNight(context: Context, nightStartMs: Long, nightEndMs: Long): NightResult {
    val hd = HealthData(context)
    val from = Instant.ofEpochMilli(nightStartMs - 2 * HOUR_MS)
    val to = Instant.ofEpochMilli(nightEndMs)

    val hr = hd.heartRateRecords(from, to)
    val steps = hd.stepRecords(from, to)
    val sleep = hd.sleepSessions(Instant.ofEpochMilli(nightStartMs - 6 * HOUR_MS), Instant.ofEpochMilli(nightEndMs + 6 * HOUR_MS))

    val report = SyncLagAnalyzer.analyze(hr.toObservations(), nightStartMs, nightEndMs)
    val detection = SleepOnsetDetector.detect(nightStartMs, hr.toSamples(), steps.toIntervals())
    val main = sleep.maxByOrNull { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }

    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val csv = File(dir, "night-${EventLog.iso(nightStartMs).take(10)}.csv")
    csv.bufferedWriter().use { w ->
        w.write("sample_time,bpm,record_id,record_written_at,lag_min,origin\n")
        for (r in hr.sortedBy { it.startTime }) {
            val written = r.metadata.lastModifiedTime.toEpochMilli()
            for (s in r.samples) {
                val t = s.time.toEpochMilli()
                w.write("${EventLog.iso(t)},${s.beatsPerMinute},${r.metadata.id},${EventLog.iso(written)},${(written - t) / 60_000},${r.metadata.dataOrigin.packageName}\n")
            }
        }
        w.write("\n# steps\nstart,end,count,origin\n")
        for (s in steps.sortedBy { it.startTime }) {
            w.write("${EventLog.iso(s.startTime.toEpochMilli())},${EventLog.iso(s.endTime.toEpochMilli())},${s.count},${s.metadata.dataOrigin.packageName}\n")
        }
        w.write("\n# samsung sleep sessions\nstart,end\n")
        for (s in sleep) {
            w.write("${EventLog.iso(s.startTime.toEpochMilli())},${EventLog.iso(s.endTime.toEpochMilli())}\n")
        }
    }

    return NightResult(
        report = report,
        detection = detection,
        samsungSleepStartMs = main?.startTime?.toEpochMilli(),
        samsungSleepEndMs = main?.endTime?.toEpochMilli(),
        stepRecords = steps.size,
        csv = csv,
    )
}
