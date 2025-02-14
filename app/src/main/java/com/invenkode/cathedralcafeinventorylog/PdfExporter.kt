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
 * Exports a list of InventoryItem objects to a PDF file.
 * @param context The context.
 * @param items The list of items to export.
 * @param reportType "Expiration" for an expiration report or "Inventory" for an inventory report.
 * @return The File where the PDF was saved, or null if there was an error.
 */
fun exportReportToPdf(context: Context, items: List<InventoryItem>, reportType: String): File? {
    // Create a new PdfDocument.
    val pdfDocument = PdfDocument()

    // Define some paint objects.
    val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isFakeBoldText = true
    }
    val textPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 16f
    }

    // Set up page dimensions (A4 size in points; adjust as needed)
    val pageWidth = 595  // ~8.3 inches at 72 dpi
    val pageHeight = 842 // ~11.7 inches at 72 dpi
    val margin = 40

    // Create one page. For simplicity, this example only creates one page.
    // For a long list, you would need to split into multiple pages.
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas: Canvas = page.canvas

    // Draw the title.
    var yPos = margin.toFloat()
    canvas.drawText("$reportType Report", margin.toFloat(), yPos, titlePaint)
    yPos += 40f

    // Date formatter for expiration dates.
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Draw each item.
    for (item in items) {
        val line = if (reportType == "Expiration") {
            // Format the expiration date.
            "${item.name}: ${dateFormat.format(Date(item.expirationDate))}"
        } else {
            "${item.name}: Quantity ${item.quantity}"
        }
        canvas.drawText(line, margin.toFloat(), yPos, textPaint)
        yPos += 30f

        // If yPos reaches near the bottom of the page, you would need to finish the page and start a new one.
        if (yPos > pageHeight - margin) {
            // For simplicity, this example stops at one page.
            break
        }
    }

    pdfDocument.finishPage(page)

    // Save the document to a file.
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
