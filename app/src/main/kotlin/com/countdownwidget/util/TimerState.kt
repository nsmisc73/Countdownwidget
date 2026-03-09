package com.countdownwidget.util

/**
 * Shared constants used across the widget, service, and notification components.
 * Centralising here avoids magic strings scattered throughout the codebase.
 */
object Constants {

    // ── Notification ───────────────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID   = "countdown_timer_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Countdown Timer"
    const val NOTIFICATION_ID_FOREGROUND = 1001   // Foreground service notification
    const val NOTIFICATION_ID_FINISHED   = 1002   // "Timer Finished" alert

    // ── Intent Actions (widget ↔ service ↔ popup) ──────────────────────────
    const val ACTION_START_TIMER   = "com.countdownwidget.ACTION_START_TIMER"
    const val ACTION_CANCEL_TIMER  = "com.countdownwidget.ACTION_CANCEL_TIMER"
    const val ACTION_RESTART_TIMER = "com.countdownwidget.ACTION_RESTART_TIMER"
    const val ACTION_WIDGET_TAP    = "com.countdownwidget.ACTION_WIDGET_TAP"
    const val ACTION_TICK          = "com.countdownwidget.ACTION_TICK"
    const val ACTION_TIMER_DONE    = "com.countdownwidget.ACTION_TIMER_DONE"

    // ── Intent Extras ──────────────────────────────────────────────────────
    const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
    const val EXTRA_WIDGET_ID        = "extra_widget_id"
    const val EXTRA_TIMER_STATE      = "extra_timer_state"

    // ── SharedPreferences ──────────────────────────────────────────────────
    const val PREFS_NAME                = "countdown_widget_prefs"
    const val PREF_KEY_END_TIME         = "end_time_"          // + widgetId
    const val PREF_KEY_DURATION         = "duration_"          // + widgetId
    const val PREF_KEY_IS_RUNNING       = "is_running_"        // + widgetId

    // ── Preset durations (seconds) ─────────────────────────────────────────
    val PRESET_DURATIONS = listOf(
        10 * 60,   //  10 minutes
        15 * 60,   //  15 minutes
        20 * 60,   //  20 minutes
        30 * 60    //  30 minutes
    )

    val PRESET_LABELS = listOf("10 min", "15 min", "20 min", "30 min")

    // ── UI thresholds ──────────────────────────────────────────────────────
    /** When remaining time drops below this fraction, ring turns red/orange */
    const val NEAR_COMPLETION_FRACTION = 0.15f

    // ── Update interval ────────────────────────────────────────────────────
    const val TICK_INTERVAL_MS = 1_000L
}

/**
 * Snapshot of a single widget's timer state, persisted to SharedPreferences
 * so it survives process death and screen-off cycles.
 */
data class TimerState(
    val widgetId: Int,
    val isRunning: Boolean,
    val durationSeconds: Int,       // Total preset duration
    val endTimeMs: Long             // System.currentTimeMillis() at which timer ends
) {
    /** Milliseconds left, clamped to [0, durationMs]. */
    val remainingMs: Long
        get() = (endTimeMs - System.currentTimeMillis()).coerceIn(0L, durationSeconds * 1000L)

    /** Remaining seconds (ceiled so 14:59.1 shows 15:00). */
    val remainingSeconds: Int
        get() = ((remainingMs + 999L) / 1000L).toInt()

    /** 0.0 → finished, 1.0 → just started. */
    val progress: Float
        get() = if (durationSeconds <= 0) 0f
                else (remainingMs / (durationSeconds * 1000f)).coerceIn(0f, 1f)

    /** MM:SS formatted string. */
    val formattedTime: String
        get() {
            val s = remainingSeconds
            return "%02d:%02d".format(s / 60, s % 60)
        }

    val isNearCompletion: Boolean
        get() = isRunning && progress < Constants.NEAR_COMPLETION_FRACTION

    val isFinished: Boolean
        get() = isRunning && remainingMs == 0L

    companion object {
        fun idle(widgetId: Int) = TimerState(
            widgetId = widgetId,
            isRunning = false,
            durationSeconds = 0,
            endTimeMs = 0L
        )
    }
}
