package com.elgenium.smartcity

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.elgenium.smartcity.databinding.ActivityFeedbackBinding
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch


class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)


        // Initialize the feedback form
        setupFeedbackForm()
    }

    private fun setupFeedbackForm() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.feedback_type_options,
            R.layout.item_spinner
        ).apply {
            setDropDownViewResource(R.layout.item_spinner)
        }

        binding.spFeedbackType.adapter = adapter

        // Handle Feedback Type Selection
        binding.spFeedbackType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, selectedItemView: View?, position: Int, id: Long) {
                val selectedFeedbackType = parentView.getItemAtPosition(position).toString()
                updateChipsForFeedbackType(selectedFeedbackType)
            }

            override fun onNothingSelected(parentView: AdapterView<*>) {
                // Handle when nothing is selected
            }
        }


        // Handle Submit Button Click
        binding.btnSubmitFeedback.setOnClickListener {
            val rating = binding.rbFeedbackRating.rating.toInt()
            val comment = binding.etFeedbackComment.text.toString()
            val feedbackType = binding.spFeedbackType.selectedItem.toString()

            // Validate Spinner (Feedback Type)
            if (feedbackType == "Select feedback type") {
                Toast.makeText(this, "Please select a feedback type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate EditText (Comment)
            if (comment.isBlank()) {
                Toast.makeText(this, "Please provide a comment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Rating (Optional but added for completeness)
            if (rating == 0) {
                Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Send the feedback data to Firebase or backend
            submitFeedback(rating, feedbackType, comment)
        }

        // Handle Cancel Button Click
        binding.btnCancelFeedback.setOnClickListener {
            finish() // Close the feedback form
        }
    }

    private fun updateChipsForFeedbackType(feedbackType: String) {
        // Clear existing chips
        binding.chipGroupFeedback.removeAllViews()

        // Define chip texts for each feedback type
        val chips = when (feedbackType) {
            "Bug Report" -> arrayOf(
                "The app crashes unexpectedly",
                "There are UI issues that need fixing",
                "Certain features are not working properly",
                "The app is performing slowly"
            )
            "Feature Suggestion" -> arrayOf(
                "A new UI design would improve user experience",
                "Adding a dark mode would be great",
                "Offline functionality would make the app more versatile",
                "Add more personalization options"
            )
            "General Feedback" -> arrayOf(
                "I love using the app",
                "The app needs some improvements",
                "It’s really easy to use",
                "The app is great but it has some issues"
            )
            else -> emptyArray()
        }


        // Dynamically add chips to ChipGroup
        chips.forEach { chipText ->
            val chip = Chip(this).apply {
                text = chipText
                isCheckable = true
                isClickable = true
                isFocusable = true

                setChipBackgroundColorResource(R.color.white)
                setTextColor(ContextCompat.getColor(context, R.color.gray))
                setOnClickListener {
                    // When a chip is clicked, populate the comment EditText
                    binding.etFeedbackComment.setText(chipText)
                }
            }



            // Add the chip to the ChipGroup
            binding.chipGroupFeedback.addView(chip)
        }
    }


    private fun submitFeedback(rating: Int, feedbackType: String, comment: String) {
        // Use the GenerativeModel to determine the tone of the feedback
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_AI_API
        )

        // Launch a coroutine to handle the async operation
        lifecycleScope.launch {
            val prompt = "Classify the following comment as Positive Feedback or Negative Feedback. Display only either Positive Feedback or Negative Feedback. DO NOT ADD ANY EXPLANATION OR REASONING JUST THOSE WORDS ONLY: \"$comment\""

            try {
                // Call the generateContent method within the coroutine
                val response = generativeModel.generateContent(prompt)

                // Get the tone from the response
                val tone = response.text?.trim() // Expecting either "Positive Feedback" or "Negative Feedback"

                // Now prepare the feedback data including the tone
                val feedbackRef = FirebaseDatabase.getInstance().getReference("Feedback")
                val feedbackId = feedbackRef.push().key ?: return@launch

                val feedbackData = mapOf(
                    "userID" to FirebaseAuth.getInstance().currentUser?.uid,
                    "rating" to rating, //from 1 to 5
                    "type" to feedbackType,
                    "comment" to comment,
                    "tone" to tone, // Determin if feedback is positive or negative
                    "timestamp" to ServerValue.TIMESTAMP,
                    "profilePic" to FirebaseAuth.getInstance().currentUser?.photoUrl.toString(),
                    "name" to FirebaseAuth.getInstance().currentUser?.displayName.toString()
                )

                feedbackRef.child(feedbackId).setValue(feedbackData)
                    .addOnSuccessListener {
                        setupNotification()
                        LayoutStateManager.showSuccessLayout(this@FeedbackActivity, "Feedback Submitted!", "Thank you very much for your feedback! Your insights will help developers improve the application.")
                    }
                    .addOnFailureListener { e ->
                        LayoutStateManager.showFailureLayout(this@FeedbackActivity, "Failed to submit feedback. Check your network connection and try again.", "Return to Settings")
                        Log.e("Feedback Activity", "Failed feedback:", e)
                    }
            } catch (e: Exception) {
                Log.e("FeedbackActivity", "Error generating content", e)
                Toast.makeText(this@FeedbackActivity, "Failed to determine feedback tone. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun setupNotification() {
        val notificationId = System.currentTimeMillis() // Unique ID based on timestamp
        val notificationTitle = "Feedback Submitted"
        val notificationMessage = "Thank you for your feedback! Your input helps us improve the app."
        val notificationData = mapOf(
            "id" to notificationId,
            "title" to notificationTitle,
            "message" to notificationMessage,
            "timestamp" to ServerValue.TIMESTAMP // For sorting or filtering by date
        )

        saveNotificationToDatabase(notificationData)
    }

    private fun saveNotificationToDatabase(notificationData: Map<String, Any?>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notificationsRef = FirebaseDatabase.getInstance().getReference("Users/$userId/Notifications")
        val notificationId = notificationData["id"] as Long
        notificationsRef.child(notificationId.toString()).setValue(notificationData)
            .addOnSuccessListener {
                Log.d("FeedbackActivity", "Notification saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FeedbackActivity", "Failed to save notification", e)
            }
    }
}
