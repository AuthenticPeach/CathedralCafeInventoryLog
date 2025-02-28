package com.invenkode.cathedralcafeinventorylog

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class InventoryAdapter(
    private val isEditable: Boolean = false,
    private val onQuantityChanged: ((InventoryItem, Int) -> Unit)? = null
) : ListAdapter<InventoryItem, InventoryAdapter.InventoryViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem) =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        // Optional separate TextView for variant.
        private val tvVariant: TextView? = itemView.findViewById(R.id.tvVariant)
        private val tvExpiration: TextView = itemView.findViewById(R.id.tvExpiration)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvStorage: TextView = itemView.findViewById(R.id.tvStorage)
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bind(item: InventoryItem) {
            // Option 1: Concatenate variant with name.
            // tvName.text = if (item.variant.isNotBlank()) "${item.name} - ${item.variant}" else item.name

            // Option 2: Show name and variant separately.
            tvName.text = item.name
            if (tvVariant != null) {
                if (item.variant.isNotBlank()) {
                    tvVariant.visibility = View.VISIBLE
                    tvVariant.text = item.variant
                } else {
                    tvVariant.visibility = View.GONE
                }
            }

            tvStorage.text = "Storage: ${item.storageType}"

            if (item.expirationDate > 0L && !item.storageType.equals("Stock", ignoreCase = true)) {
                tvExpiration.text = dateFormat.format(item.expirationDate)
                tvExpiration.visibility = View.VISIBLE
            } else {
                tvExpiration.visibility = View.GONE
            }

            val now = System.currentTimeMillis()
            val diff = item.expirationDate - now
            var bgColor = Color.WHITE
            var statusMessage: String? = null

            if (item.expirationDate > 0L) {
                when {
                    now >= item.expirationDate -> {
                        bgColor = Color.parseColor("#FFCDD2")
                        statusMessage = "expired!"
                    }
                    diff < 7 * 86_400_000L -> {
                        bgColor = Color.parseColor("#FFE0B2")
                        statusMessage = "expiring soon!"
                    }
                    diff < 14 * 86_400_000L -> {
                        bgColor = Color.parseColor("#FFF9C4")
                        statusMessage = "expiring soon!"
                    }
                    else -> {
                        bgColor = Color.WHITE
                        statusMessage = null
                    }
                }
            } else {
                bgColor = Color.WHITE
                statusMessage = null
            }

            itemView.setBackgroundColor(bgColor)
            if (statusMessage != null) {
                tvStatus.text = statusMessage
                tvStatus.visibility = View.VISIBLE
            } else {
                tvStatus.visibility = View.GONE
            }

            if (isEditable) {
                itemView.setOnLongClickListener { v ->
                    Log.d("InventoryAdapter", "Long pressed on item: ${item.name}")
                    val context = v.context
                    val intent = Intent(context, EditItemActivity::class.java).apply {
                        putExtra("itemId", item.id)
                        putExtra("name", item.name)
                        putExtra("expirationDate", item.expirationDate)
                        putExtra("quantity", item.quantity)
                        putExtra("storageType", item.storageType)
                        putExtra("variant", item.variant)
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
