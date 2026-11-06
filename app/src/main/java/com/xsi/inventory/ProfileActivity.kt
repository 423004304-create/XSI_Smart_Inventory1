package com.xsi.inventory

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.xsi.inventory.network.UserRoles

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        setupBottomNavigation()
        updateNotificationBadge()
        setupMenuClicks()

        val avatarView = findViewById<TextView>(R.id.profileAvatar)
        val avatarImage = findViewById<ImageView>(R.id.profileAvatarImage)
        val nameView = findViewById<TextView>(R.id.profileName)
        val roleView = findViewById<TextView>(R.id.profileRole)
        val emailView = findViewById<TextView>(R.id.profileEmail)
        val logoutButton = findViewById<Button>(R.id.profileLogoutButton)

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val email = currentUser.email ?: "user@example.com"
            val displayName = currentUser.displayName

            emailView.text = email

            val name = when {
                !displayName.isNullOrBlank() -> displayName
                else -> email.substringBefore("@").replace(".", " ").split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            }

            nameView.text = name

            val parts = name.trim().split("\\s+".toRegex())
            val initials = when {
                parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                parts.isNotEmpty() && parts[0].isNotBlank() -> parts[0].take(1).uppercase()
                else -> "U"
            }
            avatarView.text = initials

            if (currentUser.photoUrl != null) {
                Glide.with(this).load(currentUser.photoUrl).into(avatarImage)
                avatarImage.visibility = View.VISIBLE
                avatarView.visibility = View.GONE
            }

            // Fetch profile details from Firestore in real-time
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val dbName = snapshot?.getString("name")
                    val dbRole = snapshot?.getString("role")
                    val dbPhoto = snapshot?.getString("photoUrl")
                    if (!dbName.isNullOrBlank()) {
                        nameView.text = dbName
                        val dbParts = dbName.trim().split("\\s+".toRegex())
                        val dbInitials = when {
                            dbParts.size >= 2 -> "${dbParts[0].take(1)}${dbParts[1].take(1)}".uppercase()
                            dbParts.isNotEmpty() && dbParts[0].isNotBlank() -> dbParts[0].take(1).uppercase()
                            else -> "U"
                        }
                        avatarView.text = dbInitials
                    }
                    if (!dbRole.isNullOrBlank()) {
                        roleView?.text = UserRoles.getRoleLabel(dbRole)
                    }
                    if (!dbPhoto.isNullOrBlank()) {
                        Glide.with(this).load(dbPhoto).into(avatarImage)
                        avatarImage.visibility = View.VISIBLE
                        avatarView.visibility = View.GONE
                    }
                }
        }

        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupMenuClicks() {
        findViewById<LinearLayout>(R.id.editProfileRow).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.changePasswordRow).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.notificationSettingsRow).setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.warehouseSettingsRow).setOnClickListener {
            startActivity(Intent(this, WarehouseSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.darkModeRow).setOnClickListener {
            val prefs = getSharedPreferences("xsi_settings_prefs", MODE_PRIVATE)
            val currentNightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val options = arrayOf("System Default", "Light", "Dark")
            val checkedItem = when (currentNightMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                else -> 0
            }

            AlertDialog.Builder(this)
                .setTitle("Dark Mode")
                .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                    val selectedMode = when (which) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    prefs.edit().putInt("night_mode", selectedMode).apply()
                    AppCompatDelegate.setDefaultNightMode(selectedMode)
                    updateDarkModeStatus(findViewById(R.id.darkModeStatusText), selectedMode)
                    dialog.dismiss()
                    recreate()
                }
                .show()
        }
        findViewById<LinearLayout>(R.id.manageTeamRow).setOnClickListener {
            startActivity(Intent(this, ManageTeamActivity::class.java))
        }

        val prefs = getSharedPreferences("xsi_settings_prefs", MODE_PRIVATE)
        val currentNightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        updateDarkModeStatus(findViewById(R.id.darkModeStatusText), currentNightMode)
    }

    private fun updateDarkModeStatus(textView: TextView?, mode: Int) {
        textView?.text = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            else -> "System Default"
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.notificationBadge) ?: return
        val count = NotificationHelper.getUnreadCount(this)
        if (count > 0) {
            badge.text = count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun setupBottomNavigation() {
        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.inventoryNav).setOnClickListener {
            startActivity(Intent(this, InventoryActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.addNav).setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.scanNav).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.alertsNav).setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.profileNav).setOnClickListener {
            // Already here
        }
    }
}
