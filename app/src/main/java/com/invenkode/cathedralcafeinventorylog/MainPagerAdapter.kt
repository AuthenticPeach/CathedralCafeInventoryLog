package com.invenkode.cathedralcafeinventorylog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(
    activity: AppCompatActivity,
    private val stockFragment: GeneralStockFragment
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ExpirationFragment()
            1 -> RecipesFragment()
            2 -> stockFragment
            else -> throw IllegalArgumentException("Invalid tab index")
        }
    }
}

