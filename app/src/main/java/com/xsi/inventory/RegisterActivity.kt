
package com.xsi.inventory

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val fullNameInput =
            findViewById<EditText>(R.id.fullNameInput)

        val emailInput =
            findViewById<EditText>(R.id.registerEmailInput)

        val passwordInput =
            findViewById<EditText>(R.id.registerPasswordInput)

        val confirmPasswordInput =
            findViewById<EditText>(R.id.confirmPasswordInput)

        val createAccountButton =
            findViewById<Button>(R.id.createAccountButton)

        val signInText =
            findViewById<TextView>(R.id.signInText)

        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        createAccountButton.setOnClickListener {
            val fullName = fullNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword =
                confirmPasswordInput.text.toString()

            when {
                fullName.isEmpty() ||
                        email.isEmpty() ||
                        password.isEmpty() ||
                        confirmPassword.isEmpty() -> {
                    showMessage("Please complete all fields.")
                }

                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    showMessage("Please enter a valid email address.")
                }

                password.length < 8 -> {
                    showMessage(
                        "Password must be at least 8 characters."
                    )
                }

                password != confirmPassword -> {
                    showMessage("Passwords do not match.")
                }

                else -> {
                    createAccountButton.isEnabled = false
                    registerUser(fullName, email, password)
                }
            }
        }

        signInText.setOnClickListener {
            finish()
        }
    }

    private fun registerUser(
        fullName: String,
        email: String,
        password: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (!task.isSuccessful) {
                    finishRegistration(
                        "Registration failed. This email may already be registered."
                    )
                    return@addOnCompleteListener
                }

                val user = auth.currentUser

                if (user == null) {
                    auth.signOut()
                    finishRegistration(
                        "Account created, but sign-in failed."
                    )
                    return@addOnCompleteListener
                }

                val db = FirebaseFirestore.getInstance()
                db.collection("invitations")
                    .whereEqualTo("email", email)
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener { inviteQuery ->
                        val pendingInvite = inviteQuery.documents.firstOrNull()
                        val assignedRole = pendingInvite?.getString("role") ?: "Staff"

                        val profile = hashMapOf<String, Any>(
                            "name" to fullName,
                            "email" to email,
                            "role" to assignedRole,
                            "createdAt" to Timestamp.now()
                        )

                        db.collection("users")
                            .document(user.uid)
                            .set(profile)
                            .addOnCompleteListener(this) { profileTask ->

                                if (!profileTask.isSuccessful) {
                                    auth.signOut()
                                    finishRegistration(
                                        "Could not save your profile. Please check your internet connection and database rules."
                                    )
                                    return@addOnCompleteListener
                                }

                                pendingInvite?.reference?.update("status", "accepted")

                                user.sendEmailVerification()
                                    .addOnCompleteListener(this) { emailTask ->

                                        auth.signOut()

                                        if (emailTask.isSuccessful) {
                                            Toast.makeText(
                                                this,
                                                "Verification email sent to $email. Check your inbox and spam folder.",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            val intent = Intent(
                                                this,
                                                MainActivity::class.java
                                            )

                                            intent.flags =
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                                            startActivity(intent)
                                            finish()
                                        } else {
                                            finishRegistration(
                                                "Your account was created, but the verification email could not be sent. A resend option is needed to try again."
                                            )
                                        }
                                    }
                            }
                    }
                    .addOnFailureListener {
                        auth.signOut()
                        finishRegistration("Registration failed. Please check network connection.")
                    }
            }
    }

    private fun finishRegistration(message: String) {
        findViewById<Button>(
            R.id.createAccountButton
        ).isEnabled = true

        showMessage(message)
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
