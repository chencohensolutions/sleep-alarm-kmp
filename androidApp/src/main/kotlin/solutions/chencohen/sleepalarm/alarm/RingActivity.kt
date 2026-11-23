package solutions.chencohen.sleepalarm.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import solutions.chencohen.sleepalarm.data.EventLog
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen ringing screen, shown over the lock screen. */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 72.sp)
                        Text("Time to wake up", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = ::dismiss, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                            Text("Dismiss", fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = ::snooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text("Snooze 9 min")
                        }
                    }
                }
            }
        }
    }

    private fun dismiss() {
        Notifications.stopRinging(this)
        EventLog(this).event("Alarm dismissed")
        finish()
    }

    private fun snooze() {
        Notifications.stopRinging(this)
        val at = System.currentTimeMillis() + 9 * 60_000L
        AlarmScheduler(this).setWakeAlarm(at)
        EventLog(this).event("Snoozed until ${EventLog.fmt(at)}")
        finish()
    }
}
