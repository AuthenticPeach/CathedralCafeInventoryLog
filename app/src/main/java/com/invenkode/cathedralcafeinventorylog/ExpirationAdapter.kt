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

class ExpirationAdapter : ListAdapter<InventoryItem, ExpirationAdapter.ExpirationViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val ONE_DAY_MS = 86_400_000L
        private const val ONE_WEEK_MS = 7 * ONE_DAY_MS

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean =
                oldItem == newItem
        }
    }

    inner class ExpirationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvExpiration) // This TextView will show status

        private val dateFormat = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())

        fun bind(item: InventoryItem) {
            // Combine name and variant.
            tvName.text = if (item.variant.isNotBlank()) {
                "${item.name} - ${item.variant}"
            } else {
                item.name
            }

            val now = System.currentTimeMillis()

            // Decide what to show in the status field.
            val statusText = when {
                item.expirationDate == 0L -> "---"  // Non-expiring item.
                now >= item.expirationDate -> "Expired"
                (item.expirationDate - now) < ONE_WEEK_MS -> "Expiring Soon"
                else -> dateFormat.format(Date(item.expirationDate))
            }
            tvStatus.text = statusText

            // Optionally you can also adjust the background color here.
            val bgColor = if (item.expirationDate != 0L) {
                when {
                    now >= item.expirationDate -> Color.parseColor("#FFCDD2")  // Light red.
                    (item.expirationDate - now) < ONE_WEEK_MS -> Color.parseColor("#FFE0B2")  // Light orange.
                    else -> Color.WHITE
                }
            } else {
                Color.WHITE
            }
            itemView.setBackgroundColor(bgColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpirationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return ExpirationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpirationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
