package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Suspended function that exports a report of InventoryItems as a CSV file.
 *
 * @param context The application context.
 * @param firestore The FirebaseFirestore instance.
 * @param reportType "Expiration" or "Inventory" to choose which type of report to export.
 * @return A File pointing to the exported CSV, or null if there was an error.
 */
suspend fun exportReportToCsv(context: Context, firestore: FirebaseFirestore, reportType: String): File? {
    // Fetch all items from Firestore.
    val querySnapshot = firestore.collection("inventoryItems").get().await()
    val items = querySnapshot.documents.mapNotNull { doc ->
        val name = doc.getString("name") ?: return@mapNotNull null
        val variant = doc.getString("variant") ?: ""
        val expirationDate = doc.getLong("expirationDate") ?: 0L
        val quantity = (doc.getLong("quantity") ?: 0L).toInt()
        val storageType = doc.getString("storageType") ?: ""
        InventoryItem(doc.id.hashCode(), name, variant, expirationDate, quantity, storageType)
    }

    val sb = StringBuilder()

    // Title and export date.
    val exportDateFormat = SimpleDateFormat("MM-dd-yyyy h:mm a", Locale.getDefault())
    val exportDateText = exportDateFormat.format(Date())
    sb.append("Cathedral Café - $reportType Report\n")
    sb.append("Exported on: $exportDateText\n\n")

    // Column headers.
    if (reportType == "Expiration") {
        sb.append("Item,Exp Date\n")
    } else {
        sb.append("Item,Quantity\n")
    }

    // Group items by storage type.
    val fridgeItems = items.filter { it.storageType.equals("Fridge", ignoreCase = true) }
    val displayItems = items.filter { it.storageType.equals("Display", ignoreCase = true) }
    val stockItems = items.filter { it.storageType.equals("Stock", ignoreCase = true) }

    // Helper function to add a section.
    fun addSection(title: String, groupItems: List<InventoryItem>) {
        if (groupItems.isNotEmpty()) {
            sb.append("\n$title\n")
            for (item in groupItems) {
                val itemName = if (item.variant.isNotBlank())
                    "${item.name} - ${item.variant}" else item.name
                val rightText = if (reportType == "Expiration") {
                    if (item.expirationDate > 0L)
                        SimpleDateFormat("MM-dd-yyyy", Locale.getDefault()).format(Date(item.expirationDate))
                    else
                        "---"
                } else {
                    item.quantity.toString()
                }
                // Wrap item name in quotes (in case it contains commas)
                sb.append("\"$itemName\",$rightText\n")
            }
        }
    }

    // Add sections in the desired order.
    addSection("Fridge Items", fridgeItems)
    addSection("Display Items", displayItems)
    addSection("Stock Items", stockItems)

    // Write the CSV content to a file.
    val fileName = "$reportType-Report.csv"
    val file = File(context.getExternalFilesDir(null), fileName)
    return try {
        file.writeText(sb.toString())
        file
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}