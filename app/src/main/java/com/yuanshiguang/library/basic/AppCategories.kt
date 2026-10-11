package com.yuanshiguang.library.basic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 应用分类助手
 *
 * 读取 assets/categories.json 中的应用分类配置，
 * 为场景模式提供按分类批量操作的能力（如所有游戏应用、所有视频应用等）。
 *
 * 同步自上游 Scene N1 Alpha13 的 categories.json 数据。
 *
 * categories.json 格式：
 * [
 *   {
 *     "friendly": "抖音、西瓜、快手",
 *     "packages": ["com.ss.android.ugc.aweme", ...],
 *     "category": "ShortVideo"
 *   },
 *   ...
 * ]
 */
object AppCategories {

    private var loaded = false
    private val categoryToPackages = mutableMapOf<String, MutableList<String>>()
    private val packageToCategory = mutableMapOf<String, String>()

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            context.assets.open("categories.json").use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    val text = reader.readText()
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val category = obj.optString("category", "")
                        val packages = obj.optJSONArray("packages")
                        if (packages != null && category.isNotEmpty()) {
                            val list = categoryToPackages.getOrPut(category) { mutableListOf() }
                            for (j in 0 until packages.length()) {
                                val pkg = packages.getString(j)
                                list.add(pkg)
                                packageToCategory[pkg] = category
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // categories.json 不存在或格式错误，忽略
        }
    }

    /**
     * 获取指定分类下的所有包名
     */
    fun getPackagesByCategory(context: Context, category: String): List<String> {
        load(context)
        return categoryToPackages[category]?.toList() ?: emptyList()
    }

    /**
     * 获取应用所属分类
     */
    fun getCategory(context: Context, packageName: String): String? {
        load(context)
        return packageToCategory[packageName]
    }

    /**
     * 获取所有分类名
     */
    fun getAllCategories(context: Context): Set<String> {
        load(context)
        return categoryToPackages.keys.toSet()
    }

    /**
     * 判断是否为游戏应用
     */
    fun isGame(context: Context, packageName: String): Boolean {
        val cat = getCategory(context, packageName)
        return cat == "Game" || cat == "HeavyGame" || cat == "LightGame"
    }

    /**
     * 判断是否为视频应用
     */
    fun isVideo(context: Context, packageName: String): Boolean {
        val cat = getCategory(context, packageName)
        return cat == "Video" || cat == "ShortVideo"
    }
}
