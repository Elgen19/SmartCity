package com.elgenium.smartcity.intelligence

import android.util.Log
import com.elgenium.smartcity.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.generationConfig

class ActivityPlaceProcessor {
    private val dangerousContent = SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    private val sexuallyExplicit = SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE)
    private val hateSpeech = SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE)
    private val harassment = SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE)
    private val model = GenerativeModel(
        "tunedModels/activity-to-place-classifier-model-wmvom",
        BuildConfig.GEMINI_AI_API,
        generationConfig = generationConfig {
            temperature = 1f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 8192
            responseMimeType = "text/plain"
        },
        safetySettings = listOf(dangerousContent, sexuallyExplicit, hateSpeech, harassment)
    )
    // Method to process user query and extract activity and place
// Method to process user query and extract activity and place
    suspend fun processUserQuery(userQuery: String): List<String>? {
        Log.d("ActivityPlaceProcessor", "Processing user query: '$userQuery'")

        try {
            // Start a chat session with the model
            Log.d("ActivityPlaceProcessor", "Starting chat session with the model")
            val chat = model.startChat(listOf())

            // Send the user query to the model
            Log.d("ActivityPlaceProcessor", "Sending message to model: '$userQuery'")
            val response = chat.sendMessage(userQuery)

            // Extract the first response content
            val result = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.asTextOrNull()

            if (result.isNullOrEmpty()) {
                Log.e("ActivityPlaceProcessor", "No valid response for query: '$userQuery'")
                return null
            }

            Log.i("ActivityPlaceProcessor", "Received response: '$result'")

            // Split the result into places by either comma or new line
            val places = result.split(", ", "\n").map { it.trim() }

            // If no places were found, return a list with "unknown"
            return places.ifEmpty { listOf("unknown") }

        } catch (e: Exception) {
            Log.e("ActivityPlaceProcessor", "Error processing query: ${e.message}", e)
            return null
        }
    }




}
