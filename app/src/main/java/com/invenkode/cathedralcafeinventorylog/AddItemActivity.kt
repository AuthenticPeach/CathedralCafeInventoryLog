package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
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

        val editTextName = findViewById<EditText>(R.id.editTextName)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val spinnerStorage = findViewById<Spinner>(R.id.spinnerStorageType)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        // Set up spinner with storage types from resources.
        ArrayAdapter.createFromResource(
            this,
            R.array.storage_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerStorage.adapter = adapter
        }

        btnAdd.setOnClickListener {
            val name = editTextName.text.toString()
            // Retrieve expiration date from DatePicker.
            val calendar = Calendar.getInstance().apply {
                set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
            }
            val expirationDate = calendar.timeInMillis

            // Get selected storage type.
            val storageType = spinnerStorage.selectedItem.toString()

            // Create a new InventoryItem with default quantity 6 and selected storageType.
            val item = InventoryItem(name = name, expirationDate = expirationDate, storageType = storageType)

            CoroutineScope(Dispatchers.IO).launch {
                inventoryDao.insert(item)
            }
            finish()
        }
    }
}
