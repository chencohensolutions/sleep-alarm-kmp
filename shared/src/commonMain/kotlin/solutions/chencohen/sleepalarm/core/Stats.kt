package solutions.chencohen.sleepalarm.core

internal fun medianOf(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
}

internal fun medianLong(values: List<Long>): Long? =
    medianOf(values.map { it.toDouble() })?.toLong()

/** Nearest-rank percentile, [p] in 0..1. */
internal fun percentileLong(values: List<Long>, p: Double): Long? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val rank = kotlin.math.ceil(p * s.size).toInt().coerceIn(1, s.size)
    return s[rank - 1]
}
