package com.xsi.inventory

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VerificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_verification)

        val codeInput =
            findViewById<EditText>(R.id.codeInput)

        val verifyButton =
            findViewById<Button>(R.id.verifyButton)

        val backButton =
            findViewById<TextView>(R.id.backToForgotPasswordText)

        verifyButton.setOnClickListener {

            val code =
                codeInput.text.toString().trim()

            if (code.isEmpty()) {

                Toast.makeText(
                    this,
                    "Please enter the verification code.",
                    Toast.LENGTH_SHORT
                ).show()

            } else if (code.length != 6) {

                Toast.makeText(
                    this,
                    "Please enter a 6-digit verification code.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                // Prototype verification
                val intent = Intent(
                    this,
                    NewPasswordActivity::class.java
                )

                startActivity(intent)
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}