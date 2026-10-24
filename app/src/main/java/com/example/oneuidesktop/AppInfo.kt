package com.example.oneuidesktop

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Collator
import java.util.Locale

data class AppInfo(
    val key: String,
    val label: String,
    val icon: ImageBitmap,
    val launchIntent: Intent,
)

object AppRepository {

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(probe, 0)
        val collator = Collator.getInstance(Locale.CHINA)
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull()
                ?: return@mapNotNull null
            val drawable = runCatching { info.loadIcon(pm) }.getOrNull()
                ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(activity.packageName, activity.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppInfo(
                key = "${activity.packageName}/${activity.name}",
                label = label,
                icon = drawableToBitmap(drawable).asImageBitmap(),
                launchIntent = launch,
            )
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}

/**
 * 把应用图标转成位图；自适应图标按 One UI 风格裁成圆形（108 画布中心 72 为可见区）。
 */
fun drawableToBitmap(drawable: Drawable, sizePx: Int = 96): Bitmap {
    if (drawable is AdaptiveIconDrawable) {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val clip = Path().apply {
            addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        val scale = sizePx / 72f
        canvas.scale(scale, scale)
        canvas.translate(-18f, -18f)
        drawable.setBounds(0, 0, 108, 108)
        drawable.draw(canvas)
        return bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else sizePx
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap
}
