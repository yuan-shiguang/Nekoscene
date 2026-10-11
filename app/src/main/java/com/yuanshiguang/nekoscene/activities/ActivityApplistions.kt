package com.yuanshiguang.nekoscene.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yuanshiguang.ui.SearchTextWatcher
import com.yuanshiguang.ui.TabIconHelper2
import com.yuanshiguang.nekoscene.R
import com.yuanshiguang.nekoscene.fragments.FragmentAppBackup
import com.yuanshiguang.nekoscene.fragments.FragmentAppSystem
import com.yuanshiguang.nekoscene.fragments.FragmentAppUser
import com.yuanshiguang.utils.WorkModeManager
import kotlinx.android.synthetic.main.activity_applictions.*

class ActivityApplistions : ActivityBase() {
    private var myHandler: Handler = UpdateHandler {
        reloadList()
    }

    private val fragmentAppUser = FragmentAppUser(myHandler)
    private val fragmentAppSystem = FragmentAppSystem(myHandler)
    private val fragmentAppBackup = FragmentAppBackup(myHandler)

    class UpdateHandler(private var updateList: Runnable) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 2) {
                updateList.run()
            }
        }
    }

    data class ManagerEntry(
        val labelRes: Int,
        val requiredMode: Int,
        val action: () -> Unit
    )

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_retrieve, menu)
        menuInflater.inflate(R.menu.menu_app_mode, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_retrieve -> {
                startActivity(Intent(context, ActivityAppRetrieve::class.java))
                return true
            }
            R.id.menu_work_mode -> {
                showModeDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_applictions)

        setBackArrow()

        TabIconHelper2(tab_list, tab_content, this, supportFragmentManager).run {
            newTabSpec("Installed", ContextCompat.getDrawable(context, R.drawable.tab_app)!!, fragmentAppUser)
            newTabSpec("System", ContextCompat.getDrawable(context, R.drawable.tab_security)!!, fragmentAppSystem)
            newTabSpec("Backups", ContextCompat.getDrawable(context, R.drawable.tab_package)!!, fragmentAppBackup)
            tab_content.adapter = this.adapter
        }

        apps_search_box.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchApp(apps_search_box.text)
            }
            true
        }
        var lastInput = 0L
        apps_search_box.addTextChangedListener(SearchTextWatcher {
            val current = System.currentTimeMillis()
            lastInput = current
            myHandler.postDelayed({
                if (lastInput == current) {
                    searchApp(apps_search_box.text)
                }
            }, 500)
        })

        // 储存管理权限
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "无法申请存储管理权限~", Toast.LENGTH_SHORT).show()
            }
        }

        rebuildManagers()
    }

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_applictions)
        updateBanner()
        rebuildManagers()
    }

    private fun updateBanner() {
        mode_banner.text = getString(R.string.app_mode_banner, WorkModeManager.currentName(this))
    }

    private fun showModeDialog() {
        val modes = arrayOf(
            WorkModeManager.MODE_BASIC,
            WorkModeManager.MODE_ADB,
            WorkModeManager.MODE_ROOT
        )
        val names = modes.map { getString(WorkModeManager.nameRes(it)) }.toTypedArray()
        val current = WorkModeManager.getMode(this)
        val checked = modes.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.scene_working_mode)
            .setSingleChoiceItems(names, checked) { d, which ->
                val mode = modes[which]
                WorkModeManager.setMode(this, mode)
                updateBanner()
                rebuildManagers()
                Toast.makeText(this,
                    getString(WorkModeManager.nameRes(mode)) + "：" +
                            getString(WorkModeManager.descRes(mode)), Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun managerEntries(): List<ManagerEntry> = listOf(
        ManagerEntry(R.string.app_mgr_freeze, WorkModeManager.MODE_ADB) {
            startActivity(Intent(this, ActivityFreezeApps::class.java))
        },
        ManagerEntry(R.string.app_mgr_appscene, WorkModeManager.MODE_ADB) {
            startActivity(Intent(this, ActivityAppConfig2::class.java))
        },
        ManagerEntry(R.string.app_mgr_retrieve, WorkModeManager.MODE_BASIC) {
            startActivity(Intent(this, ActivityAppRetrieve::class.java))
        },
        ManagerEntry(R.string.app_mgr_autostart, WorkModeManager.MODE_ROOT) { openManager("autostart") },
        ManagerEntry(R.string.app_mgr_notification, WorkModeManager.MODE_ROOT) { openManager("notification") },
        ManagerEntry(R.string.app_mgr_permission, WorkModeManager.MODE_ROOT) { openManager("permission") },
        ManagerEntry(R.string.app_mgr_assoc, WorkModeManager.MODE_ROOT) { openManager("assoc") }
    )

    private fun openManager(type: String) {
        startActivity(Intent(this, ActivityAppManager::class.java).putExtra("type", type))
    }

    private fun rebuildManagers() {
        val row = findViewById<LinearLayout>(R.id.managers_row)
        row.removeAllViews()
        val mode = WorkModeManager.getMode(this)
        for (entry in managerEntries()) {
            val chip = TextView(this)
            chip.text = getString(entry.labelRes)
            chip.setPadding(28, 14, 28, 14)
            chip.textSize = 13f
            chip.setBackgroundResource(R.drawable.krscript_item_ripple)
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
            val enabled = mode >= entry.requiredMode
            chip.alpha = if (enabled) 1f else 0.4f
            chip.setOnClickListener {
                if (mode >= entry.requiredMode) {
                    entry.action()
                } else {
                    toast(getString(if (entry.requiredMode == WorkModeManager.MODE_ADB)
                        R.string.only_adb_mode else R.string.only_adb_or_root_mode))
                }
            }
            row.addView(chip)
        }
    }

    private fun searchApp(text: Editable) {
        text.toString().run {
            fragmentAppUser.searchText = this
            fragmentAppSystem.searchText = this
            fragmentAppBackup.searchText = this
        }
    }

    private fun reloadList() {
        try {
            fragmentAppUser.reloadList()
        } catch (ex: Exception) {
        }
        try {
            fragmentAppSystem.reloadList()
        } catch (ex: Exception) {
        }
        try {
            fragmentAppBackup.reloadList()
        } catch (ex: Exception) {
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
