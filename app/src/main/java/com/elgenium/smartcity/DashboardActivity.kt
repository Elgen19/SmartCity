package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.elgenium.smartcity.databinding.ActivityDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        binding.notificationButton.setOnClickListener {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }

        updateGreeting()
        loadProfileImage()
        setupListeners()
    }

    private fun setupListeners() {
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
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun updateGreeting() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour == 12 -> getString(R.string.good_noon)
            currentHour < 12 -> getString(R.string.good_morning)
            currentHour < 17 -> getString(R.string.good_afternoon)
            else -> getString(R.string.good_evening)
        }
        binding.greetingText.text = greeting
    }

    private fun loadProfileImage() {
        val userId = auth.currentUser?.uid ?: return
        database.child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val photoUrl = snapshot.child("profilePicUrl").getValue(String::class.java)
                val fullName = snapshot.child("fullName").getValue(String::class.java)
                val firstName = fullName?.split(" ")?.firstOrNull() ?: "User"

                if (photoUrl != null) {
                    // Load the profile picture using Glide or Picasso
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.female) // Use a placeholder if needed
                        .into(binding.profileImage)
                }
                // Set the first name in the user name text view
                binding.userNameText.text = firstName

            } else {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load profile picture", Toast.LENGTH_SHORT).show()
        }
    }
}
