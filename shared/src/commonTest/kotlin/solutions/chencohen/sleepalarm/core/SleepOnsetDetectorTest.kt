package solutions.chencohen.sleepalarm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SleepOnsetDetectorTest {

    private val bedtime = clock(23, 0)
    private val asleepAt = clock(23, 30)

    /** 72 bpm awake, 60 bpm asleep, readings every 5 minutes from 22:00 to 07:00. */
    private val typicalHr = hrSeries(clock(22, 0), clock(7, 0)) { t -> if (t < asleepAt) 72 else 60 }
    private val eveningWalk = listOf(StepInterval(clock(22, 0), clock(22, 30), 300))

    @Test
    fun findsOnsetAtFirstLowReading() {
        val d = SleepOnsetDetector.detect(bedtime, typicalHr, eveningWalk)
        assertIs<Detection.Found>(d)
        assertEquals(asleepAt, d.onsetMs)
        assertEquals(72.0, d.baselineBpm)
    }

    @Test
    fun stepsDelayOnsetUntilStill() {
        // Low heart rate from 23:30, but still walking (100 steps / 10 min) until 00:30.
        val walking = (0 until 9).map { i ->
            val s = clock(23, 0) + i * 10 * MINUTE_MS
            StepInterval(s, s + 10 * MINUTE_MS, 100)
        }
        val d = SleepOnsetDetector.detect(bedtime, typicalHr, walking)
        assertIs<Detection.Found>(d)
        assertEquals(clock(0, 30), d.onsetMs)
    }

    @Test
    fun coarseStepBucketsAreIgnored() {
        val coarse = listOf(StepInterval(clock(22, 30), clock(0, 30), 3000))
        val d = SleepOnsetDetector.detect(bedtime, typicalHr, coarse)
        assertIs<Detection.Found>(d)
        assertEquals(asleepAt, d.onsetMs)
    }

    @Test
    fun waitsForBaseline() {
        val lateOnly = typicalHr.filter { it.timeMs >= clock(0, 0) }
        val d = SleepOnsetDetector.detect(bedtime, lateOnly, emptyList())
        assertIs<Detection.NotYet>(d)
        assertTrue(d.reason.startsWith("Waiting for heart rate around bedtime"))
    }

    @Test
    fun noDropMeansNotYet() {
        val awakeAllNight = hrSeries(clock(22, 0), clock(7, 0)) { 72 }
        val d = SleepOnsetDetector.detect(bedtime, awakeAllNight, emptyList())
        assertIs<Detection.NotYet>(d)
        assertEquals(72.0, d.baselineBpm)
    }

    @Test
    fun doesNotFireBeforeAFullWindowHasSynced() {
        // Data synced only up to 23:35: one low reading isn't enough evidence yet.
        val partial = typicalHr.filter { it.timeMs <= clock(23, 35) }
        val d = SleepOnsetDetector.detect(bedtime, partial, eveningWalk)
        assertIs<Detection.NotYet>(d)
    }

    @Test
    fun stepsArePartiallyCountedWhenOverlapping() {
        val steps = listOf(StepInterval(0, 10 * MINUTE_MS, 100))
        assertEquals(50.0, SleepOnsetDetector.stepsInWindow(steps, 5 * MINUTE_MS, 20 * MINUTE_MS))
        assertEquals(0.0, SleepOnsetDetector.stepsInWindow(steps, 10 * MINUTE_MS, 20 * MINUTE_MS))
    }
}
