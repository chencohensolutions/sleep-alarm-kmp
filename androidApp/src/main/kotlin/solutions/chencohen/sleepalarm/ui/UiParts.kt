package solutions.chencohen.sleepalarm.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_HHMM = DateTimeFormatter.ofPattern("EEE HH:mm")

fun hhmm(ms: Long): String = HHMM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun dayHhmm(ms: Long): String = DAY_HHMM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun duration(ms: Long?): String {
    if (ms == null) return "—"
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h ${m.toString().padStart(2, '0')} min"
    }
}

fun minuteOfDayLabel(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

/** Next time the wall clock shows [minuteOfDay], strictly after [nowMs]. */
fun nextOccurrence(minuteOfDay: Int, nowMs: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var t = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(zone)
    if (!t.isAfter(now)) t = t.plusDays(1)
    return t.toInstant().toEpochMilli()
}

/**
 * The night window [nightsAgo] nights back. Night 0 is the most recent night that has ended.
 * If [startMin] is later on the clock than [endMin] (23:00 → 07:00), the night crosses midnight.
 */
fun nightWindow(nightsAgo: Int, startMin: Int, endMin: Int, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val endToday: ZonedDateTime = now.toLocalDate().atTime(LocalTime.of(endMin / 60, endMin % 60)).atZone(zone)
    val lastEnd = if (now.isBefore(endToday)) endToday.minusDays(1) else endToday
    val end = lastEnd.minusDays(nightsAgo.toLong())
    val startDate: LocalDate = if (startMin > endMin) end.toLocalDate().minusDays(1) else end.toLocalDate()
    val start = startDate.atTime(LocalTime.of(startMin / 60, startMin % 60)).atZone(zone)
    return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
}

fun shareFile(context: Context, file: File, mime: String = "text/plain") {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name))
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun TimeStepper(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    fun shift(by: Int) = onChange(((minuteOfDay + by) % 1440 + 1440) % 1440)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { shift(-60) }) { Text("−1h") }
        OutlinedButton(onClick = { shift(-15) }) { Text("−15") }
        Text(minuteOfDayLabel(minuteOfDay), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { shift(15) }) { Text("+15") }
        OutlinedButton(onClick = { shift(60) }) { Text("+1h") }
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
