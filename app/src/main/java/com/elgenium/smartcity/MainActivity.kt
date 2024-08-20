package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.sharedpreferences.PreferencesManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Delay for 3 seconds, then start OnboardingScreenActivity
        Handler(Looper.getMainLooper()).postDelayed({
            // Check if onboarding has been completed
            if (PreferencesManager.isOnboardingCompleted(this)) {
                // Navigate to sign-in screen if onboarding is completed
                navigateToSignIn()
            } else {
                // Navigate to onboarding screen if not completed
                navigateToOnboarding()
            }
        }, 3000) // 3000 milliseconds = 3 seconds
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
}
