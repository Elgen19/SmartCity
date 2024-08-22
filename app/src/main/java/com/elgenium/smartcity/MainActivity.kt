package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.sharedpreferences.PreferencesManager
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Delay for 3 seconds, then start the appropriate activity
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser != null) {
                // User is already logged in
                navigateToDashboard()
            } else {
                // Check if onboarding has been completed
                if (PreferencesManager.isOnboardingCompleted(this)) {
                    // Navigate to sign-in screen if onboarding is completed
                    navigateToSignIn()
                } else {
                    // Navigate to onboarding screen if not completed
                    navigateToOnboarding()
                }
            }
        }, 2000) // 3000 milliseconds = 3 seconds
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, DashboardActivity::class.java)
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
}
