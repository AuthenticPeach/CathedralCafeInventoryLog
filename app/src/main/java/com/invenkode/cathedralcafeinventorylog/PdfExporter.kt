package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.invenkode.cathedralcafeinventorylog.R
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// Row types for our report.
enum class RowType { SECTION_HEADER, ITEM }

// Data class representing one row in the PDF report.
data class ReportRow(
    val type: RowType,
    val leftText: String,
    val rightText: String = "",
    val bgColor: Int? = null
)

/**
 * Suspended function that fetches all InventoryItems from Firestore and exports a PDF report.
 *
 * @param context The application context.
 * @param firestore The FirebaseFirestore instance.
 * @param reportType "Expiration" or "Inventory" determines the format.
 * @return A File pointing to the exported PDF, or null if there was an error.
 */
suspend fun exportReportToPdf(context: Context, firestore: FirebaseFirestore, reportType: String): File? {
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
    Log.d("PdfExporter", "Exporting $reportType report for ${items.size} items (fetched from Firestore)")

    val pdfDocument = PdfDocument()

    // Define paints.
    val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isFakeBoldText = true
    }
    val headerTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        isFakeBoldText = true
    }
    val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 16f
    }
    val linePaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 3f
    }
    val sectionHeaderBgPaint = Paint().apply {
        color = Color.DKGRAY
    }

    // Page settings.
    val pageWidth = 595   // A4 width at 72 dpi
    val pageHeight = 842  // A4 height at 72 dpi
    val margin = 40
    val headerHeight = 70
    val rowHeight = 30

    // Overall page background.
    val pageBackgroundColor = Color.parseColor("#FFFFFF")

    // Title and export date.
    val exportDateFormat = SimpleDateFormat("MM-dd-yyyy h:mm a", Locale.getDefault())
    val exportDateText = "Exported on: ${exportDateFormat.format(Date())}"
    val titleText = "Cathedral Café - $reportType Report"

    // Date format for expiration reports.
    val dateFormat = if (reportType == "Expiration")
        SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())
    else
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Group items by storage type.
    val fridgeItems = items.filter { it.storageType.equals("Fridge", ignoreCase = true) }
    val displayItems = items.filter { it.storageType.equals("Display", ignoreCase = true) }
    val stockItems = items.filter { it.storageType.equals("Stock", ignoreCase = true) }

    // Sort each group by expiration date if needed.
    val sortedFridge = if (reportType == "Expiration") fridgeItems.sortedBy { it.expirationDate } else fridgeItems
    val sortedDisplay = if (reportType == "Expiration") displayItems.sortedBy { it.expirationDate } else displayItems
    val sortedStock = if (reportType == "Expiration") stockItems.sortedBy { it.expirationDate } else stockItems

    // Helper functions for background colors.
    fun getExpirationBgColor(expirationDate: Long, now: Long): Int {
        val oneWeek = 7 * 86_400_000L
        val twoWeeks = 14 * 86_400_000L
        return when {
            now >= expirationDate -> Color.parseColor("#FFCDD2")
            (expirationDate - now) < oneWeek -> Color.parseColor("#FFE0B2")
            (expirationDate - now) < twoWeeks -> Color.parseColor("#FFF9C4")
            else -> Color.parseColor("#C8E6C9")
        }
    }

    fun getInventoryBgColor(quantity: Int): Int {
        return when {
            quantity <= 2 -> Color.parseColor("#FFCDD2")
            quantity == 3 -> Color.parseColor("#FFF9C4")
            else -> Color.parseColor("#C8E6C9")
        }
    }

    // Build report rows.
    val reportRows = mutableListOf<ReportRow>()
    fun addSection(sectionTitle: String, groupItems: List<InventoryItem>) {
        if (groupItems.isNotEmpty()) {
            reportRows.add(ReportRow(RowType.SECTION_HEADER, sectionTitle))
            for (item in groupItems) {
                val left = if (item.variant.isNotBlank())
                    "${item.name} - ${item.variant}" else item.name
                val right = if (reportType == "Expiration") {
                    if (item.expirationDate > 0L)
                        dateFormat.format(Date(item.expirationDate))
                    else
                        "---"
                } else {
                    "Qty: ${item.quantity}"
                }
                val bg = when (reportType) {
                    "Expiration" -> if (item.expirationDate > 0L)
                        getExpirationBgColor(item.expirationDate, System.currentTimeMillis())
                    else null
                    "Inventory" -> getInventoryBgColor(item.quantity)
                    else -> null
                }
                reportRows.add(ReportRow(RowType.ITEM, left, right, bg))
            }
        }
    }

    // Add sections.
    addSection("Fridge Items", sortedFridge)
    addSection("Display Items", sortedDisplay)
    addSection("Stock Items", sortedStock)

    // Define column positions.
    val leftColumnX = margin.toFloat()
    val rightColumnX = margin.toFloat() + 400f

    // Load the icon bitmap.
    val iconBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.cathedralcafeicon)
    val iconSize = 40
    val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)

    // Pagination: iterate through reportRows and draw on pages.
    var currentPageNumber = 1
    var currentRowIndex = 0

    while (currentRowIndex < reportRows.size) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Fill page background.
        canvas.drawColor(pageBackgroundColor)

        var yPos = margin.toFloat()
        // Draw header (icon, title, and export date).
        canvas.drawBitmap(scaledIcon, margin.toFloat(), yPos - scaledIcon.height / 2, null)
        val titleOffsetX = margin.toFloat() + iconSize + 10f
        canvas.drawText(titleText, titleOffsetX, yPos, titlePaint)
        yPos += 30f
        canvas.drawText(exportDateText, titleOffsetX, yPos, textPaint)
        yPos += (headerHeight - 30)

        // Draw column headers.
        canvas.drawText("Item", leftColumnX, yPos, textPaint)
        canvas.drawText(if (reportType == "Expiration") "Exp Date" else "Quantity", rightColumnX, yPos, textPaint)
        // Draw horizontal divider.
        canvas.drawLine(margin.toFloat(), yPos + 5f, (pageWidth - margin).toFloat(), yPos + 5f, linePaint)
        yPos += rowHeight

        // Draw report rows.
        while (currentRowIndex < reportRows.size && yPos + rowHeight <= pageHeight - margin) {
            val row = reportRows[currentRowIndex]
            when (row.type) {
                RowType.SECTION_HEADER -> {
                    val fm = textPaint.fontMetrics
                    canvas.drawRect(
                        margin.toFloat(),
                        yPos + fm.ascent,
                        (pageWidth - margin).toFloat(),
                        yPos + fm.descent,
                        sectionHeaderBgPaint
                    )
                    canvas.drawText(row.leftText, leftColumnX + 10f, yPos, headerTextPaint)
                }
                RowType.ITEM -> {
                    row.bgColor?.let { color ->
                        val bgPaint = Paint().apply { this.color = color }
                        val fm = textPaint.fontMetrics
                        canvas.drawRect(
                            rightColumnX,
                            yPos + fm.ascent,
                            (pageWidth - margin).toFloat(),
                            yPos + fm.descent,
                            bgPaint
                        )
                    }
                    canvas.drawText(row.leftText, leftColumnX, yPos, textPaint)
                    canvas.drawText(row.rightText, rightColumnX, yPos, textPaint)
                }
            }
            yPos += rowHeight
            currentRowIndex++
        }
        pdfDocument.finishPage(page)
        currentPageNumber++
    }

    val fileName = "$reportType-Report.pdf"
    val file = File(context.getExternalFilesDir(null), fileName)
    try {
        pdfDocument.writeTo(FileOutputStream(file))
    } catch (e: IOException) {
        e.printStackTrace()
        pdfDocument.close()
        return null
    }
    pdfDocument.close()
    Log.d("PdfExporter", "PDF exported successfully: ${file.absolutePath}")
    return file
}