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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.sharedpreferences.PreferencesManager
import com.elgenium.smartcity.singletons.GoogleSignInClientProvider
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private lateinit var googleSignInClient: GoogleSignInClient
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize Google Sign-In Client if needed
        googleSignInClient = GoogleSignInClientProvider.getGoogleSignInClient(this)

        // Initial check for location services and permissions
        checkLocationAndPermissions()

    }

    override fun onResume() {
        super.onResume()
        Toast.makeText(this, "YES IT SHOWS", Toast.LENGTH_SHORT).show()
        Toast.makeText(this, "IS LOCATION ENABLED: ${isLocationEnabled()}", Toast.LENGTH_SHORT).show()

        if (isLocationEnabled())
            proceedWithAppLogic()

    }


    private fun checkLocationAndPermissions() {
        if (isLocationEnabled()) {
            if (isLocationPermissionGranted()) {
                alertDialog?.dismiss()
                proceedWithAppLogic()
                Toast.makeText(this, "YES IT SHOWS", Toast.LENGTH_SHORT).show()
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
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser == null) {
                // User is not logged in
                if (PreferencesManager.isOnboardingCompleted(this)) {
                    navigateToSignIn()
                } else {
                    navigateToOnboarding()
                }
            } else {
                // Check Firebase authentication state and refresh token if needed
                checkFirebaseAuthState()
            }
        }, 1000) // 1000 milliseconds = 1 second
    }


    private fun checkFirebaseAuthState() {
        val currentUser = auth.currentUser
        currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Token is refreshed, proceed to dashboard
                navigateToDashboard()
            } else {
                // Handle the specific error
                if (task.exception?.message?.contains("BAD_AUTHENTICATION") == true) {
                    // Force sign-out and redirect to sign-in
                   signOut()
                } else {
                    // Handle other potential exceptions
                    Log.e("MainActivity", "FirebaseAuth token error: ${task.exception?.message}")
                }
            }
        } ?: run {
            // If currentUser is null, navigate to sign-in screen
            navigateToSignIn()
        }
    }

    private fun signOut() {
        // Sign out from Firebase
        auth.signOut()

        // Sign out from Google
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // After sign out, you can redirect to the sign-in screen
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
        val intent = Intent(this, DashboardActivity::class.java)
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
                proceedWithAppLogic()
            } else {
                Toast.makeText(this, "Please enable location permission to use the app", Toast.LENGTH_LONG).show()
            }
        }
    }

}
