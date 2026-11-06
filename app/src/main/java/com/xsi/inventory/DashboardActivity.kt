package com.xsi.inventory

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {
    private var profileListener: ListenerRegistration? = null
    private var productsListener: ListenerRegistration? = null

    private data class Product(
        val name: String,
        val sku: String,
        val category: String,
        val quantity: String,
        val price: String,
        val status: String,
        val location: String
    )

    private data class StockAlert(
        val icon: String,
        val title: String,
        val description: String,
        val level: String,
        val critical: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setupUserGreeting()
        loadDashboardData()
        setupNavigation()
        updateNotificationBadge()
    }

    private fun setupUserGreeting() {
        val greetingText = findViewById<TextView>(R.id.greetingText) ?: return
        val profileInitials = findViewById<TextView>(R.id.profileInitials)
        val dateText = findViewById<TextView>(R.id.dateText)

        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        dateText?.text = dateFormat.format(Date())

        val currentUser = FirebaseAuth.getInstance().currentUser
        val intentName = intent.getStringExtra("USER_NAME")
        val intentEmail = intent.getStringExtra("USER_EMAIL")

        fun updateUi(fullName: String) {
            val trimmedName = fullName.trim()
            if (trimmedName.isEmpty()) return

            val firstName = trimmedName.split("\\s+".toRegex()).firstOrNull()
                ?.lowercase()
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ?: trimmedName

            val timeGreeting = getTimeGreeting()
            greetingText.text = "$timeGreeting, $firstName"

            val parts = trimmedName.split("\\s+".toRegex())
            val initials = when {
                parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                parts.isNotEmpty() && parts[0].isNotBlank() -> parts[0].take(1).uppercase()
                else -> "U"
            }
            profileInitials?.text = initials
        }

        val initialName = when {
            !intentName.isNullOrBlank() -> intentName
            !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName
            else -> {
                val email = intentEmail ?: currentUser?.email
                if (!email.isNullOrBlank()) {
                    val username = email.substringBefore("@")
                    username.replace(".", " ").split(" ")
                        .joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                } else {
                    "User"
                }
            }
        }

        updateUi(initialName.orEmpty())

        if (currentUser != null) {
            val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)

            profileListener = userRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Live profile updates failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                val dbName = snapshot?.getString("name")
                if (!dbName.isNullOrBlank()) {
                    updateUi(dbName)
                }
            }
        }
    }

    private fun getTimeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun loadDashboardData() {
        val productContainer = findViewById<LinearLayout>(R.id.productRows)
        val alertContainer = findViewById<LinearLayout>(R.id.alertsTable)
        val noPartsFoundText = findViewById<TextView>(R.id.noPartsFoundText)
        val noAlertsText = findViewById<TextView>(R.id.noAlertsText)
        val stockAlertsBadgeText = findViewById<TextView>(R.id.stockAlertsBadgeText)

        val totalPartTypesView = findViewById<TextView>(R.id.totalPartTypesValue)
        val unitsInStockView = findViewById<TextView>(R.id.unitsInStockValue)
        val lowStockView = findViewById<TextView>(R.id.lowStockValue)
        val inventoryValueView = findViewById<TextView>(R.id.inventoryValue)

        productsListener = FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Live dashboard updates failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                productContainer?.removeAllViews()
                alertContainer?.removeAllViews()

                var totalPartTypes = 0
                var lowStockCount = 0
                var outStockCount = 0
                var totalValue = 0.0
                var totalUnits = 0

                val productsList = mutableListOf<Product>()
                val alertsList = mutableListOf<StockAlert>()

                for (document in snapshot.documents) {
                    val name = document.getString("name") ?: continue
                    val sku = document.getString("sku") ?: "N/A"
                    val category = document.getString("category") ?: "General"
                    val location = document.getString("location") ?: "Warehouse"
                    
                    val qty = when (val q = document.get("quantity")) {
                        is Long -> q.toInt()
                        is Int -> q
                        is Number -> q.toInt()
                        is String -> q.toIntOrNull() ?: 0
                        else -> 0
                    }

                    val priceVal = when (val p = document.get("price")) {
                        is Double -> p
                        is Long -> p.toDouble()
                        is Number -> p.toDouble()
                        is String -> p.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val priceStr = "₱${String.format(Locale.getDefault(), "%,.2f", priceVal)}"

                    val minStock = when (val m = document.get("minStock") ?: document.get("lowStockThreshold")) {
                        is Long -> m.toInt()
                        is Int -> m
                        is Number -> m.toInt()
                        is String -> m.toIntOrNull() ?: 10
                        else -> 10
                    }

                    val status = when {
                        qty == 0 -> "Out"
                        qty <= minStock -> "Low"
                        else -> "In stock"
                    }

                    totalPartTypes++
                    totalUnits += qty
                    totalValue += (qty * priceVal)

                    if (qty == 0) {
                        outStockCount++
                        alertsList.add(StockAlert("⬡", "$name — Out of stock", "Reorder needed immediately", "Critical", true))
                    } else if (qty <= minStock) {
                        lowStockCount++
                        alertsList.add(StockAlert("⚠", "$name - Low stock", "Only $qty units remaining", "Low", false))
                    }

                    productsList.add(Product(name, sku, category, qty.toString(), priceStr, status, location))
                }

                totalPartTypesView?.text = totalPartTypes.toString()
                unitsInStockView?.text = totalUnits.toString()
                lowStockView?.text = lowStockCount.toString()
                inventoryValueView?.text = "₱${String.format(Locale.getDefault(), "%,.2f", totalValue)}"

                stockAlertsBadgeText?.text = "${alertsList.size} items"

                val textPrimary = ContextCompat.getColor(this@DashboardActivity, R.color.text_primary)
                val textSecondary = ContextCompat.getColor(this@DashboardActivity, R.color.text_secondary)

                if (productsList.isEmpty()) {
                    noPartsFoundText?.visibility = View.VISIBLE
                } else {
                    noPartsFoundText?.visibility = View.GONE
                    productsList.take(5).forEachIndexed { index, product ->
                        val row = LinearLayout(this@DashboardActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(8), 0, dp(8), 0)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(44)
                            )
                        }

                        // 1. PART / SKU
                        addCell(row, "${product.name}\n${product.sku}", 1.3f, 11, textPrimary)
                        // 2. CATEGORY
                        addCell(row, product.category, 1.0f, 11, textSecondary)
                        // 3. QTY
                        addCell(row, product.quantity, 0.6f, 11, textPrimary)
                        // 4. UNIT PRICE
                        addCell(row, product.price, 0.9f, 11, textPrimary)

                        // 5. STATUS
                        val statusColor = when (product.status) {
                            "Out" -> Color.rgb(225, 75, 75)
                            else -> Color.rgb(185, 113, 0)
                        }
                        val statusBackground = when (product.status) {
                            "Out" -> Color.rgb(252, 234, 234)
                            else -> Color.rgb(250, 238, 216)
                        }

                        val statusView = TextView(this@DashboardActivity).apply {
                            text = product.status
                            textSize = 8f
                            gravity = Gravity.CENTER
                            setTextColor(statusColor)
                            typeface = Typeface.DEFAULT_BOLD
                            background = roundedBackground(statusBackground, 12)
                            includeFontPadding = false
                        }
                        row.addView(statusView, LinearLayout.LayoutParams(0, dp(20), 0.8f))

                        // 6. LOCATION
                        addCell(row, product.location, 0.8f, 11, textSecondary)

                        productContainer?.addView(row)

                        if (index < productsList.take(5).lastIndex) {
                            productContainer?.addView(View(this@DashboardActivity).apply {
                                setBackgroundColor(Color.rgb(226, 232, 240))
                            }, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(1)
                            ))
                        }
                    }
                }

                if (alertsList.isEmpty()) {
                    noAlertsText?.visibility = View.VISIBLE
                } else {
                    noAlertsText?.visibility = View.GONE
                    alertsList.take(3).forEachIndexed { index, alert ->
                        val row = LinearLayout(this@DashboardActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(10), 0, dp(10), 0)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(40)
                            )
                        }

                        val iconBackground = if (alert.critical) {
                            Color.rgb(252, 233, 233)
                        } else {
                            Color.rgb(255, 242, 220)
                        }

                        val iconColor = if (alert.critical) {
                            Color.rgb(193, 43, 43)
                        } else {
                            Color.rgb(185, 65, 42)
                        }

                        val icon = TextView(this@DashboardActivity).apply {
                            text = alert.icon
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setTextColor(iconColor)
                            background = roundedBackground(iconBackground, 50)
                        }

                        row.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))

                        val details = LinearLayout(this@DashboardActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(9), 0, dp(4), 0)
                        }

                        details.addView(TextView(this@DashboardActivity).apply {
                            text = alert.title
                            textSize = 11f
                            setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_primary))
                            maxLines = 1
                            includeFontPadding = false
                        })

                        details.addView(TextView(this@DashboardActivity).apply {
                            text = alert.description
                            textSize = 9f
                            setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_secondary))
                            maxLines = 1
                            includeFontPadding = false
                            setPadding(0, dp(2), 0, 0)
                        })

                        row.addView(details, LinearLayout.LayoutParams(0, -1, 1f))

                        val badgeColor = if (alert.critical) {
                            Color.rgb(252, 232, 232)
                        } else {
                            Color.rgb(250, 238, 216)
                        }

                        val badgeTextColor = if (alert.critical) {
                            Color.rgb(193, 43, 43)
                        } else {
                            Color.rgb(135, 83, 0)
                        }

                        val badge = TextView(this@DashboardActivity).apply {
                            text = alert.level
                            textSize = 8f
                            gravity = Gravity.CENTER
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(badgeTextColor)
                            background = roundedBackground(badgeColor, 12)
                        }

                        val badgeWidth = if (alert.critical) 56 else 45
                        row.addView(badge, LinearLayout.LayoutParams(dp(badgeWidth), dp(20)))

                        alertContainer?.addView(row)

                        if (index < alertsList.take(3).lastIndex) {
                            alertContainer?.addView(View(this@DashboardActivity).apply {
                                setBackgroundColor(Color.rgb(226, 232, 240))
                            }, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(1)
                            ))
                        }
                    }
                }
            }
    }

    private fun addCell(
        row: LinearLayout,
        value: String,
        weight: Float,
        size: Int,
        color: Int
    ) {
        val cell = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = 2
        }

        row.addView(
            cell,
            LinearLayout.LayoutParams(0, -1, weight)
        )
    }

    override fun onResume() {
        super.onResume()
        setupUserGreeting()
        updateNotificationBadge()
    }

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.notificationBadge) ?: return
        val count = NotificationHelper.getUnreadCount(this)
        if (count > 0) {
            badge.text = count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            Toast.makeText(this, "You are already on Home.", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.addProductButton)?.setOnClickListener {
            val intent = Intent(this, AddProductActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<TextView>(R.id.viewAllProductsText)?.setOnClickListener {
            val intent = Intent(this, InventoryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<TextView>(R.id.alertsViewAll)?.setOnClickListener {
            val intent = Intent(this, AlertsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<TextView>(R.id.profileInitials)?.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            Toast.makeText(this, "You are already on Home.", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.inventoryNav).setOnClickListener {
            val intent = Intent(this, InventoryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.addNav).setOnClickListener {
            val intent = Intent(this, AddProductActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.scanNav).setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.alertsNav).setOnClickListener {
            val intent = Intent(this, AlertsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.profileNav).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        profileListener?.remove()
        productsListener?.remove()
        super.onDestroy()
    }
}
