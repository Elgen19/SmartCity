package com.elgenium.smartcity.singletons

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object TokenManager {
    private const val PREFS_NAME = "user_settings"
    private const val TOKEN_KEY = "fcm_token"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveToken(context: Context, token: String) {
        val editor = getSharedPreferences(context).edit()
        editor.putString(TOKEN_KEY, token)
        editor.apply()

        // Save the token directly to Firebase Realtime Database
        saveTokenToFirebase(context, token)
    }

    fun getSavedToken(context: Context): String? {
        return getSharedPreferences(context).getString(TOKEN_KEY, null)
    }


     fun saveTokenToFirebase(context: Context, token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "NO_USER"

        // Ensure user is authenticated
        if (userId != "NO_USER") {
            // Check the token saved in SharedPreferences
            val savedToken = getSavedToken(context)

            // If the saved token is different from the new token, save it
            if (savedToken != token) {
                // Save the new token in SharedPreferences
                saveToken(context, token)

                // Get a reference to the Firebase Realtime Database
                val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("Tokens").child(userId)

                // Create a token object to save
                val tokenData = mapOf("userToken" to token)

                // Save the token under the user's ID in Firebase
                database.setValue(tokenData).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FCM", "Token saved successfully to Firebase!")
                    } else {
                        Log.e("FCM", "Failed to save token to Firebase: ${task.exception?.message}")
                    }
                }
            } else {
                Log.d("FCM", "Token is already up-to-date in SharedPreferences: $savedToken")
            }
        } else {
            Log.e("FCM", "User ID is null or user is not authenticated.")
        }
    }



}
