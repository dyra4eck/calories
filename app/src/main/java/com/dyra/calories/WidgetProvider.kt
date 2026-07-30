package com.dyra.calories

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import java.time.LocalDate

/** Виджет: калории и БЖУ за сегодня, нажатие открывает приложение. */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            update(context, appWidgetManager, id)
        }
    }

    companion object {

        /** Обновить все экземпляры виджета (вызывается при изменении данных). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WidgetProvider::class.java)
            )
            for (id in ids) {
                update(context, manager, id)
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val store = Store(context)
            val today = LocalDate.now()
            val entries = store.entriesFor(today)
            val total = entries.sumOf { it.kcal }

            val goal = store.goalsFor(today).kcal
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setImageViewBitmap(R.id.widgetRing, ringBitmap(total, goal))
            views.setTextViewText(
                R.id.widgetKcal,
                context.getString(R.string.widget_kcal, total, goal)
            )
            views.setTextViewText(
                R.id.widgetMacros,
                context.getString(
                    R.string.entry_macros,
                    fmt(round1(entries.sumOf { it.protein })),
                    fmt(round1(entries.sumOf { it.fat })),
                    fmt(round1(entries.sumOf { it.carbs }))
                )
            )
            views.setOnClickPendingIntent(
                R.id.widgetRoot,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(id, views)
        }

        /** Кольцо калорий, как на главном экране (RemoteViews не умеет кастомные вью). */
        private fun ringBitmap(total: Int, goal: Int): Bitmap {
            val size = 216
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val stroke = size * 0.11f
            val green = Color.parseColor("#66BB6A")

            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = Color.argb(70, 255, 255, 255)
            }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = green
            }
            val overPaint = Paint(ringPaint).apply { color = Color.parseColor("#EF5350") }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                textSize = size * 0.22f
                color = Color.WHITE
            }

            val inset = stroke / 2 + size * 0.02f
            val rect = RectF(inset, inset, size - inset, size - inset)
            canvas.drawArc(rect, 0f, 360f, false, basePaint)
            val fraction = if (goal > 0) total.toFloat() / goal else 0f
            canvas.drawArc(rect, -90f, fraction.coerceAtMost(1f) * 360f, false, ringPaint)
            if (fraction > 1f) {
                canvas.drawArc(rect, -90f, (fraction - 1f).coerceAtMost(1f) * 360f, false, overPaint)
            }
            canvas.drawText(
                total.toString(),
                size / 2f,
                size / 2f - (textPaint.ascent() + textPaint.descent()) / 2,
                textPaint
            )
            return bitmap
        }
    }
}
