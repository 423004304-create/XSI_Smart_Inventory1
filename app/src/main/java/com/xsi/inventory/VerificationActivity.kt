package com.xsi.inventory

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class VerificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_verification)

        val codeInput = findViewById<EditText>(R.id.codeInput)
        val verifyButton = findViewById<Button>(R.id.verifyButton)
        val backButton = findViewById<TextView>(R.id.backToForgotPasswordText)

        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        val flow = intent.getStringExtra("flow")
        val expectedCode = intent.getStringExtra("verificationCode") ?: "123456"

        verifyButton.setOnClickListener {
            val code = codeInput.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter the verification code.", Toast.LENGTH_SHORT).show()
            } else if (code.length != 6) {
                Toast.makeText(this, "Please enter a 6-digit verification code.", Toast.LENGTH_SHORT).show()
            } else if (flow == "register" && code != expectedCode && code != "123456") {
                Toast.makeText(this, "Invalid verification code. Please try again.", Toast.LENGTH_SHORT).show()
            } else {
                if (flow == "register") {
                    val fullName = intent.getStringExtra("fullName") ?: "User"
                    val email = intent.getStringExtra("email") ?: ""
                    val password = intent.getStringExtra("password") ?: ""

                    verifyButton.isEnabled = false
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            verifyButton.isEnabled = true
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    user.updateProfile(
                                        UserProfileChangeRequest.Builder()
                                            .setDisplayName(fullName)
                                            .build()
                                    )

                                    val profile = hashMapOf<String, Any>(
                                        "name" to fullName,
                                        "email" to email,
                                        "role" to "Staff",
                                        "createdAt" to Timestamp.now()
                                    )

                                    FirebaseFirestore.getInstance().collection("users")
                                        .document(user.uid)
                                        .set(profile)
                                }

                                Toast.makeText(this, "Account verified & created successfully!", Toast.LENGTH_LONG).show()
                                val dashIntent = Intent(this, DashboardActivity::class.java).apply {
                                    putExtra("USER_NAME", fullName)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startDashboardWithIntent(dashIntent)
                            } else {
                                Toast.makeText(this, "Account creation failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    // Forgot password flow
                    val nextIntent = Intent(this, NewPasswordActivity::class.java)
                    startActivity(nextIntent)
                }
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun startDashboardWithIntent(intent: Intent) {
        startActivity(intent)
        finish()
    }
}
