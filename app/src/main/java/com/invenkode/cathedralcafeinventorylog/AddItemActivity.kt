package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
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
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        btnAdd.setOnClickListener {
            val name = editTextName.text.toString()
            // Get selected date from DatePicker
            val calendar = Calendar.getInstance().apply {
                set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
            }
            val expirationDate = calendar.timeInMillis

            val item = InventoryItem(name = name, expirationDate = expirationDate)
            CoroutineScope(Dispatchers.IO).launch {
                inventoryDao.insert(item)
            }
            finish()
        }
    }
}
