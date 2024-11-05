package com.elgenium.smartcity.work_managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.UUID

class ImageUploadWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        // Retrieve input data
        val imagePath = inputData.getString("imagePath")
        val placeName = inputData.getString("placeName")
        val savedPlaceId = inputData.getString("savedPlaceId") // Retrieve the saved place ID

        if (imagePath != null && placeName != null && savedPlaceId != null) {
            // Load the bitmap from the file
            val bitmap = BitmapFactory.decodeFile(imagePath)

            val storageRef = FirebaseStorage.getInstance().reference.child("places")
            val photoRef = storageRef.child("${placeName}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

            // Convert Bitmap to ByteArray for upload
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val data = baos.toByteArray()

            // Upload the photo to Firebase Storage
            val uploadTask = photoRef.putBytes(data)

            uploadTask.addOnSuccessListener {
                // Get the download URL of the uploaded image
                photoRef.downloadUrl.addOnSuccessListener { uri ->
                    // Handle successful upload (e.g., save the URL in the database)
                    Log.d("ImageUploadWorker", "Image uploaded successfully: $uri")
                    // Save the image URL to the database under the specific saved place ID
                    saveImageUrlToDatabase(uri.toString(), savedPlaceId)
                }
            }.addOnFailureListener { exception ->
                Log.e("ImageUploadWorker", "Error uploading image", exception)
            }

            return Result.success() // Return success or failure based on your logic
        }
        return Result.failure() // Return failure if no data
    }

    private fun saveImageUrlToDatabase(imageUrl: String, savedPlaceId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/saved_places/$savedPlaceId/imageUrls")

        // Retrieve existing image URLs
        userRef.get().addOnSuccessListener { dataSnapshot ->
            // Create a mutable list to hold existing URLs
            val imageUrls = mutableListOf<String>()

            // If there are existing URLs, add them to the list
            dataSnapshot.children.forEach { snapshot ->
                snapshot.getValue(String::class.java)?.let { imageUrls.add(it) }
            }

            // Add the new URL to the list
            imageUrls.add(imageUrl)

            // Save the updated list back to the database
            userRef.setValue(imageUrls)
                .addOnSuccessListener {
                    Log.d("ImageUploadWorker", "Image URL saved successfully: $imageUrl")
                }
                .addOnFailureListener { exception ->
                    Log.e("ImageUploadWorker", "Error saving image URL", exception)
                }
        }.addOnFailureListener { exception ->
            Log.e("ImageUploadWorker", "Error retrieving existing image URLs", exception)
        }
    }

}
