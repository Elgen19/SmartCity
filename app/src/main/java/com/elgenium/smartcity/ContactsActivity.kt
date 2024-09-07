package com.elgenium.smartcity

import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityContactsBinding
import com.elgenium.smartcity.databinding.DialogAddContactBinding
import com.elgenium.smartcity.models.Contact
import com.elgenium.smartcity.recyclerview_adapter.ContactsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var contactsAdapter: ContactsAdapter
    private val contactList = mutableListOf<Contact>()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)





        // Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        binding.rvContacts.layoutManager = layoutManager

        // Create a custom drawable with your desired color
        val dividerDrawable = ColorDrawable(ContextCompat.getColor(this, R.color.dark_gray))

        // Create the DividerItemDecoration and set the drawable
        val dividerItemDecoration = DividerItemDecoration(
            binding.rvContacts.context,
            layoutManager.orientation
        )
        dividerItemDecoration.setDrawable(dividerDrawable)

        // Add the custom divider to the RecyclerView
        binding.rvContacts.addItemDecoration(dividerItemDecoration)

        // Initialize the adapter
        contactsAdapter = ContactsAdapter(contactList) { contactId ->
            showBottomSheet(contactId)
        }
        // Set the adapter
        binding.rvContacts.adapter = contactsAdapter

        // Fetch contacts from Firebase
        fetchContactsFromFirebase()

        // Set up the Add Contact FAB button
        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        binding.backButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }
    }

    private fun showAddContactDialog() {
        // Create a dialog using the dialog_add_contact layout
        val dialog = Dialog(this)
        val dialogBinding = DialogAddContactBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set up Cancel button
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss() // Close the dialog
        }

        // Set up Save button
        dialogBinding.btnSaveContact.setOnClickListener {
            val name = dialogBinding.etContactName.text.toString().trim()
            val number = dialogBinding.etContactNumber.text.toString().trim()
            val email = dialogBinding.etContactEmail.text.toString().trim()
            val relation = dialogBinding.etContactRelation.text.toString().trim()

            if (validateContact(name, number, email, relation)) {
                // Add the contact to the Firebase Realtime Database
                saveContactToFirebase(name, number, email, relation)

                dialog.dismiss()
            }
        }

        // Adjust the size of the dialog
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)
        layoutParams.width = (resources.displayMetrics.widthPixels * 0.85).toInt() // 85% of screen width
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT // Adjust as needed
        dialog.window?.attributes = layoutParams

        // Show the dialog
        dialog.show()
    }





    private fun validateContact(name: String, number: String, email: String, relation: String): Boolean {
        // Validate name
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validate number
        val phonePattern = "^(09)\\d{9}$" // Corrected the regex pattern
        if (number.isEmpty() || !number.matches(phonePattern.toRegex())) {
            Toast.makeText(this, "Invalid phone number. Must start with '09' and be 11 digits long", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validate email
        val emailPattern = "^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$" // Corrected the regex pattern
        if (email.isNotEmpty() && !email.matches(emailPattern.toRegex())) {
            Toast.makeText(this, "Invalid email address", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validate relation
        if (relation.isEmpty()) {
            Toast.makeText(this, "Relation cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveContactToFirebase(name: String, number: String, email: String, relation: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return // Use the current user ID or exit if not signed in

        Log.d("ContactsActivity", "User ID: $userId") // Log the user ID

        val databaseReference = FirebaseDatabase.getInstance().getReference("Users/$userId/contacts")

        // Query to check if a contact with the same name or number already exists
        val numberQuery = databaseReference.orderByChild("number").equalTo(number)
        val nameQuery = databaseReference.orderByChild("name").equalTo(name)

        // Check if contact exists by number
        numberQuery.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Contact with the same number exists
                    Toast.makeText(this@ContactsActivity, "Contact with this number already exists", Toast.LENGTH_SHORT).show()
                    Log.d("ContactsActivity", "Duplicate contact found by number.")
                } else {
                    // Check if contact exists by name
                    nameQuery.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // Contact with the same name exists
                                Toast.makeText(this@ContactsActivity, "Contact with this name already exists", Toast.LENGTH_SHORT).show()
                                Log.d("ContactsActivity", "Duplicate contact found by name.")
                            } else {
                                // Save the contact if it doesn't exist
                                val contactId = databaseReference.push().key // Generate a unique key for the contact
                                val contact = Contact(contactId ?: "", name, number, email, relation)

                                Log.d("ContactsActivity", "Contact ID: $contactId") // Log the generated contact ID
                                Log.d("ContactsActivity", "Saving Contact: Name=$name, Number=$number, Email=$email, Relation=$relation") // Log the contact details

                                if (contactId != null) {
                                    databaseReference.child(contactId).setValue(contact)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                Log.d("ContactsActivity", "Contact successfully saved.") // Log success
                                                Toast.makeText(this@ContactsActivity, "Contact Saved: $name", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Log.e("ContactsActivity", "Failed to save contact", task.exception) // Log failure with exception
                                                Toast.makeText(this@ContactsActivity, "Failed to save contact", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                } else {
                                    Log.e("ContactsActivity", "Failed to generate contact ID") // Log if contact ID is null
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ContactsActivity", "Name query failed: ${error.message}") // Log query failure
                            Toast.makeText(this@ContactsActivity, "Failed to check for duplicates", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ContactsActivity", "Number query failed: ${error.message}") // Log query failure
                Toast.makeText(this@ContactsActivity, "Failed to check for duplicates", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showBottomSheet(contactId: String) {
        // Find the contact from the list
        val contact = contactList.find { it.id == contactId }

        if (contact == null) {
            // Log an error or show a message if contact is not found
            Log.e("ContactsActivity", "Contact with ID $contactId not found.")
            Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Inflate the bottom sheet layout
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_options, null)

        // Find options within the inflated layout
        val editOption: LinearLayout = bottomSheetView.findViewById(R.id.optionEdit)
        val deleteOption: LinearLayout = bottomSheetView.findViewById(R.id.optionDelete)

        // Create and set up the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Set up click listeners
        editOption.setOnClickListener {
            bottomSheetDialog.dismiss()
            // Handle edit option click
            Log.d("ContactsActivity", "Edit option clicked.")
           showEditDialog(contact)
        }

        deleteOption.setOnClickListener {
            bottomSheetDialog.dismiss()
            deleteContact(contactId)
        }

        // Show the BottomSheetDialog
        bottomSheetDialog.show()
    }

    private fun showEditDialog(contact: Contact) {
        // Inflate the dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)

        // Find views within the dialog layout
        val nameEditText: TextInputEditText = dialogView.findViewById(R.id.etContactName)
        val numberEditText: TextInputEditText = dialogView.findViewById(R.id.etContactNumber)
        val emailEditText: TextInputEditText = dialogView.findViewById(R.id.etContactEmail)
        val relationEditText: TextInputEditText = dialogView.findViewById(R.id.etContactRelation)
        val iconImageView: ImageView = dialogView.findViewById(R.id.icon)
        val titleTextView: TextView = dialogView.findViewById(R.id.label)

        // Set up button listeners within the dialog
        val btnSave: Button = dialogView.findViewById(R.id.btnSaveContact)
        val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)

        // Set the current contact's details
        nameEditText.setText(contact.name)
        numberEditText.setText(contact.number)
        emailEditText.setText(contact.email)
        relationEditText.setText(contact.relation)

        // Edit labels
        iconImageView.setImageResource(R.drawable.edit)
        iconImageView.setColorFilter(ContextCompat.getColor(this, R.color.brand_color), PorterDuff.Mode.SRC_IN)
        titleTextView.text = getString(R.string.edit_contact)
        btnSave.text = getString(R.string.update)



        // Create and set up the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()



        btnSave.setOnClickListener {
            // Save changes
            val updatedName = nameEditText.text.toString()
            val updatedNumber = numberEditText.text.toString()
            val updatedEmail = emailEditText.text.toString()
            val updatedRelation = relationEditText.text.toString()

            // Update contact in the list (you may need to handle this update with a database or another source)
            val updatedContact = contact.copy(name = updatedName, number = updatedNumber, email = updatedEmail, relation = updatedRelation)
            updateContact(contact.id, updatedContact)

            alertDialog.dismiss()
        }

        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        // Show the AlertDialog
        alertDialog.show()
    }

    private fun updateContact(contactId: String, updatedContact: Contact) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // Get reference to the specific contact node
            val contactRef = FirebaseDatabase.getInstance().reference
                .child("Users")
                .child(currentUser.uid)
                .child("contacts")
                .child(contactId)

            // Update the contact data
            contactRef.setValue(updatedContact).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("ContactsActivity", "Contact updated successfully.")
                    Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("ContactsActivity", "Failed to update contact", task.exception)
                    Toast.makeText(this, "Failed to update contact", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.e("ContactsActivity", "User is not authenticated")
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }





    private fun deleteContact(contactId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return // Use the current user ID or exit if not signed in

        val databaseReference = FirebaseDatabase.getInstance().getReference("Users/$userId/contacts")

        databaseReference.child(contactId).removeValue()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Contact Deleted", Toast.LENGTH_SHORT).show()
                    fetchContactsFromFirebase() // Refresh the contact list
                } else {
                    Toast.makeText(this, "Failed to delete contact", Toast.LENGTH_SHORT).show()
                }
            }
    }





    private fun fetchContactsFromFirebase() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return // Use the current user ID or exit if not signed in
        val databaseReference = FirebaseDatabase.getInstance().getReference("Users/$userId/contacts")

        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactList.clear()
                for (contactSnapshot in snapshot.children) {
                    val contact = contactSnapshot.getValue(Contact::class.java)
                    if (contact != null) {
                        contactList.add(contact)
                    }
                }
                contactsAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ContactsActivity, "Failed to load contacts", Toast.LENGTH_SHORT).show()
            }
        })
    }




}


