package com.elgenium.smartcity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.sharedpreferences.PreferencesManager
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initial check for location services and permissions
        checkLocationAndPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Check again when the user returns to the app
        checkLocationAndPermissions()
    }

    private fun checkLocationAndPermissions() {
        if (isLocationEnabled()) {
            if (isLocationPermissionGranted()) {
                proceedWithAppLogic()
            } else {
                requestLocationPermissions()
            }
        } else {
            promptEnableLocationAndPermissions()
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

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun promptEnableLocationAndPermissions() {
        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.location_permission_dialog, null)

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setView(dialogView)
        val alertDialog = dialogBuilder.create()

        // Set up custom view buttons
        val positiveButton: Button = dialogView.findViewById(R.id.positive_button)
        val negativeButton: Button = dialogView.findViewById(R.id.negative_button)

        positiveButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            alertDialog.dismiss()
        }

        negativeButton.setOnClickListener {
            alertDialog.dismiss()
            finish() // Close the app if the user doesn't want to enable location services
        }

        alertDialog.setCancelable(false) // Disable outside touch to dismiss
        alertDialog.show()
    }

    private fun proceedWithAppLogic() {
        // Delay for 2 seconds, then start the appropriate activity
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser == null) {
                // User is not logged in
                if (PreferencesManager.isOnboardingCompleted(this)) {
                    // Navigate to sign-in screen if onboarding is completed
                    navigateToSignIn()
                } else {
                    // Navigate to onboarding screen if not completed
                    navigateToOnboarding()
                }
            } else {
                // User is already logged in
                navigateToDashboard()
            }
        }, 1000) // 2000 milliseconds = 2 seconds
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
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All required permissions are granted
                proceedWithAppLogic()
            } else {
                // Permissions are denied
                AlertDialog.Builder(this).apply {
                    setTitle("Permissions Required")
                    setMessage("Location permissions are required for this app to function properly.")
                    setPositiveButton("Grant Permissions") { _, _ ->
                        requestLocationPermissions()
                    }
                    setNegativeButton("Cancel") { _, _ ->
                        finish() // Close the app if the user doesn't want to grant permissions
                    }
                    setCancelable(false)
                }.show()
            }
        }
    }
}
