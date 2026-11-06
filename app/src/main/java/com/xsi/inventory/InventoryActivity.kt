package com.xsi.inventory

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class InventoryActivity : AppCompatActivity() {

    private val allItems = mutableListOf<InventoryItem>()
    private val adapter = InventoryAdapter()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var activeFilter: String? = null
    private var currentSearchQuery: String = ""
    private var productsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory)

        findViewById<Button>(R.id.addInventoryProductButton).setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        setupRecyclerView()
        setupBottomNavigation()
        updateNotificationBadge()

        val searchBar = findViewById<EditText>(R.id.searchBar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                searchHandler.removeCallbacksAndMessages(null)
                searchHandler.postDelayed({
                    applyFilters()
                }, 150) // 150ms debounce for smooth typing
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val filterAll = findViewById<Button>(R.id.filterAll)
        val filterInStock = findViewById<Button>(R.id.filterInStock)
        val filterLow = findViewById<Button>(R.id.filterLow)
        val filterOut = findViewById<Button>(R.id.filterOut)

        val buttons = listOf(filterAll, filterInStock, filterLow, filterOut)

        fun selectChip(selected: Button, statusFilter: String?) {
            buttons.forEach { btn ->
                if (btn == selected) {
                    btn.background = ContextCompat.getDrawable(this, R.drawable.login_button_background)
                    btn.setTextColor(ContextCompat.getColor(this, R.color.button_text_color))
                } else {
                    btn.background = ContextCompat.getDrawable(this, R.drawable.dashboard_outline_button)
                    btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
            }
            activeFilter = statusFilter
            applyFilters()
        }

        filterAll.setOnClickListener { selectChip(filterAll, null) }
        filterInStock.setOnClickListener { selectChip(filterInStock, "In stock") }
        filterLow.setOnClickListener { selectChip(filterLow, "Low") }
        filterOut.setOnClickListener { selectChip(filterOut, "Out") }

    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.inventoryRecyclerView)
        adapter.onItemClickListener = { item ->
            FirebaseFirestore.getInstance().collection("products").document(item.id).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) return@addOnSuccessListener
                    val name = doc.getString("name") ?: item.name
                    val sku = doc.getString("sku") ?: item.sku
                    val category = doc.getString("category") ?: "General"
                    val qty = doc.get("quantity")?.toString() ?: item.quantity
                    val minStock = doc.get("minStock")?.toString() ?: "3"
                    val priceVal = when (val p = doc.get("price")) {
                        is Double -> p
                        is Long -> p.toDouble()
                        is Number -> p.toDouble()
                        is String -> p.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val location = doc.getString("location") ?: "N/A"
                    val description = doc.getString("description") ?: "N/A"
                    val imageUrl = doc.getString("imageUrl") ?: ""

                    val detailsMsg = "SKU: $sku\nCategory: $category\nStock: $qty units (Min: $minStock)\nPrice: ₱${String.format(Locale.getDefault(), "%,.2f", priceVal)}\nLocation: $location\nDescription: $description"

                    AlertDialog.Builder(this)
                        .setTitle(name)
                        .setMessage(detailsMsg)
                        .setPositiveButton("Edit Part") { _, _ ->
                            val intent = Intent(this, AddProductActivity::class.java).apply {
                                putExtra("PRODUCT_ID", item.id)
                                putExtra("PRODUCT_NAME", name)
                                putExtra("SKU", sku)
                                putExtra("CATEGORY", category)
                                putExtra("QUANTITY", qty)
                                putExtra("MIN_STOCK", minStock)
                                putExtra("PRICE", priceVal.toString())
                                putExtra("LOCATION", location)
                                putExtra("DESCRIPTION", description)
                                putExtra("IMAGE_URL", imageUrl)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Delete") { _, _ ->
                            AlertDialog.Builder(this)
                                .setTitle("Delete $name?")
                                .setMessage("Are you sure you want to delete this part from inventory?")
                                .setPositiveButton("Delete") { _, _ ->
                                    deleteProductFromFirestore(item.id)
                                    Toast.makeText(this, "$name deleted from inventory.", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        .setNeutralButton("Close", null)
                        .show()
                }
        }
        recyclerView?.apply {
            layoutManager = LinearLayoutManager(this@InventoryActivity)
            adapter = this@InventoryActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupBottomNavigation() {
        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
        findViewById<LinearLayout>(R.id.inventoryNav).setOnClickListener {
            // Already here
        }
        findViewById<LinearLayout>(R.id.addNav).setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<LinearLayout>(R.id.scanNav).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<LinearLayout>(R.id.alertsNav).setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<LinearLayout>(R.id.profileNav).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchFirestoreProducts()
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

    private fun fetchFirestoreProducts() {
        productsListener?.remove()
        productsListener = FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Live inventory updates failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val firestoreList = mutableListOf<InventoryItem>()
                for (document in snapshot.documents) {
                    val id = document.id
                    val name = document.getString("name") ?: continue
                    val sku = document.getString("sku") ?: ""
                    
                    val qty = when (val q = document.get("quantity")) {
                        is Long -> q.toInt()
                        is Int -> q
                        is Number -> q.toInt()
                        is String -> q.toIntOrNull() ?: 0
                        else -> 0
                    }

                    val minStock = when (val m = document.get("minStock") ?: document.get("lowStockThreshold")) {
                        is Long -> m.toInt()
                        is Int -> m
                        is Number -> m.toInt()
                        is String -> m.toIntOrNull() ?: 10
                        else -> 10
                    }

                    val priceVal = when (val p = document.get("price")) {
                        is Double -> p
                        is Long -> p.toDouble()
                        is Number -> p.toDouble()
                        is String -> p.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val price = "₱${String.format(Locale.getDefault(), "%,.2f", priceVal)}"

                    val status = when {
                        qty == 0 -> "Out"
                        qty <= minStock -> "Low"
                        else -> "In stock"
                    }

                    firestoreList.add(InventoryItem(id, name, sku, qty.toString(), status, price))
                }
                allItems.clear()
                allItems.addAll(firestoreList)
                applyFilters()
            }
    }

    fun updateProductInFirestore(id: String, updates: Map<String, Any>) {
        FirebaseFirestore.getInstance().collection("products").document(id)
            .update(updates)
    }

    fun deleteProductFromFirestore(id: String) {
        FirebaseFirestore.getInstance().collection("products").document(id)
            .delete()
    }

    private fun applyFilters() {
        var filteredList: List<InventoryItem> = allItems

        val filter = activeFilter
        if (filter != null) {
            filteredList = filteredList.filter { it.status.equals(filter, ignoreCase = true) }
        }

        val query = currentSearchQuery.trim()
        if (query.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.sku.contains(query, ignoreCase = true)
            }
        }

        adapter.submitList(filteredList)
    }

    override fun onDestroy() {
        productsListener?.remove()
        productsListener = null
        super.onDestroy()
        searchHandler.removeCallbacksAndMessages(null)
    }
}
