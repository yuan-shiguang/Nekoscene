package com.yuanshiguang.nekoscene.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.yuanshiguang.common.ui.DialogHelper
import com.yuanshiguang.nekoscene.R
import com.yuanshiguang.utils.CommonCmds
import com.yuanshiguang.utils.FastShareClient
import com.yuanshiguang.utils.FastShareServer
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class ActivityFastShare : ActivityBase() {
    companion object {
        private const val REQ_PICK_FOLDER = 1
        private const val REQ_PICK_FILE = 2
        private const val REQ_SAVE_DIR = 3
        private const val DEFAULT_PORT = 8899
    }

    private val handler = Handler(Looper.getMainLooper())

    private var shareTarget: File? = null
    private var shareKey = ""
    private var saveDir: File? = null
    private var server: FastShareServer? = null
    private var client: FastShareClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fast_share)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = getString(R.string.fs_title)
        setBackArrow()

        bindModeSwitch()
        bindSharePanel()
        bindPullPanel()

        saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FastShare")
        updateSaveTo()
        switchMode(true)
    }

    // ===== 模式切换 =====
    private fun bindModeSwitch() {
        findViewById<Button>(R.id.btn_mode_share).setOnClickListener { switchMode(true) }
        findViewById<Button>(R.id.btn_mode_pull).setOnClickListener { switchMode(false) }
    }

    private fun switchMode(share: Boolean) {
        findViewById<android.view.View>(R.id.share_panel).visibility =
            if (share) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<android.view.View>(R.id.pull_panel).visibility =
            if (share) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<Button>(R.id.btn_mode_share).isEnabled = !share
        findViewById<Button>(R.id.btn_mode_pull).isEnabled = share
    }

    // ===== 分享面板 =====
    private fun bindSharePanel() {
        findViewById<Button>(R.id.share_pick_folder).setOnClickListener {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("mode", ActivityFileSelector.MODE_FOLDER)
            startActivityForResult(intent, REQ_PICK_FOLDER)
        }
        findViewById<Button>(R.id.share_pick_file).setOnClickListener {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("mode", ActivityFileSelector.MODE_FILE)
            startActivityForResult(intent, REQ_PICK_FILE)
        }
        findViewById<Button>(R.id.share_set_key).setOnClickListener { showSetKeyDialog() }
        findViewById<Button>(R.id.share_start).setOnClickListener { startShare() }
        findViewById<Button>(R.id.share_stop).setOnClickListener { stopShare() }
    }

    private fun showSetKeyDialog() {
        val input = EditText(this)
        input.setText(shareKey)
        input.hint = getString(R.string.fs_set_key_msg)
        DialogHelper.confirm(this, getString(R.string.fs_set_key_title), getString(R.string.fs_set_key_msg), input,
            Runnable {
                val k = input.text.toString().trim()
                if (k.length < 4 || k.length > 10) {
                    toast(getString(R.string.fs_key_length_invalid))
                    return@Runnable
                }
                shareKey = k
                toast(getString(R.string.fs_my_key, shareKey))
            })
    }

    private fun startShare() {
        val target = shareTarget
        if (target == null) {
            toast(getString(R.string.fs_no_files_selected))
            return
        }
        if (shareKey.isEmpty()) {
            toast(getString(R.string.fs_set_key_title))
            return
        }
        val srv = FastShareServer(listOf(target), shareKey, DEFAULT_PORT)
        srv.onPhase = { phase ->
            handler.post {
                val ip = getLocalIpAddress() ?: "?"
                val status = "${getString(R.string.fs_my_key, shareKey)}\n${ip}:$DEFAULT_PORT\n${phaseText(phase)}"
                findViewById<TextView>(R.id.share_status).text = status
            }
        }
        srv.onError = { err ->
            handler.post { toast(getString(R.string.fs_failed) + ": $err") }
        }
        server = srv
        srv.start()
        findViewById<Button>(R.id.share_start).visibility = android.view.View.GONE
        findViewById<Button>(R.id.share_stop).visibility = android.view.View.VISIBLE
    }

    private fun stopShare() {
        server?.stop()
        server = null
        findViewById<Button>(R.id.share_start).visibility = android.view.View.VISIBLE
        findViewById<Button>(R.id.share_stop).visibility = android.view.View.GONE
        findViewById<TextView>(R.id.share_status).text = ""
    }

    // ===== 接收面板 =====
    private fun bindPullPanel() {
        findViewById<Button>(R.id.pull_pick_dir).setOnClickListener {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("mode", ActivityFileSelector.MODE_FOLDER)
            startActivityForResult(intent, REQ_SAVE_DIR)
        }
        findViewById<Button>(R.id.pull_start).setOnClickListener { startPull() }
        findViewById<Button>(R.id.pull_stop).setOnClickListener { stopPull() }
    }

    private fun updateSaveTo() {
        findViewById<TextView>(R.id.pull_save_to).text =
            getString(R.string.fs_save_to_label, saveDir?.absolutePath ?: "")
    }

    private fun startPull() {
        val host = findViewById<EditText>(R.id.pull_ip).text.toString().trim()
        val portStr = findViewById<EditText>(R.id.pull_port).text.toString().trim()
        val key = findViewById<EditText>(R.id.pull_key).text.toString().trim()
        if (host.isEmpty() || key.isEmpty()) {
            toast(getString(R.string.fs_pull_key_title))
            return
        }
        val port = portStr.toIntOrNull() ?: DEFAULT_PORT
        val out = saveDir ?: File(CommonCmds.SDCardDir, "FastShare")

        val cli = FastShareClient(host, port, key, out)
        cli.onPhase = { phase ->
            handler.post { findViewById<TextView>(R.id.phase_text).text = phaseText(phase) }
        }
        cli.onProgress = { done, total ->
            handler.post {
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                val bar = findViewById<android.widget.ProgressBar>(R.id.progress)
                bar.visibility = android.view.View.VISIBLE
                bar.progress = pct
            }
        }
        cli.onRemaining = { remain ->
            handler.post { findViewById<TextView>(R.id.remaining_text).text = getString(R.string.fs_remaining, remain) }
        }
        cli.onDone = { success ->
            handler.post {
                findViewById<TextView>(R.id.phase_text).text =
                    if (success) getString(R.string.fs_finished) else getString(R.string.fs_failed)
                findViewById<Button>(R.id.pull_start).visibility = android.view.View.VISIBLE
                findViewById<Button>(R.id.pull_stop).visibility = android.view.View.GONE
            }
        }
        client = cli
        cli.start()
        findViewById<Button>(R.id.pull_start).visibility = android.view.View.GONE
        findViewById<Button>(R.id.pull_stop).visibility = android.view.View.VISIBLE
    }

    private fun stopPull() {
        client?.abort()
        client = null
        findViewById<Button>(R.id.pull_start).visibility = android.view.View.VISIBLE
        findViewById<Button>(R.id.pull_stop).visibility = android.view.View.GONE
    }

    // ===== 选择结果 =====
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val path = data.getStringExtra("file") ?: return
        when (requestCode) {
            REQ_PICK_FOLDER, REQ_PICK_FILE -> {
                shareTarget = File(path)
                findViewById<TextView>(R.id.share_target).text =
                    getString(R.string.fs_share_target_label, path)
            }
            REQ_SAVE_DIR -> {
                saveDir = File(path)
                updateSaveTo()
            }
        }
    }

    private fun phaseText(key: String): String {
        return when (key) {
            "idle" -> getString(R.string.fs_phase_idle)
            "standby" -> getString(R.string.fs_phase_standby)
            "connecting" -> getString(R.string.fs_phase_connecting)
            "receiving" -> getString(R.string.fs_phase_receiving)
            "sending" -> getString(R.string.fs_phase_sending)
            "verifying" -> getString(R.string.fs_phase_verifying)
            "finished" -> getString(R.string.fs_finished)
            else -> key
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        client?.abort()
    }
}
