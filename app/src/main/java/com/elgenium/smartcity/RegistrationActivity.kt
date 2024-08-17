package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityRegistrationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference.child("Users")

        binding.registerButton.setOnClickListener {
            val fullName = binding.fullnameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val phoneNumber = binding.phoneNumberEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || phoneNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isPhoneNumberValid(phoneNumber)) {
                Toast.makeText(this, "Phone number must be 11 digits.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isEmailValid(email)) {
                Toast.makeText(this, "Email address is not valid or starts with a capital letter.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable all EditText fields and the register button
            disableInputs()
            binding.registerButton.isEnabled = false
            binding.registerButton.text = getString(R.string.registering)
            registerUser(fullName, email, phoneNumber, password)
        }

        binding.signInTextView.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }
    }

    private fun registerUser(fullName: String, email: String, phoneNumber: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: ""
                    saveUserToDatabase(userId, fullName, email, phoneNumber)
                } else {
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    // Re-enable inputs and button
                    enableInputs()
                    binding.registerButton.isEnabled = true
                    binding.registerButton.text = getString(R.string.register)
                }
            }
    }

    private fun isPhoneNumberValid(phoneNumber: String): Boolean {
        return phoneNumber.length == 11 && phoneNumber.all { it.isDigit() }
    }

    private fun isEmailValid(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches() && email.firstOrNull()?.isLowerCase() == true
    }

    private fun disableInputs() {
        binding.fullnameEditText.isEnabled = false
        binding.emailEditText.isEnabled = false
        binding.phoneNumberEditText.isEnabled = false
        binding.passwordEditText.isEnabled = false
        binding.confirmPasswordEditText.isEnabled = false
    }

    private fun enableInputs() {
        binding.fullnameEditText.isEnabled = true
        binding.emailEditText.isEnabled = true
        binding.phoneNumberEditText.isEnabled = true
        binding.passwordEditText.isEnabled = true
        binding.confirmPasswordEditText.isEnabled = true
    }

    private fun saveUserToDatabase(userId: String, fullName: String, email: String, phoneNumber: String) {
        val userMap = mapOf(
            "fullName" to fullName,
            "email" to email,
            "phoneNumber" to phoneNumber
        )

        database.child(userId).setValue(userMap)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                    // Start SignInActivity
                    val intent = Intent(this, SignInActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish() // Close the registration activity
                } else {
                    Toast.makeText(this, "Failed to save user data: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    binding.registerButton.isEnabled = true
                    binding.registerButton.text = getString(R.string.register)
                }
            }
    }

}