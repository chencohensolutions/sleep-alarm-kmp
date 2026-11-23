package solutions.chencohen.sleepalarm.core

enum class Feasibility {
    /** Data reaches the phone often enough to detect onset well before the alarm. */
    WORKS,
    /** Usable for long sleep targets, but onset will be detected late. */
    MARGINAL,
    /** Data mostly arrives in the morning: the smart alarm can't work with this setup. */
    NOT_VIABLE,
    /** Not enough heart-rate data in the window to judge. */
    NO_DATA,
}

data class SyncLagReport(
    val nightStartMs: Long,
    val nightEndMs: Long,
    val sampleCount: Int,
    /** Distinct sync batches (records with distinct write times) that carried this night's data. */
    val writeBatches: Int,
    val origins: Set<String>,
    /** Typical gap between heart-rate readings: tells you if "continuous" measurement is really on. */
    val medianSampleIntervalMs: Long?,
    /** Per reading: time it became visible in Health Connect minus time it was measured. */
    val medianIngestLagMs: Long?,
    val p90IngestLagMs: Long?,
    val maxIngestLagMs: Long?,
    /**
     * Checked minute by minute through the night: how old the newest visible reading was.
     * This is what the alarm app actually experiences.
     */
    val medianStalenessMs: Long?,
    val p90StalenessMs: Long?,
    val maxStalenessMs: Long?,
    val feasibility: Feasibility,
)

object SyncLagAnalyzer {

    /**
     * @param worksIfP90StalenessUnderMs verdict WORKS when 90% of the night the newest visible
     *   reading was at most this old.
     * @param marginalIfP90StalenessUnderMs verdict MARGINAL up to this, NOT_VIABLE beyond.
     */
    fun analyze(
        records: List<HrRecordObservation>,
        nightStartMs: Long,
        nightEndMs: Long,
        worksIfP90StalenessUnderMs: Long = 90 * MINUTE_MS,
        marginalIfP90StalenessUnderMs: Long = 3 * HOUR_MS,
    ): SyncLagReport {
        require(nightEndMs > nightStartMs) { "nightEnd must be after nightStart" }

        // Readings measured during the night, each tagged with when it became visible.
        data class Obs(val sampleMs: Long, val visibleMs: Long)
        val obs = records.flatMap { r ->
            r.sampleTimesMs
                .filter { it in nightStartMs..nightEndMs }
                .map { Obs(it, maxOf(r.lastModifiedMs, it)) }
        }

        val nightRecords = records.filter { r -> r.sampleTimesMs.any { it in nightStartMs..nightEndMs } }
        val sampleTimes = obs.map { it.sampleMs }.sorted()
        val intervals = sampleTimes.zipWithNext { a, b -> b - a }.filter { it > 0 }
        val ingestLags = obs.map { it.visibleMs - it.sampleMs }

        // Staleness sweep, 1-minute grid. Readings from before the night count as "fresh enough"
        // at the start, so evening data synced before bedtime isn't penalised.
        val priorNewest = records
            .flatMap { r -> r.sampleTimesMs.filter { it < nightStartMs }.map { it to r.lastModifiedMs } }
            .filter { (_, visible) -> visible <= nightStartMs }
            .maxOfOrNull { it.first }
        val byVisible = obs.sortedBy { it.visibleMs }
        val staleness = ArrayList<Long>()
        var idx = 0
        var newestVisible: Long? = priorNewest
        var tau = nightStartMs
        while (tau <= nightEndMs) {
            while (idx < byVisible.size && byVisible[idx].visibleMs <= tau) {
                val s = byVisible[idx].sampleMs
                if (s <= tau && (newestVisible == null || s > newestVisible)) newestVisible = s
                idx++
            }
            staleness += tau - (newestVisible ?: nightStartMs)
            tau += MINUTE_MS
        }

        val p90Stale = percentileLong(staleness, 0.9)
        val feasibility = when {
            obs.size < 3 -> Feasibility.NO_DATA
            p90Stale!! <= worksIfP90StalenessUnderMs -> Feasibility.WORKS
            p90Stale <= marginalIfP90StalenessUnderMs -> Feasibility.MARGINAL
            else -> Feasibility.NOT_VIABLE
        }

        return SyncLagReport(
            nightStartMs = nightStartMs,
            nightEndMs = nightEndMs,
            sampleCount = obs.size,
            writeBatches = nightRecords.map { it.lastModifiedMs }.distinct().size,
            origins = nightRecords.map { it.origin }.toSet(),
            medianSampleIntervalMs = medianLong(intervals),
            medianIngestLagMs = medianLong(ingestLags),
            p90IngestLagMs = percentileLong(ingestLags, 0.9),
            maxIngestLagMs = ingestLags.maxOrNull(),
            medianStalenessMs = medianLong(staleness),
            p90StalenessMs = p90Stale,
            maxStalenessMs = staleness.maxOrNull(),
            feasibility = feasibility,
        )
    }
}
