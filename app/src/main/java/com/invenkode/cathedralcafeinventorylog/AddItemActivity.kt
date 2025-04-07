package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

// Data class for a template.
data class ItemTemplate(
    val name: String,
    val variant: String = "",
    val storageType: String
) {
    // Convert to a string for storage in SharedPreferences.
    fun toStorageString(): String = "$name|$variant|$storageType"

    companion object {
        // Parse a stored string back into an ItemTemplate.
        fun fromStorageString(str: String): ItemTemplate? {
            val parts = str.split("|")
            return if (parts.size == 3) ItemTemplate(parts[0], parts[1], parts[2])
            else null
        }
    }
}

class AddItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var firestore: FirebaseFirestore

    private val PREFS_TEMPLATES = "item_templates"
    private val KEY_TEMPLATES = "templates"

    // Change to var so it can be updated.
    private var savedTemplates: List<ItemTemplate> = listOf()

    // Load saved templates from SharedPreferences.
    private fun loadTemplates(): List<ItemTemplate> {
        val prefs = getSharedPreferences(PREFS_TEMPLATES, Context.MODE_PRIVATE)
        val storedSet = prefs.getStringSet(KEY_TEMPLATES, emptySet()) ?: emptySet()
        return storedSet.mapNotNull { ItemTemplate.fromStorageString(it) }
    }

    // Save a new template to SharedPreferences.
    private fun saveTemplate(template: ItemTemplate) {
        val prefs = getSharedPreferences(PREFS_TEMPLATES, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_TEMPLATES, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(template.toStorageString())
        // Use commit() or apply() – here apply() is used.
        prefs.edit().putStringSet(KEY_TEMPLATES, currentSet).apply()
    }

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
        // Spinner for templates – ensure your layout includes this view.
        val spinnerTemplates = findViewById<Spinner>(R.id.spinnerTemplates)
        // Button to save the current entry as a template.
        val btnSaveTemplate = findViewById<Button>(R.id.btnSaveTemplate)

        // Load saved templates.
        savedTemplates = loadTemplates()

        // Populate the template spinner with "None" and any saved templates.
        fun updateTemplateSpinner() {
            val templateOptions = mutableListOf("None")
            templateOptions.addAll(savedTemplates.map { it.name })
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, templateOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTemplates.adapter = adapter
        }
        updateTemplateSpinner()

        // When a template is selected, prefill the entry fields.
        spinnerTemplates.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (position > 0) { // "None" is at position 0.
                    // Use the updated savedTemplates.
                    val selectedTemplate = savedTemplates[position - 1]
                    editTextName.setText(selectedTemplate.name)
                    editTextVariant.setText(selectedTemplate.variant)
                    // Set storage spinner to the template's storageType.
                    val storageAdapter = spinnerStorage.adapter
                    for (i in 0 until storageAdapter.count) {
                        if (storageAdapter.getItem(i).toString().equals(selectedTemplate.storageType, ignoreCase = true)) {
                            spinnerStorage.setSelection(i)
                            break
                        }
                    }
                } else {
                    // Optionally clear fields when "None" is selected.
                    editTextName.text.clear()
                    editTextVariant.text.clear()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Set up spinner adapter for storage types.
        ArrayAdapter.createFromResource(
            this,
            R.array.storage_types, // Should include "Fridge", "Display", and "Stock"
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerStorage.adapter = adapter
        }

        // Hide the date picker if "Stock" is selected.
        spinnerStorage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selected = parent?.getItemAtPosition(position).toString()
                datePickerContainer.visibility =
                    if (selected.equals("Stock", ignoreCase = true)) View.GONE else View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                datePickerContainer.visibility = View.VISIBLE
            }
        }

        // Set up "Save as Template" button.
        btnSaveTemplate.setOnClickListener {
            val name = editTextName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val variant = editTextVariant.text.toString().trim()
            val storageType = spinnerStorage.selectedItem.toString()
            val newTemplate = ItemTemplate(name, variant, storageType)
            saveTemplate(newTemplate)
            Toast.makeText(this, "Template saved", Toast.LENGTH_SHORT).show()
            // Update the savedTemplates variable and refresh the spinner.
            savedTemplates = loadTemplates()
            updateTemplateSpinner()
        }

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
                0L
            }

            CoroutineScope(Dispatchers.IO).launch {
                val newName = getBatchName(baseName)
                val item = InventoryItem(
                    name = newName,
                    variant = variant,
                    expirationDate = expirationDate,
                    quantity = 6,
                    storageType = storageType
                )
                inventoryDao.insert(item)

                val itemMap = hashMapOf(
                    "name" to item.name,
                    "variant" to item.variant,
                    "expirationDate" to item.expirationDate,
                    "quantity" to item.quantity,
                    "storageType" to item.storageType
                )
                firestore.collection("inventoryItems")
                    .add(itemMap)
                    .addOnSuccessListener {
                        runOnUiThread {
                            Toast.makeText(this@AddItemActivity, "Item added successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    .addOnFailureListener { exception: Exception ->
                        runOnUiThread {
                            Toast.makeText(this@AddItemActivity, "Failed to add item to cloud", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private suspend fun getBatchName(baseName: String): String {
        val existingItems = inventoryDao.getItemsByName(baseName)
        return if (existingItems.isEmpty()) {
            baseName
        } else {
            "$baseName Batch ${existingItems.size + 1}"
        }
    }
}
