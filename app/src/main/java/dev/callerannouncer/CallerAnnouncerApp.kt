package dev.callerannouncer

import android.app.Application

class CallerAnnouncerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CallerSpeaker.warmUp(this)
    }

    override fun onTerminate() {
        CallerSpeaker.shutdown()
        super.onTerminate()
    }
}
