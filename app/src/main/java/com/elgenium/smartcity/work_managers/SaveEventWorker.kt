package com.elgenium.smartcity.work_managers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class SaveEventWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SaveEventWorker", "doWork: Start saving event data")

        // Get event data and image URIs from input data
        val eventName = inputData.getString("eventName") ?: return Result.failure().also {
            Log.e("SaveEventWorker", "doWork: Event name is missing")
        }
        val images = inputData.getStringArray("images")?.map { Uri.parse(it) } ?: return Result.failure().also {
            Log.e("SaveEventWorker", "doWork: Image URIs are missing")
        }
        val eventDataJson = inputData.getString("eventData") ?: return Result.failure().also {
            Log.e("SaveEventWorker", "doWork: Event data JSON is missing")
        }
        val eventData = Gson().fromJson<Map<String, Any?>>(eventDataJson, object : TypeToken<Map<String, Any?>>() {}.type)

        try {
            Log.d("SaveEventWorker", "doWork: Saving textual event data to Firebase")
            // Save event textual data first
            val eventId = saveTextualEventDataToDatabase(eventData, eventName)

            // Now start uploading images
            Log.d("SaveEventWorker", "doWork: Uploading images to Firebase Storage")
            val uploadedImageUrls = images.map { uri ->
                uploadImageToFirebaseStorage(eventName, uri)
            }

            // Update the event with the image URLs after they are uploaded
            updateEventImages(eventId, uploadedImageUrls)
            Log.d("SaveEventWorker", "doWork: Event data saved successfully")

            return Result.success()
        } catch (e: Exception) {
            Log.e("SaveEventWorker", "Error uploading images or saving event", e)
            return Result.failure()
        }
    }

    private suspend fun saveTextualEventDataToDatabase(eventData: Map<String, Any?>, eventName: String): String {
        val database = FirebaseDatabase.getInstance()
        val eventsRef = database.getReference("Events")
        val usersRef = database.getReference("Users")

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: throw Exception("User not logged in")
        val fullName = usersRef.child(userId).child("fullName").get().await().getValue(String::class.java) ?: "Unknown User"
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis())

        val updatedEventData = eventData.toMutableMap().apply {
            put("submittedBy", fullName)
            put("submittedAt", currentDateTime)
            put("userId", userId)
        }

        val eventRef = eventsRef.push() // Create a new entry and get its key
        eventRef.setValue(updatedEventData).await()
        updateUserPoints(usersRef, userId)

        Log.d("SaveEventWorker", "saveTextualEventDataToDatabase: Event data saved in Firebase with ID: ${eventRef.key}")
        return eventRef.key ?: throw Exception("Failed to get event ID")
    }

    private suspend fun uploadImageToFirebaseStorage(eventName: String, uri: Uri): String {
        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("event_images/${eventName}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        val downloadUrl = imageRef.putFile(uri).await().storage.downloadUrl.await().toString()
        return downloadUrl
    }

    private suspend fun updateEventImages(eventId: String, imageUrls: List<String>) {
        val eventsRef = FirebaseDatabase.getInstance().getReference("Events").child(eventId)
        eventsRef.child("images").setValue(imageUrls).await()
        Log.d("SaveEventWorker", "updateEventImages: Image URLs updated in Firebase for event ID: $eventId")
    }

    private suspend fun updateUserPoints(usersRef: DatabaseReference, userId: String) {
        val userPointsRef = usersRef.child(userId).child("points")
        val currentPoints = userPointsRef.get().await().getValue(Int::class.java) ?: 0
        userPointsRef.setValue(currentPoints + 5).await()
    }
}
