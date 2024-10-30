package com.elgenium.smartcity.contextuals

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityDashboardBinding
import com.elgenium.smartcity.models.Event
import com.elgenium.smartcity.recyclerview_adapter.RecommendedEventAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.math.ln
import kotlin.math.sqrt

class EventRecommendation(private val auth: FirebaseAuth, private val context: Context) {

    private var hasEventsMatchUserPreferences = true

    fun fetchUserPreferencesAndEvents(binding: ActivityDashboardBinding) {
        getUserPreferences(
            onSuccess = { userPreferences ->
                Log.d("DashboardActivity", "User Preferences: $userPreferences")
                getEventData(
                    onSuccess = { events ->
                        Log.e("DashboardActivity", "Events fetched: ${events.size}")
                        Log.e("DashboardActivity", "Events all: $events")

                        val recommendedEvents = recommendEvents(events, userPreferences)
                        val eventAdapter = RecommendedEventAdapter(recommendedEvents)

                        // Set up the adapter with recommended events
                        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        binding.recyclerViewEvents.adapter = eventAdapter

                    },
                    onFailure = { exception ->
                        Log.e("DashboardActivity", "Failed to fetch events: ${exception.message}")
                    }
                )
            },
            onFailure = { exception ->
                Log.e("DashboardActivity", "Failed to fetch user preferences: ${exception.message}")
            }
        )
    }




    private fun getUserPreferences(onSuccess: (List<String>) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: "NO USER ID"
        val database = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("preferredEvents")
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val preferences = mutableListOf<String>()
                for (snapshot in dataSnapshot.children) {
                    val preference = snapshot.getValue(String::class.java)
                    preference?.let { preferences.add(it) }
                }
                onSuccess(preferences)
                Log.e("DashboardActivity", "PREFERENCES: $preferences")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                onFailure(databaseError.toException())
            }
        })
    }

    private fun getEventData(onSuccess: (List<Event>) -> Unit, onFailure: (Exception) -> Unit) {
        val database = FirebaseDatabase.getInstance().getReference("Events")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val events = mutableListOf<Event>()
                for (snapshot in dataSnapshot.children) {
                    val event = snapshot.getValue(Event::class.java)
                    event?.let { events.add(it) }
                }
                onSuccess(events)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                onFailure(databaseError.toException())
            }
        })
    }


    private fun expandVector(userVector: Map<String, Double>, synonyms: Map<String, List<String>>): Map<String, Double> {
        val expandedVector = userVector.toMutableMap()

        userVector.forEach { (key, weight) ->
            synonyms[key]?.forEach { synonym ->
                expandedVector[synonym] = expandedVector.getOrDefault(synonym, 0.0) + (weight * 0.5) // Add 50% weight to synonyms
            }
        }

        return expandedVector
    }

    private fun containsPartialMatch(text: String?, keyword: String): Boolean {
        if (text.isNullOrEmpty() || keyword.isEmpty()) return false

        // Trim whitespace and check for exact or partial match
        val trimmedText = text.trim()
        val matches = trimmedText.contains(keyword.trim(), ignoreCase = true)

        // Debugging log
        Log.d("DashboardActivity", "Text: '$trimmedText', Keyword: '$keyword', Matches Found: $matches")

        return matches
    }

    private fun recommendEvents(
        events: List<Event>,
        userPreferences: List<String>,
        topN: Int = 5
    ): List<Event> {
        val eventSynonyms = mapOf(
            "Concerts & Live Performances" to listOf(
                "concert", "live performance", "music event", "gig", "band",
                "musical", "show", "musical performance", "live show",
                "orchestra", "symphony"
            ),
            "Festivals & Celebrations" to listOf(
                "festival", "celebration", "carnival", "fair", "parade",
                "cultural festival", "local festival", "seasonal festival",
                "block party", "holiday celebration", "community festival",
                "street fair", "food festival"
            ),
            "Sales & Promotions" to listOf(
                "sale", "promotion", "discount", "clearance", "deal",
                "bargain", "offer", "markdown", "limited time offer",
                "price drop", "special offer", "sale event", "flash sale"
            ),
            "Workshops & Seminars" to listOf(
                "workshop", "seminar", "class", "course", "training",
                "learning session", "educational event", "panel discussion",
                "lecture", "skills training", "hands-on workshop"
            ),
            "Community Events" to listOf(
                "community event", "local event", "neighborhood gathering",
                "public gathering", "town hall meeting", "meetup",
                "social event", "charity event", "volunteer opportunity",
                "outreach event", "community outreach"
            ),
            "Outdoor & Adventure Events" to listOf(
                "outdoor event", "adventure", "hiking", "camping", "outdoor",
                "nature walk", "exploration", "trail running",
                "rock climbing", "picnic", "outdoor concert",
                "scavenger hunt", "sports event"
            )
        )

        // Step 2: Apply the filter to events based on user preferences with logging
        val filteredEvents = events.filter { event ->
            userPreferences.any { preference ->
                // Use equals for category matching to ensure exact match
                val categoryMatches = event.eventCategory?.equals(preference, ignoreCase = true) == true

                // Check for matches in synonyms, description, and name
                val descriptionMatches = containsPartialMatch(event.eventDescription, preference)
                val nameMatches = containsPartialMatch(event.eventName, preference)

                // Log the match results
                Log.d("DashboardActivity", "Checking event: ${event.eventName}, Category: ${event.eventCategory}, User Preference: $preference")
                Log.d("DashboardActivity", "categoryMatches: $categoryMatches, descriptionMatches: $descriptionMatches, nameMatches: $nameMatches")

                // Return true if any of the matches are true
                categoryMatches || descriptionMatches || nameMatches
            }
        }

        // Debugging log to show the filtered result count
        Log.d("DashboardActivity", "Filtered Events: ${filteredEvents.size}")

        // Log the filtered events
        Log.d("DashboardActivity", "Filtered Events: ${filteredEvents.map { it.eventName }}")

        // If no events match preferences, return empty list
        // Return default recommendations if no events match preferences
        if (filteredEvents.isEmpty()) {
            Log.d("DashboardActivity", "No events found matching user preferences.")
            hasEventsMatchUserPreferences = false
            return events.take(topN) // Return top N default events if no matches are found
        }

        val tfIdfScores = mutableMapOf<String, MutableMap<String, Double>>() // eventId -> (word -> score)

        // Step 2: Build the term frequency for each filtered event
        filteredEvents.forEach { event ->
            val words = extractWords(event)
            Log.d("DashboardActivity", "Event: ${event.eventName}, Words: $words") // Log event words

            // Check if event.checker is not null
            val checkerKey = event.checker ?: return@forEach // Skip if checker is null
            if (tfIdfScores[checkerKey] == null) {
                tfIdfScores[checkerKey] = mutableMapOf()
            }

            words.forEach { word ->
                tfIdfScores[checkerKey]!![word] = tfIdfScores[checkerKey]!!.getOrDefault(word, 0.0) + 1.0
            }
        }

        // Step 3: Calculate TF-IDF scores
        tfIdfScores.forEach { (eventId, wordMap) ->
            val totalWords = wordMap.values.sum()
            wordMap.forEach { (word, count) ->
                val tf = count / totalWords
                val idf = calculateIdf(word, filteredEvents) // Pass filteredEvents for IDF calculation
                // Only set TF-IDF if IDF is not zero
                if (idf > 0) {
                    wordMap[word] = tf * idf
                    Log.d("DashboardActivity", "Event ID: $eventId, Word: $word, TF: $tf, IDF: $idf, TF-IDF: ${tf * idf}") // Log TF-IDF scores
                } else {
                    wordMap[word] = 0.0
                    Log.d("DashboardActivity", "Event ID: $eventId, Word: $word, TF: $tf, IDF: $idf, TF-IDF: 0.0 (IDF is zero)") // Log zero TF-IDF
                }
            }
        }

        // Step 4: Expand the user vector with synonyms
        val expandedUserVector = expandVector(userPreferences.groupingBy { it }.eachCount().mapValues { it.value.toDouble() }, eventSynonyms)

        // Step 5: Calculate cosine similarity between user preferences and filtered events
        val recommendations = mutableListOf<Pair<Event, Double>>() // event -> similarity score
        filteredEvents.forEach { event ->
            val cosineSimilarity = calculateCosineSimilarity(tfIdfScores[event.checker], expandedUserVector)
            recommendations.add(Pair(event, cosineSimilarity))
            Log.d("DashboardActivity", "Event: ${event.eventName}, Cosine Similarity: $cosineSimilarity") // Log cosine similarity
        }

        // Step 6: Sort recommendations by similarity and return top N
        val topRecommendations = recommendations.sortedByDescending { it.second }.take(topN).map { it.first }
        Log.d("DashboardActivity", "Top $topN Recommendations: ${topRecommendations.map { it.eventName }}") // Log top recommendations

        return topRecommendations
    }

    fun hasMatchingEventsToUserPreferences(): Boolean {
        return hasEventsMatchUserPreferences
    }


    private fun extractWords(event: Event): List<String> {
        val words = mutableListOf<String>()
        event.eventName?.let { words.addAll(it.split("\\W+".toRegex())) }
        event.eventDescription?.let { words.addAll(it.split("\\W+".toRegex())) }
        return words.map { it.lowercase() } // Normalize to lowercase
    }


    private fun calculateIdf(word: String, events: List<Event>): Double {
        val totalEvents = events.size.toDouble()
        val count = events.count { event -> extractWords(event).contains(word.lowercase()) }.toDouble()

        // Use a smoothing constant to prevent division by zero and extreme values
        val idf = ln((totalEvents + 1) / (count + 1))
        Log.d("DashboardActivity", "Word: $word, IDF: $idf") // Log IDF value
        return idf
    }


    private fun calculateCosineSimilarity(eventScores: Map<String, Double>?, userVector: Map<String, Double>): Double {
        if (eventScores == null) return 0.0

        val dotProduct = eventScores.keys.intersect(userVector.keys).sumOf { key ->
            eventScores[key]!! * userVector[key]!!
        }
        val eventMagnitude = sqrt(eventScores.values.sumOf { it * it })
        val userMagnitude = sqrt(userVector.values.sumOf { it * it })

        val similarity = if (eventMagnitude > 0 && userMagnitude > 0) {
            dotProduct / (eventMagnitude * userMagnitude)
        } else {
            0.0
        }

        Log.d("DashboardActivity", "Event Scores: $eventScores, User Vector: $userVector, Cosine Similarity: $similarity") // Log event and user vector
        return similarity
    }
}
