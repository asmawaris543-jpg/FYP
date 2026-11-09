
package com.svgs.app.core.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryStatus(
    val percentage: Int,
    val chargingStatus: String
)

class BatteryStatusManager(
    private val context: Context
) {

    fun getBatteryStatus(): BatteryStatus {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        ) ?: -1

        val scale = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        ) ?: -1

        val percentage = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val chargingStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Fully charged"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

        return BatteryStatus(
            percentage = percentage,
            chargingStatus = chargingStatus
        )
    }
}

