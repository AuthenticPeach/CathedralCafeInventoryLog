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
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class EditItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_item)

        // Initialize local Room DAO and Firestore.
        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()
        firestore = FirebaseFirestore.getInstance()

        // Get views.
        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextVariant = findViewById<EditText>(R.id.editTextVariant)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val datePickerContainer = findViewById<LinearLayout>(R.id.datePickerContainer)
        val spinnerItemType = findViewById<Spinner>(R.id.spinnerItemType)
        val btnUpdate = findViewById<Button>(R.id.btnUpdate)

        // Set up spinner adapter using your item types defined in resources.
        ArrayAdapter.createFromResource(
            this,
            R.array.item_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerItemType.adapter = adapter
        }

        // Retrieve current values passed via the Intent.
        val itemId = intent.getIntExtra("itemId", 0)
        val currentName = intent.getStringExtra("name") ?: ""
        val currentVariant = intent.getStringExtra("variant") ?: ""
        val currentExpiration = intent.getLongExtra("expirationDate", 0L)
        val currentQuantity = intent.getIntExtra("quantity", 6)
        val currentStorageType = intent.getStringExtra("storageType") ?: "Fridge"

        // Populate fields.
        editTextName.setText(currentName)
        editTextVariant.setText(currentVariant)

        // Set spinner selection based on current storage type.
        val spinnerAdapter = spinnerItemType.adapter
        for (i in 0 until spinnerAdapter.count) {
            if (spinnerAdapter.getItem(i).toString().equals(currentStorageType, ignoreCase = true)) {
                spinnerItemType.setSelection(i)
                break
            }
        }

        // Hide date picker if storage type is "Stock".
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

            // Build the updated item (keeping quantity unchanged here).
            val updatedItem = InventoryItem(
                id = itemId,
                name = updatedName,
                variant = updatedVariant,
                expirationDate = updatedExpiration,
                quantity = currentQuantity,
                storageType = updatedStorageType
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Update Room database.
                    inventoryDao.update(updatedItem)

                    // Update Firestore.
                    // (This example uses a query based on original fields; in a production app,
                    //  consider storing the Firestore document ID in your local database.)
                    val query = firestore.collection("inventoryItems")
                        .whereEqualTo("name", currentName)
                        .whereEqualTo("variant", currentVariant)
                        .whereEqualTo("expirationDate", currentExpiration)
                        .whereEqualTo("quantity", currentQuantity)
                        .whereEqualTo("storageType", currentStorageType)
                    val querySnapshot = query.get().await()
                    for (doc in querySnapshot.documents) {
                        val updatedMap = mapOf(
                            "name" to updatedName,
                            "variant" to updatedVariant,
                            "expirationDate" to updatedExpiration,
                            "quantity" to currentQuantity,
                            "storageType" to updatedStorageType
                        )
                        // Await the update call.
                        doc.reference.update(updatedMap).await()
                    }
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditItemActivity, "Error updating item", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
