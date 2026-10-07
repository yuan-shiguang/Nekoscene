package com.yuanshiguang.scene_mode

import android.app.IntentService
import android.content.Intent
import com.yuanshiguang.store.TriggerStorage

class TriggerExecutorService : IntentService("TriggerExecutorService") {

    override fun onHandleIntent(intent: Intent?) {
        intent?.run {
            if (intent.hasExtra("triggers")) {
                executeTriggers(intent.getStringArrayListExtra("triggers")!!)
            }
        }
    }

    private fun executeTriggers(triggers: ArrayList<String>) {
        val context = this;
        val storage = TriggerStorage(this)
        triggers.forEach {
            storage.load(it)?.run {
                TaskActionsExecutor(taskActions, customTaskActions, context).run()
            }
        }
    }
}
