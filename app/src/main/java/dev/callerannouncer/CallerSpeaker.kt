package dev.callerannouncer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

object CallerSpeaker {
    private const val TAG = "CallerSpeaker"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pendingCallbacks = mutableListOf<(TextToSpeech) -> Unit>()

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var initInProgress = false

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    fun warmUp(context: Context) {
        withTts(context.applicationContext) { engine -> configureEngineBase(engine) }
    }

    fun speakName(context: Context, rawName: String) {
        val prepared = CallerNameNormalizer.prepareForSpeech(rawName) ?: return
        mainHandler.post { speakPrepared(context.applicationContext, prepared, allowRetry = true) }
    }

    fun speakDigits(context: Context, rawValue: String) {
        val prepared = CallerNameNormalizer.prepareDigits(rawValue) ?: return
        mainHandler.post { speakPrepared(context.applicationContext, prepared, allowRetry = true) }
    }

    fun stop() {
        mainHandler.post {
            tts?.runCatching { stop() }
            abandonAudioFocus()
        }
    }

    fun shutdown() {
        mainHandler.post {
            synchronized(lock) {
                pendingCallbacks.clear()
                initInProgress = false
            }
            tts?.runCatching { stop() }
            tts?.runCatching { shutdown() }
            tts = null
            abandonAudioFocus()
        }
    }

    private fun speakPrepared(context: Context, prepared: SpokenCallerName, allowRetry: Boolean) {
        requestAudioFocus(context)
        withTts(context) { engine ->
            configureEngineBase(engine)
            configureLanguage(engine, prepared.locale)

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_RING)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            val result = engine.speak(
                prepared.text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "caller-name-${SystemClock.uptimeMillis()}"
            )

            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TextToSpeech.speak returned $result")
                if (allowRetry) {
                    recreateTts(context) { retryEngine ->
                        configureEngineBase(retryEngine)
                        configureLanguage(retryEngine, prepared.locale)
                        retryEngine.speak(
                            prepared.text,
                            TextToSpeech.QUEUE_FLUSH,
                            params,
                            "caller-name-retry-${SystemClock.uptimeMillis()}"
                        )
                    }
                } else {
                    abandonAudioFocus()
                }
            }
        }
    }

    private fun withTts(context: Context, callback: (TextToSpeech) -> Unit) {
        val existing = tts
        if (existing != null) {
            callback(existing)
            return
        }

        synchronized(lock) {
            pendingCallbacks += callback
            if (initInProgress) return
            initInProgress = true
        }

        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                val engine = holder[0]
                val callbacks: List<(TextToSpeech) -> Unit>
                synchronized(lock) {
                    initInProgress = false
                    callbacks = if (status == TextToSpeech.SUCCESS && engine != null) {
                        tts = engine
                        pendingCallbacks.toList().also { pendingCallbacks.clear() }
                    } else {
                        pendingCallbacks.clear()
                        emptyList()
                    }
                }

                if (status == TextToSpeech.SUCCESS && engine != null) {
                    configureEngineBase(engine)
                    callbacks.forEach { it(engine) }
                } else {
                    engine?.runCatching { shutdown() }
                    abandonAudioFocus()
                }
            }
        }
    }

    private fun recreateTts(context: Context, callback: (TextToSpeech) -> Unit) {
        synchronized(lock) {
            pendingCallbacks.clear()
            initInProgress = false
        }
        tts?.runCatching { stop() }
        tts?.runCatching { shutdown() }
        tts = null
        withTts(context, callback)
    }

    private fun configureEngineBase(engine: TextToSpeech) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(attributes)
        }
        engine.setSpeechRate(0.90f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) { finishSpeechSoon() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { finishSpeechSoon() }
            override fun onError(utteranceId: String, errorCode: Int) { finishSpeechSoon() }
            override fun onStop(utteranceId: String, interrupted: Boolean) { finishSpeechSoon() }
        })
    }

    private fun configureLanguage(engine: TextToSpeech, preferredLocale: Locale) {
        val candidates = listOf(
            preferredLocale,
            Locale("ar", "EG"),
            Locale("ar"),
            Locale.getDefault()
        ).distinct()

        for (locale in candidates) {
            val result = engine.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return
            }
        }
    }

    private fun requestAudioFocus(context: Context) {
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        audioManager = manager
        abandonAudioFocus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun finishSpeechSoon() {
        mainHandler.postDelayed({ abandonAudioFocus() }, 300L)
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(manager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }
}
