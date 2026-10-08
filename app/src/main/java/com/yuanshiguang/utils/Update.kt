package com.yuanshiguang.utils


import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yuanshiguang.common.ui.DialogHelper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class Update {
    companion object {
        /**
         * 更新检查原先走阿里云 OSS（vtools.oss-cn-beijing.aliyuncs.com/vi/Scene5.json，上游接口随时会下线），
         * 现改为读取本仓库的 GitHub Releases。
         */
        private const val RELEASE_API = "https://api.github.com/repos/yuan-shiguang/Nekoscene/releases/latest"
        private const val USER_AGENT = "Nekoscene-Android"
    }

    private fun currentVersionCode(context: Context): Int {
        val manager = context.packageManager
        var code = 0
        try {
            val info = manager.getPackageInfo(context.packageName, 0)
            code = info.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return code
    }

    private fun currentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            ""
        }
    }

    /** 把 "v5.0.1-alpha2" 之类的版本串拆成可比较的数字序列 */
    private fun versionNumbers(version: String): List<Int> {
        return Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()
    }

    private fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remote = versionNumbers(remoteVersion)
        val current = versionNumbers(currentVersion)
        if (remote.isEmpty()) {
            return false
        }
        val size = Math.max(remote.size, current.size)
        for (i in 0 until size) {
            val r = if (i < remote.size) remote[i] else 0
            val c = if (i < current.size) current[i] else 0
            if (r != c) {
                return r > c
            }
        }
        return false
    }

    fun checkUpdate(context: Context) {
        val handler = Handler(Looper.getMainLooper());
        Thread(Runnable {
            try {
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                // 设置连接主机服务器的超时时间：15000毫秒
                connection.connectTimeout = 15000
                // 设置读取远程返回的数据时间：20000毫秒
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", USER_AGENT)

                // 仓库还没发布 Release、或触发 GitHub 限流时直接静默跳过
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return@Runnable
                }

                val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                connection.disconnect()

                val release = JSONObject(body)
                // tag_name 形如 "v5.0.1"，去掉前缀 v 后与本地 versionName 比较
                val remoteVersion = release.optString("tag_name").trim().trimStart('v', 'V')
                if (remoteVersion.isEmpty()) {
                    return@Runnable
                }

                if (isNewer(remoteVersion, currentVersionName(context))) {
                    handler.post {
                        try {
                            update(context, release, remoteVersion)
                        } catch (ex: java.lang.Exception) {

                        }
                    }
                }
            } catch (ex: Exception) {
                /*
                handler.post {
                    Toast.makeText(context, "检查更新失败！\n" + ex.message, Toast.LENGTH_SHORT).show()
                }
                */
            }
        }).start()
    }

    /** 优先取 Release 里的 .apk 资产，没有资产时退回 Release 页面 */
    private fun pickDownloadUrl(release: JSONObject): String {
        val assets = release.optJSONArray("assets")
        if (assets != null) {
            var fallback: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val url = asset.optString("browser_download_url")
                if (url.isEmpty()) {
                    continue
                }
                if (url.endsWith(".apk", ignoreCase = true)) {
                    return url
                }
                if (fallback == null) {
                    fallback = url
                }
            }
            if (fallback != null) {
                return fallback
            }
        }
        return release.optString("html_url")
    }

    private fun update(context: Context, release: JSONObject, remoteVersion: String) {
        val message = release.optString("body").ifEmpty { "（详见 GitHub Release 说明）" }
        DialogHelper.confirm(context,
                "下载新版本" + remoteVersion + " ？",
                "更新内容：" + "\n\n" + message,
                {
                    val downloadUrl = pickDownloadUrl(release)
                    try {
                        val intent = Intent()
                        intent.setAction(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(downloadUrl)
                        context.startActivity(intent)
                    } catch (ex: java.lang.Exception) {
                        Toast.makeText(context, "启动下载失败！", Toast.LENGTH_SHORT).show()
                    }
                })
                .setCancelable(false)
    }

    fun getRealFilePath(context: Context, uri: Uri?): String? {
        if (null == uri) return null
        val scheme = uri.scheme
        var data: String? = null
        if (scheme == null)
            data = uri.path
        else if (ContentResolver.SCHEME_FILE == scheme) {
            data = uri.path
        } else if (ContentResolver.SCHEME_CONTENT == scheme) {
            val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.Images.ImageColumns.DATA), null, null, null)
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                    if (index > -1) {
                        data = cursor.getString(index)
                    }
                }
                cursor.close()
            }
        }
        return data
    }


    // 安装Apk
    private fun installApk(context: Context, filePath: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            // i.setDataAndType(Uri.fromFile(File(filePath)), "application/vnd.android.package-archive")

            val fileUri = FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", File(filePath))
            i.setDataAndType(fileUri, "application/vnd.android.package-archive")

            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            Log.e("installApk", "" + e.message)
            // Log.e(TAG, "安装失败")
            e.printStackTrace()
        }
    }
}
