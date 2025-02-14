package com.invenkode.cathedralcafeinventorylog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ExpirationFragment()
            1 -> InventoryFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
