package com.invenkode.cathedralcafeinventorylog

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class InventoryCountAdapter(
    private val onQuantityChanged: (InventoryItem, Int) -> Unit,
    private val enableEdit: Boolean = true
) : ListAdapter<InventoryItem, InventoryCountAdapter.InventoryCountViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem) =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryCountViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_inventory, parent, false)
        return InventoryCountViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryCountViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class InventoryCountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Updated to use R.id.tvName as defined in list_item_inventory.xml
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val numberPicker: NumberPicker = itemView.findViewById(R.id.numberPickerQuantity)

        fun bind(item: InventoryItem) {
            tvName.text = item.name

            // Configure NumberPicker.
            numberPicker.minValue = 0
            numberPicker.maxValue = 100  // Adjust as needed.
            numberPicker.value = item.quantity

            // Listen for changes on the NumberPicker.
            numberPicker.setOnValueChangedListener { _, oldVal, newVal ->
                if (oldVal != newVal) {
                    onQuantityChanged(item, newVal)
                }
            }

            // Set background color based on quantity.
            // Quantity 2 or less: red; exactly 3: yellow; above 3: green.
            val bgColor = when {
                item.quantity <= 2 -> Color.parseColor("#FFCDD2")  // Light red.
                item.quantity == 3 -> Color.parseColor("#FFF9C4")  // Light yellow.
                else -> Color.parseColor("#C8E6C9")                // Light green.
            }
            itemView.setBackgroundColor(bgColor)
            Log.d("InventoryCountAdapter", "Item '${item.name}' quantity: ${item.quantity}, bgColor: $bgColor")

            // If editing is enabled, set a long-click listener to launch EditItemActivity.
            if (enableEdit) {
                itemView.setOnLongClickListener { v ->
                    Log.d("InventoryCountAdapter", "Long pressed on item: ${item.name}")
                    val context = v.context
                    val intent = Intent(context, EditItemActivity::class.java).apply {
                        putExtra("itemId", item.id)
                        putExtra("name", item.name)
                        putExtra("expirationDate", item.expirationDate)
                        putExtra("quantity", item.quantity)
                        putExtra("storageType", item.storageType)
                    }
                    context.startActivity(intent)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }
}
