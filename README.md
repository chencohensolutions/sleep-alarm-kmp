# Sleep Alarm

An Android alarm clock that wakes you after **X hours of actual sleep**, using heart rate from a
**Samsung Galaxy Fit 3**.

## Why it works this way

The Fit 3 runs FreeRTOS: no third-party code on the band and no live sensor access. Samsung's own
sleep record only appears after you wake up. The only path in is the band's **heart-rate and step
data**, which Samsung Health copies into **Health Connect** on the phone, batched and late.

That's enough, because the alarm doesn't need live data. It only needs to know when you fell
asleep before your X hours are up. Fall asleep at 23:20 and learn it at 01:00, and the alarm can
still ring at 06:20.

So the app:

1. Polls Health Connect every 15 minutes overnight (exact alarms, so Doze can't defer it).
2. Runs its own sleep-onset detector on the raw heart rate and steps: a 20-minute window with the
   median heart rate ≥ 8% below your bedtime baseline and almost no steps.
3. Moves the wake alarm to **onset + target**, never later than the **latest wake** time.
4. Sets that latest-wake alarm the moment you tap "I'm going to bed", so a sync failure can never
   leave you without an alarm.

## The open question, and how to test it

Nobody documents how often the Fit 3's heart rate reaches the phone at night. If it's every
15–60 minutes, the app works. If it only arrives in the morning, it can't.

**Test sync → 1 · Check a past night** answers this **today**, without waiting for a new night.
Samsung stamps each Health Connect record with the time it was written
(`metadata.lastModifiedTime`), so past nights already show the delay. The report gives:

| Metric | Meaning |
|---|---|
| Readings / interval | Is the band's heart rate really on Continuous? |
| Separate syncs | How many batches carried the night's data |
| Delay to phone | Measurement time → time it was written to Health Connect |
| **How old the newest reading was** | Checked minute by minute: what the alarm would actually see. The verdict uses its 90th percentile. |
| Samsung's sleep vs this app's onset | Compare the detector against Samsung's after-the-fact result |

Verdicts: **Works** (90% of the night the data is under 90 min old), **Marginal** (under 3 h),
**Not viable**.

Caveat: if Samsung rewrites a record later, its timestamp moves later and the delay looks worse
than it was. **2 · Overnight probe** measures it live instead: it polls every 15 minutes and logs
what had arrived each time (`polls.csv`). It also confirms that background reads keep working with
the screen off.

## Setup

1. On the Fit 3: Settings → Health → Heart rate → **Continuous**.
2. Samsung Health → Settings → **Health Connect**: allow Samsung Health to write heart rate, steps
   and sleep.
3. In the app, finish the Setup card: Health Connect read + **background read**, notifications,
   exact alarms, full-screen alarm.
4. Keep the phone **charging overnight**. Doze doesn't apply while charging, which likely makes
   Samsung Health sync more often.
5. Settings → Apps → Samsung Health / Galaxy Fit3 Plugin → Battery → **Unrestricted**.

## Project layout

```
shared/       Kotlin Multiplatform, pure logic + tests (no Android)
  SleepOnsetDetector   raw HR + steps → onset time
  AlarmPlanner         onset + target, capped at latest wake
  SyncLagAnalyzer      Health Connect records → delay / staleness report
androidApp/   Compose UI, Health Connect, AlarmManager, WorkManager
  health/     Health Connect reads, night analysis + CSV export
  alarm/      scheduler, poll worker, receivers, ringing notification + full-screen activity
  data/       session state (SharedPreferences), event log + polls.csv
```

## Build

```
./gradlew :shared:jvmTest            # logic tests, fast, no device
./gradlew :androidApp:installDebug   # Android 12+ (minSdk 31)
```

CI (`.github/workflows/build.yml`) runs both and uploads the debug APK as an artifact.

AGP 8.13 / Kotlin 2.2 / Gradle 8.14. Android Studio may offer the AGP 9 upgrade. AGP 9 needs the
`shared` module moved to `com.android.kotlin.multiplatform.library`.

## Tuning the detector

Defaults are in `DetectorConfig` (`shared/.../SleepOnsetDetector.kt`). After a few nights, export
the night CSVs and compare "This app's onset" with when you think you fell asleep and with
Samsung's sleep start. Adjust `dropFraction`, `windowMs` and `minSamplesInWindow` to fit.
