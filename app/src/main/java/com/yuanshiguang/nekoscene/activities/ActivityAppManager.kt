package com.yuanshiguang.nekoscene.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.yuanshiguang.nekoscene.R
import com.yuanshiguang.utils.WorkModeManager

/**
 * 应用管理器入口（同步自上游 N1 Alpha13 应用页 ROOT 管理器）。
 * 由 [ActivityApplistions] 通过 extra "type" 跳转，支持：
 *   autostart    自启管理
 *   notification 通知管理
 *   permission   权限管理
 *   assoc        关联启动
 * 仅 ROOT 模式可进入；进入后展示说明并可跳转至对应系统设置。
 */
class ActivityAppManager : ActivityBase() {
    companion object {
        const val EXTRA_TYPE = "type"
    }

    private data class MgrSpec(
        val titleRes: Int,
        val descRes: Int,
        val buildIntent: () -> Intent
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 防御性校验：仅 ROOT 模式可进入
        if (!WorkModeManager.isRoot(this)) {
            Toast.makeText(this, getString(R.string.app_mgr_root_required), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_app_manager)
        setBackArrow()

        val type = intent.getStringExtra(EXTRA_TYPE) ?: "autostart"
        val spec = specFor(type)
        title = getString(spec.titleRes)
        findViewById<TextView>(R.id.mgr_desc).text = getString(spec.descRes)

        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            try {
                startActivity(spec.buildIntent())
            } catch (ex: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开系统设置~", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun specFor(type: String): MgrSpec = when (type) {
        "notification" -> MgrSpec(
            R.string.app_mgr_notification,
            R.string.app_mgr_notification_desc
        ) { Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) } }
        "permission" -> MgrSpec(
            R.string.app_mgr_permission,
            R.string.app_mgr_permission_desc
        ) { Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) }
        "assoc" -> MgrSpec(
            R.string.app_mgr_assoc,
            R.string.app_mgr_assoc_desc
        ) { Intent(Settings.ACTION_SETTINGS) }
        else -> MgrSpec(
            R.string.app_mgr_autostart,
            R.string.app_mgr_autostart_desc
        ) { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
    }
}
