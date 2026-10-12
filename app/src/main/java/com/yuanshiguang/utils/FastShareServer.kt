package com.yuanshiguang.utils

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.Charset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fast Share 局域网分享服务端：基于标准 ServerSocket 实现一个极简 HTTP 服务，
 * 无需任何第三方依赖。分享方选定若干文件/目录后启动，接收方通过 HTTP 拉取。
 *
 * 接口：
 *   GET /manifest?key=KEY  -> JSON 数组 [{"p":相对路径,"s":字节数}, ...]
 *   GET /file?key=KEY&path=URL编码的相对路径 -> 流式返回文件字节
 * key 不匹配返回 403。
 */
class FastShareServer(
    private val shareItems: List<File>,
    private val key: String,
    private val port: Int = 8899
) {
    private var serverSocket: ServerSocket? = null
    private var running = false

    var onPhase: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val root: File = commonParent(shareItems)
    private val entries: List<Pair<String, File>> = buildEntries()

    private fun commonParent(items: List<File>): File {
        if (items.size == 1) return items[0].parentFile ?: items[0]
        var p: File? = items[0].parentFile
        while (p != null) {
            val cur = p
            if (items.any { !it.absolutePath.startsWith(cur.absolutePath) }) {
                p = cur.parentFile
            } else break
        }
        return p ?: File("/")
    }

    private fun buildEntries(): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()
        for (item in shareItems) {
            if (item.isDirectory) addDir(item, item, out)
            else if (item.isFile) out.add(rel(item, item.parentFile ?: root) to item)
        }
        return out
    }

    private fun addDir(dir: File, base: File, out: MutableList<Pair<String, File>>) {
        dir.listFiles()?.forEach {
            if (it.isDirectory) addDir(it, base, out) else out.add(rel(it, base) to it)
        }
    }

    private fun rel(file: File, base: File): String =
        base.toURI().relativize(file.toURI()).path

    fun fileCount(): Int = entries.size
    fun totalSize(): Long = entries.sumOf { it.second.length() }

    fun start() {
        if (running) return
        Thread {
            try {
                serverSocket = ServerSocket(port)
                running = true
                onPhase?.invoke("standby")
                while (running) {
                    val client = serverSocket!!.accept()
                    try {
                        handle(client)
                    } catch (e: Exception) {
                        // 单连接异常不影响整体
                    }
                }
            } catch (e: Exception) {
                if (running) onError?.invoke(e.message ?: "server error")
                running = false
            }
        }.start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
        }
        serverSocket = null
        onPhase?.invoke("idle")
    }

    fun isRunning(): Boolean = running

    private fun handle(socket: Socket) {
        socket.use {
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charset.forName("UTF-8")))
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendError(socket, 400)
                return
            }
            val url = parts[1]
            val qIndex = url.indexOf('?')
            val path = if (qIndex >= 0) url.substring(0, qIndex) else url
            val query = if (qIndex >= 0) url.substring(qIndex + 1) else ""
            val params = parseQuery(query)
            if (params["key"] != key) {
                sendError(socket, 403)
                return
            }
            when (path) {
                "/manifest" -> sendManifest(socket)
                "/file" -> {
                    val rel = params["path"]?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                    sendFile(socket, rel)
                }
                else -> sendError(socket, 404)
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) map[pair.substring(0, eq)] = pair.substring(eq + 1)
        }
        return map
    }

    private fun sendManifest(socket: Socket) {
        val arr = JSONArray()
        for ((p, f) in entries) {
            val o = JSONObject()
            o.put("p", p)
            o.put("s", f.length())
            arr.put(o)
        }
        val body = arr.toString().toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun sendFile(socket: Socket, relPath: String) {
        if (relPath.isEmpty()) {
            sendError(socket, 404)
            return
        }
        val file = File(root, relPath)
        if (!file.exists() || file.isDirectory) {
            sendError(socket, 404)
            return
        }
        val out = socket.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8))
        file.inputStream().use { it.copyTo(out) }
        out.flush()
    }

    private fun sendError(socket: Socket, code: Int) {
        val msg = when (code) {
            403 -> "403 Forbidden"
            404 -> "404 Not Found"
            else -> "400 Bad Request"
        }
        val body = msg.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(("HTTP/1.1 $msg\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }
}
