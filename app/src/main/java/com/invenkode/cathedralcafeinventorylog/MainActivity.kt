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

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var inventoryDao: InventoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the database DAO.
        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()

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

        // Floating Action Button to add a new item.
        val fabAdd = findViewById<FloatingActionButton>(R.id.fab)
        fabAdd.setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java))
        }

        // Export Expiration Report button.
        val fabExportExpiration = findViewById<FloatingActionButton>(R.id.fabExportExpiration)
        fabExportExpiration.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                // Get all items (you can filter if needed)
                val items = inventoryDao.getAllItemsSync()
                // Generate the PDF report for expiration.
                val file = exportReportToPdf(this@MainActivity, items, "Expiration")
                file?.let {
                    // Get a URI via FileProvider (ensure your FileProvider is set up in the manifest)
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

        // Export Inventory Report button.
        val fabExportInventory = findViewById<FloatingActionButton>(R.id.fabExportInventory)
        fabExportInventory.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val items = inventoryDao.getAllItemsSync()
                // Generate the PDF report for inventory quantities.
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
        val workRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "expirationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
