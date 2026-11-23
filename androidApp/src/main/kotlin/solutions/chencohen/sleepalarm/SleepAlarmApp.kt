package solutions.chencohen.sleepalarm

import android.app.Application
import solutions.chencohen.sleepalarm.alarm.Notifications

class SleepAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
