package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivitySignInBinding
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.GoogleSignInClientProvider
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var database: DatabaseReference
    private var starterScreen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Get GoogleSignInClient from the GoogleSignInClientProvider
        googleSignInClient = GoogleSignInClientProvider.getGoogleSignInClient(this)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account != null) {
                        // Google Sign-In was successful, authenticate with Firebase
                        firebaseAuthWithGoogle(account.idToken!!)
                        // Get user information
                        val email = account.email
                        val fullName = account.displayName
                        val photoUrl = account.photoUrl?.toString() // Get profile photo URL
                        // If photoUrl is null, use default image

                        // Store user information in Firebase Realtime Database
                        storeUserInfo(email, fullName, photoUrl)
                    }
                } catch (e: ApiException) {
                    Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Google Sign-In button click listener
        binding.googleLoginButton.setOnClickListener {
            signInWithGoogle()
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Clear focus from all input fields
            binding.emailEditText.clearFocus()
            binding.passwordEditText.clearFocus()

            binding.loginButton.isEnabled = false
            binding.loginButton.text = getString(R.string.signing_in)
            signInUser(email, password)
        }

        binding.forgotPasswordTextView.setOnClickListener {
           ActivityNavigationUtils.navigateToActivity(this, ForgotPasswordActivity::class.java, false)
        }

        binding.registerTextView.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }
    }


    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Check if the user data already exists in the database
                        database.child(user.uid).get().addOnCompleteListener { dataTask ->
                            if (dataTask.isSuccessful && dataTask.result.exists()) {
                                // User data exists, check preferences
                                val preferencesSet = dataTask.result.child("preferencesSet").getValue(Boolean::class.java) ?: false
                                if (preferencesSet) {
                                    // Preferences already set, redirect to Dashboard
                                    retrieveUserSettings {
                                        startDashboardActivity()
                                    }
                                } else {
                                    // Preferences not set, redirect to PreferencesActivity
                                    startPreferencesActivity()
                                }
                            } else {
                                // User data does not exist, store it
                                storeUserInfo(user.email, user.displayName, user.photoUrl?.toString())
                                startPreferencesActivity()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Sign in failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun retrieveUserSettings(onSettingsRetrieved: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            database.child(userId).child("settings").get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val dataSnapshot = task.result
                    if (dataSnapshot != null && dataSnapshot.exists()) {
                        // Retrieve values from snapshot
                        val eventRecommender = dataSnapshot.child("event_recommender").getValue(Boolean::class.java) ?: true
                        val mapLabels = dataSnapshot.child("map_labels").getValue(Boolean::class.java) ?: false
                        val mapLandmarks = dataSnapshot.child("map_landmarks").getValue(Boolean::class.java) ?: false
                        val mapOverlay = dataSnapshot.child("map_overlay").getValue(Boolean::class.java) ?: true
                        val mapTheme = dataSnapshot.child("map_theme").getValue(String::class.java) ?: "Light"
                        val mealNotifications = dataSnapshot.child("meal_notifications").getValue(Boolean::class.java) ?: true
                        starterScreen = dataSnapshot.child("start_screen").getValue(Boolean::class.java) ?: false // Update global variable
                        val weatherNotifications = dataSnapshot.child("weather_notifications").getValue(Boolean::class.java) ?: true
                        val cycloneAlerts = dataSnapshot.child("cyclone_alert").getValue(Boolean::class.java) ?: true
                        val trafficAlerts = dataSnapshot.child("traffic_alert").getValue(Boolean::class.java) ?: true
                        val contextRecomender = dataSnapshot.child("context_recommender").getValue(Boolean::class.java) ?: true
                        val keyActivity = dataSnapshot.child("key_activity").getValue(Boolean::class.java) ?: false
                        val similarPlace = dataSnapshot.child("similar_place").getValue(Boolean::class.java) ?: false


                        // Save values to SharedPreferences
                        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
                        with(sharedPreferences.edit()) {
                            putBoolean("event_recommender", eventRecommender)
                            putString("map_theme", mapTheme)
                            putBoolean("map_landmarks", mapLandmarks)
                            putBoolean("map_labels", mapLabels)
                            putBoolean("map_overlay", mapOverlay)
                            putBoolean("meal_notifications", mealNotifications)
                            putBoolean("start_screen", starterScreen)
                            putBoolean("traffic_alert", trafficAlerts)
                            putBoolean("cyclone_alert", cycloneAlerts)
                            putBoolean("context_recommender", contextRecomender)
                            putBoolean("weather_notifications", weatherNotifications)
                            putBoolean("key_activity", keyActivity)
                            putBoolean("similar_place", similarPlace)
                            apply()  // Use apply() to save asynchronously

                            // Log the updated SharedPreferences
                            Log.d("Settings", "SharedPreferences updated: map_theme=$mapTheme, map_labels=$mapLabels, map_landmarks=$mapLandmarks")
                        }

                        // Call the callback once settings are saved
                        onSettingsRetrieved()
                    } else {
                        Log.e("Settings", "No settings found for this user")
                        onSettingsRetrieved()  // Proceed even if no settings are found
                    }
                } else {
                    // Handle failure
                    Log.e("Settings", "Failed to retrieve settings: ${task.exception?.message}")
                    onSettingsRetrieved()  // Proceed on failure
                }
            }
        } else {
            Log.e("Settings", "User ID is null, user might not be logged in.")
            onSettingsRetrieved()  // Proceed even if user is not logged in
        }
    }


    private fun startDashboardActivity() {
        Toast.makeText(this, "Sign in successful!", Toast.LENGTH_LONG).show()

        // Choose the activity based on the starterScreen value
        val intent = if (starterScreen) {
            Intent(this, DashboardActivity::class.java)
        } else {
            Intent(this, PlacesActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }


    private fun startPreferencesActivity() {
        val intent = Intent(this, PreferencesActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }



    private fun storeUserInfo(email: String?, fullName: String?, photoUrl: String?) {
        val userId = auth.currentUser?.uid ?: return

        // Get the high-resolution profile photo URL
        val highResPhotoUrl = photoUrl?.let { getHighResPhotoUrl(it) } ?: "android.resource://com.elgenium.smartcity/drawable/male"

        database.child(userId).get().addOnCompleteListener { dataTask ->
            if (dataTask.isSuccessful && !dataTask.result.exists()) {
                // Only set preferencesSet to false if user data does not exist
                val userInfo = mapOf(
                    "email" to email,
                    "fullName" to fullName,
                    "phoneNumber" to "Not Available",
                    "profilePicUrl" to highResPhotoUrl,
                    "preferencesSet" to false  // New flag to indicate if preferences are set
                )
                database.child(userId).setValue(userInfo).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "User information saved successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to save user information: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    private fun getHighResPhotoUrl(photoUrl: String): String {
        // Append a 'sz' parameter to request a higher resolution image
        return "$photoUrl?sz=800" // Change 400 to the desired size in pixels
    }


    private fun signInUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        // Check if the user has set preferences
                        database.child(user.uid).get().addOnCompleteListener { dataTask ->
                            if (dataTask.isSuccessful && dataTask.result.exists()) {
                                val preferencesSet = dataTask.result.child("preferencesSet").getValue(Boolean::class.java) ?: false
                                if (preferencesSet) {
                                    // Preferences already set, redirect to Dashboard
                                    retrieveUserSettings {
                                        startDashboardActivity()
                                    }
                                } else {
                                    // Preferences not set, redirect to PreferencesActivity
                                    startPreferencesActivity()
                                }
                            } else {
                                // User data does not exist, something went wrong
                                Toast.makeText(this, "User data not found. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // Email not verified, show a message and offer to resend the verification email
                        Toast.makeText(this, "Please verify your email address before signing in.", Toast.LENGTH_LONG).show()
                        user?.sendEmailVerification()
                        binding.loginButton.isEnabled = true
                        binding.loginButton.text = getString(R.string.login)
                    }
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = getString(R.string.login)
                }
            }
    }

}