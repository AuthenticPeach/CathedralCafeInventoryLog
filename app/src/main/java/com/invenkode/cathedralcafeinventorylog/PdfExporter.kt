package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

enum class RowType { SECTION_HEADER, ITEM }

data class ReportRow(
    val type: RowType,
    val leftText: String,
    val rightText: String = "",
    val bgColor: Int? = null
)

/**
 * Exports a report of InventoryItems to a PDF file over multiple pages if needed.
 *
 * @param context The application context.
 * @param items The list of InventoryItem objects to export.
 * @param reportType "Expiration" or "Inventory" to choose which type of report to export.
 * @return A File pointing to the exported PDF, or null if there was an error.
 */
fun exportReportToPdf(context: Context, items: List<InventoryItem>, reportType: String): File? {
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
        color = Color.DKGRAY // Dark grey for divider lines.
        strokeWidth = 3f
    }
    val sectionHeaderBgPaint = Paint().apply {
        color = Color.DKGRAY
    }
    // Page settings.
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40
    val headerHeight = 70   // Reserved header area below title.
    val rowHeight = 30

    // Overall page background.
    val pageBackgroundColor = Color.parseColor("#FFFFFF")  // White.

    // Title and export date.
    val exportDateFormat = SimpleDateFormat("MM-dd-yyyy h:mm a", Locale.getDefault())
    val exportDateText = "Exported on: ${exportDateFormat.format(Date())}"
    val titleText = "Cathedral Café - $reportType Report"

    // Date format for expiration reports.
    val dateFormat = if (reportType == "Expiration") {
        SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())
    } else {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    // Group items by storage type.
    val fridgeItems = items.filter { it.storageType.equals("Fridge", ignoreCase = true) }
    val displayItems = items.filter { it.storageType.equals("Display", ignoreCase = true) }
    val stockItems = items.filter { it.storageType.equals("Stock", ignoreCase = true) }

    // For expiration reports, sort each group by expiration date.
    val sortedFridge = if (reportType == "Expiration") fridgeItems.sortedBy { it.expirationDate } else fridgeItems
    val sortedDisplay = if (reportType == "Expiration") displayItems.sortedBy { it.expirationDate } else displayItems
    val sortedStock = if (reportType == "Expiration") stockItems.sortedBy { it.expirationDate } else stockItems

    // Helper functions for background colors.
    fun getExpirationBgColor(expirationDate: Long, now: Long): Int {
        val oneWeek = 7 * 86_400_000L
        val twoWeeks = 14 * 86_400_000L
        return when {
            now >= expirationDate -> Color.parseColor("#FFCDD2")  // Light red.
            (expirationDate - now) < oneWeek -> Color.parseColor("#FFE0B2")  // Light orange.
            (expirationDate - now) < twoWeeks -> Color.parseColor("#FFF9C4") // Light yellow.
            else -> Color.parseColor("#C8E6C9")  // Light green.
        }
    }

    fun getInventoryBgColor(quantity: Int): Int {
        return when {
            quantity <= 2 -> Color.parseColor("#FFCDD2")  // Light red.
            quantity == 3 -> Color.parseColor("#FFF9C4")  // Light yellow.
            else -> Color.parseColor("#C8E6C9")            // Light green.
        }
    }

    // Build report rows.
    val reportRows = mutableListOf<ReportRow>()
    fun addSection(sectionTitle: String, items: List<InventoryItem>) {
        if (items.isNotEmpty()) {
            // Add section header.
            reportRows.add(ReportRow(RowType.SECTION_HEADER, sectionTitle))
            // Add each item row.
            for (item in items) {
                val left = if (item.variant.isNotBlank())
                    "${item.name} - ${item.variant}"
                else
                    item.name
                val right = if (reportType == "Expiration") {
                    if (item.expirationDate > 0L)
                        dateFormat.format(Date(item.expirationDate))
                    else
                        "---"
                } else {
                    "Qty: ${item.quantity}"
                }
                // Determine background color.
                val bg = when (reportType) {
                    "Expiration" -> if (item.expirationDate > 0L) getExpirationBgColor(item.expirationDate, System.currentTimeMillis()) else null
                    "Inventory" -> getInventoryBgColor(item.quantity)
                    else -> null
                }
                reportRows.add(ReportRow(RowType.ITEM, left, right, bg))
            }
        }
    }

    // Add sections in order.
    addSection("Fridge Items", sortedFridge)
    addSection("Display Items", sortedDisplay)
    addSection("Stock Items", sortedStock)

    // Define column positions.
    val leftColumnX = margin.toFloat()
    val rightColumnX = margin.toFloat() + 300f

    // Load the icon bitmap.
    val iconBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.cathedralcafeicon)
    val iconSize = 40
    val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)

    // Paginate the report rows.
    var currentPageNumber = 1
    var currentRowIndex = 0

    while (currentRowIndex < reportRows.size) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Fill page background.
        canvas.drawColor(pageBackgroundColor)

        var yPos = margin.toFloat()
        // Draw header.
        canvas.drawBitmap(scaledIcon, margin.toFloat(), yPos - scaledIcon.height / 2, null)
        val titleOffsetX = margin.toFloat() + iconSize + 10f
        // Shift title text to start at titleOffsetX.
        canvas.drawText(titleText, titleOffsetX, yPos, titlePaint)
        yPos += 30f
        canvas.drawText(exportDateText, titleOffsetX, yPos, textPaint)
        yPos += (headerHeight - 30)

        // Draw column headers.
        canvas.drawText("Item", leftColumnX, yPos, textPaint)
        canvas.drawText(if (reportType == "Expiration") "Exp Date" else "Quantity", rightColumnX, yPos, textPaint)
        // Draw a horizontal line immediately under the header.
        canvas.drawLine(margin.toFloat(), yPos + 5f, (pageWidth - margin).toFloat(), yPos + 5f, linePaint)
        yPos += rowHeight

        // Iterate over report rows until page is full.
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
                    // Draw background color for right column if provided.
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
    return file
}
