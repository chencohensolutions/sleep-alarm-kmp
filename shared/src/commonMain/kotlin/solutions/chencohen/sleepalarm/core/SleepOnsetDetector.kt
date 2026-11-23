package solutions.chencohen.sleepalarm.core

/**
 * Tunables for [SleepOnsetDetector]. Defaults are a starting point; calibrate them against
 * your own nights using the "Analyze a night" screen.
 */
data class DetectorConfig(
    /** Length of the stillness + low-heart-rate window that counts as "asleep". */
    val windowMs: Long = 20 * MINUTE_MS,
    /** How far the window slides between checks. */
    val slideMs: Long = MINUTE_MS,
    /** Minimum heart-rate readings inside a window (Fit 3 "continuous" mode logs every few minutes when still). */
    val minSamplesInWindow: Int = 3,
    /** Window median must be at least this fraction below the bedtime baseline (0.08 = 8%). */
    val dropFraction: Double = 0.08,
    /** Maximum steps allowed inside the window. */
    val maxStepsInWindow: Long = 20,
    /** Step records longer than this are too coarse to judge stillness, so they're ignored. */
    val ignoreStepIntervalsLongerThanMs: Long = 30 * MINUTE_MS,
    /** Baseline = median heart rate from this long before bedtime... */
    val baselineBeforeMs: Long = 60 * MINUTE_MS,
    /** ...until this long after bedtime. */
    val baselineAfterMs: Long = 15 * MINUTE_MS,
    val minBaselineSamples: Int = 3,
)

sealed interface Detection {
    data class Found(
        val onsetMs: Long,
        val baselineBpm: Double,
        val thresholdBpm: Double,
        val windowMedianBpm: Double,
    ) : Detection

    data class NotYet(
        val reason: String,
        val baselineBpm: Double? = null,
        val newestSampleMs: Long? = null,
    ) : Detection
}

/**
 * Finds when you fell asleep from raw heart rate + steps, independent of Samsung's own sleep
 * analysis (which only appears after you wake, and often starts late on the Fit 3).
 *
 * Rule: the first [DetectorConfig.windowMs] window after bedtime where the heart-rate median is
 * [DetectorConfig.dropFraction] below your bedtime baseline and you took almost no steps.
 * The onset is the first low reading inside that window.
 *
 * Deterministic on the data it gets, so re-running it as more data syncs is safe.
 */
object SleepOnsetDetector {

    fun detect(
        bedtimeMs: Long,
        heartRate: List<HrSample>,
        steps: List<StepInterval>,
        config: DetectorConfig = DetectorConfig(),
    ): Detection {
        val hr = heartRate.sortedBy { it.timeMs }
        val newest = hr.lastOrNull()?.timeMs

        val baselineSamples = hr.filter {
            it.timeMs >= bedtimeMs - config.baselineBeforeMs && it.timeMs <= bedtimeMs + config.baselineAfterMs
        }
        if (baselineSamples.size < config.minBaselineSamples) {
            return Detection.NotYet(
                reason = "Waiting for heart rate around bedtime (${baselineSamples.size}/${config.minBaselineSamples} readings synced)",
                newestSampleMs = newest,
            )
        }
        val baseline = medianOf(baselineSamples.map { it.bpm.toDouble() })!!
        val threshold = baseline * (1.0 - config.dropFraction)

        if (newest == null) {
            return Detection.NotYet("No heart-rate data yet", baseline, null)
        }

        val usableSteps = steps.filter { it.endMs - it.startMs <= config.ignoreStepIntervalsLongerThanMs }

        var t = bedtimeMs
        while (t + config.windowMs <= newest) {
            val windowEnd = t + config.windowMs
            val inWindow = hr.filter { it.timeMs >= t && it.timeMs < windowEnd }
            if (inWindow.size >= config.minSamplesInWindow) {
                val med = medianOf(inWindow.map { it.bpm.toDouble() })!!
                if (med <= threshold && stepsInWindow(usableSteps, t, windowEnd) <= config.maxStepsInWindow) {
                    val firstLow = inWindow.first { it.bpm <= threshold }
                    return Detection.Found(
                        onsetMs = firstLow.timeMs,
                        baselineBpm = baseline,
                        thresholdBpm = threshold,
                        windowMedianBpm = med,
                    )
                }
            }
            t += config.slideMs
        }
        return Detection.NotYet(
            reason = "No sustained heart-rate drop yet (baseline ${baseline.toInt()} bpm, need ≤ ${threshold.toInt()})",
            baselineBpm = baseline,
            newestSampleMs = newest,
        )
    }

    /** Steps inside [start, end), pro-rated for intervals that only partly overlap. */
    internal fun stepsInWindow(steps: List<StepInterval>, start: Long, end: Long): Double {
        var total = 0.0
        for (s in steps) {
            val duration = s.endMs - s.startMs
            if (duration <= 0) {
                if (s.startMs >= start && s.startMs < end) total += s.count
                continue
            }
            val overlap = minOf(end, s.endMs) - maxOf(start, s.startMs)
            if (overlap > 0) total += s.count * (overlap.toDouble() / duration)
        }
        return total
    }
}
