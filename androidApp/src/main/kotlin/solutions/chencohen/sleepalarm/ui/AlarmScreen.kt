package solutions.chencohen.sleepalarm.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import solutions.chencohen.sleepalarm.alarm.AlarmScheduler
import solutions.chencohen.sleepalarm.alarm.NightSession
import solutions.chencohen.sleepalarm.core.PlanReason
import solutions.chencohen.sleepalarm.data.AlarmSettings
import solutions.chencohen.sleepalarm.data.EventLog
import solutions.chencohen.sleepalarm.data.Session
import solutions.chencohen.sleepalarm.data.SessionMode

@Composable
fun AlarmScreen(setup: SetupState?, session: Session?, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { AlarmSettings(ctx) }
    var targetMin by remember { mutableIntStateOf(settings.targetSleepMinutes) }
    var latestMin by remember { mutableIntStateOf(settings.latestWakeMinuteOfDay) }

    if (session?.mode == SessionMode.ALARM) {
        ArmedCard(session) {
            NightSession.stop(ctx, "alarm cancelled by user")
            onChanged()
        }
        return
    }

    SectionCard("Wake me after") {
        Text(duration(targetMin * 60_000L) + " of sleep", style = MaterialTheme.typography.headlineMedium)
        Slider(
            value = targetMin.toFloat(),
            onValueChange = { targetMin = (it / 15).toInt() * 15 },
            valueRange = 180f..600f,
            steps = (600 - 180) / 15 - 1,
        )
        TimeStepper("…but no later than", latestMin) { latestMin = it }
        Text(
            "Counting starts when the app sees you fall asleep (heart rate settles and you stop moving). " +
                "If the band's data doesn't arrive in time, the alarm rings at the latest time anyway.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (session?.mode == SessionMode.PROBE) {
            Text("A sync probe is running. Starting the alarm replaces it (the alarm logs the same data).", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            enabled = setup?.canAlarm == true,
            onClick = {
                settings.targetSleepMinutes = targetMin
                settings.latestWakeMinuteOfDay = latestMin
                NightSession.startAlarm(ctx, targetMin * 60_000L, nextOccurrence(latestMin))
                onChanged()
            },
        ) { Text("I'm going to bed") }
        if (setup?.canAlarm != true) {
            Text("Finish the setup above first.", color = MaterialTheme.colorScheme.error)
        }
    }

    SectionCard("Check the alarm works") {
        Text("Rings in 10 seconds. Lock the phone to see the full-screen alarm.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            enabled = session == null && setup?.notifications == true,
            onClick = {
                AlarmScheduler(ctx).setWakeAlarm(System.currentTimeMillis() + 10_000)
                EventLog(ctx).event("Test alarm in 10 s")
            },
        ) { Text("Test ring") }
    }
}

@Composable
private fun ArmedCard(s: Session, onStop: () -> Unit) {
    SectionCard("Alarm armed") {
        val reason = when (s.planReason?.let { runCatching { PlanReason.valueOf(it) }.getOrNull() }) {
            PlanReason.SLEEP_ONSET -> "after ${duration(s.targetSleepMs)} of sleep"
            PlanReason.OVERDUE -> "now (data arrived late)"
            else -> "latest wake — sleep not detected yet"
        }
        Text(s.plannedAlarmMs?.let(::hhmm) ?: "—", style = MaterialTheme.typography.displayMedium)
        Text(reason, style = MaterialTheme.typography.bodyLarge)
        KeyValue("Bedtime", hhmm(s.bedtimeMs))
        KeyValue("Asleep since", s.onsetMs?.let(::hhmm) ?: "not detected yet")
        KeyValue("Target", duration(s.targetSleepMs))
        KeyValue("No later than", hhmm(s.latestWakeMs))
        KeyValue("Last check", s.lastPollMs?.let(::hhmm) ?: "—")
        KeyValue(
            "Newest reading",
            s.newestSampleMs?.let { "${hhmm(it)} (${duration(System.currentTimeMillis() - it)} ago)" } ?: "—",
        )
        s.lastStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = onStop) { Text("Cancel alarm") }
    }
}
