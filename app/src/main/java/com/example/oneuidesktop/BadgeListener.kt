package com.example.oneuidesktop

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 通知角标数据中心：按包名统计活跃（非常驻）通知数。
 * revision 作为 Compose 可观察版本号，任何变化触发读取角标的重组。
 */
object BadgeStore {
    private val active = HashMap<String, MutableSet<String>>() // pkg -> 活跃通知 key 集合

    var revision by mutableIntStateOf(0)
        private set

    /** 角标样式：true=数字模式，false=圆点模式（One UI 双模式） */
    var showNumbers by mutableStateOf(false)

    fun onPosted(pkg: String, key: String) {
        synchronized(active) {
            active.getOrPut(pkg) { HashSet() }.add(key)
            revision++
        }
    }

    fun onRemoved(pkg: String, key: String) {
        synchronized(active) {
            active[pkg]?.remove(key)
            revision++
        }
    }

    fun count(pkg: String): Int = synchronized(active) {
        active[pkg]?.size ?: 0
    }

    /** 调试辅助：手动塞入假角标 */
    fun debugSet(pkg: String, n: Int) {
        synchronized(active) {
            val set = active.getOrPut(pkg) { HashSet() }
            for (i in 0 until n) set.add("debug-$i")
            revision++
        }
    }
}

/**
 * 通知监听：需要在系统设置中授予"通知使用权"后生效。
 * One UI 规则：常驻通知（USB、输入法等）不参与角标。
 */
class BadgeListener : NotificationListenerService() {

    override fun onListenerConnected() {
        activeNotifications.forEach { track(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        track(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        BadgeStore.onRemoved(sbn.packageName, sbn.key)
    }

    private fun track(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        BadgeStore.onPosted(sbn.packageName, sbn.key)
    }
}
