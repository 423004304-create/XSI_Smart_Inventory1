package com.xsi.inventory

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val currentInput = findViewById<EditText>(R.id.currentPasswordInput)
        val newPasswordInput = findViewById<EditText>(R.id.newPasswordInput)
        val confirmInput = findViewById<EditText>(R.id.confirmNewPasswordInput)
        val updateButton = findViewById<Button>(R.id.updatePasswordButton)

        updateButton.setOnClickListener {
            val currentPass = currentInput.text.toString()
            val pass = newPasswordInput.text.toString()
            val confirm = confirmInput.text.toString()

            when {
                currentPass.isEmpty() -> {
                    Toast.makeText(this, "Please enter your current password.", Toast.LENGTH_SHORT).show()
                }
                pass.length < 8 -> {
                    Toast.makeText(this, "New password must be at least 8 characters.", Toast.LENGTH_SHORT).show()
                }
                pass != confirm -> {
                    Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val auth = FirebaseAuth.getInstance()
                    val user = auth.currentUser
                    val email = user?.email
                    if (user != null && !email.isNullOrEmpty()) {
                        updateButton.isEnabled = false
                        val credential = EmailAuthProvider.getCredential(email, currentPass)

                        user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                            if (reauthTask.isSuccessful) {
                                user.updatePassword(pass).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        // Send notification email to the user regarding the password update
                                        auth.sendPasswordResetEmail(email).addOnCompleteListener {
                                            updateButton.isEnabled = true
                                            Toast.makeText(
                                                this,
                                                "Password updated successfully! A confirmation email has been sent to $email.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            finish()
                                        }
                                    } else {
                                        updateButton.isEnabled = true
                                        Toast.makeText(
                                            this,
                                            "Failed to update password: ${task.exception?.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } else {
                                updateButton.isEnabled = true
                                Toast.makeText(this, "Current password is incorrect.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
