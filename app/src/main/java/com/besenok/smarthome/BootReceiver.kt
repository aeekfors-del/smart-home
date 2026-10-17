package com.besenok.smarthome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.besenok.smarthome.ui.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(serviceIntent)
        }
    }
}
