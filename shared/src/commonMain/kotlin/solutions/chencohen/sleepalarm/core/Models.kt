package solutions.chencohen.sleepalarm.core

const val MINUTE_MS: Long = 60_000L
const val HOUR_MS: Long = 60 * MINUTE_MS

/** One heart-rate reading. */
data class HrSample(val timeMs: Long, val bpm: Int)

/** Steps counted between [startMs] and [endMs]. Samsung may write these in coarse buckets. */
data class StepInterval(val startMs: Long, val endMs: Long, val count: Long)

/**
 * One Health Connect heart-rate record as written by Samsung Health: a batch of samples
 * that became visible at [lastModifiedMs]. The gap between a sample's time and
 * [lastModifiedMs] is the sync delay we need to measure.
 */
data class HrRecordObservation(
    val recordId: String,
    val sampleTimesMs: List<Long>,
    val lastModifiedMs: Long,
    val origin: String,
)
