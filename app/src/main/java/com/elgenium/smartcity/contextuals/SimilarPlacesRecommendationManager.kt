package com.elgenium.smartcity.contextuals

import com.elgenium.smartcity.models.PreferredVisitPlace
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.models.ViewedPlace
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SimilarPlacesRecommendationManager(userId: String) {

    private val database = FirebaseDatabase.getInstance().getReference("Users/$userId")

    // Fetch saved places
    fun fetchSavedPlaces(callback: (List<SavedPlace>) -> Unit) {
        database.child("saved_places").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val savedPlaces = mutableListOf<SavedPlace>()
                for (placeSnapshot in snapshot.children) {
                    val place = placeSnapshot.getValue(SavedPlace::class.java)
                    if (place != null) {
                        savedPlaces.add(place)
                    }
                }
                callback(savedPlaces)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    // Fetch viewed places
    fun fetchViewedPlaces(callback: (List<ViewedPlace>) -> Unit) {
        database.child("interactions/viewedPlaces").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val viewedPlaces = mutableListOf<ViewedPlace>()
                for (placeSnapshot in snapshot.children) {
                    val place = placeSnapshot.getValue(ViewedPlace::class.java)
                    if (place != null) {
                        viewedPlaces.add(place)
                    }
                }
                callback(viewedPlaces)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    // Fetch preferred visit places
    fun fetchPreferredVisitPlaces(callback: (List<PreferredVisitPlace>) -> Unit) {
        database.child("preferredVisitPlaces").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val preferredPlaces = mutableListOf<PreferredVisitPlace>()
                for (placeSnapshot in snapshot.children) {
                    val category = placeSnapshot.getValue(String::class.java)
                    if (category != null) {
                        preferredPlaces.add(PreferredVisitPlace(category))
                    }
                }
                callback(preferredPlaces)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    // Recommend similar places based on saved places
    fun recommendSimilarPlaces(savedPlaces: List<SavedPlace>, callback: (List<SavedPlace>) -> Unit) {
        if (savedPlaces.isNotEmpty()) {
            val lastSavedPlace = savedPlaces.last()
            val placeType = lastSavedPlace.types // Use the 'types' field

            val similarPlacesRef = FirebaseDatabase.getInstance().getReference("Places") // Adjust this path as needed
            similarPlacesRef.orderByChild("types").equalTo(placeType) // Query based on the types field
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val similarPlaces = mutableListOf<SavedPlace>()
                        for (placeSnapshot in snapshot.children) {
                            val place = placeSnapshot.getValue(SavedPlace::class.java)
                            if (place != null && place.id != lastSavedPlace.id) { // Exclude the last saved place
                                similarPlaces.add(place)
                            }
                        }
                        callback(similarPlaces)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Handle error
                    }
                })
        } else {
            callback(emptyList()) // No saved places
        }
    }
}


//Users{
//    9VSLAmobVCdpmSkr1xpfxy5t3pK2 {
//        saved_places {
//            -O86nCYUqSkaADFRQ1_k {
//                address:"18 A Mt. Apo St, Mandaue City, 6014 Cebu, Philippines",
//                id:"ChIJhzgvOQGZqTMRIT7HnHRHgco",
//                latLngString:"lat/lng: (10.3284615,123.9263894)",
//                name:"IRA",
//                openingDaysAndTime: "Monday: 8:00 AM – 6:00 PM, Tuesday: 8:00 AM – 6:00 PM, Wednesday: 8:00 AM – 6:00 PM, Thursday: 8:00 AM – 6:00 PM, Friday: 8:00 AM – 6:00 PM, Saturday: 8:00 AM – 6:00 PM, Sunday: Closed"
//                phoneNumber:"+63 991 933 4897"
//                rating:"5.0 star ratings"
//                websiteUri:"No Website Available"
//            }
//        }
//
//        preferredVisitPlaces {
//            0:"Cafes and restaurants"
//            1:"Shopping areas and malls"
//
//        }
//
//        interactions {
//            viewedPlaces {
//                -O8pUBEjR1JnE7ABOtDA {
//                    placeAddress:"8WHC+2QX, Hernan Cortes St, Mandaue City, Cebu, Philippines",
//                    placeId:"ChIJo0FUhwSZqTMR4EsAV9Vv4NY",
//                    placeLatLng:"lat/lng: (10.3276021,123.9219854)",
//                    placeName:"Alpa City Suites",
//                    timestamp:1728547962418,
//                    type:"[lodging, point_of_interest, establishment]"
//
//                }
//            }
//        }
//    }
//}
