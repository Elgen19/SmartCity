package com.elgenium.smartcity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.sharedpreferences.PreferencesManager
import com.elgenium.smartcity.singletons.GoogleSignInClientProvider
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var alertDialog: AlertDialog? = null
    private var permissionsHandled = false
    private val deniedPermissions = mutableListOf<String>()
    private var starterScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        retrievePreferences()

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize Google Sign-In Client if needed
        googleSignInClient = GoogleSignInClientProvider.getGoogleSignInClient(this)

        // Initial check for location services and permissions
        checkLocationAndPermissions()


    }



    override fun onResume() {
        super.onResume()
        Toast.makeText(this, "Permission handles: $permissionsHandled and Location enabled: ${isLocationEnabled()}", Toast.LENGTH_SHORT).show()

        // Ensure permissions are handled before proceeding
        if (permissionsHandled && isLocationEnabled()) {
            proceedWithAppLogic()
        }
    }

    private fun checkLocationAndPermissions() {
        if (isLocationEnabled() && isLocationPermissionGranted()) {
            permissionsHandled = true
            proceedWithAppLogic()
        } else {
            requestAllPermissions()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionsToRequest.addAll(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.VIBRATE,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            permissionsToRequest.addAll(
                listOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                1001
            )
        }
    }

    private fun promptEnableLocationAndPermissions() {
        if (isFinishing || isDestroyed) return

        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.location_permission_dialog, null)
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setView(dialogView)
        alertDialog = dialogBuilder.create()

        val positiveButton: Button = dialogView.findViewById(R.id.positive_button)
        val negativeButton: Button = dialogView.findViewById(R.id.negative_button)

        positiveButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            alertDialog?.dismiss()
        }

        negativeButton.setOnClickListener {
            alertDialog?.dismiss()
            finish()
        }

        alertDialog?.setCancelable(false)
        alertDialog?.show()
    }

    private fun proceedWithAppLogic() {
        // Delay to ensure smooth transition after permissions are granted
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser == null) {
                // User is not logged in
                if (PreferencesManager.isOnboardingCompleted(this)) {
                    navigateToSignIn()
                } else {
                    navigateToOnboarding()
                }
            } else {
                checkFirebaseAuthState()
            }
        }, 1000)
    }

    private fun checkFirebaseAuthState() {
        val currentUser = auth.currentUser
        currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                navigateToDashboard()
            } else {
                if (task.exception?.message?.contains("BAD_AUTHENTICATION") == true) {
                    signOut()
                } else {
                    Log.e("MainActivity", "FirebaseAuth token error: ${task.exception?.message}")
                }
            }
        } ?: run {
            navigateToSignIn()
        }
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            navigateToSignIn()
        }
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingScreenActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToDashboard() {
        if (starterScreen) {
            val intent = Intent(this, DashboardActivity::class.java)
            Toast.makeText(this, "Starter screen at dashboard: $starterScreen", Toast.LENGTH_SHORT).show()
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this, PlacesActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            deniedPermissions.clear() // Clear previous entries

            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i]) // Add denied permissions to the list
                }
            }

            // If all permissions are granted, proceed to check location settings
            if (deniedPermissions.isEmpty()) {
                promptEnableLocationAndPermissions()
                permissionsHandled = true
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun retrievePreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        starterScreen = sharedPreferences.getBoolean("start_screen", false)
        // Optionally log the retrieved value
        Log.e("Preferences", "contextRecommender at retrievePreferences: $starterScreen")
    }

    // Show a dialog if permissions are permanently denied (Don't ask again)
    private fun showPermissionDeniedDialog() {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_denied, null)

        // Get references to the views in the custom layout
        val dialogMessage: TextView = dialogView.findViewById(R.id.dialog_message)
        val goToSettingsButton: Button = dialogView.findViewById(R.id.button_go_to_settings)
        val cancelButton: Button = dialogView.findViewById(R.id.button_cancel)

        // Set the message for denied permissions
        val deniedPermissionsMessage = deniedPermissions.joinToString(", ") { permission ->
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
                Manifest.permission.RECORD_AUDIO -> "Audio Recording"
                Manifest.permission.CAMERA -> "Camera"
                Manifest.permission.READ_MEDIA_IMAGES -> "Media Access"
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage"
                Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                else -> permission
            }
        }

        dialogMessage.text = Html.fromHtml("The following permissions are necessary for the app to function properly: <b> $deniedPermissionsMessage.</b> <br> <br>Please go to Settings > Apps > SmartCity > Permissions and enable the required permissions.", Html.FROM_HTML_MODE_LEGACY)

        // Create the dialog
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // Prevent dismissal without user interaction

        // Set up the buttons
        goToSettingsButton.setOnClickListener {
            // Directs user to the app's settings page
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
            finish()
        }

        cancelButton.setOnClickListener {
            // Dismiss the dialog
            dialogBuilder.create().dismiss()
            finish() // Optionally, navigate the user to a safe screen or exit the app
        }

        // Show the dialog
        dialogBuilder.create().show()
    }
}
