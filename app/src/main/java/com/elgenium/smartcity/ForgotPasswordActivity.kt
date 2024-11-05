package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityForgotPasswordBinding
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        auth = FirebaseAuth.getInstance()



        binding.submitButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()

            if (email.isEmpty()) {
                binding.emailInputLayout.error = "Please enter your email address."
            } else {
                binding.emailInputLayout.error = null
                resetPassword(email)
            }
        }
    }

    private fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, SignInActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
