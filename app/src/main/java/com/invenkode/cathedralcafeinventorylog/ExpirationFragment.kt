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
    private lateinit var inventoryDao: InventoryDao

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
        adapter = InventoryAdapter(isEditable = true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        firestore = FirebaseFirestore.getInstance()
        inventoryDao = InventoryDatabase.getDatabase(requireContext()).inventoryDao()

        // Observe from Room
        inventoryDao.getAll().observe(viewLifecycleOwner) { allItems ->
            val now = System.currentTimeMillis()
            val expirationItems = allItems.filter {
                !it.storageType.equals("Stock", ignoreCase = true)
            }

            val sortedItems = expirationItems.sortedWith(
                compareBy({ getStatusOrder(it.expirationDate, now) }, { it.expirationDate })
            )

            adapter.submitList(sortedItems)
        }

        // Swipe-to-delete with undo
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.currentList[position]

                lifecycleScope.launch(Dispatchers.IO) {
                    // Delete from Room
                    inventoryDao.delete(deletedItem)

                    // Then delete from Firestore
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
                            // Re-add to Firestore
                            val itemMap = hashMapOf(
                                "name" to deletedItem.name,
                                "variant" to deletedItem.variant,
                                "expirationDate" to deletedItem.expirationDate,
                                "quantity" to deletedItem.quantity,
                                "storageType" to deletedItem.storageType
                            )
                            firestore.collection("inventoryItems").add(itemMap).await()

                            // Re-add to Room
                            inventoryDao.insert(deletedItem)
                        }
                    }
                    .show()
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }
}
