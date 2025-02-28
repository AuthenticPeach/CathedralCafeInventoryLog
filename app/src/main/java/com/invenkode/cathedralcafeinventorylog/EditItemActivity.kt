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

class EditItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_item)

        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()

        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextVariant = findViewById<EditText>(R.id.editTextVariant)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val datePickerContainer = findViewById<LinearLayout>(R.id.datePickerContainer)
        val spinnerItemType = findViewById<Spinner>(R.id.spinnerItemType)
        val btnUpdate = findViewById<Button>(R.id.btnUpdate)

        // Set up spinner adapter.
        ArrayAdapter.createFromResource(
            this,
            R.array.item_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerItemType.adapter = adapter
        }

        // Retrieve the current values from the Intent.
        val itemId = intent.getIntExtra("itemId", 0)
        val currentName = intent.getStringExtra("name") ?: ""
        val currentVariant = intent.getStringExtra("variant") ?: ""
        val currentExpiration = intent.getLongExtra("expirationDate", 0L)
        val currentQuantity = intent.getIntExtra("quantity", 6)
        val currentStorageType = intent.getStringExtra("storageType") ?: "Fridge"

        // Populate the fields.
        editTextName.setText(currentName)
        editTextVariant.setText(currentVariant)

        // Set spinner selection based on currentStorageType.
        val spinnerAdapter = spinnerItemType.adapter
        for (i in 0 until spinnerAdapter.count) {
            if (spinnerAdapter.getItem(i).toString().equals(currentStorageType, ignoreCase = true)) {
                spinnerItemType.setSelection(i)
                break
            }
        }

        // If the storage type is "Stock", hide the date picker.
        if (currentStorageType.equals("Stock", ignoreCase = true)) {
            datePickerContainer.visibility = View.GONE
        } else {
            datePickerContainer.visibility = View.VISIBLE
            if (currentExpiration > 0L) {
                val calendar = Calendar.getInstance().apply { timeInMillis = currentExpiration }
                datePicker.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            }
        }

        btnUpdate.setOnClickListener {
            val updatedName = editTextName.text.toString()
            val updatedVariant = editTextVariant.text.toString()
            val updatedStorageType = spinnerItemType.selectedItem.toString()
            val updatedExpiration = if (datePickerContainer.visibility == View.VISIBLE) {
                val calendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                }
                calendar.timeInMillis
            } else {
                0L
            }

            val updatedItem = InventoryItem(
                id = itemId,
                name = updatedName,
                variant = updatedVariant,
                expirationDate = updatedExpiration,
                quantity = currentQuantity,
                storageType = updatedStorageType
            )
            CoroutineScope(Dispatchers.IO).launch {
                inventoryDao.update(updatedItem)
            }
            finish()
        }
    }
}
