package com.countdownwidget.service

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.countdownwidget.util.WidgetPrefs
import com.countdownwidget.widget.CountdownWidgetProvider

/**
 * Listens for BOOT_COMPLETED and restores any timers that were running
 * before the device was rebooted.
 *
 * Without this, all timers would silently disappear after a reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        Log.d(TAG, "Boot completed — restoring timers")

        // Get all widget IDs currently placed on home screens
        val awm = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, CountdownWidgetProvider::class.java)
        val allWidgetIds = awm.getAppWidgetIds(componentName)

        if (allWidgetIds.isEmpty()) return

        // Find widgets that had running timers
        val runningIds = WidgetPrefs.runningWidgetIds(context, allWidgetIds)

        runningIds.forEach { widgetId ->
            val state = WidgetPrefs.loadState(context, widgetId)
            if (state.isRunning && !state.isFinished) {
                // Timer still has time left — resume it
                Log.d(TAG, "Restoring timer for widget $widgetId (${state.formattedTime} left)")
                TimerService.startTimer(context, widgetId, state.remainingSeconds)
            } else {
                // Timer expired while phone was off — clear it
                WidgetPrefs.clearState(context, widgetId)
            }
        }

        // Force a UI refresh for all widgets
        val updateIntent = Intent(context, CountdownWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
        }
        context.sendBroadcast(updateIntent)
    }
}
