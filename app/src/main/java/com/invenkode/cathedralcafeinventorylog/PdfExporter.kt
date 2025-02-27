package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Define paint objects for title and body text.
    val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isFakeBoldText = true
    }
    val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 16f
    }

    // Page settings (A4 dimensions in points; adjust as needed)
    val pageWidth = 595  // approx. 8.3 inches at 72 dpi.
    val pageHeight = 842 // approx. 11.7 inches at 72 dpi.
    val margin = 40
    val headerHeight = 70  // space reserved for title and export date on each page
    val lineSpacing = 30

    // Date formats.
    val exportDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val exportDateText = "Exported on: " + exportDateFormat.format(Date())
    val titleText = "$reportType Report"

    // For expiration report, prepare a date formatter.
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // --- NEW: Sort items if report type is Expiration ---
    val sortedItems = if (reportType == "Expiration") {
        items.sortedBy { it.expirationDate }
    } else {
        items
    }

    var currentPageNumber = 1
    var currentItemIndex = 0

    // Loop through items and create pages.
    while (currentItemIndex < sortedItems.size) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Draw the header (title and export date)
        var yPos = margin.toFloat()
        canvas.drawText(titleText, margin.toFloat(), yPos, titlePaint)
        yPos += 30f
        canvas.drawText(exportDateText, margin.toFloat(), yPos, textPaint)
        yPos += (headerHeight - 30)

        // Fill the page with as many items as possible.
        while (currentItemIndex < sortedItems.size && yPos + lineSpacing <= pageHeight - margin) {
            val item = sortedItems[currentItemIndex]
            val line = if (reportType == "Expiration") {
                "${item.name}: ${dateFormat.format(Date(item.expirationDate))}"
            } else {
                "${item.name}: Quantity ${item.quantity}"
            }
            canvas.drawText(line, margin.toFloat(), yPos, textPaint)
            yPos += lineSpacing
            currentItemIndex++
        }

        pdfDocument.finishPage(page)
        currentPageNumber++
    }

    // Save the PDF document to a file.
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
