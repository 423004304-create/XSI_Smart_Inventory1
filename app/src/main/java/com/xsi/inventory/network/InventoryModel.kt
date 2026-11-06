package com.xsi.inventory.network

import com.google.firebase.Timestamp

data class Product(
    val id: String = "",
    val name: String = "",
    val sku: String = "",
    val category: String = "General",
    val quantity: Int = 0,
    val minStock: Int = 3,
    val price: Double = 0.0,
    val location: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val imagePath: String? = null
)

data class StockMovement(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val type: String = "IN", // "IN" or "OUT"
    val quantity: Int = 0,
    val user: String = "",
    val date: String = "",
    val note: String = "",
    val unitPrice: Double? = null,
    val saleTotal: Double? = null,
    val saleId: String? = null
)

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "Staff",
    val createdAt: Timestamp? = null
)

data class CartItem(
    val productId: String,
    val productName: String,
    var price: Double,
    var qty: Int,
    var maxStock: Int
)

object UserRoles {
    const val STAFF = "Inventory Clerk"
    const val SALES_STAFF = "Sales Staff"
    const val ADMIN = "Administrator"
    const val PROJECT_ADMIN = "Project Administrator"

    fun normalizeRole(role: String?): String {
        return when (role?.trim()) {
            "Sales Staff" -> SALES_STAFF
            "Staff", "Inventory Clerk" -> STAFF
            "Admin", "Administrator", "Company Administrator" -> ADMIN
            "Project Admin", "Project Administrator" -> PROJECT_ADMIN
            else -> role?.trim() ?: STAFF
        }
    }

    fun isStaff(role: String?): Boolean {
        val norm = normalizeRole(role)
        return norm == STAFF || norm == SALES_STAFF
    }

    fun isSalesStaff(role: String?): Boolean {
        return normalizeRole(role) == SALES_STAFF
    }

    fun isAdmin(role: String?): Boolean {
        val norm = normalizeRole(role)
        return norm == ADMIN || norm == PROJECT_ADMIN
    }

    fun isProjectAdmin(role: String?): Boolean {
        return normalizeRole(role) == PROJECT_ADMIN
    }

    fun getRoleLabel(role: String?): String {
        return when (normalizeRole(role)) {
            SALES_STAFF -> "Sales Staff"
            PROJECT_ADMIN -> "Project Admin"
            ADMIN -> "Admin"
            else -> "Staff"
        }
    }
}
