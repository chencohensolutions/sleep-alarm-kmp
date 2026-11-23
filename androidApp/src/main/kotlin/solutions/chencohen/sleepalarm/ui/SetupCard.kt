package solutions.chencohen.sleepalarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import solutions.chencohen.sleepalarm.alarm.AlarmScheduler
import solutions.chencohen.sleepalarm.health.HealthData

data class SetupState(
    val healthConnectStatus: Int,
    val corePermissions: Boolean,
    val backgroundRead: Boolean,
    val backgroundReadSupported: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
) {
    val healthConnectReady get() = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE
    val canProbe get() = healthConnectReady && corePermissions
    val canRunOvernight get() = canProbe && backgroundRead
    val canAlarm get() = canRunOvernight && notifications && exactAlarms
    val allGood get() = canAlarm && fullScreen
}

suspend fun loadSetup(context: Context): SetupState {
    val hd = HealthData(context)
    val status = hd.sdkStatus()
    val ready = status == HealthConnectClient.SDK_AVAILABLE
    val granted = if (ready) runCatching { hd.grantedPermissions() }.getOrDefault(emptySet()) else emptySet()
    val nm = context.getSystemService(NotificationManager::class.java)
    val notif = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        nm.areNotificationsEnabled()
    }
    return SetupState(
        healthConnectStatus = status,
        corePermissions = granted.containsAll(HealthData.CORE_PERMISSIONS),
        backgroundRead = HealthData.BACKGROUND_PERMISSION in granted,
        backgroundReadSupported = ready && runCatching { hd.backgroundReadSupported() }.getOrDefault(false),
        notifications = notif,
        exactAlarms = AlarmScheduler(context).canScheduleExact(),
        fullScreen = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true,
    )
}

@Composable
fun SetupCard(state: SetupState?, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { onChanged() }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onChanged() }

    if (state == null) return
    if (state.allGood) {
        Text("✓ Setup complete", color = MaterialTheme.colorScheme.primary)
        return
    }

    SectionCard("Setup") {
        if (!state.healthConnectReady) {
            SetupRow(
                ok = false,
                label = if (state.healthConnectStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                    "Health Connect needs an update"
                } else {
                    "Health Connect not available"
                },
                action = "Get it",
            ) {
                val uri = "market://details?id=${HealthData.HEALTH_CONNECT_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding"
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri.toUri())
                        .setPackage("com.android.vending")
                        .putExtra("overlay", true)
                        .putExtra("callerId", ctx.packageName),
                )
            }
            return@SectionCard
        }
        SetupRow(state.corePermissions, "Read heart rate, steps, sleep", "Grant") {
            hcLauncher.launch(HealthData.ALL_PERMISSIONS)
        }
        SetupRow(
            state.backgroundRead,
            if (state.backgroundReadSupported) "Read in the background (overnight)" else "Background read not supported — update Health Connect",
            "Grant",
        ) {
            hcLauncher.launch(HealthData.ALL_PERMISSIONS)
        }
        SetupRow(state.notifications, "Notifications (the alarm rings through one)", "Allow") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        SetupRow(state.exactAlarms, "Exact alarms", "Allow") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${ctx.packageName}".toUri()))
        }
        SetupRow(state.fullScreen, "Full-screen alarm over the lock screen", "Allow") {
            if (Build.VERSION.SDK_INT >= 34) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${ctx.packageName}".toUri()))
            }
        }
        Text(
            "Also check: Samsung Health → Settings → Health Connect is on, and the Fit 3 heart rate is set to Continuous.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = {
            ctx.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }) { Text("Open Health Connect") }
    }
}

@Composable
private fun SetupRow(ok: Boolean, label: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "✗", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text("  $label", Modifier.weight(1f))
        if (!ok) OutlinedButton(onClick = onClick) { Text(action) }
    }
}
