package com.invenkode.cathedralcafeinventorylog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class InventoryAdapter(
    // Pass true for the Inventory (editable) tab, false for the Expiration (read-only) tab.
    private val isEditable: Boolean,
    // Callback for quantity changes (only used when isEditable is true).
    private val onQuantityChanged: ((InventoryItem, Int) -> Unit)? = null
) : ListAdapter<InventoryItem, InventoryAdapter.InventoryViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val layoutId = if (isEditable) R.layout.list_item_inventory else R.layout.list_item
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Common view for the item name; in both layouts, use the same ID ("tvName").
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        // New: Optional view for storage type.
        private val tvStorageType: TextView? = itemView.findViewById(R.id.tvStorageType)
        // For read-only layout.
        private val tvExpiration: TextView? = if (!isEditable) itemView.findViewById(R.id.tvExpiration) else null
        private val tvStatus: TextView? = if (!isEditable) itemView.findViewById(R.id.tvStatus) else null
        // For editable layout.
        private val numberPicker: NumberPicker? = if (isEditable) itemView.findViewById(R.id.numberPickerQuantity) else null

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bind(item: InventoryItem) {
            tvName.text = item.name
            // Display storage type in both modes.
            tvStorageType?.text = "Storage: ${item.storageType}"

            if (isEditable && numberPicker != null) {
                // Editable mode: use NumberPicker for quantity.
                numberPicker.minValue = 0
                numberPicker.maxValue = 100  // Adjust max value as needed.
                numberPicker.value = item.quantity

                // Change background color based on quantity.
                val bgColor = if (item.quantity <= 3) {
                    Color.parseColor("#FFCDD2") // Light red for low stock.
                } else {
                    Color.WHITE
                }
                itemView.setBackgroundColor(bgColor)

                numberPicker.setOnValueChangedListener { _, oldVal, newVal ->
                    if (oldVal != newVal) {
                        onQuantityChanged?.invoke(item, newVal)
                    }
                }
            } else {
                // Read-only mode: display expiration date and status.
                tvExpiration?.text = dateFormat.format(item.expirationDate)
                val now = System.currentTimeMillis()
                val diff = item.expirationDate - now
                val statusMessage = when {
                    now >= item.expirationDate -> "expired!"
                    diff < 7 * 86_400_000L -> "expiring soon!"
                    diff < 14 * 86_400_000L -> "expiring soon!"
                    else -> ""
                }
                if (statusMessage.isNotEmpty()) {
                    tvStatus?.text = statusMessage
                    tvStatus?.visibility = View.VISIBLE
                } else {
                    tvStatus?.visibility = View.GONE
                }
                // Use a white background in read-only mode.
                itemView.setBackgroundColor(Color.WHITE)
            }
        }
    }
}
