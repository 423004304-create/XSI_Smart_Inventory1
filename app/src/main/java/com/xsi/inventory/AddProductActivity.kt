package com.xsi.inventory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class AddProductActivity : AppCompatActivity() {
    private var selectedPhotoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        setupBottomNavigation()
        updateNotificationBadge()

        val cancelButton = findViewById<Button>(R.id.cancelButton)
        val saveProductButton = findViewById<Button>(R.id.saveProductButton)

        val productNameInput = findViewById<EditText>(R.id.productNameInput)
        val skuInput = findViewById<EditText>(R.id.skuInput)
        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val quantityInput = findViewById<EditText>(R.id.quantityInput)
        val lowStockInput = findViewById<EditText>(R.id.lowStockInput)
        val priceInput = findViewById<EditText>(R.id.priceInput)
        val locationInput = findViewById<EditText>(R.id.locationInput)
        val descriptionInput = findViewById<EditText>(R.id.descriptionInput)
        val productPhotoPreview = findViewById<ImageView>(R.id.productPhotoPreview)
        val productPhotoPlaceholder = findViewById<TextView>(R.id.productPhotoPlaceholder)
        val removeProductPhotoButton = findViewById<Button>(R.id.removeProductPhotoButton)

        val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
                productPhotoPreview.setImageURI(uri)
                productPhotoPreview.visibility = View.VISIBLE
                productPhotoPlaceholder.visibility = View.GONE
                removeProductPhotoButton.visibility = View.VISIBLE
            }
        }
        findViewById<Button>(R.id.addProductPhotoButton).setOnClickListener {
            photoPicker.launch("image/*")
        }
        removeProductPhotoButton.setOnClickListener {
            selectedPhotoUri = null
            productPhotoPreview.setImageDrawable(null)
            productPhotoPreview.visibility = View.GONE
            productPhotoPlaceholder.visibility = View.VISIBLE
            removeProductPhotoButton.visibility = View.GONE
        }

        // Setup Category Spinner options
        val categories = arrayOf(
            "Engine",
            "Transmission",
            "Brakes",
            "Electrical",
            "Suspension",
            "Body & Frame",
            "Accessories",
            "General"
        )
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner?.adapter = categoryAdapter

        val editProductId = intent.getStringExtra("PRODUCT_ID")
        val isEditMode = !editProductId.isNullOrBlank()
        var originalQty = 0

        if (isEditMode) {
            saveProductButton.text = "Update Part"
            val headerTitle = findViewById<TextView>(R.id.headerTitle)
            if (headerTitle != null) {
                headerTitle.text = "Edit Motorcycle Part"
            }

            val name = intent.getStringExtra("PRODUCT_NAME") ?: ""
            val sku = intent.getStringExtra("SKU") ?: ""
            val category = intent.getStringExtra("CATEGORY") ?: "Engine"
            val qtyStr = intent.getStringExtra("QUANTITY") ?: "0"
            val minStock = intent.getStringExtra("MIN_STOCK") ?: "3"
            val price = intent.getStringExtra("PRICE") ?: "0.0"
            val location = intent.getStringExtra("LOCATION") ?: ""
            val description = intent.getStringExtra("DESCRIPTION") ?: ""
            val imageUrl = intent.getStringExtra("IMAGE_URL")

            originalQty = qtyStr.toIntOrNull() ?: 0

            productNameInput.setText(name)
            skuInput.setText(sku)
            quantityInput.setText(qtyStr)
            lowStockInput.setText(minStock)
            priceInput.setText(price)
            locationInput.setText(location)
            descriptionInput.setText(description)

            val catIndex = categories.indexOf(category)
            if (catIndex >= 0) {
                categorySpinner.setSelection(catIndex)
            }

            if (!imageUrl.isNullOrBlank()) {
                Glide.with(this).load(imageUrl).into(productPhotoPreview)
                productPhotoPreview.visibility = View.VISIBLE
                productPhotoPlaceholder.visibility = View.GONE
                removeProductPhotoButton.visibility = View.VISIBLE
            }
        } else {
            val scannedSku = intent.getStringExtra("SKU")
            if (!scannedSku.isNullOrBlank()) {
                skuInput?.setText(scannedSku)
            }

            val detectedName = intent.getStringExtra("PRODUCT_NAME")
            if (!detectedName.isNullOrBlank()) {
                productNameInput?.setText(detectedName)
            }
        }

        cancelButton?.setOnClickListener { finish() }

        saveProductButton?.setOnClickListener {
            val name = productNameInput?.text?.toString()?.trim() ?: ""
            val sku = skuInput?.text?.toString()?.trim() ?: ""
            val category = categorySpinner?.selectedItem?.toString() ?: "Engine"
            val qtyStr = quantityInput?.text?.toString()?.trim() ?: "0"
            val lowStockStr = lowStockInput?.text?.toString()?.trim() ?: "3"
            val priceStr = priceInput?.text?.toString()?.trim() ?: "0"
            val location = locationInput?.text?.toString()?.trim() ?: ""
            val description = descriptionInput?.text?.toString()?.trim() ?: ""

            when {
                name.isEmpty() || sku.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty() -> {
                    Toast.makeText(this, "Please fill in required part details.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val qty = qtyStr.toIntOrNull() ?: 0
                    val lowStock = lowStockStr.toIntOrNull() ?: 3
                    val priceVal = priceStr.toDoubleOrNull() ?: 0.0

                    saveProductButton.isEnabled = false

                    val db = FirebaseFirestore.getInstance()
                    val docRef = if (isEditMode && editProductId != null) {
                        db.collection("products").document(editProductId)
                    } else {
                        db.collection("products").document()
                    }
                    val productId = docRef.id

                    val productMap = hashMapOf<String, Any>(
                        "id" to productId,
                        "name" to name,
                        "sku" to sku,
                        "category" to category,
                        "quantity" to qty,
                        "minStock" to lowStock,
                        "price" to priceVal,
                        "location" to location,
                        "description" to description
                    )

                    val photoUri = selectedPhotoUri
                    if (photoUri == null) {
                        saveProductDocument(db, docRef, productMap, name, qty, saveProductButton, isEditMode, originalQty)
                    } else {
                        val photoRef = FirebaseStorage.getInstance().reference
                            .child("product-images/$productId/${System.currentTimeMillis()}.jpg")
                        photoRef.putFile(photoUri)
                            .continueWithTask { uploadTask ->
                                if (!uploadTask.isSuccessful) {
                                    throw uploadTask.exception
                                        ?: IllegalStateException("Product photo upload failed.")
                                }
                                photoRef.downloadUrl
                            }
                            .addOnSuccessListener { downloadUri ->
                                productMap["imageUrl"] = downloadUri.toString()
                                productMap["imagePath"] = photoRef.path
                                saveProductDocument(
                                    db, docRef, productMap, name, qty, saveProductButton, isEditMode, originalQty, photoRef
                                )
                            }
                            .addOnFailureListener { error ->
                                saveProductButton.isEnabled = true
                                Toast.makeText(
                                    this,
                                    "Photo upload failed: ${error.message ?: "Please try again."}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }
            }
        }
    }

    private fun saveProductDocument(
        db: FirebaseFirestore,
        docRef: DocumentReference,
        productMap: HashMap<String, Any>,
        name: String,
        quantity: Int,
        saveButton: Button,
        isEditMode: Boolean = false,
        originalQty: Int = 0,
        uploadedPhoto: StorageReference? = null
    ) {
        val saveTask = if (isEditMode) {
            docRef.set(productMap, SetOptions.merge())
        } else {
            docRef.set(productMap)
        }

        saveTask.addOnCompleteListener { task ->
            saveButton.isEnabled = true
            if (task.isSuccessful) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val dateStr = dateFormat.format(Date())

                if (!isEditMode) {
                    val movementRef = db.collection("movements").document()
                    val movementMap = hashMapOf<String, Any>(
                        "id" to movementRef.id,
                        "productId" to docRef.id,
                        "productName" to name,
                        "type" to "IN",
                        "quantity" to quantity,
                        "user" to (FirebaseAuth.getInstance().currentUser?.email ?: "System"),
                        "date" to dateStr,
                        "note" to "Opening stock"
                    )
                    movementRef.set(movementMap)
                    Toast.makeText(this, "Part '$name' added successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    val delta = quantity - originalQty
                    if (delta != 0) {
                        val movementRef = db.collection("movements").document()
                        val movementType = if (delta > 0) "IN" else "OUT"
                        val movementMap = hashMapOf<String, Any>(
                            "id" to movementRef.id,
                            "productId" to docRef.id,
                            "productName" to name,
                            "type" to movementType,
                            "quantity" to abs(delta),
                            "user" to (FirebaseAuth.getInstance().currentUser?.email ?: "System"),
                            "date" to dateStr,
                            "note" to "Quantity adjusted while editing product"
                        )
                        movementRef.set(movementMap)
                    }
                    Toast.makeText(this, "Part '$name' updated successfully!", Toast.LENGTH_SHORT).show()
                }
                finish()
            } else {
                uploadedPhoto?.delete()
                    ?.addOnFailureListener { error ->
                        Log.e("AddProductActivity", "Could not remove photo after product save failed.", error)
                    }
                Toast.makeText(
                    this,
                    "Failed to save product: ${task.exception?.message ?: "Please try again."}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
        findViewById<ImageView>(R.id.xaveryLogo)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.homeNav)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.inventoryNav)?.setOnClickListener {
            startActivity(Intent(this, InventoryActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.addNav)?.setOnClickListener {
            // Already here
        }
        findViewById<LinearLayout>(R.id.scanNav)?.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.alertsNav)?.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.profileNav)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }
}
