package com.elgenium.smartcity.speech

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.TextView
import com.elgenium.smartcity.R
import java.util.Locale

class SpeechRecognitionHelper(
    private val activity: Activity,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var dialog: AlertDialog? = null

    fun startListening() {
        // Create a SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)

        // Set the recognition listener
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
                showDialog() // Show the dialog when ready
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Handle volume changes if needed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Handle buffer received if needed
            }

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "End of speech")
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error occurred: $error")
                onError("Error occurred: $error") // Pass error to the UI
                stopListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val transcript = matches[0]
                    Log.d("SpeechRecognizer", "Transcription: $transcript")
                    onResult(transcript) // Pass the result back to the UI
                    updateDialog(transcript) // Update the dialog with the transcription
                }
                stopListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Handle partial results if needed
                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partialMatches.isNullOrEmpty()) {
                    updateDialog(partialMatches[0]) // Update dialog with partial transcription
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Handle events if needed
            }
        })

        // Create the intent for recognizing speech
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Get partial results
        }

        // Start listening
        speechRecognizer?.startListening(intent)
    }

    private fun showDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_speech_result, null)

        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true) // Allow the dialog to be canceled
            .create()

        dialog?.show()
    }

    private fun updateDialog(transcription: String) {
        dialog?.let {
            val transcriptionTextView = it.findViewById<TextView>(R.id.transcriptionTextView)
            transcriptionTextView?.text = transcription // Update the text view with the transcription
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy() // Clean up
        dialog?.dismiss() // Dismiss the dialog when stopping
    }
}