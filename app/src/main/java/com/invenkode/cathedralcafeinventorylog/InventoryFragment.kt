package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class InventoryFragment : Fragment() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var adapter: InventoryCountAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_inventory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewInventory)
        adapter = InventoryCountAdapter(onQuantityChanged = { item, newQty ->
            lifecycleScope.launch(Dispatchers.IO) {
                inventoryDao.update(item.copy(quantity = newQty))
            }
        }, enableEdit = true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        inventoryDao = InventoryDatabase.getDatabase(requireContext()).inventoryDao()
        inventoryDao.getAll().observe(viewLifecycleOwner) { items ->
            // Filter out items stored as "Stock".
            val filteredItems = items.filter { !it.storageType.equals("Stock", ignoreCase = true) }
            // Sort items alphabetically by name (ignoring case).
            val sortedItems = filteredItems.sortedBy { it.name.toLowerCase(Locale.getDefault()) }
            adapter.submitList(sortedItems)
        }
    }
}
