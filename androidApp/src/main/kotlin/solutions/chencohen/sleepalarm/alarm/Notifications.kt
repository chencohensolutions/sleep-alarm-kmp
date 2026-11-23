package solutions.chencohen.sleepalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import solutions.chencohen.sleepalarm.MainActivity
import solutions.chencohen.sleepalarm.data.EventLog
import solutions.chencohen.sleepalarm.data.Session
import solutions.chencohen.sleepalarm.data.SessionMode

/**
 * The alarm sound is played by the notification channel itself (alarm audio usage, insistent),
 * so it rings whether or not the full-screen activity gets shown. Cancelling the notification
 * stops the sound.
 */
object Notifications {

    private const val CH_ALARM = "wake_alarm"
    private const val CH_STATUS = "night_status"
    private const val ID_RING = 100
    private const val ID_STATUS = 101

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarm = NotificationChannel(CH_ALARM, "Wake alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when it's time to wake up"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val status = NotificationChannel(CH_STATUS, "Night status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the armed alarm and the last sync while a night is running"
            setShowBadge(false)
        }
        nm.createNotificationChannels(listOf(alarm, status))
    }

    fun ring(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val fullScreen = PendingIntent.getActivity(
            context, 10,
            Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 11, Intent(context, DismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to wake up")
            .setContentText("Your sleep target is reached")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    "Dismiss", dismiss,
                ).build(),
            )
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(ID_RING, n)
    }

    fun stopRinging(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_RING)
    }

    fun showStatus(context: Context, s: Session) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            context, 12, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (s.mode) {
            SessionMode.ALARM -> "Alarm: ${s.plannedAlarmMs?.let(EventLog::fmt) ?: "—"}"
            SessionMode.PROBE -> "Sync probe running"
        }
        val text = buildString {
            append(s.lastStatus ?: "")
            s.newestSampleMs?.let { append(" · newest reading ${EventLog.fmt(it)}") }
        }
        val n = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(ID_STATUS, n)
    }

    fun cancelStatus(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_STATUS)
    }
}
