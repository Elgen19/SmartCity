package com.elgenium.smartcity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var isEditable: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference("Users")

        loadUserData()

        binding.backButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        binding.editButton.setOnClickListener {
            if (isEditable) {
                // Validate input fields when saving changes
                if (validateFields()) {
                    saveUserData()
                }
            } else {
                // Toggle edit mode
                toggleEditFields()
            }
        }

        binding.emailInfoButton.setOnClickListener {
            Toast.makeText(this, "The email address cannot be edited.", Toast.LENGTH_SHORT).show()
        }


    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userId = currentUser.uid
            databaseReference.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val fullName = snapshot.child("fullName").getValue(String::class.java)
                        val email = snapshot.child("email").getValue(String::class.java)
                        val phone = snapshot.child("phoneNumber").getValue(String::class.java)

                        binding.fullNameValue.setText(fullName)
                        binding.emailValue.setText(email)
                        binding.phoneValue.setText(phone)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle possible errors
                }
            })
        }
    }


    private fun toggleEditFields() {
        isEditable = !isEditable

        binding.fullNameValue.isEnabled = isEditable
        binding.phoneValue.isEnabled = isEditable

        if (isEditable) {
            binding.editButton.text = getString(R.string.save_changes)
            binding.fullNameValue.setTextColor(resources.getColor(R.color.gray, null))
            binding.phoneValue.setTextColor(resources.getColor(R.color.gray, null))
        } else {
            binding.editButton.text = getString(R.string.edit)
            binding.fullNameValue.setTextColor(resources.getColor(R.color.dark_gray, null))
            binding.phoneValue.setTextColor(resources.getColor(R.color.dark_gray, null))
        }
    }

    private fun validateFields(): Boolean {
        val fullName = binding.fullNameValue.text.toString().trim()
        val email = binding.emailValue.text.toString().trim()
        val phone = binding.phoneValue.text.toString().trim()

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            // Show error or toast message
            showToast("Please fill in all required fields.")
            return false
        }

        if (phone != "Not Available" && !isValidPhoneNumber(phone)) {
            // Show error or toast message
            showToast("Please enter a valid phone number or write Not Available.")
            return false
        }

        return true
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        // Check if phone number starts with "09" and is 11 digits long
        return phone.matches("^09\\d{9}$".toRegex())
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveUserData() {
        val userId = auth.currentUser?.uid ?: return
        val fullName = binding.fullNameValue.text.toString().trim()
        val email = binding.emailValue.text.toString().trim()
        val phone = binding.phoneValue.text.toString().trim()

        val userMap = mapOf(
            "fullName" to fullName,
            "email" to email,
            "phoneNumber" to phone
        )

        databaseReference.child(userId).updateChildren(userMap).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                showToast("Profile updated successfully.")
                toggleEditFields() // Switch back to non-edit mode
            } else {
                showToast("Failed to update profile.")
            }
        }
    }
}
