package com.yuanshiguang.nekoscene.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuanshiguang.common.ui.DialogHelper
import com.yuanshiguang.common.ui.ProgressBarDialog
import com.yuanshiguang.nekoscene.R
import com.yuanshiguang.ui.AdapterFileList
import com.yuanshiguang.utils.CommonCmds
import com.yuanshiguang.utils.FileOpUtils
import java.io.File
import java.util.*

class ActivityFiles : ActivityBase() {
    companion object {
        private const val MODE_COPY = 0
        private const val MODE_MOVE = 1
        private const val REQ_MANAGE = 1001
    }

    private lateinit var adapter: AdapterFileList
    private lateinit var progress: ProgressBarDialog
    private val handler = Handler(Looper.getMainLooper())

    private var currentDir: File = File(CommonCmds.SDCardDir)
    private var multiSelectMode = false

    private var clipboardFiles: ArrayList<File>? = null
    private var clipboardMode = MODE_COPY

    private val favPrefs by lazy { getSharedPreferences("fs_favorites", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = getString(R.string.fs_manager_title)
        setBackArrow()

        progress = ProgressBarDialog(this)

        adapter = AdapterFileList(
            onItemClick = { onItemClick(it) },
            onItemLongClick = { onItemLongClick(it) }
        )
        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.file_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        bindActionBar()
        bindPasteBar()

        // 路径栏点击 = 快速导航（收藏 + 根目录）
        findViewById<android.view.View>(R.id.path_bar).setOnClickListener { showQuickNav() }

        loadDir(currentDir)
    }

    // ===== 目录加载 =====
    private fun loadDir(dir: File) {
        currentDir = dir
        updatePathBar()
        progress.showDialog(getString(R.string.fs_phase_scanning))
        Thread {
            val parent = dir.parentFile
            val parentAvailable = parent != null && parent.canRead()
            val files = if (dir.canRead()) dir.listFiles() else null
            if (files == null) {
                handler.post {
                    progress.hideDialog()
                    SceneToast(getString(R.string.fs_read_permission))
                }
                return@Thread
            }
            handler.post {
                adapter.setData(dir, files, parentAvailable)
                progress.hideDialog()
                updateActionBars()
            }
        }.start()
    }

    private fun updatePathBar() {
        findViewById<android.widget.TextView>(R.id.path_bar).text = currentDir.absolutePath
    }

    private fun onItemClick(file: File) {
        val isUp = multiSelectMode && file.absolutePath == currentDir.parentFile?.absolutePath
        if (multiSelectMode && !isUp) {
            adapter.toggleSelection(file)
            updateSelectionUI()
            return
        }
        if (file.isDirectory) loadDir(file) else openFile(file)
    }

    private fun onItemLongClick(file: File) {
        if (file.absolutePath == currentDir.parentFile?.absolutePath) return
        if (!multiSelectMode) {
            multiSelectMode = true
            updateActionBars()
        }
        adapter.toggleSelection(file)
        updateSelectionUI()
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, contenttype(file))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, file.name))
        } catch (e: Exception) {
            SceneToast(getString(R.string.fs_share_unreadable))
        }
    }

    private fun contenttype(file: File): String {
        val name = file.name.lowercase(Locale.ROOT)
        return when {
            name.endsWith(".zip") -> "application/zip"
            name.endsWith(".apk") -> "application/vnd.android.package-archive"
            name.endsWith(".txt") -> "text/plain"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mp3") -> "audio/mpeg"
            else -> "*/*"
        }
    }

    // ===== 多选 / 操作栏 =====
    private fun bindActionBar() {
        findViewById<android.view.View>(R.id.act_copy).setOnClickListener { doCopy() }
        findViewById<android.view.View>(R.id.act_move).setOnClickListener { doMove() }
        findViewById<android.view.View>(R.id.act_delete).setOnClickListener { doDelete() }
        findViewById<android.view.View>(R.id.act_zip).setOnClickListener { doZip() }
        findViewById<android.view.View>(R.id.act_share).setOnClickListener { doShare() }
        findViewById<android.view.View>(R.id.act_all).setOnClickListener { adapter.selectAll(); updateSelectionUI() }
        findViewById<android.view.View>(R.id.act_cancel).setOnClickListener {
            multiSelectMode = false
            adapter.clearSelection()
            updateActionBars()
        }
    }

    private fun bindPasteBar() {
        findViewById<android.view.View>(R.id.paste_confirm).setOnClickListener { paste() }
        findViewById<android.view.View>(R.id.paste_cancel).setOnClickListener {
            clipboardFiles = null
            updateActionBars()
        }
    }

    private fun updateActionBars() {
        val actionBar = findViewById<android.view.View>(R.id.action_bar)
        actionBar.visibility = if (multiSelectMode) android.view.View.VISIBLE else android.view.View.GONE
        updateSelectionUI()

        val pasteBar = findViewById<android.view.View>(R.id.paste_bar)
        val list = clipboardFiles
        if (list != null && list.isNotEmpty()) {
            pasteBar.visibility = android.view.View.VISIBLE
            val first = list[0].name
            val text = if (clipboardMode == MODE_COPY)
                getString(R.string.fs_paste_copy_multi, first, list.size)
            else
                getString(R.string.fs_paste_move_multi, first, list.size)
            findViewById<android.widget.TextView>(R.id.paste_text).text = text
        } else {
            pasteBar.visibility = android.view.View.GONE
        }
    }

    private fun updateSelectionUI() {
        if (multiSelectMode) {
            title = getString(R.string.fs_select_count, adapter.selectionCount())
        } else {
            title = getString(R.string.fs_manager_title)
        }
    }

    // ===== 文件操作 =====
    private fun selectedOrEmpty(): ArrayList<File> {
        val sel = adapter.getSelected()
        if (sel.isEmpty()) {
            SceneToast(getString(R.string.fs_no_files_selected))
        }
        return ArrayList(sel)
    }

    private fun doCopy() {
        val sel = selectedOrEmpty()
        if (sel.isEmpty()) return
        clipboardFiles = sel
        clipboardMode = MODE_COPY
        multiSelectMode = false
        adapter.clearSelection()
        updateActionBars()
        SceneToast(getString(R.string.fs_op_done))
    }

    private fun doMove() {
        val sel = selectedOrEmpty()
        if (sel.isEmpty()) return
        clipboardFiles = sel
        clipboardMode = MODE_MOVE
        multiSelectMode = false
        adapter.clearSelection()
        updateActionBars()
        SceneToast(getString(R.string.fs_op_done))
    }

    private fun doDelete() {
        val sel = selectedOrEmpty()
        if (sel.isEmpty()) return
        val names = sel.joinToString("\n") { it.name }
        DialogHelper.confirm(this,
            getString(R.string.fs_delete),
            names,
            Runnable {
                runOp({ for (f in sel) FileOpUtils.delete(f) }, getString(R.string.fs_delete)) {
                    loadDir(currentDir)
                }
            })
    }

    private fun doZip() {
        val sel = selectedOrEmpty()
        if (sel.isEmpty()) return
        val baseName = if (sel.size == 1) sel[0].name else "archive"
        val zipFile = File(currentDir, "$baseName.zip")
        if (zipFile.exists()) {
            SceneToast(getString(R.string.fs_zip_exists))
            return
        }
        progress.showDialog(getString(R.string.fs_phase_processing))
        Thread {
            val ok = FileOpUtils.zip(sel, zipFile)
            handler.post {
                progress.hideDialog()
                SceneToast(if (ok) getString(R.string.fs_compress_done) else getString(R.string.fs_op_failed))
                loadDir(currentDir)
            }
        }.start()
    }

    private fun doUnzipSingle() {
        val sel = adapter.getSelected()
        if (sel.size != 1) {
            SceneToast(getString(R.string.fs_no_files_selected))
            return
        }
        val zip = sel[0]
        if (!zip.name.lowercase(Locale.ROOT).endsWith(".zip")) {
            SceneToast(getString(R.string.fs_unzip_file))
            return
        }
        val outDir = File(currentDir, zip.name.removeSuffix(".zip"))
        if (outDir.exists()) {
            SceneToast(getString(R.string.fs_dir_exists))
            return
        }
        progress.showDialog(getString(R.string.fs_phase_processing))
        Thread {
            val ok = FileOpUtils.unzip(zip, outDir)
            handler.post {
                progress.hideDialog()
                SceneToast(if (ok) getString(R.string.fs_unzip_done) else getString(R.string.fs_op_failed))
                loadDir(currentDir)
            }
        }.start()
    }

    private fun doShare() {
        val sel = adapter.getSelected()
        if (sel.isEmpty()) {
            SceneToast(getString(R.string.fs_no_files_selected))
            return
        }
        val uris = ArrayList<Uri>()
        for (f in sel) {
            try {
                uris.add(FileProvider.getUriForFile(this, packageName + ".fileprovider", f))
            } catch (e: Exception) {
            }
        }
        if (uris.isEmpty()) {
            SceneToast(getString(R.string.fs_share_failed))
            return
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.type = "*/*"
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.fs_share_file)))
    }

    private fun doStat() {
        val sel = adapter.getSelected()
        val targets = if (sel.isEmpty()) listOf(currentDir) else sel
        progress.showDialog(getString(R.string.fs_phase_scanning))
        Thread {
            var total = 0L
            var count = 0
            for (f in targets) {
                total += FileOpUtils.sizeOf(f)
                count += if (f.isDirectory) (f.listFiles()?.size ?: 0) else 1
            }
            val finalTotal = total
            val finalCount = count
            handler.post {
                progress.hideDialog()
                DialogHelper.alert(this,
                    getString(R.string.fs_stat_size),
                    getString(R.string.fs_child_count, finalCount) + "\n" + FileOpUtils.formatSize(finalTotal))
            }
        }.start()
    }

    private fun paste() {
        val list = clipboardFiles ?: return
        val mode = clipboardMode
        val destDir = currentDir
        // 目标不能是源自身或其子目录
        for (f in list) {
            if (destDir.absolutePath == f.absolutePath ||
                destDir.absolutePath.startsWith(f.absolutePath + "/")) {
                DialogHelper.alert(this, getString(R.string.fs_dest_invalid), destDir.absolutePath)
                return
            }
        }
        val conflicts = list.filter { File(destDir, it.name).exists() }
        val executePaste = { overwrite: Boolean ->
            runOp({
                var ok = true
                for (f in list) {
                    val target = File(destDir, f.name)
                    if (target.exists()) {
                        if (overwrite) {
                            if (!FileOpUtils.delete(target)) ok = false
                        } else {
                            continue
                        }
                    }
                    ok = ok && if (mode == MODE_COPY) FileOpUtils.copyFile(f, target)
                    else FileOpUtils.moveFile(f, target)
                }
                ok
            }, if (mode == MODE_COPY) getString(R.string.fs_copy_to) else getString(R.string.fs_move_to)) {
                clipboardFiles = null
                if (mode == MODE_MOVE) adapter.clearSelection()
                loadDir(currentDir)
            }
        }
        if (conflicts.isNotEmpty()) {
            DialogHelper.confirm(this,
                getString(R.string.fs_batch_exists),
                getString(R.string.fs_batch_overwrite) + "\n" + conflicts.joinToString("\n") { it.name },
                Runnable { executePaste(true) },
                Runnable { executePaste(false) })
        } else {
            executePaste(false)
        }
    }

    /** 后台执行文件操作，完成后回调主线程 */
    private fun runOp(block: () -> Boolean, _title: String, onDone: () -> Unit) {
        progress.showDialog(getString(R.string.fs_phase_processing))
        Thread {
            val ok = block()
            handler.post {
                progress.hideDialog()
                SceneToast(if (ok) getString(R.string.fs_op_done) else getString(R.string.fs_op_failed))
                onDone()
            }
        }.start()
    }

    // ===== 新建 / 重命名 对话框 =====
    private fun showNewFolderDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.fs_new_folder)
        DialogHelper.confirm(this, getString(R.string.fs_new_folder), "", input,
            Runnable {
                val name = input.text.toString().trim()
                if (!isValidName(name)) {
                    SceneToast(getString(R.string.fs_invalid_file_name))
                    return@Runnable
                }
                val dir = File(currentDir, name)
                if (dir.exists()) {
                    SceneToast(getString(R.string.fs_dir_exists))
                    return@Runnable
                }
                if (FileOpUtils.makeDir(dir.absolutePath)) loadDir(currentDir)
                else SceneToast(getString(R.string.fs_op_failed))
            })
    }

    private fun showNewFileDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.fs_new_file)
        DialogHelper.confirm(this, getString(R.string.fs_new_file), "", input,
            Runnable {
                val name = input.text.toString().trim()
                if (!isValidName(name)) {
                    SceneToast(getString(R.string.fs_invalid_file_name))
                    return@Runnable
                }
                val f = File(currentDir, name)
                if (f.exists()) {
                    SceneToast(getString(R.string.fs_file_exists))
                    return@Runnable
                }
                if (FileOpUtils.makeFile(f.absolutePath)) loadDir(currentDir)
                else SceneToast(getString(R.string.fs_op_failed))
            })
    }

    private fun showRenameDialog() {
        val sel = adapter.getSelected()
        if (sel.size != 1) {
            SceneToast(getString(R.string.fs_name_conflict))
            return
        }
        val file = sel[0]
        val input = EditText(this)
        input.setText(file.name)
        DialogHelper.confirm(this, getString(R.string.fs_rename), getString(R.string.fs_rename_msg), input,
            Runnable {
                val newName = input.text.toString().trim()
                if (!isValidName(newName)) {
                    SceneToast(getString(R.string.fs_invalid_file_name))
                    return@Runnable
                }
                if (FileOpUtils.rename(file, newName)) loadDir(currentDir)
                else SceneToast(getString(R.string.fs_op_failed))
            })
    }

    private fun isValidName(name: String): Boolean {
        if (name.isEmpty()) return false
        return !name.contains(Regex("[/:*?\"<>|]"))
    }

    // ===== 排序 =====
    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.fs_sort_name),
            getString(R.string.fs_sort_size),
            getString(R.string.fs_sort_time),
            getString(R.string.fs_sort_type)
        )
        val current = getSharedPreferences("fs_cfg", MODE_PRIVATE).getInt("sort", AdapterFileList.SORT_NAME)
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.fs_sort_by))
            .setSingleChoiceItems(options, current) { d, which ->
                getSharedPreferences("fs_cfg", MODE_PRIVATE).edit().putInt("sort", which).apply()
                adapter.setSortMode(which)
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ===== 收藏 / 快速导航 =====
    private fun toggleFavorite() {
        val set = favPrefs.getStringSet("paths", mutableSetOf())!!.toMutableSet()
        val path = currentDir.absolutePath
        if (set.contains(path)) {
            set.remove(path)
            SceneToast(getString(R.string.fs_fav_removed))
        } else {
            set.add(path)
            SceneToast(getString(R.string.fs_fav_added))
        }
        favPrefs.edit().putStringSet("paths", set).apply()
    }

    private fun showQuickNav() {
        val set = favPrefs.getStringSet("paths", mutableSetOf())!!.toMutableList()
        val entries = mutableListOf<String>()
        entries.add(getString(R.string.fs_external_storage) + "  (${CommonCmds.SDCardDir})")
        entries.add("/")
        entries.addAll(set)
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.fs_fav_title))
            .setItems(entries.toTypedArray()) { _, which ->
                val dir = when (which) {
                    0 -> File(CommonCmds.SDCardDir)
                    1 -> File("/")
                    else -> File(set[which - 2])
                }
                if (dir.exists() && dir.isDirectory) loadDir(dir)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ===== 菜单 =====
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_files, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_new_folder -> showNewFolderDialog()
            R.id.menu_new_file -> showNewFileDialog()
            R.id.menu_sort -> showSortDialog()
            R.id.menu_favorite -> toggleFavorite()
            R.id.menu_stat -> doStat()
            R.id.menu_rename -> showRenameDialog()
            R.id.menu_unzip -> doUnzipSingle()
            R.id.menu_multi_select -> {
                multiSelectMode = !multiSelectMode
                if (!multiSelectMode) adapter.clearSelection()
                updateActionBars()
            }
        }
        return true
    }

    // 长按列表中单文件也可重命名：通过菜单进入多选后选中一项再点重命名不方便，
    // 这里在 action_bar 内未放重命名，统一用「多选→菜单重命名」或未来扩展。
    // 为便捷，点击 action 栏空白处不处理。

    override fun onBackPressed() {
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead() && currentDir.absolutePath != File(CommonCmds.SDCardDir).absolutePath) {
            loadDir(parent)
        } else if (multiSelectMode) {
            multiSelectMode = false
            adapter.clearSelection()
            updateActionBars()
        } else {
            super.onBackPressed()
        }
    }

    private fun SceneToast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
