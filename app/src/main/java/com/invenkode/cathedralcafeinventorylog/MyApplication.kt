package com.invenkode.cathedralcafeinventorylog

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MyApplication : Application() {
    private lateinit var firestore: FirebaseFirestore
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize Firestore.
        firestore = Firebase.firestore

        // Optionally configure Firestore settings.
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
    }
}
