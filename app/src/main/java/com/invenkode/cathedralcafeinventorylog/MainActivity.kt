package com.invenkode.cathedralcafeinventorylog

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var fab: FloatingActionButton
    private lateinit var fabExport: FloatingActionButton
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var inventoryDao: InventoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()
        fab = findViewById(R.id.fab)
        fabExport = findViewById(R.id.fabExport)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        // Tab appearance
        tabLayout.setTabTextColors(
            ContextCompat.getColor(this, android.R.color.darker_gray),
            ContextCompat.getColor(this, android.R.color.black)
        )
        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        tabLayout.setBackgroundColor(Color.WHITE)

        // Create fragments and assign badge listeners
        val stockFragment = GeneralStockFragment().apply {
            badgeListener = object : BadgeUpdateListener {
                override fun onStockBadgeCountChanged(count: Int) {
                    updateTabBadge(2, count)
                }
            }
        }

        // Attach fragments to view pager
        viewPager.adapter = MainPagerAdapter(this, stockFragment)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Expirations"
                1 -> "Recipes"
                2 -> "General Stock"
                else -> ""
            }
        }.attach()

        // Observe database to update expiration tab badge
        observeDataAndUpdateTabs()

        // FAB button behavior
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                fab.setOnClickListener {
                    val intent = Intent(this@MainActivity, AddItemActivity::class.java)
                    if (position == 2) intent.putExtra("defaultCategory", "general")
                    startActivity(intent)
                }
            }
        })

        // Export dialog
        fabExport.setOnClickListener {
            val options = arrayOf(
                "Export Expiration PDF",
                "Export Expiration CSV",
                "Export Stock PDF"
            )
            val titleView = TextView(this).apply {
                text = "Choose Export Option"
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(48, 24, 48, 24)
                setBackgroundColor(Color.DKGRAY)
            }
            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> exportReport("Expiration", "pdf")
                        1 -> exportReport("Expiration", "csv")
                        2 -> exportStockPdf()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Schedule background worker
        val work = PeriodicWorkRequestBuilder<ExpirationWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("expirationWorker", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    private fun exportStockPdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            val firestore = FirebaseFirestore.getInstance()
            val file = exportStockToPdf(this@MainActivity, firestore)
            file?.let {
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", it)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(intent, "Share Stock Report"))
                }
            }
        }
    }

    private fun exportReport(reportType: String, format: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val firestore = FirebaseFirestore.getInstance()
            val file = if (format == "pdf")
                exportReportToPdf(this@MainActivity, firestore, reportType)
            else
                exportReportToCsv(this@MainActivity, firestore, reportType)

            file?.let {
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", it)
                val mime = if (format == "pdf") "application/pdf" else "text/csv"
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(share, "Share $reportType Report"))
                }
            }
        }
    }

    private fun observeDataAndUpdateTabs() {
        inventoryDao.getAll().observe(this) { items ->
            val now = System.currentTimeMillis()

            val expiringSoon = items
                .filter { !it.storageType.equals("Stock", true) }
                .count { it.expirationDate <= now || it.expirationDate - now < 7 * 86_400_000L }

            val lowStock = items
                .filter { it.storageType.equals("Stock", true) }
                .count {
                    if (it.stockSubCategory.equals("Milks", true)) {
                        it.quantity < (it.idealThreshold ?: Int.MAX_VALUE)
                    } else {
                        it.isRunningLow
                    }
                }

            updateTabBadge(0, expiringSoon)
            updateTabBadge(2, lowStock) // ✅ Make sure this is present
        }
    }



    private fun updateTabBadge(tabIndex: Int, count: Int) {
        val tab = tabLayout.getTabAt(tabIndex) ?: return
        if (count > 0) {
            val badge = tab.orCreateBadge
            badge.number = count
            badge.backgroundColor = Color.RED
            badge.badgeTextColor = Color.WHITE
            badge.isVisible = true
        } else {
            tab.removeBadge()
        }
    }
}
