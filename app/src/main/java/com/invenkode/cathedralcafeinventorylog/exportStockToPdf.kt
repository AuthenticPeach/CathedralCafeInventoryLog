package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

suspend fun exportStockToPdf(context: Context, firestore: FirebaseFirestore): File? {
    val items = firestore.collection("inventoryItems")
        .whereEqualTo("storageType", "Stock")
        .get()
        .await()
        .mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val variant = doc.getString("variant") ?: ""
            val quantity = (doc.getLong("quantity") ?: 0L).toInt()
            val subCat = doc.getString("stockSubCategory") ?: ""
            val alertType = doc.getString("stockAlertType") ?: "ideal"
            val idealThreshold = (doc.getLong("idealThreshold") ?: 0L).toInt()
            val isRunningLow = doc.getBoolean("isRunningLow") ?: false

            val fullName = if (variant.isNotBlank()) "$name - $variant" else name
            val ideal = if (alertType == "ideal") "$idealThreshold" else "running low"
            val needsRestock = if (alertType == "ideal") quantity < idealThreshold else isRunningLow

            StockChecklistItem(subCat, fullName, ideal, needsRestock, quantity)
        }

    val grouped = items.groupBy { it.category }

    val pdf = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val lineHeight = 24f

    val paint = Paint().apply { color = Color.BLACK; textSize = 14f }
    val boldPaint = Paint(paint).apply { isFakeBoldText = true }
    val headerPaint = Paint(paint).apply { textSize = 18f; isFakeBoldText = true }

    val sdf = SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.getDefault())

    var pageNum = 1
    var y = 100f
    var currentPage: PdfDocument.Page? = null
    var canvas: Canvas? = null
    val iconBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.cathedralcafeicon)
    val iconSize = 40
    val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)

    var isFirstPage = true

    fun newPage(): Canvas {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create()
        currentPage = pdf.startPage(pageInfo)
        val canvas = currentPage!!.canvas

        if (isFirstPage) {
            canvas.drawBitmap(scaledIcon, margin, 20f, null)
        }

        val textOffset = if (isFirstPage) margin + iconSize + 10f else margin
        canvas.drawText("Cathedral Café – Stock Checklist", textOffset, 40f, headerPaint)
        canvas.drawText("Exported: ${sdf.format(Date())}", textOffset, 60f, paint)

        isFirstPage = false
        y = 100f
        return canvas
    }


    canvas = newPage()

    for ((category, entries) in grouped) {
        if (y > pageHeight - 120) {
            pdf.finishPage(currentPage!!)
            canvas = newPage()
        }

        canvas!!.drawText(category, margin, y, boldPaint)
        y += lineHeight

        // Header row
        canvas!!.drawText("Ideal stock", margin + 280f, y, boldPaint)
        canvas!!.drawText(if (category.equals("Milks", ignoreCase = true)) "Qty" else "Need ✓ / ✗", margin + 460f, y, boldPaint)
        y += lineHeight

        for (entry in entries) {
            if (y > pageHeight - 80) {
                pdf.finishPage(currentPage!!)
                canvas = newPage()
            }

            canvas!!.drawText(entry.name, margin, y, paint)
            canvas!!.drawText(entry.ideal, margin + 280f, y, paint)

            if (entry.category.equals("Milks", ignoreCase = true)) {
                canvas!!.drawText(entry.quantity.toString(), margin + 480f, y, paint)
            } else {
                canvas!!.drawText(if (entry.needsRestock) "✓" else "✗", margin + 480f, y, paint)
            }

            y += lineHeight
        }

        y += lineHeight / 2
    }

    pdf.finishPage(currentPage!!)

    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val fileName = "Stock-Checklist_$dateStr.pdf"
    val file = File(context.getExternalFilesDir(null), fileName)

    try {
        pdf.writeTo(FileOutputStream(file))
    } catch (e: IOException) {
        e.printStackTrace()
        pdf.close()
        return null
    }

    pdf.close()
    return file
}

data class StockChecklistItem(
    val category: String,
    val name: String,
    val ideal: String,
    val needsRestock: Boolean,
    val quantity: Int
)
