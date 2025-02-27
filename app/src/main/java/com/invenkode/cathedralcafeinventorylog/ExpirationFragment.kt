package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpirationFragment : Fragment() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var adapter: InventoryAdapter
    private lateinit var recyclerView: RecyclerView

    // A helper function for ordering items.
    private fun getStatusOrder(expiration: Long, now: Long): Int {
        return when {
            expiration <= now -> 0  // expired
            expiration - now <= 7 * 86_400_000L -> 1  // expiring within one week
            else -> 2  // fresh
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expiration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewExpiration)
        // Instantiate adapter in read-only mode.
        adapter = InventoryAdapter(isEditable = false)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        inventoryDao = InventoryDatabase.getDatabase(requireContext()).inventoryDao()
        inventoryDao.getAll().observe(viewLifecycleOwner) { items ->
            val now = System.currentTimeMillis()
            // Sort items: expired first, then expiring soon, then fresh.
            val sortedItems = items.sortedWith(
                compareBy({ getStatusOrder(it.expirationDate, now) }, { it.expirationDate })
            )
            adapter.submitList(sortedItems)
        }

        // Attach swipe-to-delete functionality with undo.
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.currentList[position]
                lifecycleScope.launch(Dispatchers.IO) {
                    inventoryDao.delete(deletedItem)
                }
                Snackbar.make(recyclerView, "Item deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        lifecycleScope.launch(Dispatchers.IO) {
                            inventoryDao.insert(deletedItem)
                        }
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)

        // Add scroll listener to show FABs when scrolling stops.
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                super.onScrollStateChanged(rv, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    (activity as? MainActivity)?.apply {
                        fab.show()
                        fabExportExpiration.show()
                        fabExportInventory.show()
                    }
                }
            }
        })
    }
}
