package com.yuanshiguang.utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 文件管理器通用操作工具（基于标准 java.io，无需额外依赖）。
 * 所有方法均同步执行，调用方应在后台线程中使用。
 */
object FileOpUtils {
    private const val BUFFER = 8192

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2fMB", mb)
        val gb = mb / 1024.0
        if (gb < 1024) return String.format("%.2fGB", gb)
        return String.format("%.2fTB", gb / 1024.0)
    }

    /** 递归计算文件/目录大小（字节） */
    fun sizeOf(file: File): Long {
        if (!file.exists()) return 0
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += sizeOf(it) }
        return total
    }

    fun copyFile(src: File, dst: File): Boolean {
        if (src.isDirectory) return copyDir(src, dst)
        if (dst.exists()) return false
        dst.parentFile?.mkdirs()
        try {
            BufferedInputStream(FileInputStream(src)).use { input ->
                BufferedOutputStream(FileOutputStream(dst)).use { output ->
                    val buf = ByteArray(BUFFER)
                    var len: Int
                    while (input.read(buf).also { len = it } > 0) {
                        output.write(buf, 0, len)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun copyDir(src: File, dst: File, onProgress: ((String) -> Unit)? = null): Boolean {
        if (!dst.exists()) dst.mkdirs()
        var ok = true
        src.listFiles()?.forEach { child ->
            val target = File(dst, child.name)
            onProgress?.invoke(child.absolutePath)
            ok = ok && copyFile(child, target)
        }
        return ok
    }

    fun moveFile(src: File, dst: File): Boolean {
        if (dst.exists()) return false
        // 同卷优先 rename，更快
        if (src.renameTo(dst)) return true
        if (copyFile(src, dst)) {
            return delete(src)
        }
        return false
    }

    fun delete(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isFile) return file.delete()
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                if (!delete(child)) return false
            }
        }
        return file.delete()
    }

    fun makeDir(path: String): Boolean {
        val f = File(path)
        return f.exists() || f.mkdirs()
    }

    fun makeFile(path: String): Boolean {
        val f = File(path)
        if (f.exists()) return false
        f.parentFile?.mkdirs()
        return try {
            f.createNewFile()
        } catch (e: Exception) {
            false
        }
    }

    fun rename(file: File, newName: String): Boolean {
        if (!file.exists() || newName.isEmpty()) return false
        val target = File(file.parentFile, newName)
        if (target.exists()) return false
        return file.renameTo(target)
    }

    /** 将多个文件/目录打包为 zip；entry 名保留相对结构 */
    fun zip(files: List<File>, zipFile: File, baseDir: File? = null, onProgress: ((String) -> Unit)? = null): Boolean {
        if (zipFile.exists()) zipFile.delete()
        zipFile.parentFile?.mkdirs()
        val base = baseDir ?: commonParent(files)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (file in files) {
                    addToZip(file, base, zos, onProgress)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun commonParent(files: List<File>): File {
        if (files.size == 1) return files[0].parentFile ?: files[0]
        var p: File? = files[0].parentFile
        while (p != null && files.any { !it.absolutePath.startsWith(p.absolutePath) }) {
            p = p.parentFile
        }
        return p ?: File("/")
    }

    private fun addToZip(file: File, base: File, zos: ZipOutputStream, onProgress: ((String) -> Unit)?) {
        val relative = try {
            base.toURI().relativize(file.toURI()).path
        } catch (e: Exception) {
            file.name
        }
        onProgress?.invoke(file.absolutePath)
        if (file.isDirectory) {
            val dirEntry = if (relative.endsWith("/")) relative else "$relative/"
            zos.putNextEntry(ZipEntry(dirEntry))
            zos.closeEntry()
            file.listFiles()?.forEach { addToZip(it, base, zos, onProgress) }
            return
        }
        zos.putNextEntry(ZipEntry(relative))
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buf = ByteArray(BUFFER)
            var len: Int
            while (bis.read(buf).also { len = it } > 0) {
                zos.write(buf, 0, len)
            }
        }
        zos.closeEntry()
    }

    /** 解压 zip 到目标目录，保持内部目录结构 */
    fun unzip(zipFile: File, outDir: File, onProgress: ((String) -> Unit)? = null): Boolean {
        if (!zipFile.exists()) return false
        if (!outDir.exists()) outDir.mkdirs()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = File(outDir, entry.name)
                    onProgress?.invoke(entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(target)).use { bos ->
                            val buf = ByteArray(BUFFER)
                            var len: Int
                            while (zis.read(buf).also { len = it } > 0) {
                                bos.write(buf, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
