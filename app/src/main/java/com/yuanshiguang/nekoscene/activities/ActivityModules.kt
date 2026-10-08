package com.yuanshiguang.nekoscene.activities

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuanshiguang.krscript.downloader.Downloader
import com.yuanshiguang.library.basic.MagiskModulesRepo
import com.yuanshiguang.ui.AdapterModules
import com.yuanshiguang.nekoscene.R
import kotlinx.android.synthetic.main.activty_modules.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class ActivityModules : ActivityBase(), AdapterModules.OnItemClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activty_modules)

        setBackArrow()

        onViewCreated(this)
    }

    private val handle = Handler(Looper.getMainLooper())

    private fun onViewCreated(context: Context) {
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
        module_list.layoutManager = linearLayoutManager

        // 搜索关键字
        module_search.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // (module_list.adapter as AdapterProcess?)?.updateKeywords(v.text.toString())
                val text = v.text.toString()
                val view = (v as EditText)
                view.isEnabled = false
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val modules = MagiskModulesRepo().query(text)
                        if (!isDestroyed) {
                            module_list.post {
                                module_list.adapter = AdapterModules(context, modules).apply {
                                    setOnItemClickListener(this@ActivityModules)
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Toast.makeText(context, "Failed to find module: " + ex.message, Toast.LENGTH_SHORT).show()
                    } finally {
                        view.post {
                            view.isEnabled = true
                        }
                    }
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    // 更新任务列表
    private fun updateData() {
        handle.post {
            // (process_list?.adapter as AdapterProcess?)?.setList(data)
        }
    }

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_modules)
    }

    override fun onItemClick(view: View, position: Int) {
        val modules = (module_list.adapter as AdapterModules)
        val module = modules.getItem(position)
        // https://github.com/Magisk-Modules-Repo/mtd-ndk/archive/refs/heads/master.zip
        // 模块名应取 "/" 之后的部分（模块 id），原先取的是组织名，导致下载文件被命名成 "Magisk-Modules-Repo.zip"
        val moduleName = module.substring(module.indexOf("/") + 1)
        Downloader(context, this).downloadBySystem(
                "https://github.com/${module}/archive/refs/heads/master.zip",
                moduleName,
                "application/zip",
                "",
        moduleName + ".zip")
    }
}
