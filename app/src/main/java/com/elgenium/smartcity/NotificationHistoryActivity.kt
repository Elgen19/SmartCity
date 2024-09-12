package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityNotificationHistoryBinding
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize View Binding
        binding = ActivityNotificationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Set up the back button to navigate to DashboardActivity
        binding.backButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()  // Optional: call finish() if you want to remove this activity from the back stack
        }

    }
}