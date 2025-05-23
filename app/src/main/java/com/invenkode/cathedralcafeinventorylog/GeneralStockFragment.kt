package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface BadgeUpdateListener {
    fun onStockBadgeCountChanged(count: Int)
}

class GeneralStockFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: StockAdapter
    private lateinit var rv: RecyclerView

    private val subCats = listOf(
        "Cups and Lids",
        "Paper Goods",
        "Teas and lemonade",
        "Smoothies",
        "Coffee Beans",
        "Cleaning Supplies",
        "Sauces and Syrups",
        "Milks",
        "Cold Drinks",
        "Food & Snacks",
        "Powders & Condiments"
    )

    var badgeListener: BadgeUpdateListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_general_stock, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rv = view.findViewById(R.id.recyclerViewGeneral)

        adapter = StockAdapter(
            onQuantityChanged = { item, qty -> updateItemQuantity(item, qty) },
            onRunningLowChanged = { item, isLow -> updateItemRunningLow(item, isLow) }
        )

        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext())

        firestore = FirebaseFirestore.getInstance()
        firestore.collection("inventoryItems")
            .whereEqualTo("storageType", "Stock")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(requireContext(), "Error fetching updates: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val variant = doc.getString("variant") ?: ""
                        val expirationDate = doc.getLong("expirationDate") ?: 0L
                        val quantity = (doc.getLong("quantity") ?: 0L).toInt()
                        val storageType = doc.getString("storageType") ?: ""
                        val stockAlertType = doc.getString("stockAlertType") ?: ""
                        val isRunningLow = doc.getBoolean("isRunningLow") ?: false
                        val idealThreshold = doc.getLong("idealThreshold")?.toInt()
                        val subCat = doc.getString("stockSubCategory") ?: ""

                        InventoryItem(
                            id = doc.id.hashCode(),
                            name = name,
                            variant = variant,
                            expirationDate = expirationDate,
                            quantity = quantity,
                            storageType = storageType,
                            stockAlertType = stockAlertType,
                            isRunningLow = isRunningLow,
                            idealThreshold = idealThreshold,
                            stockSubCategory = subCat
                        )
                    }

                    // Convert to rows: group by subcategory, wrap in Header/Item
                    val rows = items
                        .groupBy { it.stockSubCategory }
                        .toSortedMap() // Sort subcategories alphabetically
                        .flatMap { (subCat, subItems) ->
                            listOf(StockRow.Header(subCat)) +
                                    subItems.sortedWith(compareBy({ it.name }, { it.variant })) // Sort items alphabetically
                                        .map { StockRow.Item(it) }
                        }


                    adapter.submitList(rows)

                    // Count red entries
                    val redCount = items.count {
                        (it.stockAlertType.equals("Running Low", true) && it.isRunningLow) ||
                                (it.stockAlertType.equals("Ideal", true) && it.idealThreshold != null && it.quantity < it.idealThreshold)
                    }

                    badgeListener?.onStockBadgeCountChanged(redCount)
                }
            }

        // swipe-to-delete with undo
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                when (val row = adapter.rows[pos]) {
                    is StockRow.Item -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            deleteItem(row.item)
                        }
                        Snackbar.make(rv, "Item deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    addItem(row.item)
                                }
                            }
                            .show()
                    }
                    is StockRow.Header -> {
                        adapter.notifyItemChanged(pos)
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)
    }

    private fun updateItemQuantity(item: InventoryItem, newQty: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val query = firestore.collection("inventoryItems")
                    .whereEqualTo("name", item.name)
                    .whereEqualTo("variant", item.variant)
                    .whereEqualTo("expirationDate", item.expirationDate)
                    .whereEqualTo("storageType", item.storageType)
                    .whereEqualTo("stockSubCategory", item.stockSubCategory)
                val snap = query.get().await()
                for (doc in snap.documents) {
                    doc.reference.update("quantity", newQty).await()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error updating quantity", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateItemRunningLow(item: InventoryItem, isLow: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val query = firestore.collection("inventoryItems")
                    .whereEqualTo("name", item.name)
                    .whereEqualTo("variant", item.variant)
                    .whereEqualTo("expirationDate", item.expirationDate)
                    .whereEqualTo("storageType", item.storageType)
                    .whereEqualTo("stockSubCategory", item.stockSubCategory)
                val snap = query.get().await()
                for (doc in snap.documents) {
                    doc.reference.update("isRunningLow", isLow).await()
                }

                val db = InventoryDatabase.getDatabase(requireContext())
                db.inventoryDao().updateRunningLow(item.id, isLow)

                // Fetch updated version of the item from DB and notify badge again
                val updatedItems = db.inventoryDao().getAllItemsSync()
                val redCount = updatedItems.count {
                    it.storageType.equals("Stock", true) && (
                            (it.stockAlertType.equals("Running Low", true) && it.isRunningLow) ||
                                    (it.stockAlertType.equals("Ideal", true) && it.idealThreshold != null && it.quantity < it.idealThreshold)
                            )
                }

                withContext(Dispatchers.Main) {
                    badgeListener?.onStockBadgeCountChanged(redCount)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error updating status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private suspend fun deleteItem(item: InventoryItem) {
        val query = firestore.collection("inventoryItems")
            .whereEqualTo("name", item.name)
            .whereEqualTo("variant", item.variant)
            .whereEqualTo("expirationDate", item.expirationDate)
            .whereEqualTo("storageType", item.storageType)
            .whereEqualTo("stockSubCategory", item.stockSubCategory)
        val snap = query.get().await()
        for (doc in snap.documents) {
            doc.reference.delete().await()
        }
    }

    private suspend fun addItem(item: InventoryItem) {
        val m = hashMapOf(
            "name" to item.name,
            "variant" to item.variant,
            "expirationDate" to item.expirationDate,
            "quantity" to item.quantity,
            "storageType" to item.storageType,
            "stockSubCategory" to item.stockSubCategory,
            "stockAlertType" to item.stockAlertType,
            "idealThreshold" to item.idealThreshold,
            "isRunningLow" to item.isRunningLow
        )
        firestore.collection("inventoryItems").add(m).await()
    }
}
