package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.ItemLeaderboardBinding
import com.elgenium.smartcity.models.Leaderboard

class LeaderboardAdapter(private val leaderboardList: List<Leaderboard>) :
    RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    // ViewHolder class using View Binding
    class LeaderboardViewHolder(val binding: ItemLeaderboardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        // Inflate the layout using View Binding
        val binding = ItemLeaderboardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val leaderboardItem = leaderboardList[position]

        with(holder.binding) {
            // Set the user's name
            nameTextView.text = leaderboardItem.name

            // Set the user's points
            pointsTextView.text = "Points: ${leaderboardItem.points}"

            // Load the profile image using Glide
            Glide.with(profileImageView.context)
                .load(leaderboardItem.profileImageUrl ?: R.drawable.male)
                .placeholder(R.drawable.male) // Default image if URL is null
                .circleCrop()
                .into(profileImageView)

            // Show trophy image for top 3 ranks and hide the rank text view
            when (position) {
                0 -> {
                    trophyImageview.visibility = View.VISIBLE
                    rankTextView.visibility = View.GONE
                    trophyImageview.setImageResource(R.drawable.trophy)
                }
                1 -> {
                    trophyImageview.visibility = View.VISIBLE
                    rankTextView.visibility = View.GONE
                    trophyImageview.setImageResource(R.drawable.silver)
                }
                2 -> {
                    trophyImageview.visibility = View.VISIBLE
                    rankTextView.visibility = View.GONE
                    trophyImageview.setImageResource(R.drawable.bronze)

                }
                else -> {
                    trophyImageview.visibility = View.GONE
                    rankTextView.visibility = View.VISIBLE
                    rankTextView.text = (position + 1).toString() // Display rank number
                    rankTextView.setBackgroundColor(ContextCompat.getColor(root.context, R.color.black)) // Default background color
                }
            }

            // Animation for item
            val fadeInAnimation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.fade_in)
            holder.itemView.startAnimation(fadeInAnimation)
        }
    }


    override fun getItemCount(): Int = leaderboardList.size
}
