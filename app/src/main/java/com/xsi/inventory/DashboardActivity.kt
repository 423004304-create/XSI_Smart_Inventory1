
package com.xsi.inventory

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    private data class Product(
        val name: String,
        val sku: String,
        val quantity: String,
        val status: String,
        val price: String
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

        populateProducts()
        populateAlerts()
        setupNavigation()
    }

    private fun populateProducts() {
        val container = findViewById<LinearLayout>(R.id.productRows)

        val products = listOf(
            Product("Quick Throttle\nX2", "QT-X2-001", "143", "In stock", "₱28.00"),
            Product("Sport Tire 21\"", "TI-S21-004", "9", "Low", "₱112.00"),
            Product("Chain Set Pro", "CH-PRO-009", "0", "Out", "₱45.00"),
            Product("Brake Pad Set", "BP-STD-012", "56", "In stock", "₱19.50"),
            Product("Handlebar Grip", "HG-BLK-020", "310", "In stock", "₱9.00")
        )

        products.forEachIndexed { index, product ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), 0, dp(7), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(38)
                )
            }

            addCell(row, product.name, 1.35f, 10, Color.BLACK)
            addCell(row, product.sku, 1.15f, 10, Color.GRAY)
            addCell(row, product.quantity, 0.65f, 10, Color.DKGRAY)

            val statusColor = when (product.status) {
                "Out" -> Color.rgb(195, 45, 45)
                else -> Color.rgb(135, 83, 0)
            }

            val statusBackground = when (product.status) {
                "Out" -> Color.rgb(252, 234, 234)
                else -> Color.rgb(250, 238, 216)
            }

            val status = TextView(this).apply {
                text = product.status
                textSize = 7f
                gravity = Gravity.CENTER
                setTextColor(statusColor)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBackground(statusBackground, 12)
                includeFontPadding = false
            }

            row.addView(
                status,
                LinearLayout.LayoutParams(0, dp(19), 1.05f)
            )

            val price = TextView(this).apply {
                text = product.price
                textSize = 10f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setTextColor(Color.rgb(55, 55, 55))
                includeFontPadding = false
                maxLines = 1
            }

            row.addView(
                price,
                LinearLayout.LayoutParams(0, -2, 0.75f)
            )

            container.addView(row)

            if (index < products.lastIndex) {
                container.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(222, 222, 222))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ))
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

    private fun populateAlerts() {
        val container = findViewById<LinearLayout>(R.id.alertsTable)

        val alerts = listOf(
            StockAlert(
                "⚠",
                "Sport Tire 21\" - Low stock",
                "Only 8 units remaining",
                "Low",
                false
            ),
            StockAlert(
                "⬡",
                "Chain Set Pro — Out of stock",
                "Reorder needed immediately",
                "Critical",
                true
            )
        )

        alerts.forEachIndexed { index, alert ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(39)
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

            val icon = TextView(this).apply {
                text = alert.icon
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(iconColor)
                background = roundedBackground(iconBackground, 50)
            }

            row.addView(
                icon,
                LinearLayout.LayoutParams(dp(28), dp(28))
            )

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), 0, dp(4), 0)
            }

            details.addView(TextView(this).apply {
                text = alert.title
                textSize = 8f
                setTextColor(Color.rgb(30, 30, 30))
                maxLines = 1
                includeFontPadding = false
            })

            details.addView(TextView(this).apply {
                text = alert.description
                textSize = 7f
                setTextColor(Color.GRAY)
                maxLines = 1
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })

            row.addView(
                details,
                LinearLayout.LayoutParams(0, -1, 1f)
            )

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

            val badge = TextView(this).apply {
                text = alert.level
                textSize = 7f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(badgeTextColor)
                background = roundedBackground(badgeColor, 12)
            }

            val badgeWidth = if (alert.critical) 56 else 45

            row.addView(
                badge,
                LinearLayout.LayoutParams(dp(badgeWidth), dp(19))
            )

            container.addView(row)

            if (index < alerts.lastIndex) {
                container.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(222, 222, 222))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ))
            }
        }
    }

    private fun setupNavigation() {
        findViewById<Button>(R.id.addProductButton).setOnClickListener {
            showMessage("Add Product module coming next.")
        }

        findViewById<TextView>(R.id.viewAllProductsText).setOnClickListener {
            showMessage("Inventory module coming next.")
        }

        findViewById<TextView>(R.id.alertsViewAll).setOnClickListener {
            showMessage("Alerts module coming next.")
        }

        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            showMessage("You are already on Home.")
        }

        findViewById<LinearLayout>(R.id.inventoryNav).setOnClickListener {
            showMessage("Inventory module coming next.")
        }

        findViewById<LinearLayout>(R.id.addNav).setOnClickListener {
            showMessage("Add Product module coming next.")
        }

        findViewById<LinearLayout>(R.id.scanNav).setOnClickListener {
            showMessage("Barcode Scanner coming next.")
        }

        findViewById<LinearLayout>(R.id.alertsNav).setOnClickListener {
            showMessage("Alerts module coming next.")
        }

        findViewById<LinearLayout>(R.id.profileNav).setOnClickListener {
            showMessage("Profile module coming next.")
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

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
