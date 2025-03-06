package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeneralStockFragment : Fragment() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var adapter: InventoryCountAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_general_stock, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewGeneral)
        adapter = InventoryCountAdapter(onQuantityChanged = { item, newQty ->
            lifecycleScope.launch(Dispatchers.IO) {
                inventoryDao.update(item.copy(quantity = newQty))
            }
        }, enableEdit = true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        inventoryDao = InventoryDatabase.getDatabase(requireContext()).inventoryDao()
        inventoryDao.getAll().observe(viewLifecycleOwner) { items ->
            val stockItems = items.filter { it.storageType.equals("Stock", ignoreCase = true) }
            adapter.submitList(stockItems)
        }
        // Swipe-to-delete with undo.
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.currentList[position]
                lifecycleScope.launch(Dispatchers.IO) { inventoryDao.delete(deletedItem) }
                Snackbar.make(recyclerView, "Item deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        lifecycleScope.launch(Dispatchers.IO) { inventoryDao.insert(deletedItem) }
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }
}
