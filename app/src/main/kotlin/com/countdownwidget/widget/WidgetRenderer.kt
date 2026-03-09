package com.countdownwidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.countdownwidget.R
import com.countdownwidget.util.Constants
import com.countdownwidget.util.TimerState

/**
 * Responsible for building [RemoteViews] objects that represent the widget UI.
 *
 * RemoteViews are the only way to update widget UI from outside the widget's
 * process. We create a Bitmap for the circular progress ring because custom
 * drawing is not possible directly on RemoteViews — only standard View types
 * are supported.
 *
 * Design:
 *  • Dark background  (#1A1A1A)
 *  • White countdown text, bold
 *  • Blue ring → orange/red when near completion
 */
object WidgetRenderer {

    // ── Colours ───────────────────────────────────────────────────────────────
    private val COLOR_BG           = Color.parseColor("#1A1A1A")
    private val COLOR_RING_NORMAL  = Color.parseColor("#2196F3")   // Blue
    private val COLOR_RING_WARN    = Color.parseColor("#FF9800")   // Orange
    private val COLOR_RING_DANGER  = Color.parseColor("#F44336")   // Red
    private val COLOR_RING_TRACK   = Color.parseColor("#2C2C2C")   // Dark track
    private val COLOR_TEXT         = Color.WHITE
    private val COLOR_TEXT_IDLE    = Color.parseColor("#9E9E9E")   // Grey

    // ── Canvas sizes ──────────────────────────────────────────────────────────
    private const val BITMAP_SIZE  = 300   // px
    private const val STROKE_WIDTH = 24f   // px
    private const val TEXT_SIZE_TIMER = 68f
    private const val TEXT_SIZE_IDLE  = 44f

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Builds a complete [RemoteViews] for the given [state].
     * The caller is responsible for applying it via [AppWidgetManager.updateAppWidget].
     */
    fun buildViews(context: Context, state: TimerState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Draw the circular progress ring + text onto a Bitmap
        val bitmap = drawWidget(state)
        views.setImageViewBitmap(R.id.widget_image, bitmap)

        // Attach the tap PendingIntent (opens TimerPopupActivity)
        val tapIntent = buildTapIntent(context, state.widgetId)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent)

        return views
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private fun drawWidget(state: TimerState): Bitmap {
        val size   = BITMAP_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, size)
        drawTrackRing(canvas, size)

        if (state.isRunning) {
            drawProgressRing(canvas, size, state.progress, ringColor(state))
            drawTimerText(canvas, size, state.formattedTime, COLOR_TEXT)
        } else {
            // Idle — show empty ring + READY label
            drawProgressRing(canvas, size, 0f, COLOR_RING_NORMAL)
            drawTimerText(canvas, size, "READY", COLOR_TEXT_IDLE, isIdle = true)
        }

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, size: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BG
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    }

    private fun drawTrackRing(canvas: Canvas, size: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color   = COLOR_RING_TRACK
            style   = Paint.Style.STROKE
            strokeWidth  = STROKE_WIDTH
            strokeCap    = Paint.Cap.ROUND
        }
        val inset = STROKE_WIDTH / 2f + 4f
        val rect  = RectF(inset, inset, size - inset, size - inset)
        canvas.drawArc(rect, 0f, 360f, false, paint)
    }

    private fun drawProgressRing(canvas: Canvas, size: Int, progress: Float, color: Int) {
        if (progress <= 0f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color  = color
            style       = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH
            strokeCap   = Paint.Cap.ROUND
        }
        val inset    = STROKE_WIDTH / 2f + 4f
        val rect     = RectF(inset, inset, size - inset, size - inset)
        val sweepAngle = 360f * progress

        // Start from the top (-90°)
        canvas.drawArc(rect, -90f, sweepAngle, false, paint)
    }

    private fun drawTimerText(
        canvas: Canvas,
        size: Int,
        text: String,
        color: Int,
        isIdle: Boolean = false
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color  = color
            textSize    = if (isIdle) TEXT_SIZE_IDLE else TEXT_SIZE_TIMER
            typeface    = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign   = Paint.Align.CENTER
        }
        // Vertically centre: baseline offset = half height of text bounds
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val y = size / 2f + bounds.height() / 2f - bounds.bottom
        canvas.drawText(text, size / 2f, y, paint)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Picks ring colour based on how much time is left. */
    private fun ringColor(state: TimerState): Int = when {
        state.progress < 0.05f -> COLOR_RING_DANGER   // < 5% left — red
        state.progress < 0.15f -> COLOR_RING_WARN     // < 15% left — orange
        else                   -> COLOR_RING_NORMAL   // blue
    }

    /**
     * Creates the PendingIntent that opens [TimerPopupActivity] when the
     * widget is tapped. The widgetId is passed as an extra so the popup
     * knows which widget to act on.
     */
    private fun buildTapIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, TimerPopupActivity::class.java).apply {
            action = Constants.ACTION_WIDGET_TAP
            putExtra(Constants.EXTRA_WIDGET_ID, widgetId)
            // Unique flag per widget so each widget gets its own PendingIntent
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            widgetId,   // requestCode unique per widget
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
