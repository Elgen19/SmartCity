package com.elgenium.smartcity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityFaqactivityBinding
import com.elgenium.smartcity.expandable_list_adapter.FAQExpandableListAdapter

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

        // Prepare FAQ data
        faqHeaders = listOf(
            "How do I report an issue?",
            "How do I update my profile?",
            "How do I reset my password?"
        )

        faqList = mapOf(
            faqHeaders[0] to listOf("Go to the 'Report' section and provide the details."),
            faqHeaders[1] to listOf("Visit the 'Profile' section and click on 'Edit'."),
            faqHeaders[2] to listOf("You can reset your password via the 'Forgot Password' link on the login page.")
        )

        // Set adapter for ExpandableListView using ViewBinding
        expandableListAdapter = FAQExpandableListAdapter(this, faqList, faqHeaders)
        binding.faqExpandableList.setAdapter(expandableListAdapter)
    }
}
