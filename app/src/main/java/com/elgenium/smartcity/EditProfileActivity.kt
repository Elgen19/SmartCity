package com.elgenium.smartcity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.elgenium.smartcity.databinding.ActivityEditProfileBinding
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var storageReference: StorageReference
    private var isEditable: Boolean = false
    private var imageUri: Uri? = null
    private val READ_EXTERNAL_STORAGE_PERMISSION = 101
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference("Users")
        storageReference = FirebaseStorage.getInstance().getReference("ProfilePictures")

        // Initialize the ActivityResultLauncher for picking images
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageUri = it
                binding.profileImageView.setImageURI(imageUri)
            }
        }

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
                imageUri?.let { uploadImageToFirebase() } // Upload image if selected
            } else {
                // Toggle edit mode
                toggleEditFields()
            }
        }

        binding.emailInfoButton.setOnClickListener {
            Toast.makeText(this, "The email address cannot be edited.", Toast.LENGTH_SHORT).show()
        }

        binding.profileImageView.setOnClickListener {
            if (isEditable) {
                requestStoragePermission()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Define the behavior when the back button is pressed
                val intent = Intent(this@EditProfileActivity, ProfileActivity::class.java)
                startActivity(intent)
                finish() // This will finish the current activity
            }
        })
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
                        val profilePicURL = snapshot.child("profilePicUrl").getValue(String::class.java)

                        binding.fullNameValue.setText(fullName)
                        binding.emailValue.setText(email)
                        binding.phoneValue.setText(phone)

                        // Use Glide to load the image from the URL
                        Glide.with(this@EditProfileActivity)
                            .load(profilePicURL)
                            .placeholder(R.drawable.female) // Optional placeholder image
                            .error(R.drawable.error_image) // Optional error image
                            .into(binding.profileImageView)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle possible errors
                    Toast.makeText(this@EditProfileActivity, "Failed to load user data.", Toast.LENGTH_SHORT).show()
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
            showToast("Please fill in all required fields.")
            return false
        }

        if (phone != "Not Available" && !isValidPhoneNumber(phone)) {
            showToast("Please enter a valid phone number or write Not Available.")
            return false
        }

        return true
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
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

    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use new permissions for Android 11 and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    READ_EXTERNAL_STORAGE_PERMISSION)
            } else {
                openImagePicker()
            }
        } else {
            // Use legacy permission for Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    READ_EXTERNAL_STORAGE_PERMISSION)
            } else {
                openImagePicker()
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                showToast("Permission Denied")
            }
        }
    }



    private fun uploadImageToFirebase() {
        imageUri?.let { uri ->
            val userId = auth.currentUser?.uid ?: return
            val fileRef = storageReference.child("$userId.jpg")

            fileRef.putFile(uri).addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Save the download URL to the database
                    databaseReference.child(userId).child("profilePicUrl").setValue(downloadUri.toString())
                    showToast("Profile picture updated.")
                }
            }.addOnFailureListener {
                showToast("Failed to upload image.")
            }
        }
    }
}
