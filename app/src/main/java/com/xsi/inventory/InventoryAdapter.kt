package com.xsi.inventory

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class InventoryItem(
    val id: String = "",
    val name: String,
    val sku: String,
    val quantity: String,
    val status: String,
    val price: String
)

class InventoryAdapter : ListAdapter<InventoryItem, InventoryAdapter.ViewHolder>(DiffCallback) {

    var onItemClickListener: ((InventoryItem) -> Unit)? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameView: TextView = itemView.findViewById(R.id.itemName)
        val skuView: TextView = itemView.findViewById(R.id.itemSku)
        val quantityView: TextView = itemView.findViewById(R.id.itemQuantity)
        val statusView: TextView = itemView.findViewById(R.id.itemStatus)
        val priceView: TextView = itemView.findViewById(R.id.itemPrice)
        val dividerView: View = itemView.findViewById(R.id.itemDivider)

        // Pre-cached drawables to avoid runtime allocation on every bind
        private val outBg = GradientDrawable().apply {
            setColor(Color.rgb(252, 234, 234))
            cornerRadius = 12f * itemView.resources.displayMetrics.density
        }
        private val lowBg = GradientDrawable().apply {
            setColor(Color.rgb(255, 242, 220))
            cornerRadius = 12f * itemView.resources.displayMetrics.density
        }
        private val defaultBg = GradientDrawable().apply {
            setColor(Color.rgb(250, 238, 216))
            cornerRadius = 12f * itemView.resources.displayMetrics.density
        }

        fun bind(item: InventoryItem, isLastItem: Boolean) {
            nameView.text = item.name
            skuView.text = item.sku
            quantityView.text = item.quantity
            statusView.text = item.status
            priceView.text = item.price

            when (item.status) {
                "Out" -> {
                    statusView.setTextColor(Color.rgb(225, 75, 75))
                    statusView.background = outBg
                }
                "Low" -> {
                    statusView.setTextColor(Color.rgb(215, 115, 52))
                    statusView.background = lowBg
                }
                else -> {
                    statusView.setTextColor(Color.rgb(185, 113, 0))
                    statusView.background = defaultBg
                }
            }
            statusView.typeface = Typeface.DEFAULT_BOLD

            dividerView.visibility = if (isLastItem) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position == itemCount - 1)
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(item)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<InventoryItem>() {
        override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
            return oldItem.id == newItem.id || oldItem.sku == newItem.sku || oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
