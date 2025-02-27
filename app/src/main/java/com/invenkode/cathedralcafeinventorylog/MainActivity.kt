package com.invenkode.cathedralcafeinventorylog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {

    // Public FAB properties so fragments can access them.
    lateinit var fab: FloatingActionButton
    lateinit var fabExportExpiration: FloatingActionButton
    lateinit var fabExportInventory: FloatingActionButton

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var inventoryDao: InventoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the database DAO.
        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()

        // Initialize FABs.
        fab = findViewById(R.id.fab)
        fabExportExpiration = findViewById(R.id.fabExportExpiration)
        fabExportInventory = findViewById(R.id.fabExportInventory)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        // Customize TabLayout colors.
        tabLayout.setTabTextColors(getColor(android.R.color.darker_gray), getColor(android.R.color.black))
        tabLayout.setSelectedTabIndicatorColor(getColor(android.R.color.holo_blue_dark))
        tabLayout.setTabTextColors(Color.DKGRAY, Color.BLACK)
        tabLayout.setSelectedTabIndicatorColor(Color.BLUE)
        tabLayout.setBackgroundColor(Color.WHITE)

        // Set up the ViewPager with MainPagerAdapter.
        val pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Expirations"
                1 -> "Inventory"
                else -> ""
            }
        }.attach()

        // Set up FAB click listeners.
        fab.setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java))
        }

        fabExportExpiration.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val items = inventoryDao.getAllItemsSync()
                val file = exportReportToPdf(this@MainActivity, items, "Expiration")
                file?.let {
                    val fileUri: Uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",
                        it
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runOnUiThread {
                        startActivity(Intent.createChooser(shareIntent, "Share Expiration Report"))
                    }
                }
            }
        }

        fabExportInventory.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val items = inventoryDao.getAllItemsSync()
                val file = exportReportToPdf(this@MainActivity, items, "Inventory")
                file?.let {
                    val fileUri: Uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",
                        it
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runOnUiThread {
                        startActivity(Intent.createChooser(shareIntent, "Share Inventory Report"))
                    }
                }
            }
        }

        // Schedule WorkManager to check expiration dates daily.
        val workRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "expirationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Update badges on the tabs.
        updateTabBadges()
    }
    
    private fun updateTabBadges() {
        // For example, suppose we query the database (or filter a list) to count red-zone items.
        lifecycleScope.launch(Dispatchers.IO) {
            val items = inventoryDao.getAllItemsSync()
            val now = System.currentTimeMillis()
            // Red zone for Expirations: expired or expiring within one week.
            val expirationCount = items.count {
                it.expirationDate <= now || (it.expirationDate - now) < 7 * 86_400_000L
            }
            // Red zone for Inventory: quantity 3 or less.
            val inventoryCount = items.count { it.quantity <= 3 }

            runOnUiThread {
                // Update badge for Expirations tab (position 0).
                tabLayout.getTabAt(0)?.apply {
                    val badge = orCreateBadge
                    badge.number = expirationCount
                    badge.isVisible = expirationCount > 0
                }
                // Update badge for Inventory tab (position 1).
                tabLayout.getTabAt(1)?.apply {
                    val badge = orCreateBadge
                    badge.number = inventoryCount
                    badge.isVisible = inventoryCount > 0
                }
            }
        }
    }
}
