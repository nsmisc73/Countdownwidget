package com.countdownwidget.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around SharedPreferences that serialises/deserialises
 * [TimerState] per widget ID.
 *
 * Using SharedPreferences (rather than a database) keeps things simple and
 * is fast enough for the small amount of data we store.
 */
object WidgetPrefs {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // ── Write ───────────────────────────────────────────────────────────────

    fun saveState(context: Context, state: TimerState) {
        prefs(context).edit().apply {
            putBoolean(Constants.PREF_KEY_IS_RUNNING + state.widgetId, state.isRunning)
            putInt(Constants.PREF_KEY_DURATION + state.widgetId, state.durationSeconds)
            putLong(Constants.PREF_KEY_END_TIME + state.widgetId, state.endTimeMs)
            apply()
        }
    }

    fun clearState(context: Context, widgetId: Int) {
        prefs(context).edit().apply {
            remove(Constants.PREF_KEY_IS_RUNNING + widgetId)
            remove(Constants.PREF_KEY_DURATION + widgetId)
            remove(Constants.PREF_KEY_END_TIME + widgetId)
            apply()
        }
    }

    // ── Read ────────────────────────────────────────────────────────────────

    fun loadState(context: Context, widgetId: Int): TimerState {
        val p = prefs(context)
        val isRunning = p.getBoolean(Constants.PREF_KEY_IS_RUNNING + widgetId, false)
        val duration  = p.getInt(Constants.PREF_KEY_DURATION + widgetId, 0)
        val endTime   = p.getLong(Constants.PREF_KEY_END_TIME + widgetId, 0L)

        // If the stored timer has already expired treat it as idle
        if (isRunning && endTime <= System.currentTimeMillis()) {
            return TimerState.idle(widgetId)
        }

        return TimerState(
            widgetId        = widgetId,
            isRunning       = isRunning,
            durationSeconds = duration,
            endTimeMs       = endTime
        )
    }

    /** Returns all widget IDs that currently have a running timer. */
    fun runningWidgetIds(context: Context, allWidgetIds: IntArray): List<Int> =
        allWidgetIds.filter { id ->
            prefs(context).getBoolean(Constants.PREF_KEY_IS_RUNNING + id, false)
        }
}
