package com.xsi.inventory

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Connect to the login layout
        setContentView(R.layout.activity_main)

        // Connect the UI elements
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)
        val registerText = findViewById<TextView>(R.id.registerText)

        // Underline register text
        registerText.paintFlags = registerText.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // Login button
        loginButton.setOnClickListener {

            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            when {

                // Both fields are empty
                username.isEmpty() && password.isEmpty() -> {
                    Toast.makeText(
                        this,
                        "Please enter your credentials to continue.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Username is empty
                username.isEmpty() -> {
                    Toast.makeText(
                        this,
                        "Please enter your username.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Password is empty
                password.isEmpty() -> {
                    Toast.makeText(
                        this,
                        "Please enter your password.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Temporary test login
                username == "admin" && password == "1234" -> {

                    val intent = Intent(
                        this,
                        DashboardActivity::class.java
                    )

                    startActivity(intent)
                }

                // Incorrect credentials
                else -> {
                    Toast.makeText(
                        this,
                        "The username or password you entered is incorrect.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Forgot Password
        forgotPasswordText.setOnClickListener {

            val intent = Intent(
                this,
                ForgotPasswordActivity::class.java
            )

            startActivity(intent)
        }

        // Register Text
        registerText.setOnClickListener {

            val intent = Intent(
                this,
                RegisterActivity::class.java
            )

            startActivity(intent)
        }
    }
}