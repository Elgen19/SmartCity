package com.elgenium.smartcity


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityOnboardingScreenBinding
import com.elgenium.smartcity.onboarding_adapter.OnboardingAdapter
import com.elgenium.smartcity.models.OnboardingItem


class OnboardingScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingScreenBinding
    private lateinit var onboardingAdapter: OnboardingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onboardingAdapter = OnboardingAdapter(
            onboardingItems = getOnboardingItems(),
            viewPager = binding.viewPager,
            context = this
        )

        binding.viewPager.adapter = onboardingAdapter
    }

    private fun getOnboardingItems(): List<OnboardingItem> {
        return listOf(
            OnboardingItem(R.drawable.smart_city_logo_light, R.raw.onboarding_one_lotify, "Effortless City Navigation", "Navigate your city with ease using real-time traffic updates, turn-by-turn directions, and multi-modal transport options. Say goodbye to congestion and hello to faster, smarter routes."),
            OnboardingItem(R.drawable.smart_city_logo_light, R.raw.onboarding_two_lotify, "Discover and Explore", "Find the best places around you, from restaurants to events. Get personalized recommendations tailored to your preferences and stay updated on what's happening in your city."),
            OnboardingItem(R.drawable.smart_city_logo_light, R.raw.onboarding_three_lotify, "Engage with Your Community", "Report and discover local events and issues. Earn points, climb leaderboards, and contribute to making your city a better place by staying informed and engaged.")
        )
    }
}
