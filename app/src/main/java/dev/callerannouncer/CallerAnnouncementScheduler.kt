package dev.callerannouncer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

object CallerAnnouncementScheduler {
    private const val TAG = "CallerAnnouncement"
    private const val ANNOUNCE_DELAY_MS = 1200L
    private const val DUPLICATE_SOURCE_WINDOW_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nextEventId = AtomicLong(0L)
    private val lock = Any()

    private var lastAnnouncementKey: String? = null
    private var lastAnnouncementAt: Long = 0L

    fun schedule(
        context: Context,
        incomingNumber: String,
        source: String,
        completion: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val number = incomingNumber.trim()
        if (number.isBlank() || !PermissionGate.hasRequiredRuntimePermissions(appContext)) {
            completion?.invoke()
            return
        }

        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (lastAnnouncementKey == number && now - lastAnnouncementAt < DUPLICATE_SOURCE_WINDOW_MS) {
                completion?.invoke()
                return
            }
            lastAnnouncementKey = number
            lastAnnouncementAt = now
        }

        val eventId = nextEventId.incrementAndGet()
        scope.launch {
            try {
                Log.d(TAG, "Scheduled caller announcement from $source")
                val startedAt = SystemClock.elapsedRealtime()
                val contactNameDeferred = async(Dispatchers.IO) {
                    ContactLookup.displayNameForNumber(appContext, number)
                }

                val contactName = contactNameDeferred.await()?.trim()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val remainingDelay = ANNOUNCE_DELAY_MS - elapsed
                if (remainingDelay > 0L) delay(remainingDelay)

                if (!isStillLatest(eventId, number)) return@launch
                if (!isStillRinging(appContext)) return@launch

                if (!contactName.isNullOrBlank()) {
                    CallerSpeaker.speakName(appContext, contactName)
                } else {
                    CallerSpeaker.speakDigits(appContext, number)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Caller announcement failed", t)
            } finally {
                completion?.invoke()
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            lastAnnouncementKey = null
            lastAnnouncementAt = 0L
            nextEventId.incrementAndGet()
        }
        CallerSpeaker.stop()
    }

    private fun isStillLatest(eventId: Long, key: String): Boolean = synchronized(lock) {
        eventId == nextEventId.get() && lastAnnouncementKey == key
    }

    private fun isStillRinging(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        ) return false

        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return true
        return runCatching {
            @Suppress("DEPRECATION")
            telephonyManager.callState == TelephonyManager.CALL_STATE_RINGING
        }.getOrDefault(true)
    }
}
