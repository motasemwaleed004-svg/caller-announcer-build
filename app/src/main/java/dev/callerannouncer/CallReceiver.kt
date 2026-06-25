package dev.callerannouncer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> handleRinging(context.applicationContext, intent)
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> CallerAnnouncementScheduler.reset()
        }
    }

    private fun handleRinging(context: Context, intent: Intent) {
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty().trim()
        if (incomingNumber.isBlank()) return
        if (!PermissionGate.hasRequiredRuntimePermissions(context)) return

        val pendingResult = goAsync()
        CallerAnnouncementScheduler.schedule(
            context = context,
            incomingNumber = incomingNumber,
            source = "phone-state",
            completion = { pendingResult.finish() }
        )
    }
}
