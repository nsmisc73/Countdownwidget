package com.countdownwidget.service

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.countdownwidget.notification.NotificationHelper
import com.countdownwidget.util.Constants
import com.countdownwidget.util.TimerState
import com.countdownwidget.util.WidgetPrefs
import com.countdownwidget.widget.CountdownWidgetProvider

/**
 * Foreground Service — the heart of the countdown.
 *
 * Why a foreground service?
 *  • Keeps running when the screen is locked.
 *  • Keeps running when the app process would otherwise be killed.
 *  • A 1-second Handler loop is accurate and battery-efficient for short timers.
 *
 * Lifecycle:
 *  START_TIMER  → starts the Handler loop
 *  RESTART_TIMER→ resets endTime and continues the loop
 *  CANCEL_TIMER → stops the loop and stops the service
 *  Timer done   → fires notification + vibration, stops service
 */
class TimerService : Service() {

    companion object {
        private const val TAG = "TimerService"

        // ── Convenience factory methods ────────────────────────────────────

        fun startTimer(context: Context, widgetId: Int, durationSeconds: Int) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = Constants.ACTION_START_TIMER
                putExtra(Constants.EXTRA_WIDGET_ID, widgetId)
                putExtra(Constants.EXTRA_DURATION_SECONDS, durationSeconds)
            }
            context.startForegroundService(intent)
        }

        fun restartTimer(context: Context, widgetId: Int) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = Constants.ACTION_RESTART_TIMER
                putExtra(Constants.EXTRA_WIDGET_ID, widgetId)
            }
            context.startForegroundService(intent)
        }

        fun cancelTimer(context: Context, widgetId: Int) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = Constants.ACTION_CANCEL_TIMER
                putExtra(Constants.EXTRA_WIDGET_ID, widgetId)
            }
            context.startService(intent)
        }
    }

    // ── Per-widget timer tracking ─────────────────────────────────────────────
    // Maps widgetId → end-time in epochMillis
    private val activeTimers = mutableMapOf<Int, Long>()
    // Maps widgetId → preset duration seconds (needed for restart)
    private val timerDurations = mutableMapOf<Int, Int>()

    private val handler  = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    // ── Handler tick runnable ─────────────────────────────────────────────────
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (activeTimers.isEmpty()) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()
            val finishedIds = mutableListOf<Int>()

            activeTimers.forEach { (widgetId, endTime) ->
                if (now >= endTime) {
                    finishedIds.add(widgetId)
                } else {
                    // Update widget UI with current remaining time
                    val remaining = endTime - now
                    updateWidgetDisplay(widgetId, remaining)
                }
            }

            // Handle any timers that just completed
            finishedIds.forEach { widgetId ->
                onTimerFinished(widgetId)
            }

            if (activeTimers.isNotEmpty()) {
                // Schedule next tick in ~1 second
                handler.postDelayed(this, Constants.TICK_INTERVAL_MS)
            } else {
                stopSelf()
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        NotificationHelper.createChannel(this)
        Log.d(TAG, "TimerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground quickly (within 5 seconds of startForegroundService)
        val placeholder = NotificationHelper.buildForegroundNotification(this, "--:--")
        startForeground(Constants.NOTIFICATION_ID_FOREGROUND, placeholder)

        when (intent?.action) {
            Constants.ACTION_START_TIMER   -> handleStart(intent)
            Constants.ACTION_RESTART_TIMER -> handleRestart(intent)
            Constants.ACTION_CANCEL_TIMER  -> handleCancel(intent)
        }

        return START_STICKY  // Restart service if killed by OS
    }

    override fun onBind(intent: Intent?): IBinder? = null   // Not a bound service

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        NotificationHelper.cancelForegroundNotification(this)
        Log.d(TAG, "TimerService destroyed")
        super.onDestroy()
    }

    // ── Action handlers ───────────────────────────────────────────────────────

    private fun handleStart(intent: Intent) {
        val widgetId  = intent.getIntExtra(Constants.EXTRA_WIDGET_ID, -1)
        val durationS = intent.getIntExtra(Constants.EXTRA_DURATION_SECONDS, 0)
        if (widgetId == -1 || durationS <= 0) return

        Log.d(TAG, "Starting timer for widget $widgetId, duration=${durationS}s")

        val endTime = System.currentTimeMillis() + durationS * 1000L
        activeTimers[widgetId]  = endTime
        timerDurations[widgetId] = durationS

        // Persist so we can restore after process restart
        WidgetPrefs.saveState(
            this,
            TimerState(
                widgetId        = widgetId,
                isRunning       = true,
                durationSeconds = durationS,
                endTimeMs       = endTime
            )
        )

        ensureTickerRunning()
    }

    private fun handleRestart(intent: Intent) {
        val widgetId = intent.getIntExtra(Constants.EXTRA_WIDGET_ID, -1)
        if (widgetId == -1) return

        val durationS = timerDurations[widgetId]
            ?: WidgetPrefs.loadState(this, widgetId).durationSeconds
        if (durationS <= 0) return

        Log.d(TAG, "Restarting timer for widget $widgetId")

        val endTime = System.currentTimeMillis() + durationS * 1000L
        activeTimers[widgetId]   = endTime
        timerDurations[widgetId] = durationS

        WidgetPrefs.saveState(
            this,
            TimerState(
                widgetId        = widgetId,
                isRunning       = true,
                durationSeconds = durationS,
                endTimeMs       = endTime
            )
        )

        ensureTickerRunning()
    }

    private fun handleCancel(intent: Intent) {
        val widgetId = intent.getIntExtra(Constants.EXTRA_WIDGET_ID, -1)
        if (widgetId == -1) return

        Log.d(TAG, "Cancelling timer for widget $widgetId")

        activeTimers.remove(widgetId)
        timerDurations.remove(widgetId)
        WidgetPrefs.clearState(this, widgetId)

        // Tell the widget to show READY
        broadcastWidgetReset(widgetId)

        if (activeTimers.isEmpty()) {
            handler.removeCallbacks(tickRunnable)
            stopSelf()
        }
    }

    // ── Timer completion ──────────────────────────────────────────────────────

    private fun onTimerFinished(widgetId: Int) {
        Log.d(TAG, "Timer finished for widget $widgetId")

        activeTimers.remove(widgetId)
        timerDurations.remove(widgetId)
        WidgetPrefs.clearState(this, widgetId)

        // Alert user
        NotificationHelper.showTimerFinishedNotification(this)
        vibratePattern()

        // Reset widget display to READY
        broadcastWidgetReset(widgetId)
    }

    private fun vibratePattern() {
        val pattern = longArrayOf(0, 400, 150, 400, 150, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, -1)  // -1 = no repeat
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    // ── Widget communication ──────────────────────────────────────────────────

    /**
     * Sends a broadcast to [CountdownWidgetProvider] to update the display
     * for a specific widget with the current remaining milliseconds.
     */
    private fun updateWidgetDisplay(widgetId: Int, remainingMs: Long) {
        val intent = Intent(this, CountdownWidgetProvider::class.java).apply {
            action = Constants.ACTION_TICK
            putExtra(Constants.EXTRA_WIDGET_ID, widgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        sendBroadcast(intent)

        // Also update the foreground notification text
        val durationS = timerDurations[widgetId] ?: return
        val s = ((remainingMs + 999L) / 1000L).toInt()
        NotificationHelper.updateForegroundNotification(
            this,
            "%02d:%02d".format(s / 60, s % 60)
        )
    }

    private fun broadcastWidgetReset(widgetId: Int) {
        val intent = Intent(this, CountdownWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        sendBroadcast(intent)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Starts the 1-second tick loop only if it isn't already running. */
    private fun ensureTickerRunning() {
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }
}
