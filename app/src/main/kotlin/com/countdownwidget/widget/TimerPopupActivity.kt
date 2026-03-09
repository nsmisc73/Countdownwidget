package com.countdownwidget.widget

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.countdownwidget.service.TimerService
import com.countdownwidget.util.Constants
import com.countdownwidget.util.TimerState
import com.countdownwidget.util.WidgetPrefs

/**
 * Transparent Activity that shows a popup/dialog when the user taps the widget.
 *
 * We use a transparent Activity rather than a BroadcastReceiver because only
 * an Activity can display a UI dialog that overlays the home screen.
 *
 * The activity finishes immediately after the user makes a selection or
 * dismisses the dialog, leaving no trace in the recents screen.
 *
 * Two dialog flows:
 *  1. IDLE  → "Select Duration" — lets user pick a preset
 *  2. RUNNING → "Timer Options" — Restart / Cancel / Start New
 */
class TimerPopupActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TimerPopupActivity"
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window transparent and not focusable so it looks like a popup
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        widgetId = intent?.getIntExtra(Constants.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "No valid widget ID received")
            finish()
            return
        }

        val state = WidgetPrefs.loadState(this, widgetId)
        if (state.isRunning) {
            showRunningDialog(state)
        } else {
            showIdleDialog()
        }
    }

    // ── Dialog: idle state ────────────────────────────────────────────────────

    /**
     * Shown when no timer is running. Lets the user pick a duration.
     */
    private fun showIdleDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⏱  Start Timer")
            .setItems(Constants.PRESET_LABELS.toTypedArray()) { _, which ->
                val durationSeconds = Constants.PRESET_DURATIONS[which]
                startTimer(durationSeconds)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    // ── Dialog: timer already running ────────────────────────────────────────

    /**
     * Shown when a timer is active. Presents management options.
     */
    private fun showRunningDialog(state: TimerState) {
        // Build option list dynamically:
        // [Restart Timer, Cancel Timer, ── divider labels ──, 10 min, 15 min, ...]
        val options = mutableListOf<String>()
        options.add("🔄  Restart Timer (${state.durationSeconds / 60} min)")
        options.add("⏹  Cancel Timer")
        options.add("─── Start New Timer ───")
        options.addAll(Constants.PRESET_LABELS.map { "▶  $it" })

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Timer: ${state.formattedTime} left")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> restartTimer()
                    1 -> cancelTimer()
                    2 -> { /* divider row — do nothing */ finish() }
                    else -> {
                        val presetIndex = which - 3
                        if (presetIndex in Constants.PRESET_DURATIONS.indices) {
                            startTimer(Constants.PRESET_DURATIONS[presetIndex])
                        }
                    }
                }
            }
            .setNegativeButton("Dismiss") { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun startTimer(durationSeconds: Int) {
        Log.d(TAG, "Starting ${durationSeconds}s timer for widget $widgetId")

        // Persist the state immediately so the widget redraws at once
        val endTime = System.currentTimeMillis() + durationSeconds * 1000L
        WidgetPrefs.saveState(
            this,
            TimerState(
                widgetId        = widgetId,
                isRunning       = true,
                durationSeconds = durationSeconds,
                endTimeMs       = endTime
            )
        )

        TimerService.startTimer(this, widgetId, durationSeconds)
        forceWidgetRefresh()
        finish()
    }

    private fun restartTimer() {
        Log.d(TAG, "Restarting timer for widget $widgetId")
        TimerService.restartTimer(this, widgetId)
        finish()
    }

    private fun cancelTimer() {
        Log.d(TAG, "Cancelling timer for widget $widgetId")
        WidgetPrefs.clearState(this, widgetId)
        TimerService.cancelTimer(this, widgetId)
        forceWidgetRefresh()
        finish()
    }

    /** Sends an update broadcast so the widget re-draws immediately. */
    private fun forceWidgetRefresh() {
        val awm = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, CountdownWidgetProvider::class.java)
        awm.notifyAppWidgetViewDataChanged(awm.getAppWidgetIds(provider), android.R.id.list)

        val state = WidgetPrefs.loadState(this, widgetId)
        val views = WidgetRenderer.buildViews(this, state)
        awm.updateAppWidget(widgetId, views)
    }
}
