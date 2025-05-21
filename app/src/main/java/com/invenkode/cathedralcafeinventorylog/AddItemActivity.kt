package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
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

// Template model
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
    private lateinit var spinnerTemplates: Spinner
    private var savedTemplates: MutableList<ItemTemplate> = mutableListOf()

    private val prefsTemplates = "item_templates"
    private val keyTemplates = "templates"
    private val importBackupRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        // DAOs / Firestore
        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()
        firestore = FirebaseFirestore.getInstance()

        // --- findViewById ---
        spinnerTemplates = findViewById(R.id.spinnerTemplates)
        val spinnerStorage      = findViewById<Spinner>(R.id.spinnerStorageType)
        val editTextName        = findViewById<EditText>(R.id.editTextName)
        val editTextVariant     = findViewById<EditText>(R.id.editTextVariant)
        val btnAdd              = findViewById<Button>(R.id.btnAdd)
        val btnSaveTemplate     = findViewById<Button>(R.id.btnSaveTemplate)
        val btnManageTemplates  = findViewById<Button>(R.id.btnManageTemplates)
        val stockOptions        = findViewById<LinearLayout>(R.id.stockOptionsContainer)
        val spinnerSubCat       = findViewById<Spinner>(R.id.spinnerStockSubCategory)
        val switchAlertType     = findViewById<Switch>(R.id.switchAlertType)
        val npThreshold         = findViewById<NumberPicker>(R.id.numberPickerIdealThreshold)
        val tbRunningLow        = findViewById<ToggleButton>(R.id.toggleRunningLow)
        val vsDatePicker        = findViewById<ViewStub>(R.id.vsDatePicker)

        // we’ll inflate this on demand
        var datePickerContainer: LinearLayout? = null
        var datePicker: DatePicker?           = null

        // --- Templates Spinner ---
        savedTemplates = loadTemplates()
        refreshSpinner()
        spinnerTemplates.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos > 0) {
                    val tmpl = savedTemplates[pos - 1]
                    editTextName.setText(tmpl.name)
                    editTextVariant.setText(tmpl.variant)
                    // set storageType
                    for (i in 0 until spinnerStorage.adapter.count) {
                        if (spinnerStorage.adapter.getItem(i)
                                .toString().equals(tmpl.storageType, ignoreCase = true)
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

        // --- Storage Spinner ---
        ArrayAdapter.createFromResource(
            this, R.array.storage_types, android.R.layout.simple_spinner_item
        )
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            .also { spinnerStorage.adapter = it }

        spinnerStorage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, pos: Int, id: Long
            ) {
                val isStock = spinnerStorage.selectedItem.toString() == "Stock"

                // inflate / show or hide calendar
                if (isStock) {
                    datePickerContainer?.visibility = View.GONE
                } else {
                    if (datePickerContainer == null) {
                        datePickerContainer = vsDatePicker.inflate() as LinearLayout
                        datePicker = datePickerContainer!!.findViewById(R.id.datePicker)
                    }
                    datePickerContainer!!.visibility = View.VISIBLE
                }

                // show stock options if Stock
                stockOptions.visibility = if (isStock) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- Stock Sub-category Spinner ---
        val subCats = listOf(
            "Cups and Lids","Paper Goods","Teas and lemonade","Smoothies", "Food & Snacks", "Cold Drinks",
            "Coffee Beans","Cleaning Supplies","Sauces and Syrups","Milks","Powders & Condiments"
        )
        spinnerSubCat.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, subCats
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // --- Two-state Switch for Ideal vs RunningLow ---
        switchAlertType.setOnCheckedChangeListener { _, runningLow ->
            if (runningLow) {
                // Running low → show toggle
                npThreshold.visibility  = View.GONE
                tbRunningLow.visibility = View.VISIBLE
            } else {
                // Ideal stock → show number‐picker
                npThreshold.visibility  = View.VISIBLE
                tbRunningLow.visibility = View.GONE
            }
        }
        // default = Ideal
        switchAlertType.isChecked = false
        npThreshold.visibility     = View.VISIBLE
        tbRunningLow.visibility    = View.GONE

        // --- NumberPicker setup ---
        npThreshold.minValue = 0
        npThreshold.maxValue = 100

        // --- Save template button ---
        btnSaveTemplate.setOnClickListener {
            val name = editTextName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val tmpl = ItemTemplate(
                name, editTextVariant.text.toString().trim(),
                spinnerStorage.selectedItem.toString()
            )
            saveTemplate(tmpl)
            savedTemplates = loadTemplates()
            refreshSpinner()
            Toast.makeText(this, "Template saved", Toast.LENGTH_SHORT).show()
        }

        // --- Manage templates button ---
        btnManageTemplates.setOnClickListener {
            val actions = arrayOf("Import Templates from Backup") +
                    if (savedTemplates.isNotEmpty())
                        arrayOf("Delete a Template", "Export Templates Backup")
                    else emptyArray()

            AlertDialog.Builder(this)
                .setTitle("Manage Templates")
                .setItems(actions) { _, which ->
                    when (actions[which]) {
                        "Delete a Template"          -> showDeleteDialog()
                        "Export Templates Backup"    -> exportBackup()
                        "Import Templates from Backup" -> openFilePickerForImport()
                    }
                }
                .show()
        }

        // --- Add item button ---
        btnAdd.setOnClickListener {
            val baseName       = editTextName.text.toString().trim()
            val variant        = editTextVariant.text.toString().trim()
            val storage        = spinnerStorage.selectedItem.toString()
            val expirationDate = if (datePickerContainer?.visibility == View.VISIBLE)
                datePicker!!.let {
                    Calendar.getInstance().apply {
                        set(it.year, it.month, it.dayOfMonth, 0, 0, 0)
                    }.timeInMillis
                }
            else 0L

            CoroutineScope(Dispatchers.IO).launch {
                val newName = getBatchName(baseName)
                val item = InventoryItem(
                    name             = newName,
                    variant          = variant,
                    expirationDate   = expirationDate,
                    quantity         = 6,
                    storageType      = storage,
                    stockSubCategory = spinnerSubCat.selectedItem as String,
                    stockAlertType   = if (switchAlertType.isChecked) "runningLow" else "ideal",
                    idealThreshold   = npThreshold.value,
                    isRunningLow     = tbRunningLow.isChecked
                )
                // insert locally
                inventoryDao.insert(item)
                // push to Firestore
                firestore.collection("inventoryItems").add(
                    mapOf(
                        "name"              to item.name,
                        "variant"           to item.variant,
                        "expirationDate"    to item.expirationDate,
                        "quantity"          to item.quantity,
                        "storageType"       to item.storageType,
                        "stockSubCategory"  to item.stockSubCategory,
                        "stockAlertType"    to item.stockAlertType,
                        "idealThreshold"    to item.idealThreshold,
                        "isRunningLow"      to item.isRunningLow
                    )
                )
                // back to UI
                runOnUiThread {
                    Toast.makeText(this@AddItemActivity, "Item added", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun refreshSpinner() {
        savedTemplates.sortBy { it.name }
        val opts = listOf("None") + savedTemplates.map { it.name }
        spinnerTemplates.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, opts
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun loadTemplates(): MutableList<ItemTemplate> {
        val prefs = getSharedPreferences(prefsTemplates, Context.MODE_PRIVATE)
        val set   = prefs.getStringSet(keyTemplates, emptySet()) ?: emptySet()
        return set.mapNotNull { ItemTemplate.fromStorageString(it) }
            .sortedBy { it.name }
            .toMutableList()
    }

    private fun saveTemplate(template: ItemTemplate) {
        val prefs = getSharedPreferences(prefsTemplates, Context.MODE_PRIVATE)
        val set   = prefs.getStringSet(keyTemplates, mutableSetOf())!!.toMutableSet()
        set.add(template.toStorageString())
        prefs.edit().putStringSet(keyTemplates, set).apply()
    }

    private fun showDeleteDialog() {
        val names = savedTemplates.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Delete a Template?")
            .setItems(names) { _, idx ->
                val toRemove = savedTemplates[idx].toStorageString()
                val prefs    = getSharedPreferences(prefsTemplates, Context.MODE_PRIVATE)
                val set      = prefs.getStringSet(keyTemplates, mutableSetOf())!!.toMutableSet()
                set.remove(toRemove)
                prefs.edit().putStringSet(keyTemplates, set).apply()
                savedTemplates = loadTemplates()
                refreshSpinner()
            }
            .show()
    }

    private fun openFilePickerForImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, importBackupRequestCode)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> { finish(); true }
            else              -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportBackup() {
        val file = File(getExternalFilesDir(null), "templates_backup.txt")
        try {
            BufferedWriter(FileWriter(file)).use { out ->
                savedTemplates.forEach { out.write(it.toStorageString() + "\n") }
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share Templates Backup"))
        } catch(e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode:Int, resultCode:Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == importBackupRequestCode && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val imported = mutableListOf<ItemTemplate>()
                    contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                        lines.forEach { line ->
                            ItemTemplate.fromStorageString(line.trim())
                                ?.let(imported::add)
                        }
                    }
                    if (imported.isEmpty()) {
                        Toast.makeText(this,"No valid templates",Toast.LENGTH_SHORT).show()
                    } else {
                        overwriteAllTemplates(imported)
                        savedTemplates = loadTemplates()
                        refreshSpinner()
                        Toast.makeText(this,"Imported ${imported.size}",Toast.LENGTH_SHORT).show()
                    }
                } catch(e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this,"Import failed",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun overwriteAllTemplates(newTemplates: List<ItemTemplate>) {
        val prefs = getSharedPreferences(prefsTemplates, Context.MODE_PRIVATE)
        val set   = newTemplates.map { it.toStorageString() }.toSet()
        prefs.edit().putStringSet(keyTemplates, set).apply()
    }

    private suspend fun getBatchName(baseName:String): String {
        val existing = inventoryDao.getItemsByName(baseName)
        return if (existing.isEmpty()) baseName
        else "$baseName Batch ${existing.size + 1}"
    }
}
