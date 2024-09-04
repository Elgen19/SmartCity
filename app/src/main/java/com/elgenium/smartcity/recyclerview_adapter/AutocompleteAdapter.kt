package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.google.android.libraries.places.api.model.AutocompletePrediction

// In AutocompleteAdapter.kt
class AutocompleteAdapter(
    private var suggestions: List<AutocompletePrediction>,
    private val onItemClick: (AutocompletePrediction) -> Unit // Click listener callback
) : RecyclerView.Adapter<AutocompleteAdapter.ViewHolder>() {

    // Define a ViewHolder to hold references to the views in each item
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.autocomplete_item_text)

        init {
            // Set up the click listener
            itemView.setOnClickListener {
                // Use the callback to handle item click
                onItemClick(suggestions[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.autocomplete_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = suggestions[position].getPrimaryText(null)
    }

    override fun getItemCount(): Int = suggestions.size

    fun updatePredictions(newSuggestions: List<AutocompletePrediction>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }
}

