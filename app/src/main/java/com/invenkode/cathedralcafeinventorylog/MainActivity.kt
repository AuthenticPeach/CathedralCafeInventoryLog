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
    lateinit var fabExportCsv: FloatingActionButton

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
        fabExportCsv = findViewById(R.id.fabExportCsv)

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
                2 -> "General Stock"
                else -> ""
            }
        }.attach()

        // Update the FAB behavior based on the current page.
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0, 1 -> {
                        // For Expirations and Inventory, use the default add behavior.
                        fab.setOnClickListener {
                            startActivity(Intent(this@MainActivity, AddItemActivity::class.java))
                        }
                    }
                    2 -> {
                        // For General Stock, pass an extra indicating the default category is "general".
                        fab.setOnClickListener {
                            val intent = Intent(this@MainActivity, AddItemActivity::class.java)
                            intent.putExtra("defaultCategory", "general")
                            startActivity(intent)
                        }
                    }
                }
            }
        })

        // Set up export FAB click listeners.
        fabExportExpiration.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val items = inventoryDao.getAllItemsSync()
                val file = exportReportToPdf(this@MainActivity, items, "Expiration")
                file?.let {
                    val fileUri: Uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",  // consistent authority
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
                        "${applicationContext.packageName}.fileprovider",  // consistent authority
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

        fabExportCsv.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val items = inventoryDao.getAllItemsSync()
                // Change "Expiration" to "Inventory" if needed.
                val file = exportReportToCsv(this@MainActivity, items, "Expiration")
                file?.let {
                    val fileUri: Uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",  // consistent authority
                        it
                    )
                    runOnUiThread {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share CSV Report"))
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

        // Start observing data to update tab badges dynamically.
        observeDataAndUpdateTabs()
    }

    private fun observeDataAndUpdateTabs() {
        inventoryDao.getAll().observe(this) { items ->
            val now = System.currentTimeMillis()
            // For Expirations: count items that are expiring soon or expired, excluding Stock items.
            val expiringSoonCount = items.filter {
                !it.storageType.equals("Stock", ignoreCase = true)
            }.count { it.expirationDate <= now || (it.expirationDate - now) < 7 * 86_400_000L }

            // For Inventory: count only non-stock items with quantity <=2.
            val lowStockCount = items.filter {
                !it.storageType.equals("Stock", ignoreCase = true)
            }.count { it.quantity <= 2 }

            // For General Stock: count items where storageType equals "Stock" and quantity <=2.
            val generalStockLowCount = items.filter {
                it.storageType.equals("Stock", ignoreCase = true)
            }.count { it.quantity <= 2 }

            runOnUiThread {
                updateTabBadge(0, expiringSoonCount)
                updateTabBadge(1, lowStockCount)
                updateTabBadge(2, generalStockLowCount)
            }
        }
    }

    private fun updateTabBadge(tabIndex: Int, count: Int) {
        val tab = tabLayout.getTabAt(tabIndex)
        tab?.orCreateBadge?.apply {
            if (count > 0) {
                number = count
                isVisible = true
            } else {
                isVisible = false
            }
        }
    }
}
