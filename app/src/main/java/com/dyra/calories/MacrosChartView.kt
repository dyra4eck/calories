package com.dyra.calories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** График БЖУ по дням: столбики из трёх слоёв (белки, жиры, углеводы, в граммах). */
class MacrosChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var protein: List<Double> = emptyList()
    private var fat: List<Double> = emptyList()
    private var carbs: List<Double> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    fun setData(
        labels: List<String>,
        protein: List<Double>,
        fat: List<Double>,
        carbs: List<Double>
    ) {
        this.labels = labels
        this.protein = protein
        this.fat = fat
        this.carbs = carbs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (protein.isEmpty() || width == 0 || height == 0) return

        val density = resources.displayMetrics.density
        textPaint.textSize = 11 * density

        val labelSpace = 20 * density
        val chartBottom = height - labelSpace
        val chartHeight = chartBottom - 6 * density
        val maxTotal = protein.indices.maxOf { protein[it] + fat[it] + carbs[it] }
            .coerceAtLeast(1.0).toFloat() * 1.08f

        val slot = width.toFloat() / protein.size
        val barWidth = slot * if (protein.size <= 10) 0.55f else 0.7f

        for (i in protein.indices) {
            val centerX = slot * i + slot / 2
            var y = chartBottom

            // Снизу вверх: белки, жиры, углеводы
            y = drawSegment(canvas, centerX, y, protein[i], maxTotal, chartHeight, barWidth, PROTEIN_COLOR)
            y = drawSegment(canvas, centerX, y, fat[i], maxTotal, chartHeight, barWidth, FAT_COLOR)
            drawSegment(canvas, centerX, y, carbs[i], maxTotal, chartHeight, barWidth, CARBS_COLOR)

            if (labels[i].isNotEmpty()) {
                canvas.drawText(labels[i], centerX, height - 5 * density, textPaint)
            }
        }
    }

    private fun drawSegment(
        canvas: Canvas,
        centerX: Float,
        bottom: Float,
        value: Double,
        maxTotal: Float,
        chartHeight: Float,
        barWidth: Float,
        color: Int
    ): Float {
        val segmentHeight = (value / maxTotal * chartHeight).toFloat()
        if (segmentHeight > 0) {
            paint.color = color
            canvas.drawRect(
                centerX - barWidth / 2,
                bottom - segmentHeight,
                centerX + barWidth / 2,
                bottom,
                paint
            )
        }
        return bottom - segmentHeight
    }

    companion object {
        val PROTEIN_COLOR: Int = Color.parseColor("#2E7D32")
        val FAT_COLOR: Int = Color.parseColor("#F9A825")
        val CARBS_COLOR: Int = Color.parseColor("#1565C0")
    }
}
