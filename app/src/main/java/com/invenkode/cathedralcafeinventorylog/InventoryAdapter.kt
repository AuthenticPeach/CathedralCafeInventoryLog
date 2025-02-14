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
import java.util.Locale

class InventoryAdapter : ListAdapter<InventoryItem, InventoryAdapter.InventoryViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem) = oldItem == newItem
        }

        private const val ONE_DAY_MS = 86_400_000L
        private const val ONE_WEEK_MS = 7 * ONE_DAY_MS
        private const val TWO_WEEKS_MS = 14 * ONE_DAY_MS
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
        private val tvExpiration: TextView = itemView.findViewById(R.id.tvExpiration)
        // Status TextView for additional info:
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bind(item: InventoryItem) {
            tvName.text = item.name
            tvExpiration.text = dateFormat.format(item.expirationDate)

            val now = System.currentTimeMillis()
            val diff = item.expirationDate - now

            // Default values for fresh items
            var bgColor = Color.WHITE
            var statusMessage: String? = null

            when {
                now >= item.expirationDate -> { // Expired
                    bgColor = Color.parseColor("#FFCDD2")  // Light red background
                    statusMessage = "expired!"
                }
                diff < ONE_WEEK_MS -> { // Within one week
                    bgColor = Color.parseColor("#FFE0B2")  // Light orange background
                    statusMessage = "expiring soon!"
                }
                diff < TWO_WEEKS_MS -> { // Within two weeks
                    bgColor = Color.parseColor("#FFF9C4")  // Light yellow background
                    statusMessage = "expiring soon!"
                }
                else -> { // Fresh item
                    bgColor = Color.WHITE
                    statusMessage = null  // No status text for fresh items
                }
            }

            // Set the background color of the entire item view
            itemView.setBackgroundColor(bgColor)

            // Update the status TextView
            if (statusMessage != null) {
                tvStatus.text = statusMessage
                tvStatus.visibility = View.VISIBLE
            } else {
                tvStatus.visibility = View.GONE
            }
        }
    }
}


