package solutions.chencohen.sleepalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import solutions.chencohen.sleepalarm.alarm.NightSession
import solutions.chencohen.sleepalarm.core.Detection
import solutions.chencohen.sleepalarm.core.Feasibility
import solutions.chencohen.sleepalarm.data.AlarmSettings
import solutions.chencohen.sleepalarm.data.EventLog
import solutions.chencohen.sleepalarm.data.Session
import solutions.chencohen.sleepalarm.data.SessionMode
import solutions.chencohen.sleepalarm.health.NightResult
import solutions.chencohen.sleepalarm.health.analyzeNight

@Composable
fun ProbeScreen(setup: SetupState?, session: Session?, events: List<String>, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { AlarmSettings(ctx) }
    val scope = rememberCoroutineScope()

    var nightsAgo by remember { mutableIntStateOf(0) }
    var startMin by remember { mutableIntStateOf(settings.nightStartMinuteOfDay) }
    var endMin by remember { mutableIntStateOf(settings.nightEndMinuteOfDay) }
    var result by remember { mutableStateOf<NightResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SectionCard("1 · Check a past night (instant)") {
        Text(
            "Samsung stamps every heart-rate record with the time it reached the phone, so past nights " +
                "already show how late the data arrives. Pick a night you wore the band.",
            style = MaterialTheme.typography.bodySmall,
        )
        val (ws, we) = nightWindow(nightsAgo, startMin, endMin)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { nightsAgo = (nightsAgo + 1).coerceAtMost(29); result = null }) { Text("◀ Older") }
            Text(
                "${dayHhmm(ws)} → ${dayHhmm(we)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(onClick = { nightsAgo = (nightsAgo - 1).coerceAtLeast(0); result = null }, enabled = nightsAgo > 0) { Text("Newer ▶") }
        }
        TimeStepper("From", startMin) { startMin = it; settings.nightStartMinuteOfDay = it; result = null }
        TimeStepper("To", endMin) { endMin = it; settings.nightEndMinuteOfDay = it; result = null }
        Button(
            enabled = !busy && setup?.canProbe == true,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        result = withContext(Dispatchers.IO) { analyzeNight(ctx, ws, we) }
                    } catch (e: Exception) {
                        error = "${e.javaClass.simpleName}: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Reading…" else "Analyze this night") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { NightReport(it) }
    }

    SectionCard("2 · Overnight probe (tonight)") {
        Text(
            "Polls Health Connect every 15 minutes in the background and logs what had arrived each time. " +
                "This confirms background reads keep working with the screen off. Leave the phone charging.",
            style = MaterialTheme.typography.bodySmall,
        )
        when (session?.mode) {
            SessionMode.PROBE -> {
                KeyValue("Running until", hhmm(session.latestWakeMs))
                KeyValue("Last check", session.lastPollMs?.let(::hhmm) ?: "—")
                KeyValue(
                    "Newest reading",
                    session.newestSampleMs?.let { "${hhmm(it)} (${duration(System.currentTimeMillis() - it)} ago)" } ?: "—",
                )
                session.lastStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { NightSession.stop(ctx, "probe stopped by user"); onChanged() }) { Text("Stop probe") }
            }
            SessionMode.ALARM -> Text("An alarm night is running; it logs the same data. Check the Alarm tab.")
            null -> Button(
                enabled = setup?.canRunOvernight == true,
                onClick = {
                    NightSession.startProbe(ctx, nextOccurrence(endMin))
                    onChanged()
                },
            ) { Text("Start probe until ${minuteOfDayLabel(endMin)}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val log = remember { EventLog(ctx) }
            OutlinedButton(onClick = { shareFile(ctx, log.pollsFile, "text/csv") }) { Text("Export polls.csv") }
            OutlinedButton(onClick = { shareFile(ctx, log.eventsFile) }) { Text("Export log") }
        }
    }

    SectionCard("Log") {
        if (events.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall)
        events.take(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun NightReport(r: NightResult) {
    val ctx = LocalContext.current
    val rep = r.report
    val (verdict, color, advice) = when (rep.feasibility) {
        Feasibility.WORKS -> Triple(
            "Works", Color(0xFF2E7D32),
            "Data reaches the phone often enough. The alarm would know you're asleep within about ${duration(rep.p90StalenessMs)}.",
        )
        Feasibility.MARGINAL -> Triple(
            "Marginal", Color(0xFFF9A825),
            "Usable for long sleep targets, but sleep onset would be detected late. Try with the phone charging.",
        )
        Feasibility.NOT_VIABLE -> Triple(
            "Not viable this night", MaterialTheme.colorScheme.error,
            "The data mostly landed in the morning. Try: phone charging overnight, band heart rate on Continuous, " +
                "Samsung Health and the Fit3 plugin not battery-restricted. Then re-test.",
        )
        Feasibility.NO_DATA -> Triple(
            "No data", MaterialTheme.colorScheme.error,
            "No heart rate in this window. Check the Fit 3 heart rate is Continuous and Samsung Health syncs to Health Connect.",
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(verdict, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(advice, style = MaterialTheme.typography.bodySmall)
        KeyValue("Heart-rate readings", "${rep.sampleCount} (one every ~${duration(rep.medianSampleIntervalMs)})")
        KeyValue("Separate syncs that carried them", rep.writeBatches.toString())
        Text("Delay from measurement to phone", style = MaterialTheme.typography.labelLarge)
        KeyValue("  median / 90% / worst", "${duration(rep.medianIngestLagMs)} / ${duration(rep.p90IngestLagMs)} / ${duration(rep.maxIngestLagMs)}")
        Text("How old the newest reading was, minute by minute", style = MaterialTheme.typography.labelLarge)
        KeyValue("  median / 90% / worst", "${duration(rep.medianStalenessMs)} / ${duration(rep.p90StalenessMs)} / ${duration(rep.maxStalenessMs)}")
        KeyValue("Step records", r.stepRecords.toString())
        KeyValue(
            "Samsung's sleep",
            if (r.samsungSleepStartMs != null && r.samsungSleepEndMs != null) "${hhmm(r.samsungSleepStartMs)} – ${hhmm(r.samsungSleepEndMs)}" else "none",
        )
        KeyValue(
            "This app's onset",
            when (val d = r.detection) {
                is Detection.Found -> "${hhmm(d.onsetMs)} (${d.baselineBpm.toInt()} → ${d.windowMedianBpm.toInt()} bpm)"
                is Detection.NotYet -> "not found"
            },
        )
        (r.detection as? Detection.NotYet)?.let { Text(it.reason, style = MaterialTheme.typography.bodySmall) }
        if (rep.origins.isNotEmpty()) KeyValue("Source", rep.origins.joinToString())
        OutlinedButton(onClick = { shareFile(ctx, r.csv, "text/csv") }) { Text("Export this night (CSV)") }
    }
}
