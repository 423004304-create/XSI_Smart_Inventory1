package com.xsi.inventory

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_register)

        // Connect UI elements
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

        // Underline sign in text
        signInText.paintFlags = signInText.paintFlags or Paint.UNDERLINE_TEXT_FLAG


        // Create Account
        createAccountButton.setOnClickListener {

            val fullName =
                fullNameInput.text.toString().trim()

            val email =
                emailInput.text.toString().trim()

            val password =
                passwordInput.text.toString()

            val confirmPassword =
                confirmPasswordInput.text.toString()


            when {

                fullName.isEmpty() ||
                        email.isEmpty() ||
                        password.isEmpty() ||
                        confirmPassword.isEmpty() -> {

                    Toast.makeText(
                        this,
                        "Please complete all fields.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                !android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches() -> {

                    Toast.makeText(
                        this,
                        "Please enter a valid email address.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                password.length < 8 -> {

                    Toast.makeText(
                        this,
                        "Password must be at least 8 characters.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                password != confirmPassword -> {

                    Toast.makeText(
                        this,
                        "Passwords do not match.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {

                    Toast.makeText(
                        this,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Return to Login
                    val intent = Intent(
                        this,
                        MainActivity::class.java
                    )

                    intent.flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK

                    startActivity(intent)

                    finish()
                }
            }
        }


        // Sign In
        signInText.setOnClickListener {

            val intent = Intent(
                this,
                MainActivity::class.java
            )

            startActivity(intent)

            finish()
        }
    }
}