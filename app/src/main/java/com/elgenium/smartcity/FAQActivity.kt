package com.elgenium.smartcity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityFaqactivityBinding
import com.elgenium.smartcity.expandable_list_adapter.FAQExpandableListAdapter
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper

class FAQActivity : AppCompatActivity() {

    // ViewBinding variable
    private lateinit var binding: ActivityFaqactivityBinding
    private lateinit var expandableListAdapter: FAQExpandableListAdapter
    private lateinit var faqList: Map<String, List<String>>
    private lateinit var faqHeaders: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityFaqactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        // Prepare FAQ data
        faqHeaders = listOf(
            "How do I report an event from saved places?",
            "How can I view my reported events?",
            "How do I delete my reported event?",
            "How do I edit my reported event?",
            "Why is the event I reported not automatically posted publicly?",
            "Why can't I see any event markers on the map?",
            "Can I report an event for a place by just clicking a POI marker?",
            "I mistakenly added an image when reporting an event in the 'Add an Event' screen. Can I remove it?"
        )

        faqList = mapOf(
            faqHeaders[0] to listOf("Navigate to the 'Favorites' screen. Under the 'Saved Places' tab, long press the place you want to report an event for. Select 'Report an event on this place' from the bottom sheet. You will be redirected to the 'Add an Event' screen."),
            faqHeaders[1] to listOf("Navigate to the 'Events' screen. Tap the horizontal hamburger menu (three vertical dots icon) and select 'Show/Hide My Events' from the bottom sheet. You will be redirected to the 'My Events' screen, where you can manage your reported events."),
            faqHeaders[2] to listOf("Navigate to the 'Events' screen. Tap the horizontal hamburger menu (three vertical dots icon) and select 'Show/Hide My Events' from the bottom sheet. You will be redirected to the 'My Events' screen. Long press the event you want to delete. Select 'Delete Event' from the options bottom sheet. A confirmation alert dialog will appear. Click 'Delete' to remove the event."),
            faqHeaders[3] to listOf("Navigate to the 'Events' screen. Tap the horizontal hamburger menu (three vertical dots icon) and select 'Show/Hide My Events' from the bottom sheet. You will be redirected to the 'My Events' screen. Long press the event you want to edit. Select 'Edit Event' from the options bottom sheet. You will be redirected to the 'Edit Event' screen. After making your modifications, press 'Submit' to confirm the changes. The event will be revalidated, and you will receive a push notification with the result."),
            faqHeaders[4] to listOf("Reported events are subject to a two-step validation process. The first step checks if the event name, description, and category are related. Then, an image analysis is performed to ensure the submitted photo matches the event details. If the event passes both validations, it will be posted publicly. You will be notified via push notification about the result of the validation."),
            faqHeaders[5] to listOf("For an event to appear on the map, it must be an ongoing event (happening now or within the day) or an upcoming event scheduled for the future. If no event markers are visible, it likely means the events are in the past and are not displayed."),
            faqHeaders[6] to listOf("Yes, you can. Navigate to the 'Places' screen. Select a POI marker where you want to report an event. In the place details bottom sheet, click the 'More' button. Select 'Report an Event' from the options bottom sheet. You will be redirected to the 'Add an Event' screen."),
            faqHeaders[7] to listOf("Yes, you can. Long press the image you want to delete. A bottom sheet with options will appear. Select 'Delete Image' to remove the image.")
        )


        // Set adapter for ExpandableListView using ViewBinding
        expandableListAdapter = FAQExpandableListAdapter(this, faqList, faqHeaders)
        binding.faqExpandableList.setAdapter(expandableListAdapter)
    }
}
