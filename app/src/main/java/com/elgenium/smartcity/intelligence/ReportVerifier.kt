package com.elgenium.smartcity.intelligence

// Gemini API imports
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.MyEventsActivity
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.Event
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ReportVerifier {

    private val generativeModel = GenerativeModel(
        // Specify the Gemini model version
        modelName = "gemini-1.5-flash",
        // Access your API key
        apiKey = BuildConfig.GEMINI_AI_API
    )

    private val database = FirebaseDatabase.getInstance().getReference("Events")

    // Function to fetch events for the currently signed-in user from Firebase
    suspend fun fetchEventsFromFirebase(): List<Event> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid // Get the current user's ID
        Log.e("ReportVerifier", "Fetching events for user: $currentUserId")

        return withContext(Dispatchers.IO) {
            val eventList = mutableListOf<Event>()
            val dataSnapshot = getDataSnapshotFromFirebase(database)

            for (snapshot in dataSnapshot.children) {
                val event = snapshot.getValue(Event::class.java)
                // Check if the event is not null, matches the current user's ID, and has the desired status
                if (event != null && event.userId == currentUserId &&
                    (event.status == "Unverified" || event.status == "Verification_Failed")) {
                    eventList.add(event)
                }
            }
            Log.e("ReportVerifier", "Fetched ${eventList.size} events for user: $currentUserId")
            eventList
        }
    }


    // Helper function to fetch DataSnapshot from Firebase
    private suspend fun getDataSnapshotFromFirebase(ref: DatabaseReference): DataSnapshot {
        return withContext(Dispatchers.IO) {
            try {
                val task = ref.get()
                task.await()
                Log.e("ReportVerifier", "Successfully fetched data snapshot from Firebase.")
                task.result
            } catch (e: Exception) {
                Log.e("ReportVerifier", "Error fetching data snapshot from Firebase: ${e.message}")
                throw e
            }
        }
    }

    // Function to update the status of an event in Firebase
    private suspend fun updateEventStatus(checker: String, newStatus: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.e("ReportVerifier", "Updating status for event with checker: $checker to $newStatus")
                database.orderByChild("checker")
                    .equalTo(checker)
                    .get()
                    .await()
                    .children
                    .forEach { snapshot ->
                        snapshot.ref.child("status").setValue(newStatus).await()
                        Log.e("ReportVerifier", "Updated status for checker: $checker to $newStatus")
                    }
            } catch (e: Exception) {
                Log.e("ReportVerifier", "Error updating status for checker: $checker: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Function to fetch images from URLs and convert them to Bitmap
    private suspend fun fetchImagesFromUrls(urls: List<String>): List<Bitmap> {
        Log.e("ReportVerifier", "Fetching images from URLs: $urls")
        return withContext(Dispatchers.IO) {
            urls.mapNotNull { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    Log.e("ReportVerifier", "Fetched image from URL: $url")
                    bitmap
                } catch (e: Exception) {
                    Log.e("ReportVerifier", "Error fetching image from URL: $url: ${e.message}")
                    null
                }
            }
        }
    }

    // Function to verify event details (Step 1)
    private suspend fun verifyEvent(
        eventName: String,
        eventCategory: String,
        eventDescription: String
    ): Boolean {
        val prompt = "Verify if the description: '$eventDescription' matches or is related to the event category: '$eventCategory' or the event name: '$eventName'. Respond with 'yes' if it matches, and 'no' if it doesn't."
        Log.e("ReportVerifier", "Verifying event details with prompt: $prompt")

        return try {
            val response = withContext(Dispatchers.IO) {
                generativeModel.generateContent(prompt)
            }
            val result = response.text?.trim()?.lowercase()
            Log.e("ReportVerifier", "Verification result for event: $result")
            result == "yes"
        } catch (e: Exception) {
            Log.e("ReportVerifier", "Error verifying event details: ${e.message}")
            false
        }
    }

    // Function to verify images using URLs (Step 2)
    private suspend fun verifyImages(
        imageUrls: List<String>,
        eventName: String,
        eventCategory: String,
        eventDescription: String
    ): Boolean {
        Log.e("ReportVerifier", "Verifying images for event: $eventName, category: $eventCategory")

        // Fetch images from URLs
        val images = fetchImagesFromUrls(imageUrls)

        // Verify each image
        for (image in images) {
            val inputContent = content {
                image(image)
                text("Does this image relate to an event described as: '$eventDescription' or '$eventCategory'or '$eventName'? Respond with 'yes' or 'no' only no periods or explanations")
            }

            try {
                // Generate content using Gemini API without streaming
                val response = withContext(Dispatchers.IO) {
                    generativeModel.generateContent(inputContent)
                }

                // Check if the AI response indicates a match
                val result = response.text?.trim()?.lowercase()
                Log.e("ReportVerifier", "Image verification result: $result")

                if (result != "yes") {
                    Log.e("ReportVerifier", "Image verification failed for event: $eventName")
                    return false // If any image does not match, verification fails
                }

            } catch (e: Exception) {
                Log.e("ReportVerifier", "Error verifying images: ${e.message}")
                return false
            }
        }

        // All images were verified successfully
        Log.e("ReportVerifier", "All images verified successfully for event: $eventName")
        return true
    }

    // Function to verify the event and update its status in Firebase
    suspend fun verifyAndUpdateEvent(context: Context, event: Event) {
        val isEventValid = event.eventName != null && event.eventCategory != null && event.eventDescription != null
        Log.e("ReportVerifier", "Verifying event: ${event.eventName}, Valid: $isEventValid")

        // Variables to hold failure reasons
        var eventDetailFailed = false
        var imagesFailed = false

        if (isEventValid) {
            // Step 1: Verify Event Details
            val isEventVerified = verifyEvent(
                eventName = event.eventName!!,
                eventCategory = event.eventCategory!!,
                eventDescription = event.eventDescription!!
            )

            // Update eventDetailFailed if verification failed
            if (!isEventVerified) {
                eventDetailFailed = true
            }

            // Step 2: Verify Images if the event details are valid
            val areImagesVerified = if (isEventVerified && event.images != null) {
                verifyImages(
                    imageUrls = event.images!!,
                    eventName = event.eventName,
                    eventCategory = event.eventCategory,
                    eventDescription = event.eventDescription
                )
            } else {
                false
            }

            // Update imagesFailed if verification failed
            if (!areImagesVerified) {
                imagesFailed = true
            }

            // Update status based on verification results
            val newStatus = if (isEventVerified && areImagesVerified) {
                "Verified"
            } else {
                "Verification_Failed"
            }

            // Update the status in Firebase using the checker property
            Log.e("ReportVerifier", "Updating event status for: ${event.eventName} to: $newStatus")
            if (event.checker != null) {
                updateEventStatus(event.checker, newStatus)

                // Prepare a detailed notification message
                val reasonMessages = mutableListOf<String>()
                if (eventDetailFailed) {
                    reasonMessages.add("event description, event name or category do not relate with one another.")
                }
                if (imagesFailed) {
                    reasonMessages.add("images do not match the event name, description, or category.")
                }
                val reasons = reasonMessages.joinToString(" and ")
                val notificationMessage = if (reasons.isNotEmpty()) {
                    "Your reported event '${event.eventName}' failed to be posted in public due to $reasons. Please consider editing the reported event."
                } else {
                    "Your reported event '${event.eventName}' has been approved."
                }

                sendNotification(context, "Event Update", notificationMessage)
            }
        } else {
            Log.e("ReportVerifier", "Event is not valid: ${event.eventName}")
        }
    }





    fun createNotificationChannel(context: Context) {
        val channelId = "event_updates"
        val channelName = "Event Updates"
        val channelDescription = "Notifications for event verification updates"
        val importance = NotificationManager.IMPORTANCE_HIGH // Use IMPORTANCE_HIGH

        // Create the NotificationChannel with the correct settings
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)  // Ensure vibration is enabled
            vibrationPattern = longArrayOf(0, 500, 1000) // Vibration pattern

            // Set sound
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(soundUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }

        // Register the channel with the system
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }



    private fun sendNotification(context: Context, title: String, message: String) {
        // Create a unique notification ID
        val notificationId = System.currentTimeMillis().toInt() // Generate a unique ID

        val channelId = "event_updates" // Channel ID should match what was created earlier

        // Create an Intent to open the target activity
        val intent = Intent(context, MyEventsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // For compatibility with API 31+
        )

        // Create the notification with high priority

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.smart_city_logo) // Ensure this icon is valid
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Set to high priority
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        // Ensure the notification channel is created
        createNotificationChannel(context)

        // Show the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)


        val notificationData = mapOf(
            "id" to notificationId.toLong(),
            "title" to title,
            "message" to message,
            "timestamp" to ServerValue.TIMESTAMP // For sorting or filtering by date
        )
        saveNotificationToDatabase(notificationData)
    }

    private fun saveNotificationToDatabase(notificationData: Map<String, Any?>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("ReportVerifier", "User is not authenticated. Cannot save notification.")
            return
        }

        val notificationsRef = FirebaseDatabase.getInstance().getReference("Users/$userId/Notifications")

        // Ensure that notificationData has a valid ID
        val notificationId = notificationData["id"] as? Long ?: run {
            Log.e("ReportVerifier", "Notification ID is not a valid Long.")
            return
        }

        notificationsRef.child(notificationId.toString()).setValue(notificationData)
            .addOnSuccessListener {
                Log.d("ReportVerifier", "Notification saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("ReportVerifier", "Failed to save notification", e)
                Log.e("ReportVerifier", "Error Code: ${e.message}") // More detailed error logging
            }
    }




}
