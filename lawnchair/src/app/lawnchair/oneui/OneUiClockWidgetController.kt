package app.lawnchair.oneui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Places the One UI clock widget on the first workspace page the first time
 * the launcher starts, and remembers which app widget ids belong to it.
 *
 * Binding an app widget requires the user's consent the first time a
 * launcher is not a system app: [Launcher.addPendingItem] already handles the
 * `ACTION_APPWIDGET_BIND` dialog and retries the drop once the user accepts.
 * If the dialog is cancelled the overlay clock stays visible, because no
 * widget id is ever reported back.
 */
object OneUiClockWidgetController {

    private const val PREFS_NAME = "oneui_clock_widget"
    private const val KEY_BOUND_IDS = "bound_ids"
    private const val KEY_AUTO_ADD_ATTEMPTED = "auto_add_attempted"

    private const val SCREEN_ID = 0
    private const val CELL_X = 0
    private const val CELL_Y = 0
    private const val SPAN_X = 4
    private const val SPAN_Y = 1

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Views that mirror the widget state (the fallback overlay). Keeping this
     * explicit avoids polling [AppWidgetManager] from a pre-draw listener,
     * which would stop updating as soon as the launcher window goes idle.
     */
    private val stateListeners = CopyOnWriteArraySet<() -> Unit>()

    /** Called from `LawnchairLauncher.onResume`. */
    @JvmStatic
    fun onLauncherResume(launcher: Launcher) {
        val prefs = prefs(launcher)
        pruneMissingWidgets(launcher)
        notifyWidgetStateChanged()

        if (hasBoundWidget(launcher)) {
            // Adding a widget shows the resize frame. It is not useful for a
            // clock that fills the first row, so dismiss it once the drop has
            // settled (the bind dialog may still be closing here).
            handler.postDelayed({ closeResizeFrame(launcher) }, 800L)
            return
        }

        if (!prefs.getBoolean(KEY_AUTO_ADD_ATTEMPTED, false)) {
            prefs.edit().putBoolean(KEY_AUTO_ADD_ATTEMPTED, true).apply()
            scheduleAdd(launcher, attempt = 0)
        }
    }

    private fun scheduleAdd(launcher: Launcher, attempt: Int) {
        if (attempt > 20 || launcher.isDestroyed) {
            return
        }
        handler.postDelayed({
            if (launcher.isDestroyed) {
                return@postDelayed
            }
            if (launcher.isWorkspaceLoading) {
                scheduleAdd(launcher, attempt + 1)
                return@postDelayed
            }
            addClockWidget(launcher)
        }, if (attempt == 0) 1200L else 500L)
    }

    private fun addClockWidget(launcher: Launcher) {
        val manager = AppWidgetManager.getInstance(launcher)
        val component = OneUiClockWidgetProvider.componentName(launcher)
        val providerInfo = manager.installedProviders.firstOrNull { it.provider == component }
            ?: return
        val info = LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, providerInfo)
        val pending = PendingAddWidgetInfo(info, LauncherSettings.Favorites.CONTAINER_DESKTOP)

        launcher.addPendingItem(
            pending,
            LauncherSettings.Favorites.CONTAINER_DESKTOP,
            SCREEN_ID,
            intArrayOf(CELL_X, CELL_Y),
            SPAN_X,
            SPAN_Y,
        )

        // The widget is live as soon as the host view is created; by then the
        // resize frame has been shown. Close it without waiting for another
        // resume, which also covers the no-dialog (same UID) bind path.
        handler.postDelayed({ closeResizeFrame(launcher) }, 1500L)
        handler.postDelayed({ closeResizeFrame(launcher) }, 4000L)
        handler.postDelayed({ closeResizeFrame(launcher) }, 8000L)
    }

    private fun closeResizeFrame(launcher: Launcher) {
        if (launcher.isDestroyed) {
            return
        }
        AbstractFloatingView.closeOpenViews(
            launcher,
            false,
            AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME,
        )
    }

    /** True while at least one bound clock widget is still hosted. */
    @JvmStatic
    fun hasBoundWidget(context: Context): Boolean {
        val ids = prefs(context).getStringSet(KEY_BOUND_IDS, emptySet()).orEmpty()
        if (ids.isEmpty()) {
            return false
        }
        val manager = AppWidgetManager.getInstance(context)
        return ids.any { raw ->
            val id = raw.toIntOrNull() ?: return@any false
            manager.getAppWidgetInfo(id) != null
        }
    }

    /**
     * Re-applies the current clock style to every bound widget. Called from the
     * preferences screen after the user picks a style.
     */
    @JvmStatic
    fun applyStyleToWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = prefs(context).getStringSet(KEY_BOUND_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .filter { manager.getAppWidgetInfo(it) != null }
        if (ids.isEmpty()) {
            return
        }
        val views = RemoteViews(context.packageName, OneUiClockWidgetProvider.layoutForStyle(context))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    /** Registers a callback invoked whenever the clock widget appears or goes away. */
    @JvmStatic
    fun addWidgetStateListener(listener: () -> Unit) {
        stateListeners.add(listener)
    }

    @JvmStatic
    fun removeWidgetStateListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    private fun notifyWidgetStateChanged() {
        stateListeners.forEach { it() }
    }

    internal fun onWidgetsBound(context: Context, appWidgetIds: IntArray) {
        val prefs = prefs(context)
        val ids = prefs.getStringSet(KEY_BOUND_IDS, emptySet()).orEmpty().toMutableSet()
        appWidgetIds.forEach { ids.add(it.toString()) }
        prefs.edit().putStringSet(KEY_BOUND_IDS, ids).apply()
        notifyWidgetStateChanged()
    }

    internal fun onWidgetsDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = prefs(context)
        val ids = prefs.getStringSet(KEY_BOUND_IDS, emptySet()).orEmpty().toMutableSet()
        appWidgetIds.forEach { ids.remove(it.toString()) }
        prefs.edit().putStringSet(KEY_BOUND_IDS, ids).apply()
        notifyWidgetStateChanged()
    }

    private fun pruneMissingWidgets(context: Context) {
        val prefs = prefs(context)
        val ids = prefs.getStringSet(KEY_BOUND_IDS, emptySet()).orEmpty()
        if (ids.isEmpty()) {
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        val live = ids.filter { raw ->
            val id = raw.toIntOrNull() ?: return@filter false
            manager.getAppWidgetInfo(id) != null
        }
        if (live.size != ids.size) {
            prefs.edit().putStringSet(KEY_BOUND_IDS, live.toSet()).apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
