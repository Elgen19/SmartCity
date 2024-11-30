package com.elgenium.smartcity.speech

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.R
import com.google.android.material.button.MaterialButton
import com.google.api.gax.rpc.ResponseObserver
import com.google.api.gax.rpc.StreamController
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1p1beta1.RecognitionConfig
import com.google.cloud.speech.v1p1beta1.SpeechClient
import com.google.cloud.speech.v1p1beta1.SpeechSettings
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionConfig
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeRequest
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse
import com.google.protobuf.ByteString
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class StreamingSpeechRecognition(
    private val languageCode: String = "en-US",
    private val sampleRate: Int = 16000,
    private val activity: Activity, // Add Activity context here to request permissions
    private val transcriptionCallback: (String) -> Unit // Callback to send transcription to the Activity
) {

    private var speechClient: SpeechClient? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor() // Executor service for background tasks
    private var dialog: AlertDialog? = null
    private var dialogTextView: TextView? = null  // TextView to update with real-time transcription

    init {
        // Initialize the Speech Client with credentials from the JSON file in the assets folder
        try {
            val credentialsStream: InputStream = activity.assets.open("maps_cred.json")
            val credentials = GoogleCredentials.fromStream(credentialsStream)

            // Configure the Speech client settings with the credentials
            val speechSettings = SpeechSettings.newBuilder()
                .setCredentialsProvider { credentials }
                .build()

            speechClient = SpeechClient.create(speechSettings)
            Log.d("SpeechRecognizer", "Speech Client initialized with credentials")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("SpeechRecognizer", "Error initializing Speech Client: ${e.message}")
        }
    }

    fun startStreaming() {
        Log.d("SpeechRecognizer", "Checking microphone permission")
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("SpeechRecognizer", "Permission not granted, requesting permission")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
            return
        }

        // Show dialog immediately after starting streaming
        showDialog()

        isRecording = true
        executorService.execute {
            streamingRecognize() // Execute the task in the background thread
        }
    }



    private fun streamingRecognize() {
        Log.d("SpeechRecognizer", "Starting streaming recognition")
        val responseObserver = object : ResponseObserver<StreamingRecognizeResponse> {
            private val responses = ArrayList<StreamingRecognizeResponse>()

            override fun onStart(controller: StreamController) {
                Log.d("SpeechRecognizer", "Stream started")
            }

            override fun onResponse(response: StreamingRecognizeResponse) {
                Log.d("SpeechRecognizer", "Received response")
                responses.add(response)
                processResponse(response)  // Process the response as usual
            }

            override fun onComplete() {
                Log.d("SpeechRecognizer", "Streaming complete.")
                closeDialogAndStopListening()
            }

            override fun onError(t: Throwable) {
                Log.e("SpeechRecognizer", "Error during streaming: ${t.message}")
                t.printStackTrace()
                closeDialogAndStopListening()  // Close the dialog and stop listening on error
            }
        }

        try {
            // Check if microphone permission is granted before initializing AudioRecord
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e("SpeechRecognizer", "Permission for microphone not granted")
                Toast.makeText(activity, "Microphone permission is required for speech recognition", Toast.LENGTH_SHORT).show()
                return // Exit if permission is not granted
            }

            // Initialize the speech client (check for null)
            if (speechClient == null) {
                Log.e("SpeechRecognizer", "Speech client is not initialized.")
                return
            }

            // Initialize streaming recognition
            val clientStream = speechClient!!.streamingRecognizeCallable().splitCall(responseObserver)

            val recognitionConfig = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setLanguageCode(languageCode)
                .setSampleRateHertz(sampleRate)
                .build()

            val streamingRecognitionConfig = StreamingRecognitionConfig.newBuilder()
                .setConfig(recognitionConfig)
                .build()

            // Send the initial streaming request
            clientStream.send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingRecognitionConfig).build())

            // Initialize AudioRecord
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (bufferSize <= 0) {
                Log.e("SpeechRecognizer", "Invalid buffer size: $bufferSize.")
                return
            }

            // Initialize AudioRecord only if the buffer size is valid
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)

            // Ensure AudioRecord is properly initialized
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("SpeechRecognizer", "AudioRecord initialization failed.")
                return
            }

            // Start the recording
            audioRecord!!.startRecording()
            Log.d("SpeechRecognizer", "Audio recording started successfully.")


            // Start streaming and handle recording
            while (isRecording) {
                val data = ByteArray(bufferSize)
                val readResult = audioRecord!!.read(data, 0, bufferSize)

                if (readResult > 0) {
                    clientStream.send(
                        StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(data))
                            .build()
                    )
                } else {
                    Log.w("SpeechRecognizer", "No data read from AudioRecord. Read result: $readResult")
                }
            }


            // Ensure proper cleanup of AudioRecord (only stop if initialized)
            if (audioRecord != null && audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord!!.stop()  // Stop recording if initialized successfully
                    Log.d("SpeechRecognizer", "Audio recording stopped.")
                    audioRecord!!.release()  // Release the resources after stopping
                    Log.d("SpeechRecognizer", "AudioRecord released.")
                } catch (e: Exception) {
                    Log.e("SpeechRecognizer", "Error stopping or releasing AudioRecord: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Log.e("SpeechRecognizer", "AudioRecord was not initialized, cannot stop.")
            }

        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Error in streaming recognition: ${e.message}")
            e.printStackTrace()
            closeDialogAndStopListening()  // Close dialog and stop if an exception occurs
        }
    }

    fun closeDialogAndStopListening() {
        // Ensure dialog is dismissed properly
        Log.d("SpeechRecognizer", "Closing dialog and freeing resources")

        activity.runOnUiThread {
            dialog?.dismiss()
            dialog = null
        }
        stopListening()
    }

    private fun stopListening() {
        Log.d("SpeechRecognizer", "Stopping listening")
        if (audioRecord != null && audioRecord?.state == AudioRecord.STATE_INITIALIZED && isRecording) {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                isRecording = false
                speechClient?.close()
                executorService.shutdownNow()
                Log.d("SpeechRecognizer", "AudioRecord stopped and released.")
            } catch (e: IllegalStateException) {
                Log.e("SpeechRecognizer", "Failed to stop AudioRecord: ${e.message}")
            }
        } else {
            Log.w("SpeechRecognizer", "AudioRecord is not initialized or already stopped.")
        }
    }






    private fun processResponse(response: StreamingRecognizeResponse) {
        try {
            Log.d("SpeechRecognizer", "Processing response")
            val result = response.resultsList[0]
            val alternative = result.alternativesList[0]
            val transcription = alternative.transcript
            Log.d("SpeechRecognizer", "Transcript: $transcription")

            // Update the dialog first
            updateDialog(transcription)

            // Send the transcription result to the callback
            transcriptionCallback(transcription)

        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Error processing response: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun updateDialog(transcription: String) {
        Log.d("SpeechRecognizer", "Updating dialog with transcription")
        Log.d("SpeechRecognizer", "transcription to be palce in dialog: $transcription")
        Log.d("SpeechRecognizer", "dialogTextView reference: $dialogTextView")

        if (dialogTextView == null) {
            Log.e("SpeechRecognizer", "dialogTextView is null. Cannot update with transcription.")
            return
        }

        activity.runOnUiThread {
            dialogTextView?.text = transcription
            Log.d("SpeechRecognizer", "Setting dialogTextView text to: $transcription")

            Handler(Looper.getMainLooper()).postDelayed({
                closeDialogAndStopListening()
            }, 2000)  // Delay before closing the dialog (adjust time as needed)
        }




    }

    private fun showDialog() {
        Log.d("SpeechRecognizer", "Showing dialog")
        activity.runOnUiThread {
            // Check if the dialog is already displayed
            if (dialog == null) {
                val dialogView = activity.layoutInflater.inflate(R.layout.dialog_speech_result, null)
                dialogTextView = dialogView.findViewById(R.id.transcriptionTextView)
                dialog = AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create()

                val closeButton = dialogView.findViewById<MaterialButton>(R.id.closeButton)
                closeButton.setOnClickListener {
                    Log.d("SpeechRecognizer", "Close button clicked")
                    dialog?.dismiss()
                    dialog = null
                    stopListening()
                }

                dialog?.show()

            }
        }
    }



    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
}

