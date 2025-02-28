package com.invenkode.cathedralcafeinventorylog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> ExpirationFragment()   // Should filter to non-stock items
        1 -> InventoryFragment()    // Should filter to non-stock items
        2 -> GeneralStockFragment() // Only shows items with storageType == "Stock"
        else -> Fragment()
    }
}
