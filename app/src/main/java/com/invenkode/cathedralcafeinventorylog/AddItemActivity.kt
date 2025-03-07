package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AddItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        // Initialize local Room DAO.
        val db = InventoryDatabase.getDatabase(this)
        inventoryDao = db.inventoryDao()

        // Initialize Firestore.
        firestore = FirebaseFirestore.getInstance()

        // Get views from the layout.
        val spinnerStorage = findViewById<Spinner>(R.id.spinnerStorageType)
        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextVariant = findViewById<EditText>(R.id.editTextVariant)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val datePickerContainer = findViewById<LinearLayout>(R.id.datePickerContainer)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        // Set up spinner with storage types from resources.
        ArrayAdapter.createFromResource(
            this,
            R.array.storage_types, // This array should include "Fridge", "Display", and "Stock"
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

            // Launch a coroutine to compute the new name and insert the item.
            CoroutineScope(Dispatchers.IO).launch {
                val newName = getBatchName(baseName)
                // Create a new InventoryItem with default quantity 6.
                val item = InventoryItem(
                    name = newName,
                    variant = variant,
                    expirationDate = expirationDate,
                    quantity = 6,
                    storageType = storageType // Expected: "Fridge", "Display", or "Stock"
                )
                // Insert into Room.
                inventoryDao.insert(item)

                // Also add to Firestore.
                val itemMap = hashMapOf(
                    "name" to item.name,
                    "variant" to item.variant,
                    "expirationDate" to item.expirationDate,
                    "quantity" to item.quantity,
                    "storageType" to item.storageType
                )
                firestore.collection("inventoryItems")
                    .add(itemMap)
                    .addOnSuccessListener { documentReference ->
                        Log.d("AddItemActivity", "Item added to Firestore with ID: ${documentReference.id}")
                        Toast.makeText(this@AddItemActivity, "Item added successfully", Toast.LENGTH_SHORT).show()
                        CoroutineScope(Dispatchers.Main).launch {
                            finish()
                        }
                    }
                    .addOnFailureListener { exception: Exception ->
                        Log.e("AddItemActivity", "Error adding item to Firestore", exception)
                        Toast.makeText(this@AddItemActivity, "Failed to add item to cloud", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // Helper function to compute a new name with a "Batch" suffix.
    // This function is now a member of the Activity.
    private suspend fun getBatchName(baseName: String): String {
        val existingItems = inventoryDao.getItemsByName(baseName)
        if (existingItems.isEmpty()) {
            return baseName
        } else {
            var maxBatch = 1 // Assume base item is Batch 1 so the new duplicate becomes Batch 2.
            for (item in existingItems) {
                if (item.name.startsWith("$baseName Batch ")) {
                    val suffix = item.name.substringAfter("$baseName Batch ").trim()
                    val number = suffix.toIntOrNull() ?: 1
                    maxBatch = maxOf(maxBatch, number + 1)
                }
            }
            return "$baseName Batch $maxBatch"
        }
    }
}
