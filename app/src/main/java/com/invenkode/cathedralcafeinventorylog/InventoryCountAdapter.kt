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

class InventoryCountAdapter(
    private val onQuantityChanged: (InventoryItem, Int) -> Unit
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
        // Change this to match the ID defined in list_item_inventory.xml (i.e. "tvName")
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val numberPicker: NumberPicker = itemView.findViewById(R.id.numberPickerQuantity)

        fun bind(item: InventoryItem) {
            tvName.text = item.name

            // Configure NumberPicker
            numberPicker.minValue = 0
            numberPicker.maxValue = 100  // Adjust as needed
            numberPicker.value = item.quantity

            // Change the background color based on quantity (3 or less: red; otherwise, white)
            val bgColor = if (item.quantity <= 3) {
                Color.parseColor("#FFCDD2") // light red
            } else {
                Color.WHITE
            }
            itemView.setBackgroundColor(bgColor)

            // Listen for changes on the NumberPicker
            numberPicker.setOnValueChangedListener { _, oldVal, newVal ->
                if (oldVal != newVal) {
                    onQuantityChanged(item, newVal)
                }
            }
        }
    }
}
