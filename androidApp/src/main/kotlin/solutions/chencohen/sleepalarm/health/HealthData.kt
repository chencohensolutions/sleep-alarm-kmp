package solutions.chencohen.sleepalarm.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import solutions.chencohen.sleepalarm.core.HrRecordObservation
import solutions.chencohen.sleepalarm.core.HrSample
import solutions.chencohen.sleepalarm.core.StepInterval
import java.time.Instant
import kotlin.reflect.KClass

/** Thin wrapper over Health Connect. Samsung Health copies the Fit 3's data in here. */
class HealthData(private val context: Context) {

    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun hasCorePermissions(): Boolean = grantedPermissions().containsAll(CORE_PERMISSIONS)

    suspend fun hasBackgroundRead(): Boolean =
        BACKGROUND_PERMISSION in grantedPermissions()

    fun backgroundReadSupported(): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun heartRateRecords(from: Instant, to: Instant): List<HeartRateRecord> =
        readAll(HeartRateRecord::class, from, to)

    suspend fun stepRecords(from: Instant, to: Instant): List<StepsRecord> =
        readAll(StepsRecord::class, from, to)

    suspend fun sleepSessions(from: Instant, to: Instant): List<SleepSessionRecord> =
        readAll(SleepSessionRecord::class, from, to)

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = ArrayList<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken,
                ),
            )
            out += response.records
            pageToken = response.pageToken
            // Some Android 12/13 builds return "" instead of null on the last page.
        } while (!pageToken.isNullOrEmpty())
        return out
    }

    companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        const val BACKGROUND_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

        val CORE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

        val ALL_PERMISSIONS: Set<String> = CORE_PERMISSIONS + BACKGROUND_PERMISSION
    }
}

fun List<HeartRateRecord>.toSamples(): List<HrSample> =
    flatMap { r -> r.samples.map { HrSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) } }
        .sortedBy { it.timeMs }

fun List<HeartRateRecord>.toObservations(): List<HrRecordObservation> = map { r ->
    HrRecordObservation(
        recordId = r.metadata.id,
        sampleTimesMs = r.samples.map { it.time.toEpochMilli() },
        lastModifiedMs = r.metadata.lastModifiedTime.toEpochMilli(),
        origin = r.metadata.dataOrigin.packageName,
    )
}

fun List<StepsRecord>.toIntervals(): List<StepInterval> =
    map { StepInterval(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.count) }
