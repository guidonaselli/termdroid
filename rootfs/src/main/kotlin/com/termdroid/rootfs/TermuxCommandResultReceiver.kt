package com.termdroid.rootfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TermuxCommandRunner.deliver(intent.getIntExtra("requestId", -1), intent)
    }
}
