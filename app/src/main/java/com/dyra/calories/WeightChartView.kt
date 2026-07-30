package com.dyra.calories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Плавный график изменения веса: сглаженная кривая (Катмулл-Ром → Безье)
 * с заливкой, точками записей и пунктирной линией целевого веса.
 */
class WeightChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<Pair<LocalDate, Double>> = emptyList()
    private var target: Double = 0.0

    private val dateFormat = DateTimeFormatter.ofPattern("d.MM")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        isFakeBoldText = true
    }

    fun setData(points: List<Pair<LocalDate, Double>>, target: Double) {
        this.points = points
        this.target = target
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty() || width == 0 || height == 0) return

        val density = resources.displayMetrics.density
        linePaint.strokeWidth = 2.5f * density
        targetPaint.strokeWidth = 1.5f * density
        textPaint.textSize = 11 * density
        valuePaint.textSize = 12 * density

        val topPad = 22 * density
        val bottomPad = 20 * density
        val sidePad = 12 * density
        val chartWidth = width - sidePad * 2
        val chartBottom = height - bottomPad

        // Диапазон значений с учётом цели и небольшим запасом
        val values = points.map { it.second }
        var minValue = values.min()
        var maxValue = values.max()
        if (target > 0) {
            minValue = minOf(minValue, target)
            maxValue = maxOf(maxValue, target)
        }
        val span = (maxValue - minValue).coerceAtLeast(1.0)
        minValue -= span * 0.15
        maxValue += span * 0.15

        fun yOf(value: Double): Float =
            (chartBottom - (value - minValue) / (maxValue - minValue) *
                (chartBottom - topPad)).toFloat()

        // Позиции по X — пропорционально датам
        val firstDay = points.first().first.toEpochDay()
        val lastDay = points.last().first.toEpochDay()
        val daySpan = (lastDay - firstDay).coerceAtLeast(1)

        fun xOf(date: LocalDate): Float =
            sidePad + (date.toEpochDay() - firstDay).toFloat() / daySpan * chartWidth

        // Линия целевого веса
        if (target > 0) {
            val targetY = yOf(target)
            canvas.drawLine(0f, targetY, width.toFloat(), targetY, targetPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                fmt(target),
                width - 4 * density,
                targetY - 5 * density,
                textPaint
            )
        }

        val xs = points.map { xOf(it.first) }
        val ys = points.map { yOf(it.second) }
        val trendYs = WeightMath.trend(points.map { it.second }).map { yOf(it) }

        if (points.size == 1) {
            canvas.drawCircle(width / 2f, ys[0], 4 * density, dotPaint)
            valuePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(fmt(points[0].second), width / 2f, ys[0] - 10 * density, valuePaint)
        } else {
            // Сглаженная кривая: Катмулл-Ром через точки, переведённый в Безье
            fun smoothPath(pointYs: List<Float>): Path {
                val path = Path()
                path.moveTo(xs[0], pointYs[0])
                for (i in 0 until points.size - 1) {
                    val prevX = xs.getOrElse(i - 1) { xs[i] }
                    val prevY = pointYs.getOrElse(i - 1) { pointYs[i] }
                    val nextX = xs.getOrElse(i + 2) { xs[i + 1] }
                    val nextY = pointYs.getOrElse(i + 2) { pointYs[i + 1] }
                    path.cubicTo(
                        xs[i] + (xs[i + 1] - prevX) / 6f,
                        pointYs[i] + (pointYs[i + 1] - prevY) / 6f,
                        xs[i + 1] - (nextX - xs[i]) / 6f,
                        pointYs[i + 1] - (nextY - pointYs[i]) / 6f,
                        xs[i + 1],
                        pointYs[i + 1]
                    )
                }
                return path
            }

            val trendPath = smoothPath(trendYs)

            // Заливка под трендом
            val fillPath = Path(trendPath)
            fillPath.lineTo(xs.last(), chartBottom)
            fillPath.lineTo(xs.first(), chartBottom)
            fillPath.close()
            fillPaint.shader = LinearGradient(
                0f, topPad, 0f, chartBottom,
                Color.parseColor("#4D2E7D32"), Color.parseColor("#002E7D32"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, fillPaint)

            // Сырые значения — тонкая полупрозрачная линия с точками
            linePaint.strokeWidth = 1.2f * density
            linePaint.color = Color.parseColor("#802E7D32")
            canvas.drawPath(smoothPath(ys), linePaint)
            val dotRadius = if (points.size > 40) 1.5f * density else 2.5f * density
            for (i in points.indices) {
                canvas.drawCircle(xs[i], ys[i], dotRadius, dotPaint)
            }

            // Тренд — жирная основная линия
            linePaint.strokeWidth = 2.5f * density
            linePaint.color = Color.parseColor("#2E7D32")
            canvas.drawPath(trendPath, linePaint)

            // Подписи значений у первой и последней точки
            valuePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(fmt(points.first().second), xs.first(), ys.first() - 8 * density, valuePaint)
            valuePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(fmt(points.last().second), xs.last(), ys.last() - 8 * density, valuePaint)
        }

        // Даты первой и последней записи
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(points.first().first.format(dateFormat), sidePad, height - 5 * density, textPaint)
        if (points.size > 1) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                points.last().first.format(dateFormat),
                width - sidePad,
                height - 5 * density,
                textPaint
            )
        }
    }
}
