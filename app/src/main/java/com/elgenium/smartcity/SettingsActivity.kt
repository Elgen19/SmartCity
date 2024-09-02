package com.elgenium.smartcity

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.selectedItemId = R.id.navigation_settings

        // Set up BottomNavigationView
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, DashboardActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                    true
                }
                R.id.navigation_places -> {
                    // Handle Places action
                    val intent = Intent(this, PlacesActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
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
