package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AddItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        // Initialize DAO.
        val db = InventoryDatabase.getDatabase(this)
        inventoryDao = db.inventoryDao()

        // Views from the layout.
        val spinnerStorage = findViewById<Spinner>(R.id.spinnerStorageType)
        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextVariant = findViewById<EditText>(R.id.editTextVariant)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val datePickerContainer = findViewById<LinearLayout>(R.id.datePickerContainer)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        // Set up spinner with storage types from resources.
        ArrayAdapter.createFromResource(
            this,
            R.array.storage_types, // Ensure this array includes "Fridge", "Display", and "Stock"
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerStorage.adapter = adapter
        }

        // Hide the date picker if "Stock" is selected.
        spinnerStorage.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selected = parent?.getItemAtPosition(position).toString()
                datePickerContainer.visibility =
                    if (selected.equals("Stock", ignoreCase = true)) View.GONE else View.VISIBLE
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                datePickerContainer.visibility = View.VISIBLE
            }
        })

        btnAdd.setOnClickListener {
            val baseName = editTextName.text.toString().trim()
            val variant = editTextVariant.text.toString().trim()
            // Get the storage type from the spinner.
            val storageType = spinnerStorage.selectedItem.toString()

            // Retrieve expiration date only if the date picker is visible.
            val expirationDate: Long = if (datePickerContainer.visibility == View.VISIBLE) {
                val calendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                }
                calendar.timeInMillis
            } else {
                0L // For non-expiring items.
            }

            // Launch a coroutine to compute the new name (with batch suffix if needed) and insert the item.
            CoroutineScope(Dispatchers.IO).launch {
                val newName = getBatchName(baseName)
                // Create a new InventoryItem with default quantity 6.
                val item = InventoryItem(
                    name = newName,
                    variant = variant,
                    expirationDate = expirationDate,
                    quantity = 6,
                    storageType = storageType // Expected to be "Fridge", "Display", or "Stock".
                )
                inventoryDao.insert(item)
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    // Helper function to compute a new name with a "Batch X" suffix.
    // If an item exists with the base name, treat that as Batch 1 so that the new duplicate becomes Batch 2.
    private suspend fun getBatchName(baseName: String): String {
        val existingItems = inventoryDao.getItemsByName(baseName)
        if (existingItems.isEmpty()) {
            return baseName
        } else {
            var maxBatch = 0
            for (item in existingItems) {
                if (item.name == baseName) {
                    // Treat the base item as Batch 1.
                    maxBatch = maxOf(maxBatch, 1)
                } else if (item.name.startsWith("$baseName Batch ")) {
                    val suffix = item.name.substringAfter("$baseName Batch ").trim()
                    val number = suffix.toIntOrNull() ?: 0
                    maxBatch = maxOf(maxBatch, number)
                }
            }
            return "$baseName Batch ${maxBatch + 1}"
        }
    }
}
