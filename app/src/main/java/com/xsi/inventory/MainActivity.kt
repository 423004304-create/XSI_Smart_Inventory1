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
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.xsi.inventory.network.UserRoles

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            proceedToApp(currentUser.email.orEmpty(), currentUser.uid)
            return
        }

        setContentView(R.layout.activity_main)

        val emailInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)
        val registerText = findViewById<TextView>(R.id.registerText)

        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            Toast.makeText(this, "Xavery SmartInventory System", Toast.LENGTH_SHORT).show()
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            when {
                email.isEmpty() && password.isEmpty() -> {
                    showMessage("Please enter your credentials to continue.")
                }
                email.isEmpty() -> {
                    showMessage("Please enter your email address.")
                }
                password.isEmpty() -> {
                    showMessage("Please enter your password.")
                }
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    showMessage("Please enter a valid email address.")
                }
                else -> signIn(email, password)
            }
        }

        forgotPasswordText.setOnClickListener {
            val email = emailInput.text.toString().trim()
            when {
                email.isEmpty() -> {
                    showMessage("Please enter your email address to reset password.")
                }
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    showMessage("Please enter a valid email address.")
                }
                else -> {
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                showMessage("Password reset email sent to $email. Please check your inbox.")
                            } else {
                                showMessage("Failed to send reset email: ${task.exception?.localizedMessage}")
                            }
                        }
                }
            }
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun signIn(email: String, password: String) {
        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    loginButton.isEnabled = true
                    showMessage("Sign-in failed. Check your email and password.")
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    auth.signOut()
                    loginButton.isEnabled = true
                    showMessage("Unable to sign in. Please try again.")
                    return@addOnCompleteListener
                }

                user.reload().addOnCompleteListener(this) { reloadTask ->
                    loginButton.isEnabled = true

                    if (!reloadTask.isSuccessful) {
                        auth.signOut()
                        showMessage("Could not verify credentials. Please try again.")
                        return@addOnCompleteListener
                    }

                    val refreshedUser = auth.currentUser
                    if (refreshedUser == null) {
                        auth.signOut()
                        showMessage("Unable to sign in. Please try again.")
                        return@addOnCompleteListener
                    }

                    if (!refreshedUser.isEmailVerified) {
                        refreshedUser.sendEmailVerification()
                        auth.signOut()
                        showMessage("Please verify your email address before signing in. A new verification link has been sent to $email.")
                        return@addOnCompleteListener
                    }

                    proceedToApp(email, refreshedUser.uid)
                }
            }
    }

    private fun proceedToApp(email: String, uid: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.getString("name") ?: email.substringBefore("@")
                val role = UserRoles.normalizeRole(snapshot.getString("role"))

                val targetClass = if (UserRoles.isStaff(role)) {
                    // Staff users default to POS flow
                    DashboardActivity::class.java
                } else {
                    DashboardActivity::class.java
                }

                val intent = Intent(this, targetClass).apply {
                    putExtra("USER_EMAIL", email)
                    putExtra("USER_NAME", name)
                    putExtra("USER_ROLE", role)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                val intent = Intent(this, DashboardActivity::class.java).apply {
                    putExtra("USER_EMAIL", email)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser ?: return

        user.reload().addOnCompleteListener(this) { task ->
            if (task.isSuccessful && auth.currentUser?.isEmailVerified == true) {
                val refreshedUser = auth.currentUser ?: return@addOnCompleteListener
                proceedToApp(refreshedUser.email.orEmpty(), refreshedUser.uid)
            } else {
                auth.signOut()
            }
        }
    }
}
