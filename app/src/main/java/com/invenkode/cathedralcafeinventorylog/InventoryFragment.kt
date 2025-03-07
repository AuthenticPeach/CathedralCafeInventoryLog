package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InventoryFragment : Fragment() {

    private lateinit var adapter: InventoryCountAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_inventory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewInventory)
        adapter = InventoryCountAdapter(
            onQuantityChanged = { item, newQty ->
                updateItemQuantity(item, newQty)
            },
            enableEdit = true
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        firestore = FirebaseFirestore.getInstance()

        // Listen for Firestore changes to update the list in real time.
        firestore.collection("inventoryItems")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(
                        requireContext(),
                        "Error listening for updates: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val updatedList = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id.hashCode() // Use the hash of the document ID as a temporary item ID.
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val variant = doc.getString("variant") ?: ""
                        val expirationDate = doc.getLong("expirationDate") ?: 0L
                        val quantity = (doc.getLong("quantity") ?: 0L).toInt()
                        val storageType = doc.getString("storageType") ?: ""
                        InventoryItem(id, name, variant, expirationDate, quantity, storageType)
                    }
                    // Filter out "Stock" items from this tab.
                    val filteredList = updatedList.filter { !it.storageType.equals("Stock", ignoreCase = true) }
                    // Sort alphabetically.
                    val sortedList = filteredList.sortedBy { it.name }
                    adapter.submitList(sortedList)
                }
            }

        // Set up swipe-to-delete with undo.
        val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.currentList[position]
                deleteItem(deletedItem)
                Snackbar.make(recyclerView, "Item deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        addItem(deletedItem)
                    }
                    .show()
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun updateItemQuantity(item: InventoryItem, newQty: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Query for the matching document(s) in Firestore.
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

    private fun deleteItem(item: InventoryItem) {
        lifecycleScope.launch(Dispatchers.IO) {
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
    }

    private fun addItem(item: InventoryItem) {
        lifecycleScope.launch(Dispatchers.IO) {
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
}
