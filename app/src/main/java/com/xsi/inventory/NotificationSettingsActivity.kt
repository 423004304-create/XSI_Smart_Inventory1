package com.xsi.inventory

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class NotificationSettingsActivity : AppCompatActivity() {

    private val PREFS_NAME = "xsi_notification_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val emailSubtitle = findViewById<TextView>(R.id.emailSubtitle)
        val saveButton = findViewById<Button>(R.id.savePreferencesButton)

        val lowStockSwitch = findViewById<Switch>(R.id.lowStockSwitch)
        val outOfStockSwitch = findViewById<Switch>(R.id.outOfStockSwitch)
        val restockSwitch = findViewById<Switch>(R.id.restockSwitch)
        val newProductSwitch = findViewById<Switch>(R.id.newProductSwitch)
        val productEditedSwitch = findViewById<Switch>(R.id.productEditedSwitch)
        val teamJoinSwitch = findViewById<Switch>(R.id.teamJoinSwitch)
        val inAppSwitch = findViewById<Switch>(R.id.inAppSwitch)
        val emailSwitch = findViewById<Switch>(R.id.emailSwitch)
        val smsSwitch = findViewById<Switch>(R.id.smsSwitch)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            emailSubtitle.text = currentUser.email
        }

        // 1. Load local SharedPreferences as instant defaults
        lowStockSwitch.isChecked = prefs.getBoolean("lowStock", true)
        outOfStockSwitch.isChecked = prefs.getBoolean("outOfStock", true)
        restockSwitch.isChecked = prefs.getBoolean("restock", true)
        newProductSwitch.isChecked = prefs.getBoolean("newProduct", true)
        productEditedSwitch.isChecked = prefs.getBoolean("productEdited", true)
        teamJoinSwitch.isChecked = prefs.getBoolean("teamJoin", true)
        inAppSwitch.isChecked = prefs.getBoolean("inApp", true)
        emailSwitch.isChecked = prefs.getBoolean("email", true)
        smsSwitch.isChecked = prefs.getBoolean("sms", false)

        // 2. Load Firestore remote preferences if available
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val remotePrefs = snapshot.get("notificationPrefs") as? Map<String, Boolean>
                        if (remotePrefs != null) {
                            remotePrefs["lowStock"]?.let { lowStockSwitch.isChecked = it }
                            remotePrefs["outOfStock"]?.let { outOfStockSwitch.isChecked = it }
                            remotePrefs["restock"]?.let { restockSwitch.isChecked = it }
                            remotePrefs["newProduct"]?.let { newProductSwitch.isChecked = it }
                            remotePrefs["productEdited"]?.let { productEditedSwitch.isChecked = it }
                            remotePrefs["teamJoin"]?.let { teamJoinSwitch.isChecked = it }
                            remotePrefs["inApp"]?.let { inAppSwitch.isChecked = it }
                            remotePrefs["email"]?.let { emailSwitch.isChecked = it }
                            remotePrefs["sms"]?.let { smsSwitch.isChecked = it }
                        }
                    }
                }
        }

        // 3. Save button listener
        saveButton.setOnClickListener {
            val prefMap = hashMapOf(
                "lowStock" to lowStockSwitch.isChecked,
                "outOfStock" to outOfStockSwitch.isChecked,
                "restock" to restockSwitch.isChecked,
                "newProduct" to newProductSwitch.isChecked,
                "productEdited" to productEditedSwitch.isChecked,
                "teamJoin" to teamJoinSwitch.isChecked,
                "inApp" to inAppSwitch.isChecked,
                "email" to emailSwitch.isChecked,
                "sms" to smsSwitch.isChecked
            )

            // Save locally
            prefs.edit().apply {
                prefMap.forEach { (key, valBool) ->
                    putBoolean(key, valBool)
                }
                apply()
            }

            // Save to Firestore
            if (currentUser != null) {
                saveButton.isEnabled = false
                val updates = hashMapOf<String, Any>("notificationPrefs" to prefMap)
                FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                    .set(updates, SetOptions.merge())
                    .addOnCompleteListener { task ->
                        saveButton.isEnabled = true
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Notification preferences saved successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Saved locally, but server update failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
            } else {
                Toast.makeText(this, "Notification preferences saved locally!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
