package com.xsi.inventory

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_forgot_password)

        val emailInput =
            findViewById<EditText>(R.id.emailInput)

        val resetPasswordButton =
            findViewById<Button>(R.id.resetPasswordButton)

        val backToLoginText =
            findViewById<TextView>(R.id.backToLoginText)

        resetPasswordButton.setOnClickListener {

            val email =
                emailInput.text.toString().trim()

            if (email.isEmpty()) {

                Toast.makeText(
                    this,
                    "Please enter your email address.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                // Go to verification page
                val intent = Intent(
                    this,
                    VerificationActivity::class.java
                )

                intent.putExtra("email", email)

                startActivity(intent)
            }
        }

        backToLoginText.setOnClickListener {
            finish()
        }
    }
}