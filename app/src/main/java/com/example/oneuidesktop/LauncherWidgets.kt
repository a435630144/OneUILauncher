package com.example.oneuidesktop

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 主屏承载小组件的宿主（HOST_ID 任意固定值） */
class LauncherWidgetHost(context: Context) : AppWidgetHost(context, 4242)

object WidgetCatalog {

    fun installed(context: Context): List<AppWidgetProviderInfo> =
        AppWidgetManager.getInstance(context).installedProviders

    fun label(context: Context, info: AppWidgetProviderInfo): String =
        info.loadLabel(context.packageManager)

    /** 预览图；失败用图标兜底，最终返回 null 由 UI 显示占位 */
    fun preview(context: Context, info: AppWidgetProviderInfo, widthPx: Int): ImageBitmap? =
        runCatching {
            val drawable = info.loadPreviewImage(context, 0)
                ?: info.loadIcon(context, 0)
                ?: return@runCatching null
            val dw = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else widthPx
            val dh = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else widthPx / 2
            val scale = widthPx.toFloat() / dw
            val bmp = Bitmap.createBitmap(
                widthPx,
                (dh * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, bmp.width, bmp.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()

    /** 根据小组件声明的最小宽度估算占几列（竖屏 4 列为基准） */
    fun suggestedSpan(context: Context, info: AppWidgetProviderInfo, cols: Int): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val cellDp = dm.widthPixels / (cols * dm.density)
        val minWidthCells = ((info.minWidth / dm.density) / cellDp + 0.5f).toInt().coerceIn(1, cols)
        val minHeightCells = ((info.minHeight / dm.density) / (cellDp * 0.82f) + 0.5f).toInt().coerceIn(1, 4)
        return minWidthCells to minHeightCells
    }

    fun createView(
        host: AppWidgetHost,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ): AppWidgetHostView? = runCatching {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return@runCatching null
        host.createView(null, appWidgetId, info)
    }.getOrNull()
}
