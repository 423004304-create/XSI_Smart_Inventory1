package com.xsi.inventory

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.xsi.inventory.network.UserRoles

class ManageTeamActivity : AppCompatActivity() {
    private var membersListener: ListenerRegistration? = null
    private var invitesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_team)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val addInviteButton = findViewById<Button>(R.id.addInviteButton)
        addInviteButton?.setOnClickListener {
            showInviteMemberDialog()
        }

        loadActiveMembersAndInvites(addInviteButton)
    }

    private fun loadActiveMembersAndInvites(addInviteButton: Button?) {
        val container = findViewById<LinearLayout>(R.id.activeMembersContainer) ?: return
        val titleView = findViewById<TextView>(R.id.activeMembersTitle)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()

        membersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Live team updates failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                container.removeAllViews()
                val documents = snapshot.documents
                val count = documents.size
                titleView?.text = "Active Members ($count)"

                val currentUserDoc = documents.firstOrNull { it.id == currentUserId }
                val currentUserRole = currentUserDoc?.getString("role")
                val canManageTeam = UserRoles.isAdmin(currentUserRole)

                if (canManageTeam) {
                    addInviteButton?.visibility = View.VISIBLE
                } else {
                    addInviteButton?.visibility = View.GONE
                }

                documents.forEachIndexed { index, doc ->
                    val userId = doc.id
                    val name = doc.getString("name") ?: "User"
                    val email = doc.getString("email") ?: ""
                    val isYou = userId == currentUserId

                    val row = LinearLayout(this@ManageTeamActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(8), 0, dp(8))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val initials = name.split("\\s+".toRegex()).let { parts ->
                        when {
                            parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                            parts.isNotEmpty() && parts[0].isNotBlank() -> parts[0].take(1).uppercase()
                            else -> "U"
                        }
                    }

                    val avatar = TextView(this@ManageTeamActivity).apply {
                        text = initials
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#1267AA"))
                        setTypeface(null, Typeface.BOLD)
                        background = roundedBackground(Color.parseColor("#E8F2FC"), 50)
                    }
                    row.addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)))

                    val infoLayout = LinearLayout(this@ManageTeamActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                    }

                    infoLayout.addView(TextView(this@ManageTeamActivity).apply {
                        text = if (isYou) "$name (you)" else name
                        textSize = 13f
                        setTextColor(Color.parseColor("#222222"))
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                    })

                    infoLayout.addView(TextView(this@ManageTeamActivity).apply {
                        text = email
                        textSize = 11f
                        setTextColor(Color.parseColor("#777777"))
                        maxLines = 1
                        setPadding(0, dp(2), 0, 0)
                    })

                    row.addView(infoLayout, LinearLayout.LayoutParams(0, -2, 1f))

                    val role = doc.getString("role") ?: "Staff"
                    val displayRole = UserRoles.getRoleLabel(role)
                    val isAdminOrHigher = UserRoles.isAdmin(role)

                    val roleBadge = TextView(this@ManageTeamActivity).apply {
                        text = displayRole
                        textSize = 10f
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                        setPadding(dp(8), 0, dp(8), 0)
                        setTextColor(if (isAdminOrHigher) Color.parseColor("#1267AA") else Color.parseColor("#C83232"))
                        background = roundedBackground(if (isAdminOrHigher) Color.parseColor("#E8F2FC") else Color.parseColor("#FCE9E9"), 12)
                        isClickable = canManageTeam
                        if (isClickable) setOnClickListener {
                            val roles = arrayOf("Staff", "Sales Staff", "Admin", "Project Admin")
                            AlertDialog.Builder(this@ManageTeamActivity)
                                .setTitle("Select Role for $name")
                                .setItems(roles) { _, which ->
                                    val newRole = roles[which]
                                    val updateRole = {
                                        doc.reference.update("role", newRole).addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                Toast.makeText(this@ManageTeamActivity, "Updated $name's role.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@ManageTeamActivity, "Failed to update role: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    if (isYou && newRole != "Project Admin" && newRole != "Admin") {
                                        AlertDialog.Builder(this@ManageTeamActivity)
                                            .setTitle("Change your own role?")
                                            .setMessage("You will lose Admin permissions.")
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton("Change role") { _, _ -> updateRole() }
                                            .show()
                                    } else {
                                        updateRole()
                                    }
                                }
                                .show()
                        }
                    }

                    row.addView(roleBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(22)))
                    container.addView(row)

                    if (index < documents.lastIndex) {
                        container.addView(View(this@ManageTeamActivity).apply {
                            setBackgroundColor(Color.parseColor("#EEEEEE"))
                        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            setMargins(0, dp(6), 0, dp(6))
                        })
                    }
                }

                if (count == 0) {
                    titleView?.text = "Active Members (0)"
                    val emptyView = TextView(this@ManageTeamActivity).apply {
                        text = "No team members registered yet."
                        textSize = 12f
                        setTextColor(Color.parseColor("#888888"))
                        setPadding(0, dp(8), 0, dp(8))
                    }
                    container.addView(emptyView)
                }
            }

        // Listen to Pending Invitations
        val pendingCard = findViewById<MaterialCardView>(R.id.pendingInvitationsCard)
        val pendingContainer = findViewById<LinearLayout>(R.id.pendingInvitationsContainer)
        val pendingTitle = findViewById<TextView>(R.id.pendingInvitationsTitle)

        invitesListener = db.collection("invitations")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                pendingContainer?.removeAllViews()
                val docs = snapshot.documents
                if (docs.isEmpty()) {
                    pendingCard?.visibility = View.GONE
                } else {
                    pendingCard?.visibility = View.VISIBLE
                    pendingTitle?.text = "Pending Invitations (${docs.size})"

                    docs.forEachIndexed { index, doc ->
                        val email = doc.getString("email") ?: ""
                        val role = doc.getString("role") ?: "Staff"
                        val invitedBy = doc.getString("invitedBy") ?: "Admin"

                        val row = LinearLayout(this@ManageTeamActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, dp(8), 0, dp(8))
                        }

                        val infoLayout = LinearLayout(this@ManageTeamActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        infoLayout.addView(TextView(this@ManageTeamActivity).apply {
                            text = email
                            textSize = 13f
                            setTextColor(Color.parseColor("#222222"))
                            setTypeface(null, Typeface.BOLD)
                            maxLines = 1
                        })

                        infoLayout.addView(TextView(this@ManageTeamActivity).apply {
                            text = "Invited as $role by $invitedBy"
                            textSize = 10f
                            setTextColor(Color.parseColor("#777777"))
                            maxLines = 1
                            setPadding(0, dp(2), 0, 0)
                        })

                        row.addView(infoLayout)

                        val cancelButton = TextView(this@ManageTeamActivity).apply {
                            text = "Cancel"
                            textSize = 11f
                            setTextColor(Color.parseColor("#C83232"))
                            setTypeface(null, Typeface.BOLD)
                            setPadding(dp(8), dp(4), dp(8), dp(4))
                            background = roundedBackground(Color.parseColor("#FCE9E9"), 8)
                            isClickable = true
                            setOnClickListener {
                                doc.reference.update("status", "cancelled")
                                    .addOnSuccessListener {
                                        Toast.makeText(this@ManageTeamActivity, "Invitation for $email cancelled.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }

                        row.addView(cancelButton)
                        pendingContainer?.addView(row)

                        if (index < docs.lastIndex) {
                            pendingContainer?.addView(View(this@ManageTeamActivity).apply {
                                setBackgroundColor(Color.parseColor("#EEEEEE"))
                            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                                setMargins(0, dp(6), 0, dp(6))
                            })
                        }
                    }
                }
            }
    }

    private fun showInviteMemberDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val emailInput = EditText(this).apply {
            hint = "Member Email (e.g. alex@xavery.com)"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        val roleSpinner = Spinner(this)
        val roles = arrayOf("Staff", "Sales Staff", "Admin", "Project Admin")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        roleSpinner.adapter = adapter

        val roleLabel = TextView(this).apply {
            text = "Assigned Role:"
            textSize = 12f
            setPadding(0, 16, 0, 8)
            setTextColor(Color.parseColor("#555555"))
        }

        dialogLayout.addView(emailInput)
        dialogLayout.addView(roleLabel)
        dialogLayout.addView(roleSpinner)

        AlertDialog.Builder(this)
            .setTitle("Invite Team Member")
            .setView(dialogLayout)
            .setPositiveButton("Send Invite") { _, _ ->
                val email = emailInput.text.toString().trim()
                val selectedRole = roles[roleSpinner.selectedItemPosition]

                when {
                    email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                        Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val db = FirebaseFirestore.getInstance()
                        val currentAdminEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Admin"

                        // Check if user already exists
                        db.collection("users").whereEqualTo("email", email).get()
                            .addOnSuccessListener { userQuery ->
                                if (!userQuery.isEmpty) {
                                    val userDoc = userQuery.documents.first()
                                    userDoc.reference.update("role", selectedRole)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "$email is already registered. Role updated to $selectedRole.", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    val docRef = db.collection("invitations").document()
                                    val inviteData = hashMapOf<String, Any>(
                                        "id" to docRef.id,
                                        "email" to email,
                                        "role" to selectedRole,
                                        "invitedBy" to currentAdminEmail,
                                        "invitedAt" to Timestamp.now(),
                                        "status" to "pending"
                                    )
                                    docRef.set(inviteData).addOnSuccessListener {
                                        Toast.makeText(this, "Invitation sent to $email as $selectedRole!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        membersListener?.remove()
        invitesListener?.remove()
        super.onDestroy()
    }
}
