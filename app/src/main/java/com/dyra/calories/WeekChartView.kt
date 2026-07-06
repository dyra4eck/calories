package com.dyra.calories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Столбчатый график калорий по дням с линией дневной цели. */
class WeekChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var values: List<Int> = emptyList()
    private var goal: Int = 0
    private var todayIndex: Int = -1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    fun setData(labels: List<String>, values: List<Int>, goal: Int, todayIndex: Int) {
        this.labels = labels
        this.values = values
        this.goal = goal
        this.todayIndex = todayIndex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty() || width == 0 || height == 0) return

        val density = resources.displayMetrics.density
        textPaint.textSize = 11 * density
        todayPaint.textSize = 11 * density
        goalPaint.strokeWidth = 1.5f * density

        val showValues = values.size <= 10
        val labelSpace = 20 * density
        val valueSpace = if (showValues) 18 * density else 6 * density
        val chartBottom = height - labelSpace
        val chartHeight = chartBottom - valueSpace
        val maxValue = maxOf(values.max(), goal, 1) * 1.08f

        // Линия цели
        if (goal > 0) {
            val goalY = chartBottom - goal / maxValue * chartHeight
            canvas.drawLine(0f, goalY, width.toFloat(), goalY, goalPaint)
        }

        val slot = width.toFloat() / values.size
        val barWidth = slot * if (values.size <= 10) 0.55f else 0.7f
        val corner = if (values.size <= 10) 4 * density else 2 * density

        for (i in values.indices) {
            val value = values[i]
            val centerX = slot * i + slot / 2
            val barHeight = value / maxValue * chartHeight

            barPaint.color = if (goal in 1 until value) {
                Color.parseColor("#C62828")
            } else {
                Color.parseColor("#2E7D32")
            }
            canvas.drawRoundRect(
                RectF(
                    centerX - barWidth / 2,
                    chartBottom - barHeight,
                    centerX + barWidth / 2,
                    chartBottom
                ),
                corner,
                corner,
                barPaint
            )

            if (showValues && value > 0) {
                canvas.drawText(
                    value.toString(),
                    centerX,
                    chartBottom - barHeight - 5 * density,
                    textPaint
                )
            }

            if (labels[i].isNotEmpty()) {
                canvas.drawText(
                    labels[i],
                    centerX,
                    height - 5 * density,
                    if (i == todayIndex) todayPaint else textPaint
                )
            }
        }
    }
}
