package com.countdownwidget.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.countdownwidget.R
import com.countdownwidget.util.Constants

/**
 * Centralises all notification-related logic:
 *  • Channel creation (required on API 26+)
 *  • Foreground service keep-alive notification
 *  • "Timer Finished" alert notification
 */
object NotificationHelper {

    // ── Channel setup ────────────────────────────────────────────────────────

    /**
     * Creates the notification channel.
     * Safe to call multiple times — the system is idempotent.
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) != null) return

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description          = "Countdown timer alerts"
            enableVibration(true)
            vibrationPattern     = longArrayOf(0, 300, 200, 300, 200, 600)
            setSound(alarmUri, audioAttr)
            enableLights(true)
        }

        manager.createNotificationChannel(channel)
    }

    // ── Foreground service notification ──────────────────────────────────────

    /**
     * Silent, ongoing notification displayed while the timer is running.
     * Required by Android to keep a foreground service alive.
     */
    fun buildForegroundNotification(context: Context, timeLeft: String): Notification {
        createChannel(context)

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⏱ Countdown Timer")
            .setContentText("Time remaining: $timeLeft")
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setOngoing(true)
            .setSilent(true)          // No sound/vibration for the keep-alive notice
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Updates the foreground notification with the latest remaining time.
     * Called every second from the TimerService.
     */
    fun updateForegroundNotification(context: Context, timeLeft: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.notify(
            Constants.NOTIFICATION_ID_FOREGROUND,
            buildForegroundNotification(context, timeLeft)
        )
    }

    // ── "Timer Finished" alert ───────────────────────────────────────────────

    /**
     * High-priority notification with sound, shown when a timer completes.
     */
    fun showTimerFinishedNotification(context: Context) {
        createChannel(context)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("✅ Timer Finished")
            .setContentText("Your countdown timer has ended.")
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setSound(alarmUri)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 800))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.notify(Constants.NOTIFICATION_ID_FINISHED, notification)
    }

    // ── Dismiss ──────────────────────────────────────────────────────────────

    fun cancelForegroundNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.cancel(Constants.NOTIFICATION_ID_FOREGROUND)
    }
}
