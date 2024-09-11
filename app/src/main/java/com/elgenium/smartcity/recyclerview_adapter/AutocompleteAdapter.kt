package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.google.android.libraries.places.api.model.AutocompletePrediction

class AutocompleteAdapter(
    private var predictions: List<AutocompletePrediction>,
    private val onItemClick: (AutocompletePrediction) -> Unit
) : RecyclerView.Adapter<AutocompleteAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val primaryText: TextView = itemView.findViewById(R.id.autocomplete_item_primary_text)
        private val secondaryText: TextView = itemView.findViewById(R.id.autocomplete_item_secondary_text)

        fun bind(prediction: AutocompletePrediction) {
            primaryText.text = prediction.getPrimaryText(null).toString()
            secondaryText.text = prediction.getSecondaryText(null).toString()

            itemView.setOnClickListener {
                onItemClick(prediction)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.autocomplete_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(predictions[position])
    }

    override fun getItemCount(): Int = predictions.size

    fun updatePredictions(newPredictions: List<AutocompletePrediction>) {
        predictions = newPredictions
        notifyDataSetChanged()
    }
}



