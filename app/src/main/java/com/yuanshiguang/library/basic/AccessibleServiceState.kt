package com.yuanshiguang.library.basic

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

class AccessibleServiceState {
    companion object {
        // 由 AccessibilityScenceMode 在 onServiceConnected()/onUnbind() 维护，
        // 作为“真正已连接运行”的权威状态，避免仅依据 enabled 列表导致的误判（issue #19）
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