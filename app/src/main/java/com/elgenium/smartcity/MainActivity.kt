package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Delay for 3 seconds, then start OnboardingScreenActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, OnboardingScreenActivity::class.java)
            startActivity(intent)
            finish() // Finish the current activity to prevent the user from going back to the splash screen
        }, 3000) // 3000 milliseconds = 3 seconds
    }
}
