package com.yuanshiguang.utils

import android.content.Context
import android.content.SharedPreferences
import com.yuanshiguang.nekoscene.R

/**
 * 工作模式管理（同步自上游 N1 Alpha13）：
 *   基础模式(BASIC) / ADB模式(ADB) / ROOT模式(ROOT)
 * 不同模式可用功能范围不同，ROOT 模式解锁全部[查看/修改]能力。
 * 模式持久化保存在 SharedPreferences。
 */
object WorkModeManager {
    const val MODE_BASIC = 0
    const val MODE_ADB = 1
    const val MODE_ROOT = 2

    private const val PREF = "scene_work_mode"
    private const val KEY = "mode"

    private fun pref(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getMode(context: Context): Int = pref(context).getInt(KEY, MODE_BASIC)

    fun setMode(context: Context, mode: Int) {
        val m = when (mode) {
            MODE_ADB -> MODE_ADB
            MODE_ROOT -> MODE_ROOT
            else -> MODE_BASIC
        }
        pref(context).edit().putInt(KEY, m).apply()
    }

    /** 当前模式是否达到 required 级别 */
    fun atLeast(context: Context, required: Int): Boolean = getMode(context) >= required

    fun isAdb(context: Context): Boolean = getMode(context) >= MODE_ADB
    fun isRoot(context: Context): Boolean = getMode(context) >= MODE_ROOT

    fun nameRes(mode: Int): Int = when (mode) {
        MODE_ADB -> R.string.mode_adb
        MODE_ROOT -> R.string.mode_root
        else -> R.string.mode_basic
    }

    fun descRes(mode: Int): Int = when (mode) {
        MODE_ADB -> R.string.mode_adb_desc
        MODE_ROOT -> R.string.mode_root_desc
        else -> R.string.mode_basic_desc
    }

    fun currentName(context: Context): String = context.getString(nameRes(getMode(context)))
}
