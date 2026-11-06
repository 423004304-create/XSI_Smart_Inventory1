package com.xsi.inventory

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class WarehouseSettingsActivity : AppCompatActivity() {

    private val PREFS = "xsi_warehouse_prefs"
    private var warehousesListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    private data class WarehouseItem(
        val id: String = "",
        val name: String = "",
        val location: String = ""
    )

    private val warehouseList = mutableListOf<WarehouseItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warehouse_settings)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val defaultWarehouseSpinner = findViewById<Spinner>(R.id.defaultWarehouseSpinner)
        val lowStockThresholdInput = findViewById<EditText>(R.id.lowStockThresholdInput)
        val currencySpinner = findViewById<Spinner>(R.id.currencySpinner)
        val timezoneSpinner = findViewById<Spinner>(R.id.timezoneSpinner)
        val addWarehouseButton = findViewById<Button>(R.id.addWarehouseButton)
        val saveButton = findViewById<Button>(R.id.saveWarehouseButton)

        val currencies = arrayOf("PHP (₱)", "USD ($)", "EUR (€)")
        val timezones = arrayOf("Asia/Manila", "UTC", "America/New_York")

        currencySpinner?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencies)
        timezoneSpinner?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timezones)

        loadSettingsFromFirestore(defaultWarehouseSpinner, lowStockThresholdInput, currencySpinner, timezoneSpinner, currencies, timezones)
        listenToWarehousesFromFirestore(defaultWarehouseSpinner)

        addWarehouseButton?.setOnClickListener {
            showAddWarehouseDialog()
        }

        saveButton?.setOnClickListener {
            val selectedWarehouse = defaultWarehouseSpinner?.selectedItem?.toString() ?: ""
            val thresholdStr = lowStockThresholdInput?.text?.toString()?.replace(Regex("[^0-9]"), "")?.trim() ?: "10"
            val thresholdVal = thresholdStr.toIntOrNull() ?: 10
            val currency = currencySpinner?.selectedItem?.toString() ?: currencies[0]
            val timezone = timezoneSpinner?.selectedItem?.toString() ?: timezones[0]

            val db = FirebaseFirestore.getInstance()
            val settingsMap = hashMapOf<String, Any>(
                "defaultWarehouse" to selectedWarehouse,
                "lowStockThreshold" to thresholdVal,
                "currency" to currency,
                "timezone" to timezone
            )

            // Save to Firestore settings document
            db.collection("settings").document("warehouse")
                .set(settingsMap, SetOptions.merge())
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Caching in local SharedPreferences fallback
                        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                        prefs.edit()
                            .putString("default_warehouse", selectedWarehouse)
                            .putInt("low_stock_threshold", thresholdVal)
                            .putString("currency", currency)
                            .putString("timezone", timezone)
                            .apply()

                        Toast.makeText(this, "Warehouse settings saved successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to save settings: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun listenToWarehousesFromFirestore(defaultWarehouseSpinner: Spinner?) {
        val db = FirebaseFirestore.getInstance()
        warehousesListener = db.collection("warehouses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Could not load warehouses: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                warehouseList.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val name = doc.getString("name") ?: continue
                        val loc = doc.getString("location") ?: ""
                        warehouseList.add(WarehouseItem(doc.id, name, loc))
                    }
                } else {
                    // Seed initial defaults if collection is empty
                    seedDefaultWarehouses(db)
                }
                renderWarehouseRows(defaultWarehouseSpinner)
            }
    }

    private fun seedDefaultWarehouses(db: FirebaseFirestore) {
        val initialDefaults = listOf(
            WarehouseItem("", "Warehouse A", "Sampaloc, Manila"),
            WarehouseItem("", "Warehouse B", "Araneta Avenue, Quezon City"),
            WarehouseItem("", "Warehouse C", "Malabon")
        )

        for (item in initialDefaults) {
            val docRef = db.collection("warehouses").document()
            val data = hashMapOf(
                "id" to docRef.id,
                "name" to item.name,
                "location" to item.location,
                "createdAt" to Timestamp.now()
            )
            docRef.set(data)
        }
    }

    private fun renderWarehouseRows(defaultWarehouseSpinner: Spinner?) {
        val container = findViewById<LinearLayout>(R.id.warehousesListContainer) ?: return
        container.removeAllViews()

        val headerTitle = TextView(this).apply {
            text = "Warehouses"
            setTextColor(Color.parseColor("#222222"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(headerTitle)

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, dp(10), 0, dp(14))
            }
        }
        container.addView(divider)

        warehouseList.forEachIndexed { index, item ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                background = AppCompatResources.getDrawable(this@WarehouseSettingsActivity, typedValue.resourceId)
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageResource(R.drawable.ic_warehouse_building)
                setBackgroundResource(R.drawable.profile_circle_background)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            rowLayout.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleView = TextView(this).apply {
                text = item.name
                setTextColor(Color.parseColor("#222222"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }
            val locView = TextView(this).apply {
                text = "📍 ${item.location}"
                setTextColor(Color.parseColor("#777777"))
                textSize = 11f
            }
            infoLayout.addView(titleView)
            infoLayout.addView(locView)
            rowLayout.addView(infoLayout)

            val editIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_edit)
                contentDescription = "Edit"
            }
            rowLayout.addView(editIcon, LinearLayout.LayoutParams(dp(22), dp(22)))

            rowLayout.setOnClickListener {
                showEditWarehouseDialog(item)
            }

            container.addView(rowLayout)

            if (index < warehouseList.lastIndex) {
                val rowDivider = View(this).apply {
                    setBackgroundColor(Color.parseColor("#EEEEEE"))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        setMargins(0, dp(8), 0, dp(8))
                    }
                }
                container.addView(rowDivider)
            }
        }

        // Update default warehouse spinner options
        val spinnerItems = warehouseList.map { "${it.name} (${it.location})" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems)
        defaultWarehouseSpinner?.adapter = adapter
    }

    private fun showAddWarehouseDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Warehouse Name (e.g. Warehouse D)"
        }
        val locationInput = EditText(this).apply {
            hint = "Location (e.g. Valenzuela City)"
        }

        dialogLayout.addView(nameInput)
        dialogLayout.addView(locationInput)

        AlertDialog.Builder(this)
            .setTitle("Add New Warehouse")
            .setView(dialogLayout)
            .setPositiveButton("Add") { _, _ ->
                val whName = nameInput.text.toString().trim()
                val whLoc = locationInput.text.toString().trim()
                if (whName.isNotEmpty() && whLoc.isNotEmpty()) {
                    val db = FirebaseFirestore.getInstance()
                    val docRef = db.collection("warehouses").document()
                    val data = hashMapOf<String, Any>(
                        "id" to docRef.id,
                        "name" to whName,
                        "location" to whLoc,
                        "createdAt" to Timestamp.now()
                    )
                    docRef.set(data).addOnSuccessListener {
                        Toast.makeText(this, "$whName added successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter both warehouse name and location.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditWarehouseDialog(item: WarehouseItem) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Warehouse Name"
            setText(item.name)
            setSelection(text.length)
        }
        val locationInput = EditText(this).apply {
            hint = "Warehouse Location"
            setText(item.location)
            setSelection(text.length)
        }

        dialogLayout.addView(nameInput)
        dialogLayout.addView(locationInput)

        AlertDialog.Builder(this)
            .setTitle("Edit Warehouse")
            .setView(dialogLayout)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString().trim()
                val newLoc = locationInput.text.toString().trim()
                if (newName.isNotEmpty() && newLoc.isNotEmpty()) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("warehouses").document(item.id)
                        .update("name", newName, "location", newLoc)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Warehouse updated successfully!", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Warehouse?")
                    .setMessage("Are you sure you want to delete ${item.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        FirebaseFirestore.getInstance().collection("warehouses").document(item.id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "${item.name} deleted.", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    private fun loadSettingsFromFirestore(
        defaultWarehouseSpinner: Spinner?,
        lowStockInput: EditText?,
        currencySpinner: Spinner?,
        timezoneSpinner: Spinner?,
        currencies: Array<String>,
        timezones: Array<String>
    ) {
        val db = FirebaseFirestore.getInstance()
        settingsListener = db.collection("settings").document("warehouse")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val defaultWh = snapshot.getString("defaultWarehouse") ?: ""
                    val threshold = snapshot.getLong("lowStockThreshold") ?: 10L
                    val currency = snapshot.getString("currency") ?: currencies[0]
                    val timezone = snapshot.getString("timezone") ?: timezones[0]

                    lowStockInput?.setText("$threshold units")

                    if (defaultWarehouseSpinner?.adapter != null && defaultWh.isNotEmpty()) {
                        val adapter = defaultWarehouseSpinner.adapter
                        for (i in 0 until adapter.count) {
                            if (adapter.getItem(i).toString().startsWith(defaultWh)) {
                                defaultWarehouseSpinner.setSelection(i)
                                break
                            }
                        }
                    }

                    val currIdx = currencies.indexOf(currency)
                    if (currIdx >= 0) currencySpinner?.setSelection(currIdx)

                    val tzIdx = timezones.indexOf(timezone)
                    if (tzIdx >= 0) timezoneSpinner?.setSelection(tzIdx)
                }
            }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        warehousesListener?.remove()
        settingsListener?.remove()
        super.onDestroy()
    }
}
