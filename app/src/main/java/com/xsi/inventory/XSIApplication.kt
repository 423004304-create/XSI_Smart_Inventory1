package com.xsi.inventory

import androidx.appcompat.app.AppCompatDelegate
import com.chaquo.python.android.PyApplication
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class XSIApplication : PyApplication() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("xsi_settings_prefs", MODE_PRIVATE)
        val nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (e: Exception) {
            // Firestore settings already initialized or default persistence enabled
        }
    }
}
