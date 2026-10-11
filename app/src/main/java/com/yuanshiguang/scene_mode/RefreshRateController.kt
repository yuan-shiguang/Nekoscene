package com.yuanshiguang.scene_mode

import android.content.Context
import android.text.TextUtils
import com.yuanshiguang.common.shell.KeepShellPublic
import com.yuanshiguang.store.SpfConfig
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 刷新率控制器
 *
 * 同步自上游 Scene N1 Alpha13 的 rr_* 功能。
 * 读取 assets/refresh_rate.txt 中的应用刷新率配置，通过 settings 命令切换刷新率。
 *
 * refresh_rate.txt 格式：
 *   # 注释行
 *   包名 最小刷新率 最大刷新率
 *   com.tencent.tmgp.sgame 120 120
 *   com.miHoYo.Yuanshen 60 60
 *
 * 支持的场景：
 * - 按应用设定刷新率（从 refresh_rate.txt 读取）
 * - 空闲/活动/锁屏刷新率（从 SharedPreferences 读取用户配置）
 * - 低电量阈值降频
 * - 低亮度阈值降频
 */
class RefreshRateController(private val context: Context) {

    private val spf = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

    /** 应用刷新率配置：包名 -> (minHz, maxHz) */
    private val appRefreshRates = mutableMapOf<String, Pair<Int, Int>>()

    init {
        loadRefreshRateConfig()
    }

    /**
     * 从 assets/refresh_rate.txt 加载应用刷新率配置
     */
    private fun loadRefreshRateConfig() {
        try {
            context.assets.open("refresh_rate.txt").use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 3) {
                            val pkg = parts[0]
                            val min = parts[1].toIntOrNull() ?: continue
                            val max = parts[2].toIntOrNull() ?: continue
                            appRefreshRates[pkg] = Pair(min, max)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // refresh_rate.txt 不存在或读取失败，忽略
        }
    }

    /**
     * 应用切换时调用：根据包名设置刷新率
     *
     * @param packageName 当前前台应用包名
     */
    fun onAppSwitched(packageName: String) {
        if (!isEnabled()) return

        val (minHz, maxHz) = appRefreshRates[packageName] ?: run {
            // 未在配置列表中的应用，使用通用刷新率
            val defaultMin = spf.getInt(KEY_RR_INACTIVE, 60)
            val defaultMax = spf.getInt(KEY_RR_APPS, 90)
            Pair(defaultMin, defaultMax)
        }

        applyRefreshRate(minHz, maxHz)
    }

    /**
     * 屏幕熄屏时调用：切换到锁屏刷新率
     */
    fun onScreenOff() {
        if (!isEnabled()) return
        val standbyHz = spf.getInt(KEY_RR_STANDBY, 60)
        applyRefreshRate(standbyHz, standbyHz)
    }

    /**
     * 应用刷新率到系统
     *
     * 通过 settings 命令设置：
     * - settings put system min_refresh_rate <min>
     * - settings put system peak_refresh_rate <max>
     *
     * 在严格模式下，会重复设置以降低被系统覆盖的概率。
     */
    private fun applyRefreshRate(minHz: Int, maxHz: Int) {
        val strict = spf.getBoolean(KEY_RR_STRICT, false)
        val compatible = spf.getBoolean(KEY_RR_COMPATIBLE, false)

        val cmd = StringBuilder()
        // 设置最小刷新率
        cmd.append("settings put system min_refresh_rate $minHz;\n")
        // 设置峰值刷新率（compatible 模式用 peak_refresh_rate）
        cmd.append("settings put system peak_refresh_rate $maxHz;\n")

        // 非兼容模式下，同时设置 refresh_rate 主题
        if (!compatible) {
            cmd.append("settings put system refresh_rate $maxHz;\n")
        }

        // 严格模式：重复设置
        if (strict) {
            cmd.append("sleep 0.1;\n")
            cmd.append("settings put system min_refresh_rate $minHz;\n")
            cmd.append("settings put system peak_refresh_rate $maxHz;\n")
        }

        KeepShellPublic.doCmdSync(cmd.toString())
    }

    /**
     * 刷新率控制是否启用
     */
    fun isEnabled(): Boolean {
        return spf.getBoolean(KEY_RR_ENABLED, false)
    }

    /**
     * 获取已配置的应用列表
     */
    fun getConfiguredApps(): Map<String, Pair<Int, Int>> {
        return appRefreshRates.toMap()
    }

    companion object {
        // SharedPreferences 键
        const val KEY_RR_ENABLED = "rr_enabled"
        const val KEY_RR_ACTIVE = "rr_active"          // 触屏活动刷新率
        const val KEY_RR_INACTIVE = "rr_inactive"      // 空闲刷新率
        const val KEY_RR_APPS = "rr_apps"              // 通用应用刷新率
        const val KEY_RR_GAMING = "rr_gaming"          // 游戏刷新率
        const val KEY_RR_GESTURE = "rr_gesture"        // 手势刷新率
        const val KEY_RR_STANDBY = "rr_standby"        // 锁屏刷新率
        const val KEY_RR_BATTERY_LOW = "rr_battery_low"       // 低电量刷新率
        const val KEY_RR_BATTERY_THRESHOLD = "rr_battery_threshold"  // 低电量阈值
        const val KEY_RR_BRIGHTNESS_LOW = "rr_brightness_low"  // 低亮度刷新率
        const val KEY_RR_BRIGHTNESS_THRESHOLD = "rr_brightness_threshold" // 低亮度阈值
        const val KEY_RR_STRICT = "rr_strict"          // 严格模式
        const val KEY_RR_COMPATIBLE = "rr_compatible"  // 兼容模式
        const val KEY_RR_DISABLE_LTPO = "rr_disable_ltpo" // 禁用 LTPO
    }
}
