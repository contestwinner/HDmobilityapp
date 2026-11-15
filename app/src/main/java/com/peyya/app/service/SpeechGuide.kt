package com.peyya.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class SpeechGuide(
    context: Context,
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false

    /** Latest message requested before the engine finished binding (e.g. restored trip on launch). */
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            Log.w(TAG, "TTS init failed status=$status")
            ready = false
            return
        }
        val result = engine.setLanguage(Locale.KOREAN)
        ready = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) {
            Log.w(TAG, "Korean TTS language not available")
            return
        }
        pending?.let { speak(it) }
        pending = null
    }

    fun speak(message: String) {
        val engine = tts
        if (!ready || engine == null) {
            Log.i(TAG, "TTS not ready yet, queued: $message")
            pending = message
            return
        }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "peyya-guide")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }

    companion object {
        private const val TAG = "PeyyaSpeechGuide"
    }
}
