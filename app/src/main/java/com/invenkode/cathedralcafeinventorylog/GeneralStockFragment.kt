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

class GeneralStockFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: InventoryCountAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_general_stock, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewGeneral)
        adapter = InventoryCountAdapter(onQuantityChanged = { item, newQty ->
            updateItemQuantity(item, newQty)
        }, enableEdit = true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        firestore = FirebaseFirestore.getInstance()

        // Listen for real-time changes for items with storageType "Stock"
        firestore.collection("inventoryItems")
            .whereEqualTo("storageType", "Stock")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(requireContext(),
                        "Error fetching updates: ${exception.message}",
                        Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val variant = doc.getString("variant") ?: ""
                        val expirationDate = doc.getLong("expirationDate") ?: 0L
                        val quantity = (doc.getLong("quantity") ?: 0L).toInt()
                        val storageType = doc.getString("storageType") ?: ""
                        // Use the document’s hash code as the ID.
                        InventoryItem(doc.id.hashCode(), name, variant, expirationDate, quantity, storageType)
                    }
                    // Sort alphabetically by name.
                    val sortedItems = items.sortedBy { it.name }
                    adapter.submitList(sortedItems)
                }
            }

        // Set up swipe-to-delete with undo.
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.currentList[position]
                lifecycleScope.launch(Dispatchers.IO) { deleteItem(deletedItem) }
                Snackbar.make(recyclerView, "Item deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        lifecycleScope.launch(Dispatchers.IO) { addItem(deletedItem) }
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun updateItemQuantity(item: InventoryItem, newQty: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Query Firestore for the document matching the item.
                val query = firestore.collection("inventoryItems")
                    .whereEqualTo("name", item.name)
                    .whereEqualTo("variant", item.variant)
                    .whereEqualTo("expirationDate", item.expirationDate)
                    .whereEqualTo("storageType", item.storageType)
                val snapshot = query.get().await()
                for (doc in snapshot.documents) {
                    doc.reference.update("quantity", newQty).await()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error updating quantity", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun deleteItem(item: InventoryItem) {
        try {
            val query = firestore.collection("inventoryItems")
                .whereEqualTo("name", item.name)
                .whereEqualTo("variant", item.variant)
                .whereEqualTo("expirationDate", item.expirationDate)
                .whereEqualTo("storageType", item.storageType)
            val snapshot = query.get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error deleting item", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun addItem(item: InventoryItem) {
        try {
            val itemMap = hashMapOf(
                "name" to item.name,
                "variant" to item.variant,
                "expirationDate" to item.expirationDate,
                "quantity" to item.quantity,
                "storageType" to item.storageType
            )
            firestore.collection("inventoryItems").add(itemMap).await()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error adding item back", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
