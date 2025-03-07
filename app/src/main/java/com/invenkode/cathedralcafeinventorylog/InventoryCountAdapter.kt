package com.invenkode.cathedralcafeinventorylog

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class InventoryCountAdapter(
    private val onQuantityChanged: (InventoryItem, Int) -> Unit,
    private val enableEdit: Boolean = true
) : ListAdapter<InventoryItem, InventoryCountAdapter.InventoryCountViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryCountViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_inventory, parent, false)
        // Optionally adjust vertical scale if you wish; for now we leave it as defined in XML.
        return InventoryCountViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryCountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InventoryCountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnTouchListener {

        // Find views by their IDs from list_item_inventory.xml.
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvVariant: TextView = itemView.findViewById(R.id.tvVariant)
        private val numberPicker: NumberPicker = itemView.findViewById(R.id.numberPickerQuantity)
        private val tvFinalQuantity: TextView = itemView.findViewById(R.id.tvFinalQuantity)

        // Create a ScaleGestureDetector to support vertical scaling.
        private val scaleDetector = ScaleGestureDetector(itemView.context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    val newScaleY = itemView.scaleY * scaleFactor
                    // Limit vertical scale between 0.5 (50%) and 1.0 (original).
                    itemView.scaleY = newScaleY.coerceIn(0.5f, 1.0f)
                    return true
                }
            }
        )

        init {
            itemView.setOnTouchListener(this)
        }

        fun bind(item: InventoryItem) {
            tvName.text = item.name

            // Show variant if available.
            if (item.variant.isNotBlank()) {
                tvVariant.text = item.variant
                tvVariant.visibility = View.VISIBLE
            } else {
                tvVariant.visibility = View.GONE
            }

            // Configure NumberPicker.
            numberPicker.minValue = 0
            numberPicker.maxValue = 100  // Adjust as needed.
            numberPicker.value = item.quantity

            // Display the final quantity in a larger font.
            tvFinalQuantity.text = String.format(Locale.getDefault(), "%d", item.quantity)

            // Listen for changes in the NumberPicker.
            numberPicker.setOnValueChangedListener { _, oldVal, newVal ->
                if (oldVal != newVal) {
                    onQuantityChanged(item, newVal)
                    tvFinalQuantity.text = String.format(Locale.getDefault(), "%d", newVal)
                }
            }

            // Set background color based on quantity:
            // Quantity <= 2: light red; exactly 3: light yellow; above 3: light green.
            val bgColor = when {
                item.quantity <= 2 -> Color.parseColor("#FFCDD2")
                item.quantity == 3 -> Color.parseColor("#FFF9C4")
                else -> Color.parseColor("#C8E6C9")
            }
            itemView.setBackgroundColor(bgColor)
            Log.d("InventoryCountAdapter", "Bound item: ${item.name}, qty: ${item.quantity}")

            // Enable long-press editing if enabled.
            if (enableEdit) {
                itemView.setOnLongClickListener { v ->
                    Log.d("InventoryCountAdapter", "Long pressed on item: ${item.name}")
                    val context = v.context
                    val intent = Intent(context, EditItemActivity::class.java).apply {
                        putExtra("itemId", item.id)
                        putExtra("name", item.name)
                        putExtra("variant", item.variant)
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

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            event?.let {
                scaleDetector.onTouchEvent(it)
                if (it.action == MotionEvent.ACTION_UP) {
                    v?.performClick()
                }
            }
            return false
        }
    }
}
