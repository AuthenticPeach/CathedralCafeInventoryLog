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

class ExpirationFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: InventoryAdapter
    private lateinit var recyclerView: RecyclerView

    /**
     * Returns an ordering value:
     * 0 = Expired, 1 = Expiring Soon (within 1 week), 2 = Fresh.
     */
    private fun getStatusOrder(expiration: Long, now: Long): Int {
        return when {
            expiration <= now -> 0
            expiration - now <= 7 * 86_400_000L -> 1
            else -> 2
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expiration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewExpiration)
        // Enable editing so that a long-press launches EditItemActivity.
        adapter = InventoryAdapter(isEditable = true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        firestore = FirebaseFirestore.getInstance()

        // Listen for real-time changes in the Firestore collection "inventoryItems".
        firestore.collection("inventoryItems")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(
                        requireContext(),
                        "Error fetching updates: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    // Map each document to an InventoryItem. Here we use the document’s hash code as an ID.
                    val items = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val variant = doc.getString("variant") ?: ""
                        val expirationDate = doc.getLong("expirationDate") ?: 0L
                        val quantity = (doc.getLong("quantity") ?: 0L).toInt()
                        val storageType = doc.getString("storageType") ?: ""
                        // Exclude Stock items for the expiration tab.
                        if (storageType.equals("Stock", ignoreCase = true)) null
                        else InventoryItem(doc.id.hashCode(), name, variant, expirationDate, quantity, storageType)
                    }
                    // Sort the items by status (expired, expiring soon, fresh) then by expiration date.
                    val sortedItems = items.sortedWith(
                        compareBy({ getStatusOrder(it.expirationDate, now) }, { it.expirationDate })
                    )
                    adapter.submitList(sortedItems)
                }
            }

        // Add swipe-to-delete functionality with an Undo option.
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.currentList[position]
                lifecycleScope.launch(Dispatchers.IO) {
                    // Delete from Firestore.
                    val query = firestore.collection("inventoryItems")
                        .whereEqualTo("name", deletedItem.name)
                        .whereEqualTo("variant", deletedItem.variant)
                        .whereEqualTo("expirationDate", deletedItem.expirationDate)
                        .whereEqualTo("storageType", deletedItem.storageType)
                    val snapshot = query.get().await()
                    for (doc in snapshot.documents) {
                        doc.reference.delete().await()
                    }
                }
                Snackbar.make(recyclerView, "Item deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        lifecycleScope.launch(Dispatchers.IO) {
                            // Re-add the item to Firestore.
                            val itemMap = hashMapOf(
                                "name" to deletedItem.name,
                                "variant" to deletedItem.variant,
                                "expirationDate" to deletedItem.expirationDate,
                                "quantity" to deletedItem.quantity,
                                "storageType" to deletedItem.storageType
                            )
                            firestore.collection("inventoryItems").add(itemMap).await()
                        }
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }
}
