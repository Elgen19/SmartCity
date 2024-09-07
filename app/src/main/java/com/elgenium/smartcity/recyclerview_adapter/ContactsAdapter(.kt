package com.elgenium.smartcity.recyclerview_adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.Contact

class ContactsAdapter(
    private val contacts: List<Contact>,
    private val onContactLongClick: (String) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.contactName)
        private val numberTextView: TextView = itemView.findViewById(R.id.contactNumber)
        private val emailTextView: TextView = itemView.findViewById(R.id.contactEmail)
        private val relationTextView: TextView = itemView.findViewById(R.id.contactRelation)

        fun bind(contact: Contact) {
            nameTextView.text = contact.name
            numberTextView.text = contact.number
            emailTextView.text = contact.email
            relationTextView.text = contact.relation

            itemView.setOnLongClickListener {
                Log.d("ContactsAdapter", "Item long clicked: ${contact.name}")

                onContactLongClick(contact.id)
                true
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount(): Int = contacts.size
}
