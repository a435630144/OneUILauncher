package com.example.oneuidesktop

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

/** One UI 图标圆角比例（约 27% 对角圆角，视觉近似三星 squircle） */
private const val ICON_CORNER_RATIO = 0.27f

/**
 * 统一处理为 One UI 风格图标：
 * - 自适应图标：背景层+前景层裁进圆角方形（One UI 形状，替代旧的圆形裁剪）
 * - 普通位图图标：提取主色生成柔和底板，内容按比例居中缩放（Samsung 对无底板图标的处理）
 */
fun drawableToBitmap(drawable: Drawable, sizePx: Int = 96): Bitmap {
    if (drawable is AdaptiveIconDrawable) {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val clip = Path().apply {
            val r = sizePx * ICON_CORNER_RATIO
            addRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), r, r, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        val scale = sizePx / 72f
        canvas.scale(scale, scale)
        canvas.translate(-18f, -18f)
        drawable.setBounds(0, 0, 108, 108)
        drawable.draw(canvas)
        return bitmap
    }

    // 普通图标：先画到小位图提取主色
    val probe = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val probeCanvas = Canvas(probe)
    drawable.setBounds(0, 0, 24, 24)
    drawable.draw(probeCanvas)
    val bg = dominantPanelColor(probe)

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
    val r = sizePx * ICON_CORNER_RATIO
    canvas.drawRoundRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), r, r, paint)

    // 内容缩放到约 62% 居中，保持宽高比
    val dw = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else sizePx
    val dh = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else sizePx
    val content = sizePx * 0.62f
    val scale = minOf(content / dw, content / dh)
    val w = dw * scale
    val h = dh * scale
    val left = (sizePx - w) / 2f
    val top = (sizePx - h) / 2f
    drawable.setBounds(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt())
    drawable.draw(canvas)
    return bitmap
}

/** 取平均色并柔和化（低饱和高明度），灰白图标落到 One UI 浅灰底 */
private fun dominantPanelColor(probe: Bitmap): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (x in 0 until probe.width) {
        for (y in 0 until probe.height) {
            val c = probe.getPixel(x, y)
            if (Color.alpha(c) < 40) continue
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
            n++
        }
    }
    if (n == 0L) return Color.parseColor("#F1F3F5")
    val avg = Color.rgb((r / n).toInt().coerceIn(0, 255), (g / n).toInt().coerceIn(0, 255), (b / n).toInt().coerceIn(0, 255))
    val hsv = FloatArray(3)
    Color.colorToHSV(avg, hsv)
    return if (hsv[1] < 0.12f) {
        // 本身接近灰白：直接用 One UI 浅灰底
        Color.parseColor("#F1F3F5")
    } else {
        hsv[1] = (hsv[1] * 0.38f).coerceIn(0f, 0.45f)
        hsv[2] = 0.90f
        Color.HSVToColor(hsv)
    }
}
