package com.example.oneuidesktop

import android.content.Context
import org.json.JSONArray

/**
 * 桌面持久化：抽屉排序、主屏布局（含文件夹）、显示设置。
 */
class LauncherStore(context: Context) {
    private val prefs = context.getSharedPreferences("oneui_launcher", Context.MODE_PRIVATE)

    // ---- 抽屉排序 ----

    fun isCustomOrder(): Boolean = prefs.getBoolean(KEY_CUSTOM, false)

    fun setCustomOrder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CUSTOM, enabled).apply()
    }

    fun loadCustomOrder(): List<String> {
        val raw = prefs.getString(KEY_ORDER, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i -> array.getString(i) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomOrder(keys: List<String>) {
        prefs.edit().putString(KEY_ORDER, JSONArray(keys).toString()).apply()
    }

    // ---- 主屏布局 ----

    fun loadHomeLayout(): HomeLayout? =
        prefs.getString(KEY_HOME, null)?.let { parseHomeLayout(it) }

    fun saveHomeLayout(layout: HomeLayout) {
        prefs.edit().putString(KEY_HOME, layout.toJson()).apply()
    }

    // ---- 显示设置 ----

    fun showLabels(): Boolean = prefs.getBoolean(KEY_LABELS, true)

    fun setShowLabels(value: Boolean) {
        prefs.edit().putBoolean(KEY_LABELS, value).apply()
    }

    fun badgeNumbers(): Boolean = prefs.getBoolean(KEY_BADGE_NUMBERS, false)

    fun setBadgeNumbers(value: Boolean) {
        prefs.edit().putBoolean(KEY_BADGE_NUMBERS, value).apply()
    }

    companion object {
        private const val KEY_CUSTOM = "custom_order"
        private const val KEY_ORDER = "order"
        private const val KEY_HOME = "home_layout"
        private const val KEY_LABELS = "show_labels"
        private const val KEY_BADGE_NUMBERS = "badge_numbers"
    }
}
