package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.profileImage.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Handle Home action
                    true
                }
                R.id.navigation_places -> {
                    // Handle Places action
                    true
                }
                R.id.navigation_favorites -> {
                    // Handle Favorites action
                    true
                }
                R.id.navigation_events -> {
                    // Handle Events action
                    true
                }
                R.id.navigation_settings -> {
                    // Handle Settings action
                    true
                }
                else -> false
            }
        }
    }

}
