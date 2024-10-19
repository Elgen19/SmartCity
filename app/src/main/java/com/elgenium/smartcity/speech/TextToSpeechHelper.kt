package com.elgenium.smartcity.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechHelper {
    private var textToSpeech: TextToSpeech ?= null

    private fun initializeTTS(context: Context) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                }
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }
    }

    private fun speakResponse(response: String) {
        // Speak the response
        textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun stopResponse() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }


}