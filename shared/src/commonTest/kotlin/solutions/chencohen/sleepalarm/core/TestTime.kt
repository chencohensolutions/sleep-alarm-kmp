package solutions.chencohen.sleepalarm.core

/** Clock time on a test night: 18:00–23:59 is the evening, 00:00–11:59 is the next morning. */
fun clock(h: Int, m: Int = 0): Long {
    val day = if (h < 12) 24 * HOUR_MS else 0L
    return day + h * HOUR_MS + m * MINUTE_MS
}

/** Heart-rate readings every [everyMin] minutes from [from] (inclusive) to [to] (exclusive). */
fun hrSeries(from: Long, to: Long, everyMin: Int = 5, bpm: (Long) -> Int): List<HrSample> {
    val out = ArrayList<HrSample>()
    var t = from
    while (t < to) {
        out += HrSample(t, bpm(t))
        t += everyMin * MINUTE_MS
    }
    return out
}
