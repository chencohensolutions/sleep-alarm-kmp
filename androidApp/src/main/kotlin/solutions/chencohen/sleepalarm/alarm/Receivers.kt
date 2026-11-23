package solutions.chencohen.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Every 15 minutes overnight. */
class PollTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler(context).pollNow()
    }
}

/** Time to wake up. */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.ring(context)
        NightSession.onWakeAlarmFired(context)
    }
}

/** "Dismiss" action on the ringing notification. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.stopRinging(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NightSession.restoreAfterBoot(context)
        }
    }
}
