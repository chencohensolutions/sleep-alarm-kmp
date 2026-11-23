package solutions.chencohen.sleepalarm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncLagAnalyzerTest {

    private val nightStart = clock(23, 0)
    private val nightEnd = clock(7, 0)

    private fun samples(from: Long, to: Long) = hrSeries(from, to) { 60 }.map { it.timeMs }

    @Test
    fun hourlyBatchesAreWorkable() {
        // Each hour's readings (every 5 min) are written at the top of the next hour.
        val records = (0 until 8).map { h ->
            val start = nightStart + h * HOUR_MS
            HrRecordObservation("r$h", samples(start, start + HOUR_MS), start + HOUR_MS, "com.sec.android.app.shealth")
        }
        val r = SyncLagAnalyzer.analyze(records, nightStart, nightEnd)

        assertEquals(96, r.sampleCount)
        assertEquals(8, r.writeBatches)
        assertEquals(5 * MINUTE_MS, r.medianSampleIntervalMs)
        // Readings are 5–60 min old when written.
        assertEquals(60 * MINUTE_MS, r.maxIngestLagMs)
        // Just before each write the newest visible reading is from 64 min ago (59 min in the first hour).
        assertEquals(64 * MINUTE_MS, r.maxStalenessMs)
        assertEquals(Feasibility.WORKS, r.feasibility)
    }

    @Test
    fun morningOnlySyncIsNotViable() {
        val records = listOf(
            HrRecordObservation("night", samples(nightStart, nightEnd), clock(7, 30), "com.sec.android.app.shealth"),
        )
        val r = SyncLagAnalyzer.analyze(records, nightStart, nightEnd)
        assertEquals(1, r.writeBatches)
        assertEquals(8 * HOUR_MS, r.maxStalenessMs)
        assertEquals(Feasibility.NOT_VIABLE, r.feasibility)
    }

    @Test
    fun eveningDataCountsAsFreshAtNightStart() {
        val evening = HrRecordObservation("eve", samples(clock(22, 0), nightStart), clock(22, 59), "x")
        val r = SyncLagAnalyzer.analyze(listOf(evening), nightStart, nightEnd)
        // No night readings at all.
        assertEquals(Feasibility.NO_DATA, r.feasibility)
        // Newest visible reading at night start is 22:55.
        assertTrue(r.maxStalenessMs!! >= 8 * HOUR_MS)
    }

    @Test
    fun emptyIsNoData() {
        val r = SyncLagAnalyzer.analyze(emptyList(), nightStart, nightEnd)
        assertEquals(Feasibility.NO_DATA, r.feasibility)
        assertEquals(0, r.sampleCount)
    }
}
