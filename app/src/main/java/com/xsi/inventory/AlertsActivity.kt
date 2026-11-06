package com.xsi.inventory

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.widget.Toast

class AlertsActivity : AppCompatActivity() {
    private var productsListener: ListenerRegistration? = null

    private data class StockAlert(
        val icon: String,
        val title: String,
        val description: String,
        val level: String,
        val type: String // "critical", "low", "info"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        setupBottomNavigation()
        populateAlerts()
        updateNotificationBadge()
    }

    override fun onResume() {
        super.onResume()
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

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.inventoryNav).setOnClickListener {
            startActivity(Intent(this, InventoryActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.addNav).setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.scanNav).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.alertsNav).setOnClickListener {
            // Already here
        }
        findViewById<LinearLayout>(R.id.profileNav).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }

    private fun populateAlerts() {
        val container = findViewById<LinearLayout>(R.id.alertsContainer) ?: return
        val isAllRead = NotificationHelper.isRead(this)

        productsListener = FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Live alert updates failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                container.removeAllViews()
                val alerts = mutableListOf<StockAlert>()
                for (document in snapshot.documents) {
                    val name = document.getString("name") ?: continue
                    val qty = when (val q = document.get("quantity")) {
                        is Long -> q.toInt()
                        is Int -> q
                        is Number -> q.toInt()
                        is String -> q.toIntOrNull() ?: 0
                        else -> 0
                    }
                    val threshold = when (val m = document.get("minStock") ?: document.get("lowStockThreshold")) {
                        is Long -> m.toInt()
                        is Int -> m
                        is Number -> m.toInt()
                        is String -> m.toIntOrNull() ?: 10
                        else -> 10
                    }

                    if (qty == 0) {
                        alerts.add(StockAlert("⬡", "$name — Out of stock", "0 units · Reorder immediately", "Critical", "critical"))
                    } else if (qty <= threshold) {
                        alerts.add(StockAlert("⚠", "$name — Low stock", "$qty units · Threshold: $threshold", "Low", "low"))
                    }
                }

                NotificationHelper.currentActiveAlertsCount = alerts.size
                val countText = findViewById<TextView>(R.id.activeAlertsCountText)
                countText?.text = getString(R.string.active_alerts_count, alerts.size)

                renderAlertRows(container, alerts, isAllRead)
            }
    }

    private fun renderAlertRows(container: LinearLayout, alerts: List<StockAlert>, isAllRead: Boolean) {
        alerts.forEachIndexed { index, alert ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (isAllRead) {
                    alpha = 0.7f
                }

            }

            val (iconBg, iconColor) = when (alert.type) {
                "critical" -> Pair(Color.rgb(252, 233, 233), Color.rgb(193, 43, 43))
                "low" -> Pair(Color.rgb(255, 242, 220), Color.rgb(185, 65, 42))
                else -> Pair(Color.rgb(232, 242, 252), Color.rgb(30, 103, 169))
            }

            val icon = TextView(this).apply {
                text = alert.icon
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(iconColor)
                background = roundedBackground(iconBg, 50)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(6), 0)
            }

            details.addView(TextView(this).apply {
                text = alert.title
                textSize = 13f
                setTextColor(Color.rgb(21, 21, 21))
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            })

            details.addView(TextView(this).apply {
                text = alert.description
                textSize = 10f
                setTextColor(Color.rgb(119, 119, 119))
                maxLines = 1
                setPadding(0, dp(2), 0, 0)
            })

            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))

            val (badgeBg, badgeTextColor) = if (isAllRead) {
                Pair(Color.rgb(240, 240, 240), Color.GRAY)
            } else {
                when (alert.type) {
                    "critical" -> Pair(Color.rgb(252, 232, 232), Color.rgb(193, 43, 43))
                    "low" -> Pair(Color.rgb(250, 238, 216), Color.rgb(135, 83, 0))
                    else -> Pair(Color.rgb(222, 238, 252), Color.rgb(30, 103, 169))
                }
            }

            val badge = TextView(this).apply {
                text = if (isAllRead) "Read" else alert.level
                textSize = 10f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(badgeTextColor)
                background = roundedBackground(badgeBg, 12)
            }
            val badgeWidth = if (isAllRead) 48 else when (alert.type) {
                "critical" -> 64
                "low" -> 50
                else -> 48
            }
            row.addView(badge, LinearLayout.LayoutParams(dp(badgeWidth), dp(24)))

            container.addView(row)

            if (index < alerts.lastIndex) {
                container.addView(
                    View(this).apply { setBackgroundColor(Color.rgb(238, 238, 238)) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                )
            }
        }
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
        productsListener?.remove()
        super.onDestroy()
    }
}
