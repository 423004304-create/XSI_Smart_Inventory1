package com.xsi.inventory

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NewPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_new_password)

        val newPasswordInput =
            findViewById<EditText>(R.id.newPasswordInput)

        val confirmPasswordInput =
            findViewById<EditText>(R.id.confirmPasswordInput)

        val updatePasswordButton =
            findViewById<Button>(R.id.updatePasswordButton)

        updatePasswordButton.setOnClickListener {

            val newPassword =
                newPasswordInput.text.toString()

            val confirmPassword =
                confirmPasswordInput.text.toString()

            when {

                newPassword.isEmpty() ||
                        confirmPassword.isEmpty() -> {

                    Toast.makeText(
                        this,
                        "Please complete both password fields.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                newPassword.length < 8 -> {

                    Toast.makeText(
                        this,
                        "Password must be at least 8 characters.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                newPassword != confirmPassword -> {

                    Toast.makeText(
                        this,
                        "Passwords do not match.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {

                    val intent = Intent(
                        this,
                        PasswordResetSuccessActivity::class.java
                    )

                    startActivity(intent)

                    finish()
                }
            }
        }
    }
}