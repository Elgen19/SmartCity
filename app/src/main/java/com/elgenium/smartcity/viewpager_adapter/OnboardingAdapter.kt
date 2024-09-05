package com.elgenium.smartcity.viewpager_adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.OnboardingScreenActivity
import com.elgenium.smartcity.R
import com.elgenium.smartcity.SignInActivity
import com.elgenium.smartcity.databinding.ItemOnboardingBinding
import com.elgenium.smartcity.models.OnboardingItem
import com.elgenium.smartcity.sharedpreferences.PreferencesManager

class OnboardingAdapter(
    private val onboardingItems: List<OnboardingItem>,
    private val viewPager: ViewPager2,
    private val context: Context
) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(val binding: ItemOnboardingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OnboardingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val onboardingItem = onboardingItems[position]
        holder.binding.logoImageView.setImageResource(onboardingItem.logo)
        holder.binding.lottieAnimation.setAnimation(onboardingItem.featureAnimation)
        holder.binding.titleTextView.text = onboardingItem.title
        holder.binding.descriptionTextView.text = onboardingItem.description

        // Update progress dots
        updateProgressDots(holder, position)

        // Handle Next button click
        holder.binding.nextButton.setOnClickListener {
            if (position + 1 < onboardingItems.size) {
                viewPager.currentItem = position + 1
            } else {
                navigateToSignIn()
            }
        }

        // Handle Skip button click
        holder.binding.skipButton.setOnClickListener {
                navigateToSignIn()
        }

        // Update the text of the Next button when on the last page
        if (position == onboardingItems.size - 1) {
            holder.binding.nextButton.text = context.getString(R.string.get_started)
            holder.binding.skipButton.visibility = View.GONE
        } else {
            holder.binding.nextButton.text = context.getString(R.string.next)
        }
    }


    override fun getItemCount() = onboardingItems.size

    private fun updateProgressDots(holder: OnboardingViewHolder, position: Int) {
        val dotViews = listOf(
            holder.binding.progressIndicator.getChildAt(0),
            holder.binding.progressIndicator.getChildAt(1),
            holder.binding.progressIndicator.getChildAt(2)
        )

        for (i in dotViews.indices) {
            if (i == position) {
                dotViews[i].setBackgroundResource(R.drawable.indicator_active)
            } else {
                dotViews[i].setBackgroundResource(R.drawable.indicator_inactive)
            }
        }
    }

    private fun navigateToSignIn() {
        // Set the flag that onboarding is completed
        PreferencesManager.setOnboardingCompleted(context, true)

        val intent = Intent(context, SignInActivity::class.java)
        context.startActivity(intent)
        if (context is OnboardingScreenActivity) {
            context.finish()
        }
    }
}
