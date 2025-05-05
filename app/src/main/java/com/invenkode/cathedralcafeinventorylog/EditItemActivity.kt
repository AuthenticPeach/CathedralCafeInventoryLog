package com.invenkode.cathedralcafeinventorylog

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
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

    // UI
    private lateinit var editTextName: EditText
    private lateinit var editTextVariant: EditText
    private lateinit var datePicker: DatePicker
    private lateinit var datePickerContainer: LinearLayout
    private lateinit var spinnerItemType: Spinner
    private lateinit var btnUpdate: Button

    // Stock UI
    private lateinit var stockOptions: LinearLayout
    private lateinit var spinnerSubCat: Spinner
    private lateinit var spinnerAlert: Spinner
    private lateinit var npThreshold: NumberPicker
    private lateinit var tbRunningLow: ToggleButton

    // Intent‐extra keys
    companion object {
        private const val EXTRA_STOCK_SUBCAT = "stockSubCategory"
        private const val EXTRA_STOCK_ALERT  = "stockAlertType"
        private const val EXTRA_IDEAL_THRESH = "idealThreshold"
        private const val EXTRA_RUNNING_LOW = "isRunningLow"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_item)

        // wire up
        inventoryDao = InventoryDatabase.getDatabase(this).inventoryDao()
        firestore   = FirebaseFirestore.getInstance()

        editTextName        = findViewById(R.id.editTextName)
        editTextVariant     = findViewById(R.id.editTextVariant)
        datePicker          = findViewById(R.id.datePicker)
        datePickerContainer = findViewById(R.id.datePickerContainer)
        spinnerItemType     = findViewById(R.id.spinnerItemType)
        btnUpdate           = findViewById(R.id.btnUpdate)

        stockOptions    = findViewById(R.id.stockOptionsContainer)
        spinnerSubCat   = findViewById(R.id.spinnerStockSubCategory)
        spinnerAlert    = findViewById(R.id.spinnerStockAlertType)
        npThreshold     = findViewById(R.id.numberPickerIdealThreshold)
        tbRunningLow    = findViewById(R.id.toggleRunningLow)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 1) fill Type spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.storage_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerItemType.adapter = adapter
        }

        // 2) pull intent extras
        val itemId           = intent.getIntExtra("itemId", 0)
        val currentName      = intent.getStringExtra("name").orEmpty()
        val currentVariant   = intent.getStringExtra("variant").orEmpty()
        val currentExpiration= intent.getLongExtra("expirationDate", 0L)
        val currentQuantity  = intent.getIntExtra("quantity", 6)
        val currentStorage   = intent.getStringExtra("storageType").orEmpty()

        val currentSubCat      = intent.getStringExtra(EXTRA_STOCK_SUBCAT).orEmpty()
        val currentAlertType   = intent.getStringExtra(EXTRA_STOCK_ALERT).orEmpty()
        val currentThreshold   = intent.getIntExtra(EXTRA_IDEAL_THRESH, 0)
        val currentIsRunningLow= intent.getBooleanExtra(EXTRA_RUNNING_LOW, false)

        // 3) set up Stock‐alert spinner
        val alertTypes = listOf("Ideal Stock", "Running low")
        spinnerAlert.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, alertTypes
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerAlert.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                npThreshold.visibility   = if (pos == 0) View.VISIBLE else View.GONE
                tbRunningLow.visibility  = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }

        npThreshold.minValue = 0
        npThreshold.maxValue = 100

        // 4) set up SubCat spinner
        val subCats = listOf(
            "Cups and Lids","Paper Goods","Teas and lemonade","Smoothies", "Food & Snacks", "Cold Drinks",
            "Coffee Beans","Cleaning Supplies","Sauces and Syrups","Milks","Powders & Condiments"
        )
        spinnerSubCat.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, subCats
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 5) now prefill everything:

        // name & variant
        editTextName.text.clear();      editTextName.setText(currentName)
        editTextVariant.text.clear();   editTextVariant.setText(currentVariant)

        // storage‐type
        subCats.indexOf(currentSubCat).takeIf { it >= 0 }?.let {
            spinnerSubCat.setSelection(it)
        }
        alertTypes.indexOfFirst { it.startsWith(currentAlertType, true) }
            .takeIf { it >= 0 }?.let { spinnerAlert.setSelection(it) }

        // threshold & toggle state
        npThreshold.value    = currentThreshold
        tbRunningLow.isChecked = currentIsRunningLow

        // date & stock options visibility
        spinnerItemType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val isStock = spinnerItemType.selectedItem.toString().equals("Stock", true)
                datePickerContainer.visibility = if (isStock) View.GONE else View.VISIBLE
                stockOptions.visibility        = if (isStock) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // set type spinner to current, to fire that listener:
        for (i in 0 until spinnerItemType.adapter.count) {
            if (spinnerItemType.adapter.getItem(i).toString().equals(currentStorage, true)) {
                spinnerItemType.setSelection(i)
                break
            }
        }

        // initialize date‐picker if non‐stock
        if (!currentStorage.equals("Stock", true) && currentExpiration > 0L) {
            Calendar.getInstance().apply {
                timeInMillis = currentExpiration
                datePicker.updateDate(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
            }
        }

        // 6) save updates
        btnUpdate.setOnClickListener {
            val updatedName  = editTextName.text.toString().trim()
            val updatedVar   = editTextVariant.text.toString().trim()
            val updatedStore = spinnerItemType.selectedItem.toString()
            val updatedExp   = if (datePickerContainer.visibility == View.VISIBLE) {
                Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 0, 0, 0)
                }.timeInMillis
            } else 0L

            val updatedItem = InventoryItem(
                id               = itemId,
                name             = updatedName,
                variant          = updatedVar,
                expirationDate   = updatedExp,
                quantity         = currentQuantity,
                storageType      = updatedStore,
                stockSubCategory = spinnerSubCat.selectedItem as String,
                stockAlertType   = if (spinnerAlert.selectedItem=="Ideal Stock") "ideal" else "runningLow",
                idealThreshold   = npThreshold.value,
                isRunningLow     = tbRunningLow.isChecked
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    inventoryDao.update(updatedItem)
                    firestore.collection("inventoryItems")
                        .whereEqualTo("name", currentName)
                        .whereEqualTo("variant", currentVariant)
                        .whereEqualTo("expirationDate", currentExpiration)
                        .whereEqualTo("quantity", currentQuantity)
                        .whereEqualTo("storageType", currentStorage)
                        .get().await()
                        .documents
                        .forEach { doc ->
                            doc.reference.update(
                                mapOf(
                                    "name"             to updatedItem.name,
                                    "variant"          to updatedItem.variant,
                                    "expirationDate"   to updatedItem.expirationDate,
                                    "quantity"         to updatedItem.quantity,
                                    "storageType"      to updatedItem.storageType,
                                    "stockSubCategory" to updatedItem.stockSubCategory,
                                    "stockAlertType"   to updatedItem.stockAlertType,
                                    "idealThreshold"   to updatedItem.idealThreshold,
                                    "isRunningLow"     to updatedItem.isRunningLow
                                )
                            ).await()
                        }

                    withContext(Dispatchers.Main) { finish() }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditItemActivity, "Error updating item", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem)= when(item.itemId) {
        android.R.id.home -> { finish(); true }
        else              -> super.onOptionsItemSelected(item)
    }
}
