package dev.callerannouncer

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

class AnnouncerCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }
        if (!isIncoming) return

        val number = callDetails.handle?.schemeSpecificPart.orEmpty().trim()
        if (number.isBlank()) return

        CallerAnnouncementScheduler.schedule(
            context = applicationContext,
            incomingNumber = number,
            source = "call-screening"
        )
    }
}
