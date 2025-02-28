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
import java.util.Calendar

class AddItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        // Initialize DAO
        val db = InventoryDatabase.getDatabase(this)
        inventoryDao = db.inventoryDao()

        val spinnerItemType = findViewById<Spinner>(R.id.spinnerItemType)
        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextVariant = findViewById<EditText>(R.id.editTextVariant)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val datePickerContainer = findViewById<LinearLayout>(R.id.datePickerContainer)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        // Set up spinner with item types from resources.
        ArrayAdapter.createFromResource(
            this,
            R.array.item_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerItemType.adapter = adapter
        }

        // Hide the date picker if "Stock" is selected.
        spinnerItemType.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selected = parent?.getItemAtPosition(position).toString()
                datePickerContainer.visibility = if (selected.equals("Stock", ignoreCase = true)) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                datePickerContainer.visibility = View.VISIBLE
            }
        })

        btnAdd.setOnClickListener {
            val name = editTextName.text.toString()
            val variant = editTextVariant.text.toString()
            val itemType = spinnerItemType.selectedItem.toString()  // This now serves as the storage type.

            // Only retrieve expiration date if the datePicker is visible.
            val expirationDate: Long = if (datePickerContainer.visibility == View.VISIBLE) {
                val calendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                }
                calendar.timeInMillis
            } else {
                0L  // Use 0 for items that don't expire.
            }

            // Create a new InventoryItem with default quantity 6.
            val item = InventoryItem(
                name = name,
                variant = variant,
                expirationDate = expirationDate,
                quantity = 6,
                storageType = itemType  // "Fridge", "Display", or "Stock".
            )

            CoroutineScope(Dispatchers.IO).launch {
                inventoryDao.insert(item)
            }
            finish()
        }
    }
}
