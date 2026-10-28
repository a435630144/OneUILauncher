package app.lawnchair.oneui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Hosts the One UI clock / date block as a real home screen widget.
 *
 * The RemoteViews only contain two [android.widget.TextClock]s, so the widget
 * keeps ticking without a periodic update broadcast; the provider is only
 * invoked when the widget is added or re-inflated.
 */
class OneUiClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.oneui_clock_widget)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views)
        }
        OneUiClockWidgetController.onWidgetsBound(context, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        OneUiClockWidgetController.onWidgetsDeleted(context, appWidgetIds)
    }

    companion object {
        @JvmStatic
        fun componentName(context: Context): ComponentName =
            ComponentName(context.packageName, OneUiClockWidgetProvider::class.java.name)
    }
}
