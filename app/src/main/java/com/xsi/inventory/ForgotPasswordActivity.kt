
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
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        val emailInput =
            findViewById<EditText>(R.id.emailInput)

        val resetPasswordButton =
            findViewById<Button>(R.id.resetPasswordButton)

        val backToLoginText =
            findViewById<TextView>(R.id.backToLoginText)

        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        resetPasswordButton.setOnClickListener {
            val email = emailInput.text.toString().trim()

            when {
                email.isEmpty() -> {
                    showMessage("Please enter your email address.")
                }

                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    showMessage("Please enter a valid email address.")
                }

                else -> {
                    sendPasswordResetEmail(email, resetPasswordButton)
                }
            }
        }

        backToLoginText.setOnClickListener {
            finish()
        }
    }

    private fun sendPasswordResetEmail(
        email: String,
        resetButton: Button
    ) {
        resetButton.isEnabled = false

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->

                resetButton.isEnabled = true

                if (task.isSuccessful) {
                    showMessage(
                        "If an account exists for this email, " +
                                "a password reset link has been sent. " +
                                "Check your inbox and spam folder."
                    )

                    finish()
                } else {
                    showMessage(
                        "Unable to process your request. " +
                                "Check your internet connection and try again."
                    )
                }
            }
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
