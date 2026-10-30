package app.lawnchair.oneui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R

/**
 * Hosts the One UI clock / date block as a real home screen widget.
 *
 * Four RemoteViews layouts map to the four One UI clock styles (see
 * [PreferenceManager.clockStyle]). The RemoteViews only contain
 * [android.widget.TextClock]s, so the widget keeps ticking without a periodic
 * update broadcast; the provider is only invoked when the widget is added or
 * re-inflated.
 */
class OneUiClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val layoutRes = when (PreferenceManager.getInstance(context).clockStyle.get()) {
            1 -> R.layout.oneui_clock_widget_compact
            2 -> R.layout.oneui_clock_widget_time_only
            3 -> R.layout.oneui_clock_widget_centered
            else -> R.layout.oneui_clock_widget
        }
        val views = RemoteViews(context.packageName, layoutRes)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views)
        }
        OneUiClockWidgetController.onWidgetsBound(context, appWidgetIds)
    }

    companion object {
        @JvmStatic
        fun componentName(context: Context): ComponentName =
            ComponentName(context.packageName, OneUiClockWidgetProvider::class.java.name)

        @JvmStatic
        fun layoutForStyle(context: Context): Int = when (
            PreferenceManager.getInstance(context).clockStyle.get()
        ) {
            1 -> R.layout.oneui_clock_widget_compact
            2 -> R.layout.oneui_clock_widget_time_only
            3 -> R.layout.oneui_clock_widget_centered
            else -> R.layout.oneui_clock_widget
        }
    }
}
