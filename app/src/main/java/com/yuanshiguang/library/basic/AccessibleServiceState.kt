package com.yuanshiguang.library.basic

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

class AccessibleServiceState {
    companion object {
        // 记录无障碍服务的“真实连接态”，由 AccessibilityScenceMode 在 onServiceConnected/onUnbind 中维护。
        // 仅用 getEnabledAccessibilityServiceList 只能判断“是否已启用”，在 MIUI/HyperOS 等 ROM 上服务被系统杀掉后仍会误报为已启用。
        @Volatile
        var isServiceConnected = false
    }

    fun serviceRunning(context: Context, serviceName: String): Boolean {
        if (isServiceConnected) return true
        val m = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceInfos = m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (serviceInfo in serviceInfos) {
            if (serviceInfo.id.endsWith(serviceName) || serviceInfo.id.contains(serviceName)) {
                return true
            }
        }
        return false
    }
}