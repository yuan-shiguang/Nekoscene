package com.yuanshiguang.nekoscene

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yuanshiguang.nekoscene.activities.ActivityMain
import com.yuanshiguang.scene_mode.ModeSwitcher

/**
 * 桌面小组件：显示当前场景模式，点击打开主界面。
 *
 * 同步自上游 Scene N1 Alpha13 的 SceneAppWidgetProvider。
 */
class SceneAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SceneAppWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_scene_mode)

        // 显示当前模式
        val mode = ModeSwitcher.getCurrentPowerMode()
        val modeText = ModeSwitcher.getModName(mode)
        views.setTextViewText(R.id.widget_mode, modeText)

        // 点击打开主界面
        val openIntent = Intent(context, ActivityMain::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        views.setOnClickPendingIntent(
            R.id.widget_icon,
            PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        const val ACTION_REFRESH = "com.yuanshiguang.nekoscene.WIDGET_REFRESH"

        /**
         * 通知所有小组件刷新（在模式切换后调用）。
         */
        fun notifyRefresh(context: Context) {
            val intent = Intent(context, SceneAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
