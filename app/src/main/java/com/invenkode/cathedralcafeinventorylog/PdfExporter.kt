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
    val querySnapshot = firestore.collection("inventoryItems").get().await()
    val items = querySnapshot.documents.mapNotNull { doc ->
        val name = doc.getString("name") ?: return@mapNotNull null
        val variant = doc.getString("variant") ?: ""
        val expirationRaw = doc.get("expirationDate")
        val expirationDate = when (expirationRaw) {
            is Number -> expirationRaw.toLong()
            is String -> expirationRaw.toLongOrNull() ?: 0L
            else -> 0L
        }
        val quantity = ((doc.get("quantity") as? Number)?.toInt()) ?: 0
        val storageType = doc.getString("storageType") ?: ""
        InventoryItem(doc.id.hashCode(), name, variant, expirationDate, quantity, storageType)
    }


    val pdfDocument = PdfDocument()

    val titlePaint = Paint().apply { color = Color.BLACK; textSize = 24f; isFakeBoldText = true }
    val headerTextPaint = Paint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true }
    val textPaint = Paint().apply { color = Color.DKGRAY; textSize = 16f }
    val yellowTextPaint = Paint(textPaint).apply {
        color = Color.parseColor("#FBC02D")  // Yellow
        isFakeBoldText = true                // Make it bold
    }

    val linePaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 3f }
    val sectionHeaderBgPaint = Paint().apply { color = Color.DKGRAY }

    val pageWidth = 595
    val pageHeight = 842
    val margin = 40
    val headerHeight = 70
    val rowHeight = 30
    val pageBackgroundColor = Color.WHITE

    val exportDateText = "Exported on: ${SimpleDateFormat("MM-dd-yyyy h:mm a", Locale.getDefault()).format(Date())}"
    val titleText = "Cathedral Café - $reportType Report"

    val dateFormat = if (reportType == "Expiration")
        SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())
    else
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val fridgeItems = items.filter { it.storageType.equals("Fridge", true) }
    val displayItems = items.filter { it.storageType.equals("Display", true) }
    val stockItems = items.filter { it.storageType.equals("Stock", true) }

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

    val reportRows = mutableListOf<ReportRow>()
    fun addSection(title: String, group: List<InventoryItem>) {
        if (group.isNotEmpty()) {
            reportRows.add(ReportRow(RowType.SECTION_HEADER, title))
            val sortedGroup = if (reportType == "Expiration") {
                group.sortedBy { it.expirationDate }
            } else {
                group.sortedWith(compareBy({ it.name }, { it.variant }))
            }

            for (item in sortedGroup) {
                val left = if (item.variant.isNotBlank()) "${item.name} - ${item.variant}" else item.name
                val right = when (reportType) {
                    "Expiration" -> if (item.expirationDate > 0L) dateFormat.format(Date(item.expirationDate)) else "---"
                    "Inventory" -> if (item.quantity <= 2) "✓" else "Qty: ${item.quantity}"
                    else -> "Qty: ${item.quantity}"
                }
                val bgColor = when (reportType) {
                    "Expiration" -> if (item.expirationDate > 0L) getExpirationBgColor(item.expirationDate, System.currentTimeMillis()) else null
                    "Inventory" -> getInventoryBgColor(item.quantity)
                    else -> null
                }
                reportRows.add(ReportRow(RowType.ITEM, left, right, bgColor))
            }
        }
    }

    addSection("Fridge Items", fridgeItems)
    addSection("Display Items", displayItems)
    addSection("Stock Items", stockItems)

    val leftColumnX = margin.toFloat()
    val rightColumnX = margin + 400f

    val iconBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.cathedralcafeicon)
    val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, 40, 40, true)

    var currentPageNumber = 1
    var currentRowIndex = 0

    while (currentRowIndex < reportRows.size) {
        val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber++).create())
        val canvas = page.canvas

        canvas.drawColor(pageBackgroundColor)
        var y = margin.toFloat()

        canvas.drawBitmap(scaledIcon, margin.toFloat(), y - 10f, null)
        canvas.drawText(titleText, margin + 50f, y, titlePaint)
        y += 30f
        canvas.drawText(exportDateText, margin + 50f, y, textPaint)
        y += headerHeight - 30

        canvas.drawText("Item", leftColumnX, y, textPaint)
        canvas.drawText(if (reportType == "Expiration") "Exp Date" else "Qty / ✓", rightColumnX, y, textPaint)
        canvas.drawLine(margin.toFloat(), y + 5f, (pageWidth - margin).toFloat(), y + 5f, linePaint)
        y += rowHeight

        while (currentRowIndex < reportRows.size && y + rowHeight <= pageHeight - margin) {
            val row = reportRows[currentRowIndex]
            when (row.type) {
                RowType.SECTION_HEADER -> {
                    val fm = textPaint.fontMetrics
                    canvas.drawRect(
                        margin.toFloat(),
                        y + fm.ascent,
                        (pageWidth - margin).toFloat(),
                        y + fm.descent,
                        sectionHeaderBgPaint
                    )
                    canvas.drawText(row.leftText, leftColumnX + 10f, y, headerTextPaint)
                }
                RowType.ITEM -> {
                    row.bgColor?.let { color ->
                        val paint = Paint().apply { this.color = color }
                        val fm = textPaint.fontMetrics
                        canvas.drawRect(
                            rightColumnX,
                            y + fm.ascent,
                            (pageWidth - margin).toFloat(),
                            y + fm.descent,
                            paint
                        )
                    }
                    canvas.drawText(row.leftText, leftColumnX, y, textPaint)

                    val rightTextTrimmed = row.rightText.trim()
                    val isCheckmark = rightTextTrimmed == "✓"
                    val paintToUse = if (isCheckmark) yellowTextPaint else textPaint

                    canvas.drawText(row.rightText, rightColumnX, y, paintToUse)
                }
            }
            y += rowHeight
            currentRowIndex++
        }

        pdfDocument.finishPage(page)
    }

    val fileName = "$reportType-Report.pdf"
    val file = File(context.getExternalFilesDir(null), fileName)
    return try {
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        file
    } catch (e: IOException) {
        e.printStackTrace()
        pdfDocument.close()
        null
    }
}

