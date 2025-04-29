package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.util.Calendar

data class ItemTemplate(
    val name: String,
    val variant: String = "",
    val storageType: String
) {
    fun toStorageString(): String = "$name|$variant|$storageType"
    companion object {
        fun fromStorageString(str: String): ItemTemplate? {
            val parts = str.split("|")
            return if (parts.size == 3) ItemTemplate(parts[0], parts[1], parts[2]) else null
        }
    }
}

class AddItemActivity : AppCompatActivity() {

    private lateinit var inventoryDao: InventoryDao
    private lateinit var firestore: FirebaseFirestore

    private val PREFS_TEMPLATES = "item_templates"
    private val KEY_TEMPLATES = "templates"

    private var savedTemplates: MutableList<ItemTemplate> = mutableListOf()
    private lateinit var spinnerTemplates: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()
        firestore = FirebaseFirestore.getInstance()

        // Bind views
        spinnerTemplates = findViewById(R.id.spinnerTemplates)
        val spinnerStorage = findViewById<Spinner>(R.id.spinnerStorageType)
        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextVariant = findViewById<EditText>(R.id.editTextVariant)
        val datePicker = findViewById<DatePicker>(R.id.datePicker)
        val datePickerContainer = findViewById<LinearLayout>(R.id.datePickerContainer)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnSaveTemplate = findViewById<Button>(R.id.btnSaveTemplate)
        val btnManageTemplates = findViewById<Button>(R.id.btnManageTemplates)
        // <-- enable the Up button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Load and show templates
        savedTemplates = loadTemplates()
        refreshSpinner()

        // Template selection
        spinnerTemplates.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                if (pos > 0) {
                    val tmpl = savedTemplates[pos - 1]
                    editTextName.setText(tmpl.name)
                    editTextVariant.setText(tmpl.variant)
                    // set storage
                    for (i in 0 until spinnerStorage.adapter.count) {
                        if (spinnerStorage.adapter.getItem(i).toString()
                                .equals(tmpl.storageType, ignoreCase = true)
                        ) {
                            spinnerStorage.setSelection(i)
                            break
                        }
                    }
                } else {
                    editTextName.text.clear()
                    editTextVariant.text.clear()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Storage spinner
        ArrayAdapter.createFromResource(
            this, R.array.storage_types, android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerStorage.adapter = it
        }
        spinnerStorage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                datePickerContainer.visibility =
                    if (spinnerStorage.selectedItem.toString().equals("Stock", true)) DatePicker.INVISIBLE else DatePicker.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Save as template
        btnSaveTemplate.setOnClickListener {
            val name = editTextName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            val tmpl = ItemTemplate(name, editTextVariant.text.toString().trim(), spinnerStorage.selectedItem.toString())
            saveTemplate(tmpl)
            savedTemplates = loadTemplates()
            refreshSpinner()
            Toast.makeText(this, "Template saved", Toast.LENGTH_SHORT).show()
        }

        // Manage (delete/export/import)
        btnManageTemplates.setOnClickListener {
            if (savedTemplates.isEmpty()) {
                Toast.makeText(this, "No templates to manage", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val actions = arrayOf("Delete a Template", "Export Templates Backup", "Import Templates from Backup")
            AlertDialog.Builder(this)
                .setTitle("Manage Templates")
                .setItems(actions) { _, which ->
                    when (which) {
                        0 -> showDeleteDialog()
                        1 -> exportBackup()
                        2 -> importBackup()
                    }
                }
                .show()
        }

        // Add item
        btnAdd.setOnClickListener {
            val baseName = editTextName.text.toString().trim()
            val variant = editTextVariant.text.toString().trim()
            val storage = spinnerStorage.selectedItem.toString()
            val expirationDate = if (datePickerContainer.visibility == DatePicker.VISIBLE) {
                Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                }.timeInMillis
            } else 0L

            CoroutineScope(Dispatchers.IO).launch {
                val newName = getBatchName(baseName)
                val item = InventoryItem(
                    name = newName,
                    variant = variant,
                    expirationDate = expirationDate,
                    quantity = 6,
                    storageType = storage
                )
                inventoryDao.insert(item)
                firestore.collection("inventoryItems").add(
                    mapOf(
                        "name" to item.name,
                        "variant" to item.variant,
                        "expirationDate" to item.expirationDate,
                        "quantity" to item.quantity,
                        "storageType" to item.storageType
                    )
                )
                runOnUiThread {
                    Toast.makeText(this@AddItemActivity, "Item added", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // -- TEMPLATE HELPERS --

    private fun refreshSpinner() {
        savedTemplates.sortBy { it.name }
        val opts = listOf("None") + savedTemplates.map { it.name }
        spinnerTemplates.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, opts
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun loadTemplates(): MutableList<ItemTemplate> {
        val prefs = getSharedPreferences(PREFS_TEMPLATES, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_TEMPLATES, emptySet()) ?: emptySet()
        return set.mapNotNull { ItemTemplate.fromStorageString(it) }
            .sortedBy { it.name }
            .toMutableList()
    }

    private fun saveTemplate(template: ItemTemplate) {
        val prefs = getSharedPreferences(PREFS_TEMPLATES, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_TEMPLATES, mutableSetOf())!!.toMutableSet()
        set.add(template.toStorageString())
        prefs.edit().putStringSet(KEY_TEMPLATES, set).apply()
    }

    private fun deleteTemplate(idx: Int) {
        val toRemove = savedTemplates[idx].toStorageString()
        val prefs = getSharedPreferences(PREFS_TEMPLATES, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_TEMPLATES, mutableSetOf())!!.toMutableSet()
        set.remove(toRemove)
        prefs.edit().putStringSet(KEY_TEMPLATES, set).apply()
        savedTemplates.removeAt(idx)
    }

    private fun showDeleteDialog() {
        val names = savedTemplates.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Delete a Template?")
            .setItems(names) { _, idx ->
                AlertDialog.Builder(this)
                    .setMessage("Really delete “${names[idx]}”?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteTemplate(idx)
                        savedTemplates = loadTemplates()
                        refreshSpinner()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()      // go back to the TabActivity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun exportBackup() {
        val file = File(getExternalFilesDir(null), "templates_backup.txt")
        try {
            BufferedWriter(FileWriter(file)).use { out ->
                savedTemplates.forEach { out.write(it.toStorageString() + "\n") }
            }
            val uri: Uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                setType("text/plain")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share Templates Backup"))
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importBackup() {
        val file = File(getExternalFilesDir(null), "templates_backup.txt")
        if (!file.exists()) {
            Toast.makeText(this, "No backup file found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val imported = mutableListOf<ItemTemplate>()
            BufferedReader(FileReader(file)).useLines { lines ->
                lines.forEach { line ->
                    ItemTemplate.fromStorageString(line.trim())?.let { imported.add(it) }
                }
            }
            if (imported.isEmpty()) {
                Toast.makeText(this, "No valid templates in file", Toast.LENGTH_SHORT).show()
            } else {
                overwriteAllTemplates(imported)
                savedTemplates = loadTemplates()
                refreshSpinner()
                Toast.makeText(this, "Imported ${imported.size} templates", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun overwriteAllTemplates(newTemplates: List<ItemTemplate>) {
        val prefs = getSharedPreferences(PREFS_TEMPLATES, Context.MODE_PRIVATE)
        val set = newTemplates.map { it.toStorageString() }.toSet()
        prefs.edit().putStringSet(KEY_TEMPLATES, set).apply()
    }

    private suspend fun getBatchName(baseName: String): String {
        val existing = inventoryDao.getItemsByName(baseName)
        return if (existing.isEmpty()) baseName else "$baseName Batch ${existing.size + 1}"
    }
}
