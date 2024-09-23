package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.databinding.ItemRecentSearchBinding
import com.elgenium.smartcity.models.Search

class RecentSearchAdapter(
    private val recentSearches: List<Search>,
    private val onItemClick: (Search) -> Unit
) : RecyclerView.Adapter<RecentSearchAdapter.RecentSearchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        val binding = ItemRecentSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentSearchViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) {
        holder.bind(recentSearches[position])
    }

    override fun getItemCount(): Int = recentSearches.size

    class RecentSearchViewHolder(
        private val binding: ItemRecentSearchBinding,
        private val onItemClick: (Search) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentSearch: Search? = null

        init {
            binding.root.setOnClickListener {
                currentSearch?.let { search -> onItemClick(search) }
            }
        }

        fun bind(recentSearch: Search) {
            currentSearch = recentSearch
            binding.locationTextView.text = recentSearch.placeName
            binding.placeAddressTextView.text = recentSearch.placeAddress
            binding.timestampTextView.text = recentSearch.timestamp
        }
    }
}
