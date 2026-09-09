package com.sleeptalker.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.R as MaterialR

/**
 * Draws amplitude [samples] (each in [0, 1]) as mirrored vertical bars — the classic
 * audio-waveform look — and optionally a playhead cursor at [progress] (fraction of
 * width, 0..1). Used both as a small static "icon" for a saved clip (see
 * item_clip.xml) and as the live-updating waveform on the recording screen; the
 * playhead + [onSeek] (tap/drag to scrub) are only used on the playback screen.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var samples: List<Float> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** Fraction of playback progress in [0, 1], or null to hide the playhead. */
    var progress: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Set to receive scrub gestures (tap/drag); leave null to disable seeking. */
    var onSeek: ((Float) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = fetchThemeColor(MaterialR.attr.colorPrimary)
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = fetchThemeColor(MaterialR.attr.colorOnSurface)
    }
    private val playheadDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fetchThemeColor(MaterialR.attr.colorOnSurface)
    }

    private fun fetchThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        return try {
            context.theme.resolveAttribute(attr, typedValue, true)
            typedValue.data
        } catch (e: Exception) {
            android.graphics.Color.DKGRAY
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBars(canvas)
        progress?.let { drawPlayhead(canvas, it) }
    }

    private fun drawBars(canvas: Canvas) {
        if (samples.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        val slot = w / samples.size

        samples.forEachIndexed { i, amp ->
            val x = slot * i + slot / 2f
            val halfBar = (amp.coerceIn(0f, 1f) * midY).coerceAtLeast(2f)
            canvas.drawLine(x, midY - halfBar, x, midY + halfBar, barPaint)
        }
    }

    private fun drawPlayhead(canvas: Canvas, fraction: Float) {
        val x = width * fraction.coerceIn(0f, 1f)
        canvas.drawLine(x, 0f, x, height.toFloat(), playheadPaint)
        canvas.drawCircle(x, height / 2f, PLAYHEAD_DOT_RADIUS, playheadDotPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val seekListener = onSeek ?: return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = (event.x / width).coerceIn(0f, 1f)
                seekListener(fraction)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private companion object {
        const val PLAYHEAD_DOT_RADIUS = 14f
    }
}
