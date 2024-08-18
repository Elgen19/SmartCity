package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityProfileBinding
import com.elgenium.smartcity.googlesignin.GoogleSignInClientProvider
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize Google Sign-In Client if needed
        googleSignInClient = GoogleSignInClientProvider.getGoogleSignInClient(this)

        // Set up the logout button click listener
        binding.logoutButton.setOnClickListener {
            signOut()
        }

        binding.profileCard.setOnClickListener{
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun signOut() {
        // Sign out from Firebase
        auth.signOut()

        // If the user is signed in with Google, sign them out from Google too
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // Redirect to the sign-in activity
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show()
        }
    }
}
