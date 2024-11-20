package com.elgenium.smartcity.speech

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.TextView
import com.elgenium.smartcity.R
import com.google.android.material.button.MaterialButton
import java.util.Locale

class SpeechRecognitionHelper(
    private val activity: Activity,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var dialog: AlertDialog? = null
    private val LISTENING_DURATION = 5000L // Timeout after 5 seconds
    private var isFinalResultProcessed = false  // Flag to prevent multiple calls to onResult
    private var partialResultBuffer: String? = null // Buffer to hold the most recent partial result

    fun startListening() {
        Log.d("SpeechRecognizer", "Starting speech recognition process")

        // Reinitialize the SpeechRecognizer every time we start listening
        speechRecognizer?.destroy() // Destroy the previous instance to free resources
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        Log.d("SpeechRecognizer", "SpeechRecognizer created")

        // Set the recognition listener
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
                showDialog() // Show the dialog when ready
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
                startListeningTimeout() // Start the timeout after speech begins
            }

            override fun onRmsChanged(rmsdB: Float) {
                Log.d("SpeechRecognizer", "RMS changed: $rmsdB")
                // No need to update dialog during partial results
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d("SpeechRecognizer", "Buffer received: ${buffer?.size ?: 0} bytes")
                // No need to update dialog during partial results
            }

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "End of speech")
                // Do not stop listening here, it is handled by timeout or results
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error occurred: $error")
                onError("Error occurred: $error")
                stopListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                Log.d("SpeechRecognizer", "Partial results received")

                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partialMatches.isNullOrEmpty()) {
                    partialResultBuffer = partialMatches[0] // Save partial result as main output
                    updateDialog(partialResultBuffer!!) // Update the dialog with partial result
                } else {
                    Log.w("SpeechRecognizer", "No partial results")
                }
            }

            override fun onResults(results: Bundle?) {
                Log.d("SpeechRecognizer", "Recognition results received")
                Log.d("SpeechRecognizer", "partial results: $partialResultBuffer")
                Log.d("SpeechRecognizer", "results: $results")
                Log.d("SpeechRecognizer", "results stringed: ${
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                }")

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val finalTranscript = matches[0]
                    Log.d("SpeechRecognizer", "Final transcription: $finalTranscript")

                    // Process the final result only once
                    if (!isFinalResultProcessed) {
                        onResult(finalTranscript) // Pass the result back to the UI
                        updateDialog(finalTranscript) // Update the dialog with the final transcription
                        isFinalResultProcessed = true  // Prevent further processing
                    }
                } else {
                    // If no final result, use the last partial result (if available)
                    if (!partialResultBuffer.isNullOrEmpty()) {
                        onResult(partialResultBuffer!!) // Use the last partial result
                        updateDialog(partialResultBuffer!!) // Update the dialog with the last partial result
                    } else {
                        onError("No recognition result available") // Notify if no results
                    }
                }

                // Ensure proper cleanup of the SpeechRecognizer after results
                stopListening()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("SpeechRecognizer", "Event occurred: $eventType")
            }
        })

        // Reset flag before starting new recognition
        isFinalResultProcessed = false

        // Create the intent for recognizing speech
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)  // Force US English (online)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Get partial results
        }

        Log.d("SpeechRecognizer", "Starting speech recognition with intent: $intent")
        speechRecognizer?.startListening(intent)
    }

    private fun startListeningTimeout() {
        // Start a timer that will stop listening after the specified time (5 seconds)
        Log.d("SpeechRecognizer", "Setting timeout for speech recognition.")
        Handler().postDelayed({
            Log.d("SpeechRecognizer", "Stopping recognition after timeout.")
            stopListening() // Stop listening after timeout
        }, LISTENING_DURATION)
    }

    fun stopListening() {
        Log.d("SpeechRecognizer", "Stopping speech recognition")

        // Clean up
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        Log.d("SpeechRecognizer", "SpeechRecognizer stopped and destroyed")

        dialog?.dismiss() // Dismiss the dialog when stopping
        Log.d("SpeechRecognizer", "Dialog dismissed")
    }

    private fun showDialog() {
        Log.d("SpeechRecognizer", "Showing dialog")

        // Inflate the custom dialog view
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_speech_result, null)

        // Initialize the dialog
        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Set up the Close button click listener
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.closeButton)
        closeButton.setOnClickListener {
            Log.d("SpeechRecognizer", "Close button clicked")
            // Dismiss the dialog when the Close button is clicked
            dialog?.dismiss()
            stopListening() // Stop the speech recognizer when the dialog is closed
        }

        // Show the dialog
        dialog?.show()
    }

    private fun updateDialog(transcription: String) {
        Log.d("SpeechRecognizer", "Updating dialog with transcription: $transcription")

        dialog?.let {
            val transcriptionTextView = it.findViewById<TextView>(R.id.transcriptionTextView)
            transcriptionTextView?.text = transcription // Update the text view with the transcription
        }
    }
}
