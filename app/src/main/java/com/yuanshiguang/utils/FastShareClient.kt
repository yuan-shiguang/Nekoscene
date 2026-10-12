package com.yuanshiguang.utils

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

/**
 * Fast Share 局域网接收端：通过 HTTP 从分享方拉取文件清单与文件内容。
 * 进度回调在主线程之外触发，调用方自行切回 UI 线程。
 */
class FastShareClient(
    private val host: String,
    private val port: Int,
    private val key: String,
    private val outDir: File
) {
    var onPhase: ((String) -> Unit)? = null
    var onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    var onRemaining: ((String) -> Unit)? = null
    var onDone: ((success: Boolean) -> Unit)? = null

    private var aborted = false

    fun abort() {
        aborted = true
    }

    fun start() {
        aborted = false
        Thread {
            try {
                onPhase?.invoke("connecting")
                val manifest = fetchManifest()
                if (manifest.isEmpty()) {
                    onPhase?.invoke("idle")
                    onDone?.invoke(false)
                    return@Thread
                }
                var total = 0L
                for (e in manifest) total += e.second
                onPhase?.invoke("receiving")
                outDir.mkdirs()
                var done = 0L
                val start = System.currentTimeMillis()
                for ((rel, size) in manifest) {
                    if (aborted) break
                    val got = downloadFile(rel, size)
                    done += got
                    onProgress?.invoke(done, total)
                    val elapsed = (System.currentTimeMillis() - start) / 1000.0
                    if (elapsed > 0.5 && done < total) {
                        val speed = done / elapsed
                        val remainSec = ((total - done) / speed).toLong()
                        onRemaining?.invoke(formatDuration(remainSec))
                    }
                }
                onPhase?.invoke("verifying")
                onPhase?.invoke(if (aborted) "idle" else "finished")
                onDone?.invoke(!aborted)
            } catch (e: Exception) {
                e.printStackTrace()
                onPhase?.invoke("idle")
                onDone?.invoke(false)
            }
        }.start()
    }

    private fun fetchManifest(): List<Pair<String, Long>> {
        val url = URL("http://$host:$port/manifest?key=${encode(key)}")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        try {
            if (conn.responseCode != 200) return emptyList()
            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            val list = mutableListOf<Pair<String, Long>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(o.getString("p") to o.getLong("s"))
            }
            return list
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(rel: String, size: Long): Long {
        val url = URL("http://$host:$port/file?key=${encode(key)}&path=${encode(rel)}")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 60000
        val target = File(outDir, rel)
        target.parentFile?.mkdirs()
        var got: Long = 0
        try {
            if (conn.responseCode != 200) return 0
            conn.inputStream.use { input ->
                FileOutputStream(target).use { fos ->
                    val buf = ByteArray(8192)
                    var len: Int
                    while (input.read(buf).also { len = it } > 0) {
                        if (aborted) break
                        fos.write(buf, 0, len)
                        got += len
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        // 若中断则删除不完整文件
        if (aborted && got < size) target.delete()
        return got
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun formatDuration(sec: Long): String {
        return when {
            sec < 60 -> "${sec}秒"
            sec < 3600 -> "${sec / 60}分${sec % 60}秒"
            else -> "${sec / 3600}小时${(sec % 3600) / 60}分"
        }
    }
}
