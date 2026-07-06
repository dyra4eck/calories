package com.dyra.calories

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
            val entries = store.entriesFor(LocalDate.now())
            val total = entries.sumOf { it.kcal }

            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(
                R.id.widgetKcal,
                context.getString(R.string.widget_kcal, total, store.goal)
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
    }
}
