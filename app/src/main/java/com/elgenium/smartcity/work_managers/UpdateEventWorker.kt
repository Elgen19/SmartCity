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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class UpdateEventWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val oldChecker = inputData.getString("oldChecker") ?: return Result.failure()
        val newChecker = inputData.getString("newChecker") ?: return Result.failure()
        val eventDataJson = inputData.getString("eventData") ?: return Result.failure()
        val newImageUris = inputData.getStringArray("newImageUris")?.map { Uri.parse(it) } ?: emptyList()

        val eventData: Map<String, Any?> = Gson().fromJson(eventDataJson, object : TypeToken<Map<String, Any?>>() {}.type)

        val database = FirebaseDatabase.getInstance().reference.child("Events")
        val usersRef = FirebaseDatabase.getInstance().getReference("Users")
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()

        // Fetch user details
        val fullName = usersRef.child(userId).child("fullName").get().await().getValue(String::class.java) ?: "Unknown User"
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis())

        // Prepare updated event data
        val updatedEventData = eventData.toMutableMap().apply {
            put("submittedBy", fullName)
            put("submittedAt", currentDateTime)
            put("userId", userId)
            put("checker", newChecker)
        }

        // Retrieve existing images if no new images are provided
        val existingUrls = eventData["images"] as? List<String> ?: emptyList()
        val newImageUrls = if (newImageUris.isNotEmpty()) uploadNewImages(newImageUris, newChecker) else emptyList()
        updatedEventData["images"] = existingUrls + newImageUrls.filter { it.isNotEmpty() }

        return try {
            // Step 1: Delete or update events with oldChecker
            updateOldCheckerEvents(database, oldChecker)

            // Step 2: Add or update event with newChecker
            val newSnapshot = database.orderByChild("checker").equalTo(newChecker).get().await()
            if (newSnapshot.exists()) {
                for (eventSnapshot in newSnapshot.children) {
                    eventSnapshot.ref.updateChildren(updatedEventData).await()
                }
            } else {
                database.push().setValue(updatedEventData).await()
            }

            Result.success()

        } catch (e: Exception) {
            Log.e("UpdateEventWorker", "Error updating event", e)
            Result.failure()
        }
    }

    private suspend fun updateOldCheckerEvents(database: DatabaseReference, oldChecker: String) {
        val oldSnapshot = database.orderByChild("checker").equalTo(oldChecker).get().await()
        if (oldSnapshot.exists()) {
            for (eventSnapshot in oldSnapshot.children) {
                eventSnapshot.ref.removeValue().await()
            }
        }
    }

    private suspend fun uploadNewImages(imageUris: List<Uri>, newChecker: String): List<String> = coroutineScope {
        imageUris.map { uri ->
            async {
                try {
                    uploadImageToFirebaseStorage(newChecker, uri)
                } catch (e: Exception) {
                    Log.e("UpdateEventWorker", "Error uploading image: $uri", e)
                    ""
                }
            }
        }.map { it.await() }
    }

    private suspend fun uploadImageToFirebaseStorage(eventName: String, uri: Uri): String {
        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("event_images/${eventName}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        return imageRef.putFile(uri).await().storage.downloadUrl.await().toString()
    }
}
