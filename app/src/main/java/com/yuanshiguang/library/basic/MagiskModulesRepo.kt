package com.yuanshiguang.library.basic

import org.json.JSONObject
import java.net.URL
import java.util.Locale

/**
 * Magisk 模块仓库查询。
 *
 * 旧实现直接抓取 https://github.com/orgs/Magisk-Modules-Repo/repositories?q=xxx 的 HTML 再正则匹配
 * href="/Magisk-Modules-Repo/xxx"，但 GitHub 早已改为前端渲染，返回的 HTML 中不再包含模块链接，
 * 导致搜索始终为空（接口已失效）。
 *
 * 现改为读取官方模块索引（与新版 Scene 的做法一致）：
 *   https://magisk-modules-repo.github.io/submission/modules.json
 */
class MagiskModulesRepo {

    companion object {
        private const val MODULES_INDEX = "https://magisk-modules-repo.github.io/submission/modules.json"

        /** 索引中未识别出归属时的兜底组织名 */
        private const val OFFICIAL_OWNER = "Magisk-Modules-Repo"

        /** 从 zip_url 中提取 owner：https://github.com/<owner>/<repo>/archive/<sha>.zip */
        private val OWNER_REGEX = Regex("github\\.com/([^/]+)/[^/]+/archive")
    }

    /**
     * 按关键字查询模块。
     *
     * @param keywords 关键字，为空时返回全部模块
     * @return "owner/repo" 形式的结果列表，例如 "Magisk-Modules-Repo/nano-ndk"
     */
    fun query(keywords: String?): ArrayList<String> {
        val modules = ArrayList<String>()
        val keyword = keywords?.trim()?.toLowerCase(Locale.ROOT) ?: ""

        val connection = URL(MODULES_INDEX).openConnection()
        // 设置连接主机服务器的超时时间 毫秒
        connection.connectTimeout = 8000
        // 设置读取远程返回的数据时间 毫秒
        connection.readTimeout = 15000
        connection.connect()

        val body = connection.getInputStream().use { String(it.readBytes(), Charsets.UTF_8) }
        val list = JSONObject(body).optJSONArray("modules") ?: return modules

        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isEmpty()) {
                continue
            }

            val owner = OWNER_REGEX.find(item.optString("zip_url"))?.groupValues?.get(1) ?: OFFICIAL_OWNER
            val path = "$owner/$id"

            if (keyword.isEmpty() || path.toLowerCase(Locale.ROOT).contains(keyword)) {
                modules.add(path)
            }
        }

        return modules
    }
}
