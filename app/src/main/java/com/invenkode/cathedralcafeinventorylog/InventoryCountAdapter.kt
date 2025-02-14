package com.invenkode.cathedralcafeinventorylog

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class InventoryCountAdapter(
    private val onQuantityChanged: (InventoryItem, Int) -> Unit
) : ListAdapter<InventoryItem, InventoryCountAdapter.InventoryCountViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryCountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_inventory, parent, false)
        return InventoryCountViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryCountViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class InventoryCountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvNameInventory)
        private val etQuantity: EditText = itemView.findViewById(R.id.etQuantity)
        private val btnIncrement: Button = itemView.findViewById(R.id.btnIncrement)
        private val btnDecrement: Button = itemView.findViewById(R.id.btnDecrement)
        private var currentTextWatcher: TextWatcher? = null

        fun bind(item: InventoryItem) {
            tvName.text = item.name
            // Remove any previous text watcher to avoid multiple triggers
            currentTextWatcher?.let { etQuantity.removeTextChangedListener(it) }
            etQuantity.setText(item.quantity.toString())

            // Create and add a new TextWatcher
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newQty = s.toString().toIntOrNull() ?: item.quantity
                    // Only update if the quantity has really changed
                    if (newQty != item.quantity) {
                        onQuantityChanged(item, newQty)
                    }
                }
            }
            etQuantity.addTextChangedListener(watcher)
            currentTextWatcher = watcher

            // Set click listeners for increment/decrement buttons
            btnIncrement.setOnClickListener {
                val newQty = item.quantity + 1
                etQuantity.setText(newQty.toString())
                onQuantityChanged(item, newQty)
            }

            btnDecrement.setOnClickListener {
                if (item.quantity > 0) {
                    val newQty = item.quantity - 1
                    etQuantity.setText(newQty.toString())
                    onQuantityChanged(item, newQty)
                }
            }
        }
    }
}
