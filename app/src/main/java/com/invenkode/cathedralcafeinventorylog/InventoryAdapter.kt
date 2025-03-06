package com.invenkode.cathedralcafeinventorylog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class InventoryAdapter(
    private val isEditable: Boolean = false
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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvVariant: TextView = itemView.findViewById(R.id.tvVariant)
        private val tvExpiration: TextView = itemView.findViewById(R.id.tvExpiration)
        private val dateFormat = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())

        fun bind(item: InventoryItem) {
            // Set name and variant.
            tvName.text = item.name
            if (item.variant.isNotBlank()) {
                tvVariant.text = item.variant
                tvVariant.visibility = View.VISIBLE
            } else {
                tvVariant.visibility = View.GONE
            }

            // Set expiration text.
            val now = System.currentTimeMillis()
            if (item.expirationDate == 0L) {
                // For non-expiring items.
                tvExpiration.text = "---"
                tvExpiration.setTextColor(Color.DKGRAY)
            } else {
                val formattedDate = dateFormat.format(Date(item.expirationDate))
                if (now >= item.expirationDate) {
                    // Expired: show "Expired: <date>" in red.
                    tvExpiration.text = "Expired: $formattedDate"
                    tvExpiration.setTextColor(Color.RED)
                } else if ((item.expirationDate - now) < 7 * 86_400_000L) {
                    // Expiring Soon: show "Expiring Soon: <date>" in dark gray.
                    tvExpiration.text = "Expiring Soon: $formattedDate"
                    tvExpiration.setTextColor(Color.DKGRAY)
                } else {
                    // Otherwise, just show the date.
                    tvExpiration.text = formattedDate
                    tvExpiration.setTextColor(Color.DKGRAY)
                }
            }

            // Set background color based on expiration.
            val bgColor = when {
                item.expirationDate == 0L -> Color.WHITE
                now >= item.expirationDate -> Color.parseColor("#FFCDD2")
                (item.expirationDate - now) < 7 * 86_400_000L -> Color.parseColor("#FFE0B2")
                (item.expirationDate - now) < 14 * 86_400_000L -> Color.parseColor("#FFF9C4")
                else -> Color.parseColor("#C8E6C9")
            }
            itemView.setBackgroundColor(bgColor)

            // Set up long-press editing if enabled.
            if (isEditable) {
                itemView.setOnLongClickListener { v ->
                    val context = v.context
                    val intent = android.content.Intent(context, EditItemActivity::class.java).apply {
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
    }
}
