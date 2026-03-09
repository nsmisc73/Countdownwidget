package com.countdownwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.countdownwidget.util.Constants
import com.countdownwidget.util.WidgetPrefs

/**
 * The AppWidgetProvider (a specialised BroadcastReceiver) for the countdown widget.
 *
 * Responsibilities:
 *  • Render initial READY state when a widget is first placed on the home screen.
 *  • Re-render on APPWIDGET_UPDATE (e.g. after boot, launcher refresh).
 *  • Handle ACTION_TICK broadcast from TimerService to refresh the display.
 *  • Clean up state when a widget is removed.
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownWidgetProvider"
    }

    // ── Called by Android when one or more widgets need to be updated ──────────
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widgets: ${appWidgetIds.toList()}")
        appWidgetIds.forEach { widgetId ->
            refreshWidget(context, appWidgetManager, widgetId)
        }
    }

    // ── Receives all broadcasts directed at this provider class ───────────────
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // Let parent handle APPWIDGET_UPDATE etc.

        when (intent.action) {
            // Periodic tick from TimerService — refresh one widget
            Constants.ACTION_TICK -> {
                val widgetId = intent.getIntExtra(Constants.EXTRA_WIDGET_ID, -1)
                if (widgetId == -1) return

                val awm = AppWidgetManager.getInstance(context)
                refreshWidget(context, awm, widgetId)
            }
        }
    }

    // ── Widget placed on home screen ──────────────────────────────────────────
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First widget added")
    }

    // ── Widget removed from home screen ──────────────────────────────────────
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { widgetId ->
            Log.d(TAG, "Widget $widgetId deleted — cleaning up")
            WidgetPrefs.clearState(context, widgetId)
        }
    }

    // ── All widgets removed ───────────────────────────────────────────────────
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "All widgets removed")
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Loads persisted state for [widgetId] and pushes updated [RemoteViews]
     * to the home screen. Called both on tick and on explicit update requests.
     */
    private fun refreshWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val state = WidgetPrefs.loadState(context, widgetId)
        Log.d(TAG, "Refreshing widget $widgetId — running=${state.isRunning}, time=${state.formattedTime}")

        val views = WidgetRenderer.buildViews(context, state)
        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
