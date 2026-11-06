package com.xsi.inventory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.xsi.inventory.network.UserRoles

class EditProfileActivity : AppCompatActivity() {

    private var selectedPhotoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val firstNameInput = findViewById<EditText>(R.id.firstNameInput)
        val lastNameInput = findViewById<EditText>(R.id.lastNameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val phoneInput = findViewById<EditText>(R.id.phoneInput)
        val jobTitleInput = findViewById<EditText>(R.id.jobTitleInput)
        val departmentInput = findViewById<EditText>(R.id.departmentInput)
        val companyInput = findViewById<EditText>(R.id.companyInput)

        val saveButton = findViewById<Button>(R.id.saveProfileButton)
        val deleteButton = findViewById<Button>(R.id.deleteAccountButton)
        val changePhotoButton = findViewById<Button>(R.id.changePhotoButton)
        val avatarView = findViewById<TextView>(R.id.profileAvatar)
        val avatarImage = findViewById<ImageView>(R.id.profileAvatarImage)
        val nameHeader = findViewById<TextView>(R.id.profileNameHeader)
        val roleHeader = findViewById<TextView>(R.id.profileRoleHeader)

        val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
                avatarImage?.setImageURI(uri)
                avatarImage?.visibility = View.VISIBLE
                avatarView?.visibility = View.GONE
            }
        }

        changePhotoButton?.setOnClickListener {
            photoPicker.launch("image/*")
        }

        val currentUser = FirebaseAuth.getInstance().currentUser

        fun populateFields(fullName: String, email: String, phone: String = "", role: String = "Staff", photoUrl: String? = null) {
            val displayRole = UserRoles.getRoleLabel(role)
            nameHeader.text = fullName
            roleHeader.text = displayRole

            val parts = fullName.trim().split("\\s+".toRegex())
            if (parts.isNotEmpty()) {
                firstNameInput.setText(parts[0])
                if (parts.size > 1) {
                    lastNameInput.setText(parts.drop(1).joinToString(" "))
                }
            }
            emailInput.setText(email)
            phoneInput.setText(phone)
            jobTitleInput.setText(displayRole)
            departmentInput.setText(R.string.app_name)
            companyInput.setText(getString(R.string.default_company))

            val initials = when {
                parts.size >= 2 -> "${parts[0].take(1)}${parts.last().take(1)}".uppercase()
                parts.isNotEmpty() -> parts[0].take(1).uppercase()
                else -> "U"
            }
            avatarView.text = initials

            if (selectedPhotoUri == null) {
                val urlToLoad = photoUrl ?: currentUser?.photoUrl?.toString()
                if (!urlToLoad.isNullOrBlank()) {
                    Glide.with(this).load(urlToLoad).into(avatarImage)
                    avatarImage?.visibility = View.VISIBLE
                    avatarView?.visibility = View.GONE
                } else {
                    avatarImage?.visibility = View.GONE
                    avatarView?.visibility = View.VISIBLE
                }
            }
        }

        if (currentUser != null) {
            val email = currentUser.email ?: "user@example.com"
            val defaultName = when {
                !currentUser.displayName.isNullOrBlank() -> currentUser.displayName!!
                else -> email.substringBefore("@").replace(".", " ").split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            }

            populateFields(defaultName, email)

            // Fetch from Firestore
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get().addOnSuccessListener { snapshot ->
                    val dbName = snapshot.getString("name") ?: defaultName
                    val dbPhone = snapshot.getString("phone") ?: ""
                    val dbRole = snapshot.getString("role") ?: "Staff"
                    val dbPhoto = snapshot.getString("photoUrl")
                    populateFields(dbName, email, dbPhone, dbRole, dbPhoto)
                }
        }

        saveButton.setOnClickListener {
            val first = firstNameInput.text.toString().trim()
            val last = lastNameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val fullName = "$first $last".trim()

            if (fullName.isEmpty()) {
                Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveButton.isEnabled = false

            fun saveProfileToFirestoreAndAuth(photoDownloadUrl: String? = null) {
                val updates = mutableMapOf<String, Any>(
                    "name" to fullName,
                    "phone" to phone
                )
                if (photoDownloadUrl != null) {
                    updates["photoUrl"] = photoDownloadUrl
                }

                val profileBuilder = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                if (photoDownloadUrl != null) {
                    profileBuilder.setPhotoUri(Uri.parse(photoDownloadUrl))
                }

                currentUser?.updateProfile(profileBuilder.build())
                    ?.addOnCompleteListener { task ->
                        saveButton.isEnabled = true
                        if (task.isSuccessful && currentUser != null) {
                            val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                            userRef.set(updates, SetOptions.merge())
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }

            val photoUri = selectedPhotoUri
            if (photoUri != null && currentUser != null) {
                val photoRef = FirebaseStorage.getInstance().reference
                    .child("profile-photos/${currentUser.uid}.jpg")
                photoRef.putFile(photoUri)
                    .continueWithTask { uploadTask ->
                        if (!uploadTask.isSuccessful) {
                            throw uploadTask.exception ?: IllegalStateException("Photo upload failed")
                        }
                        photoRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUri ->
                        saveProfileToFirestoreAndAuth(downloadUri.toString())
                    }
                    .addOnFailureListener { error ->
                        saveButton.isEnabled = true
                        Toast.makeText(this, "Photo upload failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                saveProfileToFirestoreAndAuth()
            }
        }

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Account?")
                .setMessage("Are you sure you want to permanently delete your account? This action cannot be undone and will delete all your profile data.")
                .setPositiveButton("Delete Account") { _, _ ->
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        Toast.makeText(this, "No signed-in user found.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    deleteButton.isEnabled = false
                    Toast.makeText(this, "Deleting account permanently...", Toast.LENGTH_SHORT).show()

                    val uid = user.uid
                    val db = FirebaseFirestore.getInstance()
                    val storage = FirebaseStorage.getInstance()

                    // 1. Delete Firestore user profile document
                    db.collection("users").document(uid).delete()
                        .addOnCompleteListener {
                            // 2. Delete profile photo from Firebase Storage if present
                            storage.reference.child("profile-photos/$uid.jpg").delete()
                                .addOnCompleteListener {
                                    // 3. Permanently delete Firebase Auth user
                                    user.delete().addOnCompleteListener { deleteTask ->
                                        deleteButton.isEnabled = true
                                        if (deleteTask.isSuccessful) {
                                            FirebaseAuth.getInstance().signOut()
                                            Toast.makeText(this, "Account permanently deleted.", Toast.LENGTH_LONG).show()
                                            val intent = Intent(this, MainActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            }
                                            startActivity(intent)
                                            finish()
                                        } else {
                                            val errorMsg = deleteTask.exception?.localizedMessage
                                                ?: "Security sensitive action. Please sign out and sign back in before deleting your account."
                                            Toast.makeText(this, "Failed to delete account: $errorMsg", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
