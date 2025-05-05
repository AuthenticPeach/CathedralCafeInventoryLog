package com.invenkode.cathedralcafeinventorylog

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

sealed class StockRow {
    data class Header(val title: String)   : StockRow()
    data class Item(val item: InventoryItem) : StockRow()
}

class StockAdapter(
    private val onQuantityChanged: (InventoryItem, Int) -> Unit,
    private val onRunningLowChanged: (InventoryItem, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM   = 1
    }

    val rows = mutableListOf<StockRow>()

    fun submitList(newRows: List<StockRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) =
        when (rows[position]) {
            is StockRow.Header -> TYPE_HEADER
            is StockRow.Item   -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_stock_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_stock, parent, false)
            StockItemViewHolder(view, onQuantityChanged, onRunningLowChanged)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is StockRow.Header -> (holder as HeaderViewHolder).bind(row.title)
            is StockRow.Item   -> (holder as StockItemViewHolder).bind(row.item)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.tvHeader)
        fun bind(title: String) {
            titleView.text = title
            val ctx = itemView.context
            titleView.setTextColor(ContextCompat.getColor(ctx, R.color.onSurface))
            itemView.setBackgroundColor(ContextCompat.getColor(ctx, R.color.surfaceColor))
        }
    }

    inner class StockItemViewHolder(
        view: View,
        private val onQuantityChanged: (InventoryItem, Int) -> Unit,
        private val onRunningLowChanged: (InventoryItem, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val tvName     = view.findViewById<TextView>(R.id.tvStockName)
        private val tvVariant  = view.findViewById<TextView>(R.id.tvStockVariant)
        private val tvSubtitle = view.findViewById<TextView>(R.id.tvStockSubtitle)
        private val picker     = view.findViewById<NumberPicker>(R.id.numberPickerStockQty)
        private val toggle     = view.findViewById<ToggleButton>(R.id.toggleStockLow)

        fun bind(item: InventoryItem) {
            // Name & variant
            tvName.text = item.name
            tvName.setTextColor(ContextCompat.getColor(itemView.context, R.color.onSurface))

            tvVariant.text = item.variant
            tvVariant.setTextColor(ContextCompat.getColor(itemView.context, R.color.darker_gray))
            tvVariant.visibility = if (item.variant.isBlank()) View.GONE else View.VISIBLE

            // ONLY for "Milks" category: show NumberPicker and threshold subtitle
            if (item.stockSubCategory.equals("Milks", ignoreCase = true)) {
                tvSubtitle.text = "Ideal Stock: ${item.idealThreshold}"
                tvSubtitle.visibility = View.VISIBLE

                picker.visibility = View.VISIBLE
                toggle.visibility = View.GONE

                picker.minValue = 0
                picker.maxValue = 100
                picker.value = item.quantity
                picker.setOnValueChangedListener { _, _, newVal ->
                    onQuantityChanged(item, newVal)
                }

            } else {
                // All other categories: hide subtitle & picker, show toggle
                tvSubtitle.visibility = View.GONE

                picker.visibility = View.GONE
                toggle.visibility = View.VISIBLE

                toggle.visibility = View.VISIBLE

                // 🔧 Fix: Remove any existing listener before setting new state
                toggle.setOnCheckedChangeListener(null) // ← prevents old callbacks from firing

                toggle.isChecked = item.isRunningLow

                toggle.setOnCheckedChangeListener { _, isChecked ->
                    onRunningLowChanged(item, isChecked)
                }

            }

            // Highlight if low (either below ideal for Milks, or flagged runningLow elsewhere)
            val alerting = if (item.stockSubCategory.equals("Milks", true)) {
                (item.idealThreshold ?: Int.MAX_VALUE).let { item.quantity < it }
            } else {
                item.isRunningLow
            }
            itemView.setBackgroundColor(
                if (alerting) Color.parseColor("#FFCDD2")
                else Color.TRANSPARENT
            )

            // Long-press to edit
            itemView.setOnLongClickListener {
                val ctx = it.context
                val intent = Intent(ctx, EditItemActivity::class.java).apply {
                    putExtra("itemId",           item.id)
                    putExtra("name",             item.name)
                    putExtra("variant",          item.variant)
                    putExtra("expirationDate",   item.expirationDate)
                    putExtra("quantity",         item.quantity)
                    putExtra("storageType",      item.storageType)
                    putExtra("stockSubCategory", item.stockSubCategory)
                    putExtra("stockAlertType",   item.stockAlertType)
                    putExtra("idealThreshold",   item.idealThreshold)
                    putExtra("isRunningLow",     item.isRunningLow)
                }
                ctx.startActivity(intent)
                true
            }
        }
    }
}
