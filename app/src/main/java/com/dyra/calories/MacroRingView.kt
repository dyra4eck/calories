package com.dyra.calories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Кольцевой индикатор прогресса к цели: заполнение дуги по кругу,
 * значение в центре. Превышение цели рисуется вторым, красным кольцом.
 */
class MacroRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var fraction = 0f
    private var mainText = ""
    private var subText = ""
    private var ringColor = Color.parseColor("#2E7D32")

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#C62828")
    }

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#888888")
    }

    fun setRingColor(color: Int) {
        ringColor = color
        invalidate()
    }

    /** value/goal задают заполнение (goal <= 0 — пустое кольцо), тексты — центр. */
    fun setData(value: Double, goal: Double, main: String, sub: String) {
        fraction = if (goal > 0) (value / goal).toFloat() else 0f
        mainText = main
        subText = sub
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val size = minOf(width, height).toFloat()
        val stroke = size * 0.11f
        basePaint.strokeWidth = stroke
        ringPaint.strokeWidth = stroke
        overPaint.strokeWidth = stroke
        ringPaint.color = ringColor
        basePaint.color = Color.argb(
            38, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor)
        )

        val inset = stroke / 2 + size * 0.02f
        val rect = RectF(
            (width - size) / 2 + inset,
            (height - size) / 2 + inset,
            (width + size) / 2 - inset,
            (height + size) / 2 - inset
        )

        canvas.drawArc(rect, 0f, 360f, false, basePaint)
        val sweep = fraction.coerceAtMost(1f) * 360f
        if (sweep > 0f) {
            canvas.drawArc(rect, -90f, sweep, false, ringPaint)
        }
        if (fraction > 1f) {
            canvas.drawArc(rect, -90f, (fraction - 1f).coerceAtMost(1f) * 360f, false, overPaint)
        }

        mainPaint.textSize = size * 0.2f
        subPaint.textSize = size * 0.13f
        mainPaint.color = if (fraction > 1f) Color.parseColor("#C62828") else ringColor

        val cx = width / 2f
        val cy = height / 2f
        if (subText.isEmpty()) {
            canvas.drawText(mainText, cx, cy - (mainPaint.ascent() + mainPaint.descent()) / 2, mainPaint)
        } else {
            canvas.drawText(mainText, cx, cy - size * 0.02f, mainPaint)
            canvas.drawText(subText, cx, cy + size * 0.15f, subPaint)
        }
    }
}
