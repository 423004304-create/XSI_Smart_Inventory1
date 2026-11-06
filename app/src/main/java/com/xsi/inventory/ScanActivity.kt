package com.xsi.inventory

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.xsi.inventory.network.FlaskRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

@SuppressLint("SetTextI18n")
class ScanActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private var lastScannedBarcode: String? = null
    private var lastScanTime: Long = 0L

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        setupBottomNavigation()
        updateNotificationBadge()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        val scanResultCard = findViewById<LinearLayout>(R.id.scanResultCard)
        val nameView = findViewById<TextView>(R.id.scannedProductName)
        val skuView = findViewById<TextView>(R.id.scannedProductSku)
        val qtyView = findViewById<TextView>(R.id.scannedProductQty)
        val priceView = findViewById<TextView>(R.id.scannedProductPrice)

        findViewById<Button>(R.id.snapDetectButton)?.setOnClickListener {
            if (allPermissionsGranted()) {
                snapAndDetectPart()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
            }
        }

        findViewById<Button>(R.id.useCameraButton).setOnClickListener {
            if (allPermissionsGranted()) {
                startCamera()
                Toast.makeText(this, "Camera active. Point at barcode.", Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
            }
        }

        findViewById<Button>(R.id.enterManuallyButton).setOnClickListener {
            val input = EditText(this).apply {
                hint = "Enter SKU (e.g. QT-X2-001)"
            }
            AlertDialog.Builder(this)
                .setTitle("Enter SKU Manually")
                .setView(input)
                .setPositiveButton("Search") { _, _ ->
                    val sku = input.text.toString().trim()
                    if (sku.isNotEmpty()) {
                        lookupProductAndDisplay(sku, scanResultCard, nameView, skuView, qtyView, priceView)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun snapAndDetectPart() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, "Camera initialized. Point at part and try again.", Toast.LENGTH_SHORT).show()
            startCamera()
            return
        }

        val photoFile = File(cacheDir, "temp_part_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        Toast.makeText(this, "Capturing photo & analyzing part with AI...", Toast.LENGTH_SHORT).show()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processImageDetection(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@ScanActivity, "Failed to capture image: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processImageDetection(photoFile: File) {
        lifecycleScope.launch {
            Toast.makeText(this@ScanActivity, "Sending image to AI detection server...", Toast.LENGTH_SHORT).show()
            val detections = FlaskRepository.uploadAndPredict(photoFile)

            if (photoFile.exists()) {
                photoFile.delete()
            }

            if (detections == null) {
                Toast.makeText(this@ScanActivity, "Detection failed or server offline. Check Flask server connection.", Toast.LENGTH_LONG).show()
            } else if (detections.isEmpty()) {
                Toast.makeText(this@ScanActivity, "Server online! No trained parts recognized in this photo. Try a closer angle.", Toast.LENGTH_LONG).show()
            } else {
                val topDetection = detections.maxByOrNull { it.confidence }
                if (topDetection != null) {
                    val className = topDetection.className
                    val confidencePct = String.format(Locale.getDefault(), "%.1f%%", topDetection.confidence * 100)
                    Toast.makeText(this@ScanActivity, "AI Detected: $className ($confidencePct confidence)", Toast.LENGTH_LONG).show()
                    lookupProductByPartNameOrSku(className, confidencePct)
                } else {
                    Toast.makeText(this@ScanActivity, "No motorcycle parts recognized.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun lookupProductByPartNameOrSku(partName: String, confidence: String) {
        val scanResultCard = findViewById<LinearLayout>(R.id.scanResultCard)
        val nameView = findViewById<TextView>(R.id.scannedProductName)
        val skuView = findViewById<TextView>(R.id.scannedProductSku)
        val qtyView = findViewById<TextView>(R.id.scannedProductQty)
        val priceView = findViewById<TextView>(R.id.scannedProductPrice)

        FirebaseFirestore.getInstance().collection("products").get()
            .addOnSuccessListener { snapshot ->
                var foundDoc: DocumentSnapshot? = null
                for (doc in snapshot.documents) {
                    val dbName = doc.getString("name") ?: ""
                    val dbSku = doc.getString("sku") ?: ""
                    val dbCategory = doc.getString("category") ?: ""

                    if (dbName.contains(partName, ignoreCase = true) ||
                        dbSku.contains(partName, ignoreCase = true) ||
                        dbCategory.contains(partName, ignoreCase = true) ||
                        partName.contains(dbName, ignoreCase = true)
                    ) {
                        foundDoc = doc
                        break
                    }
                }

                if (foundDoc != null) {
                    val doc = foundDoc
                    val dbName = doc.getString("name") ?: partName
                    val dbSku = doc.getString("sku") ?: "N/A"
                    val currentQty = when (val q = doc.get("quantity")) {
                        is Long -> q.toInt()
                        is Int -> q
                        is Number -> q.toInt()
                        is String -> q.toIntOrNull() ?: 0
                        else -> 0
                    }
                    val priceVal = when (val p = doc.get("price")) {
                        is Double -> p
                        is Long -> p.toDouble()
                        is Number -> p.toDouble()
                        is String -> p.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val price = "₱${String.format(Locale.getDefault(), "%,.2f", priceVal)}"
                    val boxQty = when (val b = doc.get("boxQuantity")) {
                        is Long -> b.toInt()
                        is Int -> b
                        is Number -> b.toInt()
                        is String -> b.toIntOrNull() ?: 25
                        else -> 25
                    }
                    nameView.text = "🤖 AI Detected: $dbName"
                    skuView.text = "SKU: $dbSku  |  Confidence: $confidence  |  📦 $boxQty units/box"
                    qtyView.text = "Current Stock: $currentQty units"
                    priceView.text = price
                    scanResultCard.visibility = View.VISIBLE

                    val receiveButton = findViewById<Button>(R.id.receiveBoxButton)
                    receiveButton?.text = "Receive Box into Inventory (+$boxQty units)"
                    receiveButton?.setOnClickListener {
                        adjustProductStock(doc, dbName, boxQty, "Received box via AI scan") { newQty ->
                            Toast.makeText(this, "Added $boxQty units to $dbName. New stock: $newQty", Toast.LENGTH_LONG).show()
                            qtyView.text = "Current Stock: $newQty units"
                        }
                    }

                    val sellButton = findViewById<Button>(R.id.sellItemButton)
                    sellButton?.text = "Sell Item / Checkout (-1 unit)"
                    sellButton?.setOnClickListener {
                        adjustProductStock(doc, dbName, -1, "POS Sale - Sold via AI scan/checkout") { newQty ->
                            Toast.makeText(this, "Sold 1 unit of $dbName. Remaining stock: $newQty", Toast.LENGTH_LONG).show()
                            qtyView.text = "Current Stock: $newQty units"
                        }
                    }
                } else {
                    nameView.text = "🤖 AI Detected: $partName ($confidence)"
                    skuView.text = "Part not listed in inventory database yet"
                    qtyView.text = "Stock: 0 units"
                    priceView.text = "₱0.00"
                    scanResultCard.visibility = View.VISIBLE

                    val receiveButton = findViewById<Button>(R.id.receiveBoxButton)
                    receiveButton?.text = "Add Part to Inventory"
                    receiveButton?.setOnClickListener {
                        val intent = Intent(this, AddProductActivity::class.java).apply {
                            putExtra("PRODUCT_NAME", partName)
                        }
                        startActivity(intent)
                    }

                    val sellButton = findViewById<Button>(R.id.sellItemButton)
                    sellButton?.text = "Not in Inventory"
                    sellButton?.setOnClickListener {
                        Toast.makeText(this, "Part not found in inventory.", Toast.LENGTH_SHORT).show()
                    }

                    AlertDialog.Builder(this)
                        .setTitle("AI Part Detected: $partName")
                        .setMessage("Detected '$partName' with $confidence confidence, but it is not in your inventory database yet. Would you like to add it?")
                        .setPositiveButton("Add Product") { _, _ ->
                            val intent = Intent(this, AddProductActivity::class.java).apply {
                                putExtra("PRODUCT_NAME", partName)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error querying database.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun lookupProductAndDisplay(
        sku: String,
        scanResultCard: LinearLayout,
        nameView: TextView,
        skuView: TextView,
        qtyView: TextView,
        priceView: TextView
    ) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("products")
            .whereEqualTo("sku", sku)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val directMatch = querySnapshot.documents.firstOrNull()
                if (directMatch != null) {
                    bindScannedProduct(directMatch, sku, scanResultCard, nameView, skuView, qtyView, priceView)
                } else {
                    firestore.collection("products").get().addOnSuccessListener { snapshot ->
                        val fallbackMatch = snapshot.documents.firstOrNull { doc ->
                            (doc.getString("sku") ?: "").equals(sku, ignoreCase = true)
                        }
                        if (fallbackMatch != null) {
                            bindScannedProduct(fallbackMatch, sku, scanResultCard, nameView, skuView, qtyView, priceView)
                        } else {
                            nameView.text = "Product not in inventory"
                            skuView.text = "SKU: $sku"
                            qtyView.text = "Stock: 0 units"
                            priceView.text = "₱0.00"
                            scanResultCard.visibility = View.VISIBLE

                            val receiveButton = findViewById<Button>(R.id.receiveBoxButton)
                            receiveButton?.text = "Product Not Found"
                            receiveButton?.setOnClickListener {
                                val intent = Intent(this, AddProductActivity::class.java).apply {
                                    putExtra("SKU", sku)
                                }
                                startActivity(intent)
                            }

                            val sellButton = findViewById<Button>(R.id.sellItemButton)
                            sellButton?.text = "Product Not Found"
                            sellButton?.setOnClickListener {
                                Toast.makeText(this, "Product not found in inventory.", Toast.LENGTH_SHORT).show()
                            }

                            AlertDialog.Builder(this)
                                .setTitle("Product Not Found")
                                .setMessage("SKU $sku is not in your inventory yet. Would you like to add it now?")
                                .setPositiveButton("Add Product") { _, _ ->
                                    val intent = Intent(this, AddProductActivity::class.java).apply {
                                        putExtra("SKU", sku)
                                    }
                                    startActivity(intent)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error looking up SKU", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bindScannedProduct(
        doc: DocumentSnapshot,
        sku: String,
        scanResultCard: LinearLayout,
        nameView: TextView,
        skuView: TextView,
        qtyView: TextView,
        priceView: TextView
    ) {
        val name = doc.getString("name") ?: "Unknown Product"
        val dbSku = doc.getString("sku") ?: sku
        val currentQty = when (val q = doc.get("quantity")) {
            is Long -> q.toInt()
            is Int -> q
            is Number -> q.toInt()
            is String -> q.toIntOrNull() ?: 0
            else -> 0
        }
        val priceVal = when (val p = doc.get("price")) {
            is Double -> p
            is Long -> p.toDouble()
            is Number -> p.toDouble()
            is String -> p.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        val price = "₱${String.format(Locale.getDefault(), "%,.2f", priceVal)}"
        val boxQty = when (val b = doc.get("boxQuantity")) {
            is Long -> b.toInt()
            is Int -> b
            is Number -> b.toInt()
            is String -> b.toIntOrNull() ?: 25
            else -> 25
        }
        nameView.text = "Box of $name"
        skuView.text = "SKU: $dbSku  |  📦 $boxQty units inside this box"
        qtyView.text = "Current Stock: $currentQty units"
        priceView.text = price
        scanResultCard.visibility = View.VISIBLE

        val receiveButton = findViewById<Button>(R.id.receiveBoxButton)
        receiveButton?.text = "Receive Box into Inventory (+$boxQty units)"
        receiveButton?.setOnClickListener {
            adjustProductStock(doc, name, boxQty, "Received box") { newQty ->
                Toast.makeText(this, "Successfully received box! Added $boxQty units to $name. New stock: $newQty", Toast.LENGTH_LONG).show()
                qtyView.text = "Current Stock: $newQty units"
            }
        }

        val sellButton = findViewById<Button>(R.id.sellItemButton)
        sellButton?.text = "Sell Item / Checkout (-1 unit)"
        sellButton?.setOnClickListener {
            adjustProductStock(doc, name, -1, "POS Sale - Sold item via checkout") { newQty ->
                Toast.makeText(this, "Sale recorded! Sold 1 unit of $name. Remaining stock: $newQty", Toast.LENGTH_LONG).show()
                qtyView.text = "Current Stock: $newQty units"
            }
        }

        Toast.makeText(this, "Scanned box for $name ($boxQty units inside)", Toast.LENGTH_SHORT).show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(barcodeScanner, imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera error: ${exc.localizedMessage}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(scanner: BarcodeScanner, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            val currentTime = System.currentTimeMillis()
                            if ((rawValue == lastScannedBarcode) && ((currentTime - lastScanTime) < 1500L)) {
                                break
                            }
                            lastScannedBarcode = rawValue
                            lastScanTime = currentTime

                            runOnUiThread {
                                val scanResultCard = findViewById<LinearLayout>(R.id.scanResultCard)
                                val nameView = findViewById<TextView>(R.id.scannedProductName)
                                val skuView = findViewById<TextView>(R.id.scannedProductSku)
                                val qtyView = findViewById<TextView>(R.id.scannedProductQty)
                                val priceView = findViewById<TextView>(R.id.scannedProductPrice)

                                lookupProductAndDisplay(rawValue, scanResultCard, nameView, skuView, qtyView, priceView)
                            }
                            break
                        }
                    }
                }
                .addOnFailureListener {
                    // Ignore fail for smooth frame processing
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show()
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

    private fun adjustProductStock(
        product: DocumentSnapshot,
        productName: String,
        quantityDelta: Int,
        note: String,
        onSuccess: (Int) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val movementRef = db.collection("movements").document()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val date = dateFormat.format(Date())
        val email = FirebaseAuth.getInstance().currentUser?.email ?: "System"
        val movementType = if (quantityDelta > 0) "IN" else "OUT"
        val movementQuantity = abs(quantityDelta)

        db.runTransaction { transaction ->
            val latestProduct = transaction.get(product.reference)
            if (!latestProduct.exists()) {
                throw IllegalStateException("This product was removed. Refresh inventory and try again.")
            }

            val currentQuantity = when (val value = latestProduct.get("quantity")) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
            val updatedQuantity = currentQuantity + quantityDelta
            if (updatedQuantity < 0) {
                throw IllegalStateException("There is not enough stock to complete this sale.")
            }

            val unitPrice = when (val value = latestProduct.get("price")) {
                is Number -> value.toDouble()
                is String -> value.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val movement = hashMapOf<String, Any>(
                "id" to movementRef.id,
                "productId" to product.id,
                "productName" to productName,
                "type" to movementType,
                "quantity" to movementQuantity,
                "user" to email,
                "date" to date,
                "note" to note,
                "unitPrice" to unitPrice
            )
            if (movementType == "OUT") {
                movement["saleTotal"] = unitPrice * movementQuantity
                movement["saleId"] = movementRef.id
            }

            transaction.update(product.reference, "quantity", updatedQuantity)
            transaction.set(movementRef, movement)
            updatedQuantity
        }.addOnSuccessListener(onSuccess)
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message ?: "Could not update stock.", Toast.LENGTH_LONG).show()
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
        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
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
            // Already here
        }
        findViewById<LinearLayout>(R.id.alertsNav).setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.profileNav).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        try {
            barcodeScanner.close()
        } catch (_: Exception) {
            // Ignore close exception
        }
    }
}
