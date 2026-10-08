package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.DailyClosing
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    data class UriResult(val success: Boolean, val pathMessage: String)

    /**
     * Helper to download cached PDF inside the user's public Downloads directory.
     */
    fun savePdfToDownloads(context: Context, cachedFile: File, userFriendlyName: String): UriResult {
        try {
            val finalFileName = "${userFriendlyName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BLS_Reports")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            FileInputStream(cachedFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    return UriResult(true, "Saved to Downloads/BLS_Reports/$finalFileName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val blsDir = File(downloadsDir, "BLS_Reports")
                if (!blsDir.exists()) {
                    blsDir.mkdirs()
                }
                val destinationFile = File(blsDir, finalFileName)
                FileInputStream(cachedFile).use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return UriResult(true, "Saved to Downloads/BLS_Reports/$finalFileName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return UriResult(false, "Failed to copy file to Downloads directory")
    }

    fun exportBackupFile(context: Context, jsonString: String): File? {
        return try {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(context.cacheDir, "BLS_Backup_$dateStr.json")
            file.writeText(jsonString)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareBackupFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share BLS Database Backup"))
    }

    fun saveBackupToDownloads(context: Context, cachedFile: File): UriResult {
        try {
            val finalFileName = cachedFile.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BLS_Backups")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            FileInputStream(cachedFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    return UriResult(true, "Saved to Downloads/BLS_Backups/$finalFileName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val blsDir = File(downloadsDir, "BLS_Backups")
                if (!blsDir.exists()) blsDir.mkdirs()
                val destFile = File(blsDir, finalFileName)
                FileInputStream(cachedFile).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return UriResult(true, "Saved to Downloads/BLS_Backups/$finalFileName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return UriResult(false, "Failed to save backup to Downloads")
    }

    private var cachedLogoBitmap: Bitmap? = null

    /**
     * Holds the currently logged-in operator name & role for watermark & signature tracking.
     */
    var activeOperatorName: String = ""
    var activeOperatorRole: String = ""

    fun setActiveOperator(name: String, role: String = "") {
        activeOperatorName = name.trim()
        activeOperatorRole = role.trim()
    }

    fun getReceivedByLabel(customFallback: String = "Accounts Officer"): String {
        return if (activeOperatorName.isNotBlank()) {
            if (activeOperatorRole.isNotBlank() && !activeOperatorName.equals(activeOperatorRole, ignoreCase = true)) {
                "$activeOperatorName ($activeOperatorRole)"
            } else {
                activeOperatorName
            }
        } else {
            customFallback
        }
    }

    private fun getCachedLogo(context: Context): Bitmap? {
        if (cachedLogoBitmap == null || cachedLogoBitmap?.isRecycled == true) {
            try {
                val raw = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
                if (raw != null) {
                    cachedLogoBitmap = Bitmap.createScaledBitmap(raw, 64, 64, true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return cachedLogoBitmap
    }

    private fun drawCommonHeader(context: Context, canvas: Canvas, textPaint: Paint, headerPaint: Paint, reportTitle: String) {
        canvas.drawRect(0f, 0f, 595f, 90f, headerPaint)

        var textStartX = 30f
        val logo = getCachedLogo(context)
        if (logo != null) {
            canvas.drawBitmap(logo, 25f, 13f, null)
            textStartX = 100f
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 17f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", textStartX, 38f, textPaint)

        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Academic Financial & Ledger Center", textStartX, 58f, textPaint)

        textPaint.textSize = 10.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText(reportTitle, textStartX, 75f, textPaint)
    }

    private fun drawCommonFooter(canvas: Canvas, textPaint: Paint, pageNum: Int = 1, totalPages: Int = 1) {
        textPaint.color = Color.GRAY
        textPaint.textSize = 8f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val operatorTag = if (activeOperatorName.isNotBlank()) " | Generated By: ${getReceivedByLabel()}" else ""
        val footerText = if (totalPages > 1) {
            "Page $pageNum of $totalPages$operatorTag | BLS Accounting Records. Confidential."
        } else {
            "Page 1$operatorTag | BLS Accounting Records. Confidential."
        }
        canvas.drawText(footerText, 50f, 820f, textPaint)
    }

    /**
     * 1. Export General Ledger Transactions with Multi-Page Dynamic Pagination
     */
    fun exportTransactionsToPdf(
        context: Context,
        title: String,
        transactions: List<Transaction>,
        totalIncome: Double,
        totalExpense: Double,
        studentsMap: Map<Int, Student> = emptyMap()
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 0.5f
        }
        val txSdf = SimpleDateFormat("dd-MM-yy", Locale.getDefault())

        // Calculate pages dynamically
        val rowsOnPage1 = 20
        val rowsOnNextPages = 26
        val totalRows = transactions.size
        val totalPages = if (totalRows <= rowsOnPage1) 1 else {
            1 + Math.ceil((totalRows - rowsOnPage1).toDouble() / rowsOnNextPages).toInt()
        }

        var currentItemIndex = 0

        for (pageIndex in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            drawCommonHeader(context, canvas, textPaint, headerPaint, "Finance Report - $title")

            var yPos: Float

            if (pageIndex == 1) {
                // Metadata block
                textPaint.color = Color.DKGRAY
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                canvas.drawText("Generated: ${sdf.format(Date())}", 30f, 110f, textPaint)
                val ledgerIssuedBy = if (activeOperatorName.isNotBlank()) "Issued By: ${getReceivedByLabel()}" else "Issued By: Central Accounts Desk"
                canvas.drawText(ledgerIssuedBy, 30f, 122f, textPaint)

                // Summary box
                val summaryPaint = Paint().apply {
                    color = Color.parseColor("#E8F5E9")
                    isAntiAlias = true
                }
                canvas.drawRect(30f, 135f, 565f, 190f, summaryPaint)

                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.textSize = 10f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("GENERAL TRANSACTION CASHFLOW SUMMARY", 45f, 153f, textPaint)

                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Income: Rs. ${String.format(Locale.US, "%,.1f", totalIncome)}", 45f, 175f, textPaint)
                canvas.drawText("Expense: Rs. ${String.format(Locale.US, "%,.1f", totalExpense)}", 230f, 175f, textPaint)
                
                val net = totalIncome - totalExpense
                textPaint.color = if (net >= 0) Color.parseColor("#2E7D32") else Color.RED
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Net Balance: Rs. ${String.format(Locale.US, "%,.1f", net)}", 410f, 175f, textPaint)

                // Table Header
                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                canvas.drawRect(30f, 205f, 565f, 225f, headerPaint)
                canvas.drawText("DATE", 35f, 218f, textPaint)
                canvas.drawText("PARTICULARS / STUDENT REFERENCE", 105f, 218f, textPaint)
                canvas.drawText("MODE", 350f, 218f, textPaint)
                canvas.drawText("BY", 420f, 218f, textPaint)
                canvas.drawText("AMOUNT", 495f, 218f, textPaint)

                yPos = 245f
            } else {
                // Secondary Page Table Header
                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                canvas.drawRect(30f, 105f, 565f, 125f, headerPaint)
                canvas.drawText("DATE", 35f, 118f, textPaint)
                canvas.drawText("PARTICULARS / STUDENT REFERENCE", 105f, 118f, textPaint)
                canvas.drawText("MODE", 350f, 118f, textPaint)
                canvas.drawText("BY", 420f, 118f, textPaint)
                canvas.drawText("AMOUNT", 495f, 118f, textPaint)

                yPos = 145f
            }

            val maxRowsThisPage = if (pageIndex == 1) rowsOnPage1 else rowsOnNextPages
            var rowsDrawn = 0

            textPaint.color = Color.BLACK
            textPaint.textSize = 9f

            while (currentItemIndex < totalRows && rowsDrawn < maxRowsThisPage) {
                val tx = transactions[currentItemIndex]
                val dateStr = txSdf.format(Date(tx.date))
                val studentInfo = (tx.studentId?.let { studentsMap[it] })
                    ?: (tx.studentName?.let { name -> studentsMap.values.firstOrNull { it.studentName.equals(name, ignoreCase = true) } })
                val studentDisplayName = tx.studentName ?: studentInfo?.studentName ?: "General"
                val studentClass = studentInfo?.className?.takeIf { it.isNotBlank() }
                val details = if (tx.isIncome) {
                    if (studentClass != null) {
                        "${tx.category} [$studentDisplayName ($studentClass)]"
                    } else {
                        "${tx.category} [$studentDisplayName]"
                    }
                } else {
                    "${tx.category} [${tx.description.take(15)}]"
                }

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(dateStr, 35f, yPos, textPaint)
                val clippedDetails = if (details.length > 44) details.take(41) + "..." else details
                canvas.drawText(clippedDetails, 105f, yPos, textPaint)
                canvas.drawText(tx.paymentMode, 350f, yPos, textPaint)
                canvas.drawText(tx.recordedBy, 420f, yPos, textPaint)

                val amtText = if (tx.isIncome) "+Rs.${tx.amount.toInt()}" else "-Rs.${tx.amount.toInt()}"
                val amtPaint = Paint(textPaint).apply {
                    color = if (tx.isIncome) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(amtText, 555f, yPos, amtPaint)

                canvas.drawLine(30f, yPos + 6f, 565f, yPos + 6f, linePaint)
                yPos += 24f
                currentItemIndex++
                rowsDrawn++
            }

            drawCommonFooter(canvas, textPaint, pageIndex, totalPages)
            pdfDocument.finishPage(page)
        }

        val fileName = "BLS_General_Ledger_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * 2. Export Monthly Income & Expense Category Summary PDF
     */
    fun exportMonthlySummaryToPdf(
        context: Context,
        transactions: List<Transaction>,
        totalIncome: Double,
        totalExpense: Double,
        monthYearString: String
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }

        drawCommonHeader(context, canvas, textPaint, headerPaint, "Monthly Income & Expense Summary: $monthYearString")

        // Metadata block
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Billing Period: $monthYearString", 30f, 110f, textPaint)
        canvas.drawText("Generated: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}", 30f, 122f, textPaint)

        // Summary Cards Section
        val blockPaint = Paint().apply { isAntiAlias = true }
        
        // Total Incomes
        blockPaint.color = Color.parseColor("#E8F5E9")
        canvas.drawRect(30f, 145f, 200f, 210f, blockPaint)
        textPaint.color = Color.parseColor("#2E7D32")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 9f
        canvas.drawText("TOTAL INCOMES", 40f, 165f, textPaint)
        textPaint.textSize = 14f
        canvas.drawText("Rs. ${totalIncome.toInt()}", 40f, 195f, textPaint)

        // Total Expenses
        blockPaint.color = Color.parseColor("#FFEBEE")
        canvas.drawRect(212f, 145f, 382f, 210f, blockPaint)
        textPaint.color = Color.parseColor("#C62828")
        textPaint.textSize = 9f
        canvas.drawText("TOTAL EXPENSES", 222f, 165f, textPaint)
        textPaint.textSize = 14f
        canvas.drawText("Rs. ${totalExpense.toInt()}", 222f, 195f, textPaint)

        // Net Savings
        val net = totalIncome - totalExpense
        blockPaint.color = Color.parseColor("#E3F2FD")
        canvas.drawRect(395f, 145f, 565f, 210f, blockPaint)
        textPaint.color = Color.parseColor("#0D47A1")
        textPaint.textSize = 9f
        canvas.drawText("NET SAVINGS", 405f, 165f, textPaint)
        textPaint.textSize = 14f
        canvas.drawText("Rs. ${net.toInt()}", 405f, 195f, textPaint)

        // Category aggregations
        val incomeCategories = transactions.filter { it.isIncome }.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val expenseCategories = transactions.filter { !it.isIncome }.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // Start drawing table
        textPaint.color = Color.WHITE
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawRect(30f, 235f, 565f, 255f, headerPaint)
        canvas.drawText("REVENUE CATEGORIES (INFLUX)", 35f, 248f, textPaint)
        canvas.drawText("TOTAL Rs (PKR)", 450f, 248f, textPaint)

        textPaint.color = Color.BLACK
        textPaint.textSize = 10f
        var yPos = 275f

        if (incomeCategories.isEmpty()) {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("(No income recorded in this period)", 40f, yPos, textPaint)
            yPos += 20f
        } else {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            for ((cat, sum) in incomeCategories) {
                canvas.drawText(cat, 40f, yPos, textPaint)
                canvas.drawText("Rs. ${String.format(Locale.US, "%,.1f", sum)}", 450f, yPos, textPaint)

                val divider = Paint().apply { color = Color.parseColor("#E0E0E0") }
                canvas.drawLine(30f, yPos + 6f, 565f, yPos + 6f, divider)
                yPos += 24f
            }
        }

        yPos += 15f

        // Expenses Categories Header
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val expenseHeaderPaint = Paint(headerPaint).apply { color = Color.parseColor("#C62828") }
        canvas.drawRect(30f, yPos, 565f, yPos + 20f, expenseHeaderPaint)
        canvas.drawText("EXPENDITURE CATEGORIES (OUTFLOW)", 35f, yPos + 13f, textPaint)
        canvas.drawText("TOTAL Rs (PKR)", 450f, yPos + 13f, textPaint)

        yPos += 35f
        textPaint.color = Color.BLACK
        textPaint.textSize = 10f

        if (expenseCategories.isEmpty()) {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("(No expenses recorded in this period)", 40f, yPos, textPaint)
            yPos += 20f
        } else {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            for ((cat, sum) in expenseCategories) {
                canvas.drawText(cat, 40f, yPos, textPaint)
                canvas.drawText("Rs. ${String.format(Locale.US, "%,.1f", sum)}", 450f, yPos, textPaint)

                val divider = Paint().apply { color = Color.parseColor("#E0E0E0") }
                canvas.drawLine(30f, yPos + 6f, 565f, yPos + 6f, divider)
                yPos += 24f
            }
        }

        yPos = maxOf(yPos + 20f, 680f)
        val auditPaint = Paint().apply {
            color = Color.parseColor("#FFF3E0")
            isAntiAlias = true
        }
        canvas.drawRect(30f, yPos, 565f, yPos + 75f, auditPaint)

        textPaint.color = Color.parseColor("#E65100")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RECONCILIATION & MANAGEMENT NOTE", 45f, yPos + 20f, textPaint)

        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 8.5f
        canvas.drawText("This PDF document matches verified balances recorded inside BLS local database.", 45f, yPos + 40f, textPaint)
        canvas.drawText("Any discrepancy should be filed immediately with the school Principal.", 45f, yPos + 55f, textPaint)

        drawCommonFooter(canvas, textPaint, 1, 1)
        pdfDocument.finishPage(page)

        val fileName = "BLS_Monthly_Summary_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * 3. Export Individual Student Payment History PDF with Multi-Page Support
     */
    fun exportStudentPaymentHistoryToPdf(
        context: Context,
        student: Student,
        transactions: List<Transaction>
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EAEAEA")
            strokeWidth = 0.5f
        }
        val listSdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        val filteredTransactions = transactions.filter { it.studentId == student.id && it.isIncome }
        val grandTotalDeposited = filteredTransactions.sumOf { it.amount }

        val rowsOnPage1 = 18
        val rowsOnNextPages = 26
        val totalRows = filteredTransactions.size
        val totalPages = if (totalRows <= rowsOnPage1) 1 else {
            1 + Math.ceil((totalRows - rowsOnPage1).toDouble() / rowsOnNextPages).toInt()
        }

        var currentItemIndex = 0

        for (pageIndex in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            drawCommonHeader(context, canvas, textPaint, headerPaint, "Student Tuition Card & Payment History Log")

            var yPos: Float

            if (pageIndex == 1) {
                val infoPaint = Paint().apply {
                    color = Color.parseColor("#F8FAFC")
                    isAntiAlias = true
                }
                canvas.drawRect(30f, 105f, 565f, 215f, infoPaint)

                // Left Side Student Details
                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.textSize = 11f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("STUDENT PERSONAL CARD", 45f, 125f, textPaint)

                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.textSize = 9.5f
                canvas.drawText("Reg No:   ${student.regNo.ifEmpty { "N/A" }}", 45f, 145f, textPaint)
                canvas.drawText("Name:     ${student.studentName.uppercase()}", 45f, 163f, textPaint)
                canvas.drawText("Father:   ${student.fatherName.uppercase()}", 45f, 181f, textPaint)
                canvas.drawText("Class:    ${student.className}", 45f, 199f, textPaint)

                // Right Side tuition structure details
                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("FEE STRUCTURE RATES", 340f, 125f, textPaint)

                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Adm Fee:      Rs. ${student.admissionFee.toInt()}", 340f, 145f, textPaint)
                canvas.drawText("Monthly Fee:  Rs. ${student.monthlyFee.toInt()}", 340f, 163f, textPaint)
                canvas.drawText("Annual Fee:   Rs. ${student.annualCharges.toInt()}", 340f, 181f, textPaint)
                canvas.drawText("Discount Code: ${student.discountType}", 340f, 199f, textPaint)

                // Table headers
                textPaint.color = Color.WHITE
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawRect(30f, 230f, 565f, 250f, headerPaint)
                canvas.drawText("DATE PAID", 35f, 244f, textPaint)
                canvas.drawText("FEE CATEGORY MONTH", 120f, 244f, textPaint)
                canvas.drawText("MODE", 340f, 244f, textPaint)
                canvas.drawText("REMARKS / RECEIPT", 410f, 244f, textPaint)
                canvas.drawText("AMOUNT PAID", 490f, 244f, textPaint)

                yPos = 270f
            } else {
                textPaint.color = Color.WHITE
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawRect(30f, 105f, 565f, 125f, headerPaint)
                canvas.drawText("DATE PAID", 35f, 119f, textPaint)
                canvas.drawText("FEE CATEGORY MONTH", 120f, 119f, textPaint)
                canvas.drawText("MODE", 340f, 119f, textPaint)
                canvas.drawText("REMARKS / RECEIPT", 410f, 119f, textPaint)
                canvas.drawText("AMOUNT PAID", 490f, 119f, textPaint)

                yPos = 145f
            }

            val maxRowsThisPage = if (pageIndex == 1) rowsOnPage1 else rowsOnNextPages
            var rowsDrawn = 0

            textPaint.color = Color.BLACK
            textPaint.textSize = 9f

            if (filteredTransactions.isEmpty() && pageIndex == 1) {
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No historical financial deposits found for this student.", 45f, yPos, textPaint)
            } else {
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                while (currentItemIndex < totalRows && rowsDrawn < maxRowsThisPage) {
                    val tx = filteredTransactions[currentItemIndex]
                    val dateStr = listSdf.format(Date(tx.date))
                    val categoryAndMonth = if (tx.monthOfFee.isNullOrEmpty()) tx.category else "${tx.category} (${tx.monthOfFee})"
                    
                    canvas.drawText(dateStr, 35f, yPos, textPaint)
                    canvas.drawText(categoryAndMonth, 120f, yPos, textPaint)
                    canvas.drawText(tx.paymentMode, 340f, yPos, textPaint)
                    canvas.drawText(tx.description.take(16), 410f, yPos, textPaint)
                    canvas.drawText("Rs. ${tx.amount.toInt()}", 495f, yPos, textPaint)

                    canvas.drawLine(30f, yPos + 6f, 565f, yPos + 6f, linePaint)
                    yPos += 24f
                    currentItemIndex++
                    rowsDrawn++
                }
            }

            if (pageIndex == totalPages) {
                val summaryY = maxOf(yPos + 15f, 720f)
                val rectPaint = Paint().apply {
                    color = Color.parseColor("#E8F5E9")
                    isAntiAlias = true
                }
                canvas.drawRect(30f, summaryY, 565f, summaryY + 50f, rectPaint)

                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.textSize = 10f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("GRAND SUMMARY RECEIVED LOGS", 45f, summaryY + 18f, textPaint)

                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Total tuition & charges paid: Rs. ${String.format(Locale.US, "%,.1f", grandTotalDeposited)} (PKR)", 45f, summaryY + 36f, textPaint)
            }

            drawCommonFooter(canvas, textPaint, pageIndex, totalPages)
            pdfDocument.finishPage(page)
        }

        val fileName = "BLS_Student_Ledger_${student.id}_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * 4. Export Daily Closing History List with Multi-Page Support
     */
    fun exportDailyClosingHistoryToPdf(
        context: Context,
        closingsHistory: List<DailyClosing>
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EAEAEA")
            strokeWidth = 0.5f
        }

        val rowsOnPage1 = 20
        val rowsOnNextPages = 26
        val totalRows = closingsHistory.size
        val totalPages = if (totalRows <= rowsOnPage1) 1 else {
            1 + Math.ceil((totalRows - rowsOnPage1).toDouble() / rowsOnNextPages).toInt()
        }

        var currentItemIndex = 0

        for (pageIndex in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            drawCommonHeader(context, canvas, textPaint, headerPaint, "Daily Closing History Accounts Audit Record")

            var yPos: Float

            if (pageIndex == 1) {
                textPaint.color = Color.DKGRAY
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val closingGenDate = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val closingGenBy = if (activeOperatorName.isNotBlank()) " | Operator: ${getReceivedByLabel()}" else ""
                canvas.drawText("Generated: $closingGenDate$closingGenBy", 30f, 122f, textPaint)

                // Table header
                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawRect(30f, 140f, 565f, 160f, headerPaint)
                canvas.drawText("DATE", 35f, 153f, textPaint)
                canvas.drawText("CASH BOX (IN)", 125f, 153f, textPaint)
                canvas.drawText("BANK BAL (IN)", 215f, 153f, textPaint)
                canvas.drawText("INFLOW (C/B)", 305f, 153f, textPaint)
                canvas.drawText("OUTFLOW (C/B)", 395f, 153f, textPaint)
                canvas.drawText("REGISTRAR", 485f, 153f, textPaint)

                yPos = 180f
            } else {
                textPaint.color = Color.WHITE
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawRect(30f, 105f, 565f, 125f, headerPaint)
                canvas.drawText("DATE", 35f, 118f, textPaint)
                canvas.drawText("CASH BOX (IN)", 125f, 118f, textPaint)
                canvas.drawText("BANK BAL (IN)", 215f, 118f, textPaint)
                canvas.drawText("INFLOW (C/B)", 305f, 118f, textPaint)
                canvas.drawText("OUTFLOW (C/B)", 395f, 118f, textPaint)
                canvas.drawText("REGISTRAR", 485f, 118f, textPaint)

                yPos = 145f
            }

            val maxRowsThisPage = if (pageIndex == 1) rowsOnPage1 else rowsOnNextPages
            var rowsDrawn = 0

            textPaint.color = Color.BLACK
            textPaint.textSize = 8.5f

            if (closingsHistory.isEmpty() && pageIndex == 1) {
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No daily closed balances registered inside the database.", 45f, yPos, textPaint)
            } else {
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                while (currentItemIndex < totalRows && rowsDrawn < maxRowsThisPage) {
                    val dc = closingsHistory[currentItemIndex]
                    canvas.drawText(dc.dateString, 35f, yPos, textPaint)
                    canvas.drawText("Rs. ${dc.closingCash.toInt()}", 125f, yPos, textPaint)
                    canvas.drawText("Rs. ${dc.closingBank.toInt()}", 215f, yPos, textPaint)
                    
                    val inflowText = "${dc.totalIncomeCash.toInt()}/${dc.totalIncomeBank.toInt()}"
                    val outflowText = "${dc.totalExpenseCash.toInt()}/${dc.totalExpenseBank.toInt()}"
                    
                    canvas.drawText(inflowText, 305f, yPos, textPaint)
                    canvas.drawText(outflowText, 395f, yPos, textPaint)
                    canvas.drawText(dc.closedBy.uppercase().take(12), 485f, yPos, textPaint)

                    canvas.drawLine(30f, yPos + 6f, 565f, yPos + 6f, linePaint)
                    yPos += 24f
                    currentItemIndex++
                    rowsDrawn++
                }
            }

            if (pageIndex == totalPages) {
                val sigY = maxOf(yPos + 15f, 720f)
                val sigPaint = Paint().apply {
                    color = Color.parseColor("#F5F5F5")
                    isAntiAlias = true
                }
                canvas.drawRect(30f, sigY, 565f, sigY + 60f, sigPaint)

                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.textSize = 10f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("AUDITING & SIGN-OFF BLOCKS", 45f, sigY + 18f, textPaint)

                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.textSize = 8f
                canvas.drawText("Sign Auditor: ___________________        Sign Accountant: ____________________", 45f, sigY + 42f, textPaint)
            }

            drawCommonFooter(canvas, textPaint, pageIndex, totalPages)
            pdfDocument.finishPage(page)
        }

        val fileName = "BLS_Daily_Closings_Ledger_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * Prints an elegant BLS school admissions certificate/receipt as a printable PDF.
     */
    fun exportAdmissionFormToPdf(context: Context, student: Student): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }

        canvas.drawRect(0f, 0f, 595f, 100f, headerPaint)

        var textStartX = 30f
        try {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
            if (logoBitmap != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 72, 72, true)
                canvas.drawBitmap(scaledLogo, 25f, 14f, null)
                textStartX = 110f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 19f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", textStartX, 42f, textPaint)

        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("OFFICIAL STUDENT ADMISSION RECORD & PROFILE", textStartX, 65f, textPaint)

        textPaint.textSize = 10.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Verified & Authenticated by Admissions Office", textStartX, 85f, textPaint)

        val linePaint = Paint().apply {
            color = Color.parseColor("#388E3C")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(20f, 110f, 575f, 810f, linePaint)

        val photoPaint = Paint().apply {
            color = Color.parseColor("#EAEAEA")
            style = Paint.Style.FILL
        }
        canvas.drawRect(430f, 130f, 550f, 250f, photoPaint)
        val borderPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(430f, 130f, 550f, 250f, borderPaint)

        textPaint.color = Color.GRAY
        textPaint.textSize = 8f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("PASTE RECENT", 450f, 180f, textPaint)
        canvas.drawText("PHOTOGRAPH", 453f, 195f, textPaint)
        canvas.drawText("HERE", 470f, 210f, textPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 14f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. PERSONAL INFORMATION", 40f, 140f, textPaint)

        val itemDivider = Paint().apply {
            color = Color.parseColor("#388E3C")
            strokeWidth = 1f
        }
        canvas.drawLine(40f, 148f, 250f, 148f, itemDivider)

        textPaint.color = Color.BLACK
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        var yPos = 175f
        val listSdf = SimpleDateFormat("dd-MM-yyyy EEEE", Locale.US)
        val formattedDate = listSdf.format(Date(student.admissionDate))

        canvas.drawText("Student Name:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.studentName.uppercase(), 170f, yPos, textPaint)
        
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 24f
        canvas.drawText("Father Name:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.fatherName.uppercase(), 170f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 24f
        canvas.drawText("Registration Number:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.regNo.ifEmpty { "Pending allocation" }, 170f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 24f
        canvas.drawText("Class Allocated:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("GRADE ${student.className.uppercase()}", 170f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 24f
        canvas.drawText("Guardian Contact No:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.contactNumber, 170f, yPos, textPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 14f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        yPos += 45f
        canvas.drawText("2. SUPPLEMENTARY CREDENTIALS", 40f, yPos, textPaint)
        canvas.drawLine(40f, yPos + 6f, 300f, yPos + 6f, itemDivider)

        textPaint.color = Color.BLACK
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 30f
        
        canvas.drawText("Student ID / B-Form:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.idCardNumber.ifEmpty { "Not Provided" }, 190f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 24f
        canvas.drawText("Father ID Card / CNIC:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.fatherIdCardNumber.ifEmpty { "Not Provided" }, 190f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 24f
        canvas.drawText("Previous School Name:", 40f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.previousSchoolName.ifEmpty { "Fresh Admission" }, 190f, yPos, textPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 14f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        yPos += 45f
        canvas.drawText("3. VERIFIED DOCUMENTS ATTACHED CHECKLIST", 40f, yPos, textPaint)
        canvas.drawLine(40f, yPos + 6f, 370f, yPos + 6f, itemDivider)

        textPaint.color = Color.BLACK
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 30f

        val checkIconSLC = if (student.schoolCertAttached) "[✓] YES (Attached)" else "[x] NO (Not Attached)"
        val checkIconFormB = if (student.formBAttached) "[✓] YES (Attached)" else "[x] NO (Not Attached)"
        val checkIconCNIC = if (student.fatherCnicAttached) "[✓] YES (Attached)" else "[x] NO (Not Attached)"
        val checkIconPics = if (student.picsAttached) "[✓] YES (Attached)" else "[x] NO (Not Attached)"

        canvas.drawText("School Leaving Certificate:", 55f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(checkIconSLC, 280f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 22f
        canvas.drawText("Form-B / Child ID Card:", 55f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(checkIconFormB, 280f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 22f
        canvas.drawText("Father CNIC Copy:", 55f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(checkIconCNIC, 280f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 22f
        canvas.drawText("Recent Photographs:", 55f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(checkIconPics, 280f, yPos, textPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 14f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        yPos += 45f
        canvas.drawText("4. ALLOCATED FEE STRUCTURE RATES", 40f, yPos, textPaint)
        canvas.drawLine(40f, yPos + 6f, 320f, yPos + 6f, itemDivider)

        textPaint.color = Color.BLACK
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 30f

        val feeBg = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            isAntiAlias = true
        }
        canvas.drawRect(40f, yPos - 15f, 550f, yPos + 80f, feeBg)

        canvas.drawText("Admission Fee (One-Time):", 55f, yPos, textPaint)
        canvas.drawText("Rs. ${student.admissionFee.toInt()}", 320f, yPos, textPaint)

        yPos += 22f
        canvas.drawText("Monthly Tuition Fee Rate:", 55f, yPos, textPaint)
        canvas.drawText("Rs. ${student.monthlyFee.toInt()}", 320f, yPos, textPaint)

        yPos += 22f
        canvas.drawText("Annual Promotional Fee:", 55f, yPos, textPaint)
        canvas.drawText("Rs. ${student.annualCharges.toInt()}", 320f, yPos, textPaint)

        yPos += 22f
        canvas.drawText("Scholarship / Concession Mode:", 55f, yPos, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(student.discountType, 320f, yPos, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos += 50f
        canvas.drawText("Admission Registered At Date:  $formattedDate", 40f, yPos, textPaint)

        textPaint.color = Color.GRAY
        textPaint.textSize = 8.5f
        yPos += 45f
        canvas.drawText("I hereby declare that the student particulars are correct to the best of my knowledge.", 40f, yPos, textPaint)
        canvas.drawText("The school will provide blending courses strictly following regulatory frameworks.", 40f, yPos + 12f, textPaint)

        yPos += 60f
        textPaint.color = Color.BLACK
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("_________________________", 40f, yPos, textPaint)
        canvas.drawText("Parent / Guardian Sign", 40f, yPos + 15f, textPaint)

        canvas.drawText("_________________________", 380f, yPos, textPaint)
        val adminSignTitle = if (activeOperatorName.isNotBlank()) "Admitted / Received By: ${getReceivedByLabel()}" else "Principal / Admin Officer"
        canvas.drawText(adminSignTitle, 350f, yPos + 15f, textPaint)

        drawCommonFooter(canvas, textPaint, 1, 1)
        pdfDocument.finishPage(page)

        val fileName = "BLS_Admission_Certificate_${student.studentName.replace(" ", "_")}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * Generates an official School Leaving Certificate (SLC) / Character & Clearance Certificate as a printable PDF.
     */
    fun exportSchoolLeavingCertificateToPdf(
        context: Context,
        student: Student,
        reason: String = "Parent's Request / Completion of Studies",
        conduct: String = "Good & Satisfactory"
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }

        // Header Background
        canvas.drawRect(0f, 0f, 595f, 100f, headerPaint)

        var textStartX = 30f
        try {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
            if (logoBitmap != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 72, 72, true)
                canvas.drawBitmap(scaledLogo, 25f, 14f, null)
                textStartX = 110f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 18f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", textStartX, 40f, textPaint)

        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("SCHOOL LEAVING CERTIFICATE & CLEARANCE", textStartX, 62f, textPaint)

        textPaint.textSize = 10f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Official Character & Academic Migration Record", textStartX, 82f, textPaint)

        // Ornate Double Border
        val borderOuter = Paint().apply {
            color = Color.parseColor("#0F52BA")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRect(20f, 110f, 575f, 810f, borderOuter)

        val borderInner = Paint().apply {
            color = Color.parseColor("#81C784")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRect(24f, 114f, 571f, 806f, borderInner)

        val todayDate = SimpleDateFormat("dd MMMM yyyy", Locale.US).format(Date())
        val regStr = student.regNo.ifEmpty { "BLS-${student.id}" }
        val slcNo = "SLC-$regStr"

        // Meta header row
        textPaint.color = Color.parseColor("#555555")
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CERTIFICATE NO: $slcNo", 40f, 136f, textPaint)
        canvas.drawText("ISSUE DATE: $todayDate", 410f, 136f, textPaint)

        val divPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 1f
        }
        canvas.drawLine(40f, 144f, 555f, 144f, divPaint)

        // Title Badge inside page
        val badgePaint = Paint().apply {
            color = Color.parseColor("#E8F5E9")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(150f, 155f, 445f, 185f, 8f, 8f, badgePaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TO WHOM IT MAY CONCERN", 215f, 175f, textPaint)

        // Opening certification statement
        textPaint.color = Color.parseColor("#222222")
        textPaint.textSize = 11.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        var y = 215f
        canvas.drawText("This is to solemnly certify that the student whose particulars are documented below", 40f, y, textPaint)
        y += 18f
        canvas.drawText("was a bonafide student of Blended Learning School (BLS).", 40f, y, textPaint)

        // Section 1: Student Particulars
        y += 35f
        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. STUDENT PARTICULARS", 40f, y, textPaint)
        canvas.drawLine(40f, y + 4f, 250f, y + 4f, divPaint)

        val particulars = listOf(
            "Student Full Name:" to student.studentName.uppercase(Locale.US),
            "Father's / Guardian Name:" to student.fatherName.uppercase(Locale.US),
            "Registration / Admission No:" to regStr,
            "Form-B / ID Card Number:" to student.idCardNumber.ifEmpty { "Not Provided" },
            "Class Last Attended:" to "Class ${student.className}",
            "Date of Admission:" to SimpleDateFormat("dd MMMM yyyy", Locale.US).format(Date(student.admissionDate)),
            "Date of Leaving / Withdrawal:" to todayDate,
            "Status in Institutional Record:" to student.status
        )

        y += 20f
        textPaint.textSize = 11f
        for ((label, value) in particulars) {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.color = Color.parseColor("#444444")
            canvas.drawText(label, 50f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = Color.BLACK
            canvas.drawText(value, 260f, y, textPaint)
            y += 20f
        }

        // Section 2: Conduct & Reason
        y += 15f
        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("2. CONDUCT & REMARKS", 40f, y, textPaint)
        canvas.drawLine(40f, y + 4f, 230f, y + 4f, divPaint)

        y += 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#444444")
        textPaint.textSize = 11f
        canvas.drawText("Reason for School Leaving:", 50f, y, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.BLACK
        canvas.drawText(reason, 260f, y, textPaint)

        y += 20f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#444444")
        canvas.drawText("General Conduct & Character:", 50f, y, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#2E7D32")
        canvas.drawText(conduct, 260f, y, textPaint)

        // Section 3: Clearance Checklist
        y += 30f
        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("3. CLEARANCE FROM SCHOOL DEPARTMENTS", 40f, y, textPaint)
        canvas.drawLine(40f, y + 4f, 340f, y + 4f, divPaint)

        val clearances = listOf(
            "Tuition & Annual Dues:" to "[✓] FULLY CLEARED / NO OUTSTANDING DUES",
            "Library & Book Borrowings:" to "[✓] CLEARED (All books returned)",
            "Science / IT Computer Lab:" to "[✓] CLEARED (No lab property due)",
            "Sports & Extra Curricular:" to "[✓] CLEARED"
        )

        y += 20f
        textPaint.textSize = 10.5f
        for ((dept, status) in clearances) {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.color = Color.parseColor("#444444")
            canvas.drawText(dept, 50f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = Color.parseColor("#2E7D32")
            canvas.drawText(status, 260f, y, textPaint)
            y += 19f
        }

        // Certification statement
        y += 15f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        textPaint.color = Color.parseColor("#333333")
        textPaint.textSize = 10f
        canvas.drawText("It is certified that the above particulars have been cross-verified with the Admission & Withdrawal", 40f, y, textPaint)
        y += 14f
        canvas.drawText("Register of Blended Learning School (BLS) and found correct in all respects.", 40f, y, textPaint)

        // Official Stamp Box & Signatures
        y += 45f
        // Stamp Box
        val stampPaint = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(240f, y - 20f, 355f, y + 45f, stampPaint)
        textPaint.color = Color.parseColor("#888888")
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("OFFICIAL", 278f, y + 8f, textPaint)
        canvas.drawText("SCHOOL STAMP", 262f, y + 22f, textPaint)

        // Signatures
        textPaint.color = Color.BLACK
        textPaint.textSize = 10.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("______________________", 50f, y + 10f, textPaint)
        canvas.drawText("Accounts Incharge", 65f, y + 28f, textPaint)

        canvas.drawText("______________________", 410f, y + 10f, textPaint)
        canvas.drawText("Principal / Headmaster", 425f, y + 28f, textPaint)

        drawCommonFooter(canvas, textPaint, 1, 1)
        pdfDocument.finishPage(page)

        val fileName = "BLS_SLC_${student.studentName.replace(" ", "_")}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * Prints a Pakistani standard double copy slip (Student Copy & School Copy).
     */
    fun exportFeeSlipToPdf(
        context: Context,
        transaction: Transaction,
        student: Student
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        val greenPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        
        val lightGreenBg = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            isAntiAlias = true
        }

        val dashPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val listSdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        val dateString = listSdf.format(Date(transaction.date))

        fun drawCopy(yOffset: Float, isSchoolCopy: Boolean) {
            canvas.drawRect(20f, yOffset, 575f, yOffset + 50f, greenPaint)

            var textStartX = 35f
            try {
                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
                if (logoBitmap != null) {
                    val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 40, 40, true)
                    canvas.drawBitmap(scaledLogo, 28f, yOffset + 5f, null)
                    textStartX = 78f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = 13f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", textStartX, yOffset + 24f, textPaint)

            textPaint.textSize = 9f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val subText = if (isSchoolCopy) "FEE RECEIVE SLIP - SCHOOL COPY" else "FEE RECEIVE SLIP - STUDENT COPY"
            canvas.drawText(subText, textStartX, yOffset + 40f, textPaint)

            val border = Paint().apply {
                color = Color.parseColor("#0F52BA")
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(20f, yOffset, 575f, yOffset + 350f, border)

            textPaint.color = Color.BLACK
            textPaint.textSize = 9f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val receiptLabel = if (transaction.voucherNo.isNotBlank()) "Receipt/Voucher: ${transaction.voucherNo}" else "Receipt No: BLS-TX-${transaction.id}"
            canvas.drawText(receiptLabel, 35f, yOffset + 75f, textPaint)
            canvas.drawText("Deposited: $dateString", 340f, yOffset + 75f, textPaint)

            canvas.drawRect(35f, yOffset + 90f, 560f, yOffset + 175f, lightGreenBg)

            textPaint.color = Color.parseColor("#0F52BA")
            textPaint.textSize = 10f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DEPOSITOR PARTICULARS", 45f, yOffset + 108f, textPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 9.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Student Name:  ${student.studentName.uppercase()}", 45f, yOffset + 128f, textPaint)
            canvas.drawText("Class Group:   ${student.className} | Reg No: ${student.regNo.ifEmpty { "Pending" }}", 45f, yOffset + 144f, textPaint)
            canvas.drawText("Father Name:   ${student.fatherName.uppercase()} | Contact: ${student.contactNumber}", 45f, yOffset + 160f, textPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 9.5f
            canvas.drawText("Fee Categories / Period Details", 35f, yOffset + 205f, textPaint)
            canvas.drawText("Amount", 480f, yOffset + 205f, textPaint)

            val darkDivider = Paint().apply {
                color = Color.parseColor("#0F52BA")
                strokeWidth = 1f
            }
            canvas.drawLine(35f, yOffset + 215f, 560f, yOffset + 215f, darkDivider)

            val itemY = yOffset + 235f
            val billingMonth = transaction.monthOfFee ?: "School Fees"
            canvas.drawText("${transaction.category} ($billingMonth)", 35f, itemY, textPaint)
            
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Rs. ${transaction.amount.toInt()}", 480f, itemY, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val subLine = Paint().apply { color = Color.parseColor("#EAEAEA") }
            canvas.drawLine(35f, itemY + 10f, 560f, itemY + 10f, subLine)

            val itemY2 = itemY + 28f
            canvas.drawText("Payment Mode Received:", 35f, itemY2, textPaint)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(transaction.paymentMode, 180f, itemY2, textPaint)

            textPaint.color = Color.parseColor("#0F52BA")
            canvas.drawText("GRAND TOTAL PAID:", 310f, itemY2, textPaint)
            canvas.drawText("Rs. ${transaction.amount.toInt()}", 480f, itemY2, textPaint)

            textPaint.color = Color.GRAY
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("Generated via BLS Ledger Center. Self-certified by central accounts system.", 35f, yOffset + 310f, textPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("_________________________", 380f, yOffset + 300f, textPaint)
            val feeSlipSign = if (activeOperatorName.isNotBlank()) "Received By: ${getReceivedByLabel()}" else "Cashier / Bank Stamp Sign"
            canvas.drawText(feeSlipSign, 360f, yOffset + 314f, textPaint)
        }

        drawCopy(25f, isSchoolCopy = true)

        var x = 20f
        while (x < 575f) {
            canvas.drawLine(x, 400f, x + 8f, 400f, dashPaint)
            x += 16f
        }
        textPaint.color = Color.GRAY
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("✂  (CLIP & SEVER - SCHOOL RECORD DUAL COPY)", 200f, 403f, textPaint)

        drawCopy(425f, isSchoolCopy = false)

        pdfDocument.finishPage(page)

        val fileName = "BLS_Fee_Receive_Receipt_${transaction.id}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * Compact 58mm / 80mm Thermal POS Mini-Receipt PDF Exporter.
     * Specially designed for ESC/POS Bluetooth and USB thermal receipt printers with zero ink usage.
     */
    fun exportThermalReceiptToPdf(
        context: Context,
        transaction: Transaction,
        student: Student
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(200, 390, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 0.8f
        }

        val dashDividerPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.5f
        }

        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
        val dateStr = sdf.format(Date(transaction.date))

        var yPos = 24f

        // School Header
        boldPaint.textSize = 10.5f
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("BLENDED LEARNING SCHOOL", 100f, yPos, boldPaint)
        yPos += 13f

        textPaint.textSize = 7.5f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("OFFICIAL COUNTER FEE RECEIPT", 100f, yPos, textPaint)
        yPos += 10f

        canvas.drawLine(10f, yPos, 190f, yPos, dividerPaint)
        yPos += 14f

        // Transaction Details
        textPaint.textAlign = Paint.Align.LEFT
        boldPaint.textAlign = Paint.Align.LEFT
        boldPaint.textSize = 8.5f
        textPaint.textSize = 8f

        val slipNumber = if (transaction.voucherNo.isNotBlank()) transaction.voucherNo else "TX-${transaction.id}"
        canvas.drawText("Slip No: $slipNumber", 10f, yPos, textPaint)
        yPos += 12f
        canvas.drawText("Date: $dateStr", 10f, yPos, textPaint)
        yPos += 14f

        canvas.drawLine(10f, yPos, 190f, yPos, dashDividerPaint)
        yPos += 14f

        // Student Details
        canvas.drawText("Student: ${student.studentName.uppercase()}", 10f, yPos, boldPaint)
        yPos += 12f
        val regStr = if (student.regNo.isNotBlank()) student.regNo else "N/A"
        canvas.drawText("Class: ${student.className}  |  Roll#: $regStr", 10f, yPos, textPaint)
        yPos += 12f
        canvas.drawText("Father: ${student.fatherName}", 10f, yPos, textPaint)
        yPos += 14f

        canvas.drawLine(10f, yPos, 190f, yPos, dashDividerPaint)
        yPos += 14f

        // Fee Category & Mode
        canvas.drawText("Category: ${transaction.category}", 10f, yPos, textPaint)
        yPos += 12f
        if (!transaction.monthOfFee.isNullOrBlank()) {
            canvas.drawText("Billing Month: ${transaction.monthOfFee}", 10f, yPos, textPaint)
            yPos += 12f
        }
        canvas.drawText("Payment Mode: ${transaction.paymentMode}", 10f, yPos, textPaint)
        yPos += 15f

        canvas.drawLine(10f, yPos, 190f, yPos, dividerPaint)
        yPos += 18f

        // Grand Total Row
        boldPaint.textSize = 12f
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL PAID:", 10f, yPos, boldPaint)
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Rs. ${transaction.amount.toInt()}", 190f, yPos, boldPaint)
        yPos += 16f

        canvas.drawLine(10f, yPos, 190f, yPos, dividerPaint)
        yPos += 18f

        // Footer Note
        textPaint.textSize = 7f
        textPaint.textAlign = Paint.Align.CENTER
        val thermalReceivedBy = if (activeOperatorName.isNotBlank()) "Received By: ${getReceivedByLabel()}" else "Cashier / Counter Desk"
        canvas.drawText(thermalReceivedBy, 100f, yPos, textPaint)
        yPos += 10f
        canvas.drawText("Thank you for your timely payment!", 100f, yPos, textPaint)
        yPos += 12f
        canvas.drawText(". . . . . . . . . . . . . . . . . . . . . .", 100f, yPos, textPaint)

        pdfDocument.finishPage(page)

        val cachedFile = File(context.cacheDir, "BLS_ThermalReceipt_${transaction.id}.pdf")
        return try {
            pdfDocument.writeTo(FileOutputStream(cachedFile))
            pdfDocument.close()
            cachedFile
        } catch (e: Exception) {
            pdfDocument.close()
            e.printStackTrace()
            null
        }
    }

    /**
     * Exports transaction balances to CSV spreadsheet (3 columns: Cash Ledger, Bank Ledger, Expenses)
     */
    fun exportClosingToCsv(
        context: Context, 
        transactions: List<Transaction>, 
        titleWord: String = "Closing_Statement",
        studentsMap: Map<Int, Student> = emptyMap()
    ): File? {
        val csvHeader = "Transaction ID,Date,Description/Category,Cash Ledger (Inflow),Bank Ledger (Inflow),Expenses (Outflow)\n"
        val csvBody = StringBuilder(csvHeader)
        
        var totalCash = 0.0
        var totalBank = 0.0
        var totalExpense = 0.0
        
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        
        for (tx in transactions) {
            val dateStr = sdf.format(Date(tx.date))
            val studentInfo = (tx.studentId?.let { studentsMap[it] })
                ?: (tx.studentName?.let { name -> studentsMap.values.firstOrNull { it.studentName.equals(name, ignoreCase = true) } })
            val studentClass = studentInfo?.className?.takeIf { it.isNotBlank() }
            val desc = if (tx.isIncome) {
                val sName = tx.studentName ?: studentInfo?.studentName ?: "General"
                val classSuffix = if (studentClass != null) " ($studentClass)" else ""
                "${tx.category} - $sName$classSuffix"
            } else {
                tx.description.ifEmpty { tx.category }
            }.replace("\"", "\"\"")
            val sanitizedDesc = "\"$desc\""
            
            var cashVal = ""
            var bankVal = ""
            var expenseVal = ""
            
            if (tx.isIncome) {
                if (tx.paymentMode.equals("Cash", ignoreCase = true)) {
                    cashVal = tx.amount.toString()
                    totalCash += tx.amount
                } else {
                    bankVal = tx.amount.toString()
                    totalBank += tx.amount
                }
            } else {
                expenseVal = tx.amount.toString()
                totalExpense += tx.amount
            }
            
            csvBody.append("${tx.id},$dateStr,$sanitizedDesc,$cashVal,$bankVal,$expenseVal\n")
        }
        
        csvBody.append("\n\n")
        csvBody.append(",,Summary Metrics Summary,Total Cash Ledger,Total Bank Ledger,Total Expenses\n")
        csvBody.append(",,Calculated Rates,Rs. $totalCash,Rs. $totalBank,Rs. $totalExpense\n")
        csvBody.append("\n")
        
        val balance = (totalCash + totalBank) - totalExpense
        val formulaLabel = "\"Closing Balance Calculation (Cash + Bank - Expense = Balance)\""
        csvBody.append(",,$formulaLabel,Rs. $totalCash,Rs. $totalBank,Rs. $totalExpense\n")
        csvBody.append(",,FINAL CLOSING BALANCE RESULT,,,,Rs. $balance\n")
        
        val fileName = "BLS_${titleWord}_GoogleSheet.csv"
        val cached = File(context.cacheDir, fileName)
        return try {
            FileOutputStream(cached).use { outputStream ->
                outputStream.write(csvBody.toString().toByteArray(Charsets.UTF_8))
            }
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveCsvToDownloads(context: Context, cachedFile: File, userFriendlyName: String): UriResult {
        try {
            val finalFileName = "${userFriendlyName.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BLS_Reports")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            FileInputStream(cachedFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                    return UriResult(true, "Saved to Downloads/BLS_Reports/$finalFileName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val blsDir = File(downloadsDir, "BLS_Reports")
                if (!blsDir.exists()) {
                    blsDir.mkdirs()
                }
                val destinationFile = File(blsDir, finalFileName)
                FileInputStream(cachedFile).use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return UriResult(true, "Saved to Downloads/BLS_Reports/$finalFileName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return UriResult(false, "Failed to copy CSV spreadsheet to local Downloads directory")
    }

    fun sharePdf(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "BLS Report Export"))
    }

    fun shareCsv(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "BLS Google Sheet Export"))
    }

    /**
     * 1-Click WhatsApp Fee Reminder notification directly to parent's phone.
     */
    fun sendWhatsAppFeeReminder(
        context: Context,
        phoneNumber: String,
        studentName: String,
        className: String,
        month: String,
        currentMonthFee: Double,
        arrears: Double,
        totalPayable: Double
    ) {
        val cleanNumber = InputFormatUtils.sanitizePakistanPhoneForWhatsApp(phoneNumber)

        val arrearsText = if (arrears > 0) "⚠️ *Previous Arrears:* Rs. ${arrears.toInt()}\n" else ""
        val message = """
*BLENDED LEARNING SCHOOL (BLS)*
*Official Fee Reminder Notice*
---------------------------------------
Dear Parent,
This is a gentle reminder regarding the school fee for your child:

👤 *Student Name:* $studentName
📚 *Class:* $className
📅 *Fee Month:* $month
💵 *Current Month Fee:* Rs. ${currentMonthFee.toInt()}
${arrearsText}💰 *TOTAL PAYABLE:* Rs. ${totalPayable.toInt()}
---------------------------------------
Kindly submit the fee at the school accounts office or online to avoid late charges.
Thank you,
*BLS Accounts Department*
        """.trimIndent()

        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Offline SMS Notice for parents without internet or WhatsApp.
     */
    fun sendSmsFeeNotice(
        context: Context,
        phoneNumber: String,
        studentName: String,
        className: String,
        month: String,
        totalPayable: Double
    ) {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        val message = "BLS School Notice: Dear Parent, fee for $studentName (Class $className) for $month is Rs. ${totalPayable.toInt()}. Kindly pay at accounts office. BLS Accounts."
        try {
            val uri = Uri.parse("smsto:$cleanNumber")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Offline SMS Welcome Message for newly admitted student.
     */
    fun sendSmsAdmissionWelcome(
        context: Context,
        student: Student,
        initialPaid: Double
    ) {
        val cleanNumber = student.contactNumber.filter { it.isDigit() }
        val paidText = if (initialPaid > 0) " Fee Paid: Rs. ${initialPaid.toInt()}." else ""
        val message = "BLS Admission Confirmed: Welcome ${student.studentName} (Reg: ${student.regNo}, Class: ${student.className}) to BLS School.$paidText Monthly Fee: Rs. ${student.monthlyFee.toInt()}. BLS Admissions."
        try {
            val uri = Uri.parse("smsto:$cleanNumber")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Overloaded helper taking a Student model directly.
     */
    fun sendWhatsAppFeeReminder(
        context: Context,
        student: Student,
        monthYear: String,
        currentMonthFee: Double,
        arrears: Double
    ) {
        val total = currentMonthFee + arrears
        sendWhatsAppFeeReminder(
            context = context,
            phoneNumber = student.contactNumber,
            studentName = student.studentName,
            className = student.className,
            month = monthYear,
            currentMonthFee = currentMonthFee,
            arrears = arrears,
            totalPayable = total
        )
    }

    /**
     * 1-Click WhatsApp Instant Fee Payment Receipt directly to parent's phone.
     */
    fun sendWhatsAppFeeReceipt(
        context: Context,
        student: Student,
        transaction: Transaction,
        remainingArrears: Double = 0.0
    ) {
        val cleanNumber = InputFormatUtils.sanitizePakistanPhoneForWhatsApp(student.contactNumber)
        if (cleanNumber.isBlank()) {
            Toast.makeText(context, "No contact number available for WhatsApp", Toast.LENGTH_SHORT).show()
            return
        }

        val dateStr = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date(transaction.date))
        val arrearsText = if (remainingArrears > 0) "⚠️ *Remaining Balance / Arrears:* Rs. ${remainingArrears.toInt()}\n" else "✅ *Payment Status:* All Dues Cleared\n"
        val message = """
🏫 *BLENDED LEARNING SCHOOL (BLS)*
📄 *OFFICIAL FEE PAYMENT RECEIPT*
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
👤 *Student Name:* ${student.studentName}
🏷️ *Reg / Roll No:* ${student.regNo.ifBlank { "N/A" }}
📚 *Class:* ${student.className}
👨 *Father Name:* ${student.fatherName}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💵 *Amount Paid:* Rs. ${transaction.amount.toInt()}
💼 *Payment Mode:* ${transaction.paymentMode}
📅 *Fee Month Period:* ${transaction.monthOfFee ?: "N/A"}
🔖 *Voucher No:* ${transaction.voucherNo}
🕒 *Payment Date:* $dateStr
${arrearsText}━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Fee payment has been acknowledged & recorded in accounts ledger.
Thank you for your timely payment!
_Accounts Department, Beacon Light School_
        """.trimIndent()

        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 1-Click WhatsApp Sibling / Family Fee Payment Receipt to Guardian's phone.
     */
    fun sendWhatsAppFamilyFeeReceipt(
        context: Context,
        fatherName: String,
        phoneNumber: String,
        familyVoucherNo: String,
        siblingItems: List<Triple<Student, Double, String>>,
        totalPaid: Double,
        paymentMode: String
    ) {
        val cleanNumber = InputFormatUtils.sanitizePakistanPhoneForWhatsApp(phoneNumber)
        if (cleanNumber.isBlank()) {
            Toast.makeText(context, "No contact number available for WhatsApp", Toast.LENGTH_SHORT).show()
            return
        }

        val dateStr = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        siblingItems.forEachIndexed { index, (s, amt, mth) ->
            sb.append("${index + 1}. *${s.studentName}* (${s.className}) - Rs. ${amt.toInt()} [$mth]\n")
        }

        val message = """
🏫 *BLENDED LEARNING SCHOOL (BLS)*
👨‍👩‍👧‍👦 *FAMILY SIBLING FEE RECEIPT*
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
👨 *Guardian / Father:* $fatherName
🔖 *Family Voucher:* $familyVoucherNo
💼 *Payment Mode:* $paymentMode
🕒 *Date:* $dateStr
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
*ENROLLED SIBLINGS BREAKDOWN:*
${sb.toString()}━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💰 *TOTAL AMOUNT RECEIVED:* Rs. ${totalPaid.toInt()}
✅ *Payment Status:* Cleared & Verified
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Thank you for your timely payment!
_Accounts Department, Beacon Light School_
        """.trimIndent()

        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Official 3-Part Admission Fee Package Challan & Counter Receipt on A4 Landscape.
     * Itemizes the one-time admission fee, 1st month tuition, annual charges, other charges,
     * paid amount at counter, and remaining balance.
     */
    fun exportAdmissionChallanToPdf(
        context: Context,
        student: Student,
        monthYear: String,
        admissionFee: Double,
        monthlyFee: Double,
        discountedMonthlyFee: Double,
        discountType: String,
        annualCharges: Double,
        otherCharges: Double,
        paidAtCounter: Double,
        dueDateStr: String = "Due at Admission",
        bankName: String = "BLS Cash Counter / School Bank Account",
        accountNumber: String = "BLS Central Account"
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        val greenHeader = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }

        val cardBg = Paint().apply {
            color = Color.parseColor("#F8FAF8")
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val dashPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.8f
        }

        val copies = listOf("BANK / CASHIER COPY", "SCHOOL / OFFICE COPY", "STUDENT / PARENT COPY")
        val colWidth = 262f
        val startXs = listOf(14f, 290f, 566f)
        val totalPackage = admissionFee + discountedMonthlyFee + annualCharges + otherCharges
        val balanceDue = maxOf(0.0, totalPackage - paidAtCounter)

        val issueDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val challanNo = "BLS-ADM-${student.id}-${SimpleDateFormat("yyMM", Locale.getDefault()).format(Date())}"

        for (i in 0..2) {
            val startX = startXs[i]
            val copyTitle = copies[i]

            // Outer border box
            canvas.drawRect(startX, 14f, startX + colWidth, 580f, cardBg)
            canvas.drawRect(startX, 14f, startX + colWidth, 580f, borderPaint)

            // Header banner
            canvas.drawRect(startX, 14f, startX + colWidth, 64f, greenHeader)

            // School Logo
            var textX = startX + 12f
            try {
                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
                if (logoBitmap != null) {
                    val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 38, 38, true)
                    canvas.drawBitmap(scaledLogo, startX + 8f, 20f, null)
                    textX = startX + 50f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = 10.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BLENDED LEARNING SCHOOL", textX, 33f, textPaint)

            textPaint.textSize = 7.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Official Admission Package Challan", textX, 46f, textPaint)

            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(copyTitle, textX, 58f, textPaint)

            // Challan Meta Info
            textPaint.color = Color.DKGRAY
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Challan No: $challanNo", startX + 10f, 78f, textPaint)
            canvas.drawText("Issue Date: $issueDate", startX + 140f, 78f, textPaint)
            canvas.drawText("Status: ${if (balanceDue == 0.0) "Fully Paid" else "Partially Paid"}", startX + 10f, 92f, textPaint)
            canvas.drawText("Due Date: $dueDateStr", startX + 140f, 92f, textPaint)

            // Student Particulars Box
            val particularsBg = Paint().apply { color = Color.parseColor("#E8F5E9") }
            canvas.drawRect(startX + 10f, 100f, startX + colWidth - 10f, 168f, particularsBg)

            textPaint.color = Color.parseColor("#0F52BA")
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("STUDENT PARTICULARS", startX + 16f, 114f, textPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Name: ${student.studentName.uppercase()}", startX + 16f, 128f, textPaint)
            canvas.drawText("Father: ${student.fatherName.uppercase()}", startX + 16f, 142f, textPaint)
            canvas.drawText("Class: ${student.className} | Reg No: ${student.regNo.ifEmpty { "Pending" }}", startX + 16f, 156f, textPaint)

            // Billing Period Banner
            textPaint.color = Color.parseColor("#0E3A14")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Admission Period: $monthYear", startX + 10f, 184f, textPaint)
            canvas.drawLine(startX + 10f, 190f, startX + colWidth - 10f, 190f, dividerPaint)

            // Table Header
            var yPos = 206f
            textPaint.textSize = 8.5f
            textPaint.color = Color.DKGRAY
            canvas.drawText("Particulars", startX + 12f, yPos, textPaint)
            canvas.drawText("Amount (Rs.)", startX + colWidth - 75f, yPos, textPaint)
            canvas.drawLine(startX + 10f, yPos + 6f, startX + colWidth - 10f, yPos + 6f, dividerPaint)

            fun drawRow(label: String, amount: Double, isBold: Boolean = false, isGreen: Boolean = false, isRed: Boolean = false) {
                yPos += 20f
                textPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.color = when {
                    isGreen -> Color.parseColor("#0F52BA")
                    isRed -> Color.parseColor("#C62828")
                    else -> Color.BLACK
                }
                canvas.drawText(label, startX + 12f, yPos, textPaint)
                canvas.drawText("Rs. ${amount.toInt()}", startX + colWidth - 75f, yPos, textPaint)
                canvas.drawLine(startX + 10f, yPos + 4f, startX + colWidth - 10f, yPos + 4f, dividerPaint)
            }

            if (admissionFee > 0.0) drawRow("Admission Fee (One-Time)", admissionFee)
            val tuitionLabel = if (discountType.isNotBlank() && discountType != "None" && monthlyFee > discountedMonthlyFee) {
                "1st Month Tuition [$discountType] (Base: ${monthlyFee.toInt()})"
            } else {
                "1st Month Tuition Fee"
            }
            drawRow(tuitionLabel, discountedMonthlyFee)
            if (annualCharges > 0.0) drawRow("Annual Session Charges", annualCharges)
            if (otherCharges > 0.0) drawRow("Other / Prospectus Charges", otherCharges)

            drawRow("TOTAL ADMISSION PACKAGE", totalPackage, isBold = true, isGreen = true)
            drawRow("Paid at Counter", paidAtCounter, isBold = true, isGreen = true)
            drawRow("Remaining Balance Due", balanceDue, isBold = true, isRed = balanceDue > 0)

            // Bank Information Note
            yPos += 20f
            textPaint.textSize = 7.5f
            textPaint.color = Color.DKGRAY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Desk: $bankName", startX + 12f, yPos, textPaint)
            yPos += 12f
            canvas.drawText("A/C: $accountNumber", startX + 12f, yPos, textPaint)
            yPos += 14f
            canvas.drawText("* Admission once confirmed is non-refundable.", startX + 12f, yPos, textPaint)

            // Signatures
            val sigY = 540f
            textPaint.color = Color.BLACK
            textPaint.textSize = 7.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawLine(startX + 12f, sigY, startX + 115f, sigY, dividerPaint)
            val admissionChallanSign = if (activeOperatorName.isNotBlank()) "Received: ${getReceivedByLabel()}" else "Cashier / Admin Stamp"
            canvas.drawText(admissionChallanSign, startX + 14f, sigY + 12f, textPaint)

            canvas.drawLine(startX + colWidth - 110f, sigY, startX + colWidth - 12f, sigY, dividerPaint)
            canvas.drawText("Parent / Depositor", startX + colWidth - 105f, sigY + 12f, textPaint)

            // Vertical Cut lines between columns
            if (i < 2) {
                val cutX = startX + colWidth + 7f
                var cutY = 20f
                while (cutY < 570f) {
                    canvas.drawLine(cutX, cutY, cutX, cutY + 8f, dashPaint)
                    cutY += 16f
                }
                textPaint.color = Color.GRAY
                textPaint.textSize = 7f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.save()
                canvas.rotate(-90f, cutX, 300f)
                canvas.drawText("✂  CUT HERE  ✂", cutX - 25f, 300f, textPaint)
                canvas.restore()
            }
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "BLS_Admission_Challan_${student.regNo.ifBlank { student.id.toString() }}.pdf")
        return try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * Official 3-Part Student Exit & Closing Fee Challan (Settlement Voucher) on A4 Landscape.
     * Itemizes unpaid tuition arrears, annual dues, leaving/migration fee, payment received,
     * and final NOC / outstanding status.
     */
    fun exportStudentClosingChallanPdf(
        context: Context,
        student: Student,
        monthlyArrears: Double,
        unpaidMonthsDescription: String = "",
        annualDues: Double = 0.0,
        otherDues: Double = 0.0,
        leavingProcessingFee: Double = 0.0,
        amountReceivedNow: Double = 0.0,
        remarks: String = "",
        bankName: String = "BLS Accounts Desk / Official Bank A/C",
        accountNumber: String = "BLS Central Account"
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        val headerColor = Paint().apply {
            color = Color.parseColor("#1B365D")
            isAntiAlias = true
        }

        val cardBg = Paint().apply {
            color = Color.parseColor("#FDFDFD")
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.parseColor("#1B365D")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val dashPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.8f
        }

        val copies = listOf("BANK / CASHIER COPY", "SCHOOL / OFFICE COPY", "STUDENT / PARENT COPY")
        val colWidth = 262f
        val startXs = listOf(14f, 290f, 566f)

        val totalExitDues = monthlyArrears + annualDues + otherDues + leavingProcessingFee
        val finalOutstanding = maxOf(0.0, totalExitDues - amountReceivedNow)
        val isFullyCleared = finalOutstanding <= 0.0

        val issueDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val challanNo = "BLS-EXIT-${student.id}-${SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())}"

        for (i in 0..2) {
            val startX = startXs[i]
            val copyTitle = copies[i]

            // Outer border box
            canvas.drawRect(startX, 14f, startX + colWidth, 580f, cardBg)
            canvas.drawRect(startX, 14f, startX + colWidth, 580f, borderPaint)

            // Header banner
            canvas.drawRect(startX, 14f, startX + colWidth, 64f, headerColor)

            // School Logo
            var textX = startX + 12f
            try {
                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
                if (logoBitmap != null) {
                    val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 38, 38, true)
                    canvas.drawBitmap(scaledLogo, startX + 8f, 20f, null)
                    textX = startX + 50f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = 10.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BLENDED LEARNING SCHOOL", textX, 33f, textPaint)

            textPaint.textSize = 7.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Student Closing Fee Challan & Clearance", textX, 46f, textPaint)

            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(copyTitle, textX, 58f, textPaint)

            // Challan Meta Info
            textPaint.color = Color.DKGRAY
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Voucher No: $challanNo", startX + 10f, 78f, textPaint)
            canvas.drawText("Date: $issueDate", startX + 140f, 78f, textPaint)

            // Status Banner Box
            val statusBg = Paint().apply {
                color = if (isFullyCleared) Color.parseColor("#E8F5E9") else Color.parseColor("#FFEBEE")
            }
            canvas.drawRect(startX + 10f, 86f, startX + colWidth - 10f, 106f, statusBg)
            textPaint.color = if (isFullyCleared) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val statusText = if (isFullyCleared) "[✓] DUES CLEARED - NOC ISSUED" else "[!] SETTLEMENT DUES OUTSTANDING"
            canvas.drawText(statusText, startX + 16f, 100f, textPaint)

            // Student Particulars Box
            val particularsBg = Paint().apply { color = Color.parseColor("#F5F7FA") }
            canvas.drawRect(startX + 10f, 112f, startX + colWidth - 10f, 176f, particularsBg)

            textPaint.color = Color.parseColor("#1B365D")
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("STUDENT DEPARTURE RECORD", startX + 16f, 126f, textPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Name: ${student.studentName.uppercase()}", startX + 16f, 140f, textPaint)
            canvas.drawText("Father: ${student.fatherName.uppercase()}", startX + 16f, 154f, textPaint)
            canvas.drawText("Class: ${student.className} | Reg No: ${student.regNo.ifEmpty { "Pending" }}", startX + 16f, 168f, textPaint)

            // Settlement Header
            var yPos = 194f
            textPaint.color = Color.parseColor("#1B365D")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 8.5f
            canvas.drawText("FINAL CLEARANCE & DUES AUDIT", startX + 10f, yPos, textPaint)
            canvas.drawLine(startX + 10f, yPos + 4f, startX + colWidth - 10f, yPos + 4f, dividerPaint)

            // Table Header
            yPos += 18f
            textPaint.textSize = 8f
            textPaint.color = Color.DKGRAY
            canvas.drawText("Description", startX + 12f, yPos, textPaint)
            canvas.drawText("Amount (Rs.)", startX + colWidth - 75f, yPos, textPaint)
            canvas.drawLine(startX + 10f, yPos + 4f, startX + colWidth - 10f, yPos + 4f, dividerPaint)

            fun drawRow(label: String, amount: Double, isBold: Boolean = false, isGreen: Boolean = false, isRed: Boolean = false) {
                yPos += 18f
                textPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.color = when {
                    isGreen -> Color.parseColor("#2E7D32")
                    isRed -> Color.parseColor("#C62828")
                    else -> Color.BLACK
                }
                canvas.drawText(label, startX + 12f, yPos, textPaint)
                canvas.drawText("Rs. ${amount.toInt()}", startX + colWidth - 75f, yPos, textPaint)
                canvas.drawLine(startX + 10f, yPos + 4f, startX + colWidth - 10f, yPos + 4f, dividerPaint)
            }

            val arrearsLabel = if (unpaidMonthsDescription.isNotBlank()) {
                "Tuition Arrears (${unpaidMonthsDescription.take(22)})"
            } else {
                "Unpaid Tuition Arrears"
            }
            drawRow(arrearsLabel, monthlyArrears)
            if (annualDues > 0.0) drawRow("Annual Session Dues", annualDues)
            if (otherDues > 0.0) drawRow("Other Outstanding Charges", otherDues)
            if (leavingProcessingFee > 0.0) drawRow("SLC / Migration Processing", leavingProcessingFee)

            drawRow("TOTAL SETTLEMENT DUES", totalExitDues, isBold = true)
            drawRow("Paid at Final Clearance", amountReceivedNow, isBold = true, isGreen = true)
            drawRow("NET OUTSTANDING BALANCE", finalOutstanding, isBold = true, isRed = finalOutstanding > 0, isGreen = finalOutstanding <= 0)

            // Clearance Remarks Note
            yPos += 20f
            textPaint.textSize = 7.5f
            textPaint.color = Color.DKGRAY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (isFullyCleared) {
                canvas.drawText("• Accounts verified. No dues pending against student.", startX + 12f, yPos, textPaint)
                yPos += 12f
                canvas.drawText("• Student is cleared for School Leaving Certificate.", startX + 12f, yPos, textPaint)
            } else {
                canvas.drawText("• Please clear outstanding balance of Rs. ${finalOutstanding.toInt()}.", startX + 12f, yPos, textPaint)
                yPos += 12f
                canvas.drawText("• Bank: $bankName | A/C: $accountNumber", startX + 12f, yPos, textPaint)
            }
            if (remarks.isNotBlank()) {
                yPos += 12f
                canvas.drawText("Note: ${remarks.take(34)}", startX + 12f, yPos, textPaint)
            }

            // Signatures
            val sigY = 540f
            textPaint.color = Color.BLACK
            textPaint.textSize = 7.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawLine(startX + 12f, sigY, startX + 105f, sigY, dividerPaint)
            val closingSign = if (activeOperatorName.isNotBlank()) "Received: ${getReceivedByLabel()}" else "Accounts Officer"
            canvas.drawText(closingSign, startX + 14f, sigY + 12f, textPaint)

            canvas.drawLine(startX + 115f, sigY, startX + 175f, sigY, dividerPaint)
            canvas.drawText("Principal Stamp", startX + 117f, sigY + 12f, textPaint)

            canvas.drawLine(startX + colWidth - 90f, sigY, startX + colWidth - 12f, sigY, dividerPaint)
            canvas.drawText("Parent / Depositor", startX + colWidth - 85f, sigY + 12f, textPaint)

            // Vertical Cut lines between columns
            if (i < 2) {
                val cutX = startX + colWidth + 7f
                var cutY = 20f
                while (cutY < 570f) {
                    canvas.drawLine(cutX, cutY, cutX, cutY + 8f, dashPaint)
                    cutY += 16f
                }
                textPaint.color = Color.GRAY
                textPaint.textSize = 7f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.save()
                canvas.rotate(-90f, cutX, 300f)
                canvas.drawText("✂  CUT HERE  ✂", cutX - 25f, 300f, textPaint)
                canvas.restore()
            }
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "BLS_Closing_Challan_${student.regNo.ifBlank { student.id.toString() }}.pdf")
        return try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * Standard Pakistani 3-Copy Bank/School Challan on A4 Landscape (Bank Copy, School Copy, Student Copy).
     */
    fun exportThreePartChallanToPdf(
        context: Context,
        student: Student,
        monthYear: String,
        monthlyFee: Double,
        arrears: Double = 0.0,
        annualCharges: Double = 0.0,
        otherCharges: Double = 0.0,
        lateFeeFine: Double = 200.0,
        dueDateStr: String = "10th of this month",
        bankName: String = "Bank Account / BLS Cash Counter",
        accountNumber: String = "Main School Account"
    ): File? {
        val pdfDocument = PdfDocument()
        // A4 Landscape: 842 pt width x 595 pt height
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        val greenHeader = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }

        val cardBg = Paint().apply {
            color = Color.parseColor("#F8FAF8")
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val dashPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.8f
        }

        val copies = listOf("BANK / CASHIER COPY", "SCHOOL / OFFICE COPY", "STUDENT / PARENT COPY")
        val colWidth = 262f
        val startXs = listOf(14f, 290f, 566f)
        val totalWithinDueDate = monthlyFee + arrears + annualCharges + otherCharges
        val totalAfterDueDate = totalWithinDueDate + lateFeeFine

        val issueDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val challanNo = "BLS-${student.id}-${SimpleDateFormat("yyMM", Locale.getDefault()).format(Date())}"

        for (i in 0..2) {
            val startX = startXs[i]
            val copyTitle = copies[i]

            // Outer border box
            canvas.drawRect(startX, 14f, startX + colWidth, 580f, cardBg)
            canvas.drawRect(startX, 14f, startX + colWidth, 580f, borderPaint)

            // Header banner
            canvas.drawRect(startX, 14f, startX + colWidth, 64f, greenHeader)

            // School Logo
            var textX = startX + 12f
            try {
                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
                if (logoBitmap != null) {
                    val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 38, 38, true)
                    canvas.drawBitmap(scaledLogo, startX + 8f, 20f, null)
                    textX = startX + 50f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = 10.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BLENDED LEARNING SCHOOL", textX, 33f, textPaint)

            textPaint.textSize = 7.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Campuses & Academics Ledger", textX, 46f, textPaint)

            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(copyTitle, textX, 58f, textPaint)

            // Challan Meta Info
            textPaint.color = Color.DKGRAY
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Challan No: $challanNo", startX + 10f, 78f, textPaint)
            canvas.drawText("Issue Date: $issueDate", startX + 140f, 78f, textPaint)
            canvas.drawText("Due Date: $dueDateStr", startX + 10f, 92f, textPaint)

            // Student Particulars Box
            val particularsBg = Paint().apply { color = Color.parseColor("#E8F5E9") }
            canvas.drawRect(startX + 10f, 100f, startX + colWidth - 10f, 168f, particularsBg)

            textPaint.color = Color.parseColor("#0F52BA")
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("STUDENT PARTICULARS", startX + 16f, 114f, textPaint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Name: ${student.studentName.uppercase()}", startX + 16f, 128f, textPaint)
            canvas.drawText("Father: ${student.fatherName.uppercase()}", startX + 16f, 142f, textPaint)
            canvas.drawText("Class: ${student.className} | Reg No: ${student.regNo.ifEmpty { "Pending" }}", startX + 16f, 156f, textPaint)

            // Billing Month Banner
            textPaint.color = Color.parseColor("#0E3A14")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Billing Period: $monthYear", startX + 10f, 184f, textPaint)
            canvas.drawLine(startX + 10f, 190f, startX + colWidth - 10f, 190f, dividerPaint)

            // Table Header
            var yPos = 206f
            textPaint.textSize = 8.5f
            textPaint.color = Color.DKGRAY
            canvas.drawText("Particulars", startX + 12f, yPos, textPaint)
            canvas.drawText("Amount (Rs.)", startX + colWidth - 75f, yPos, textPaint)
            canvas.drawLine(startX + 10f, yPos + 6f, startX + colWidth - 10f, yPos + 6f, dividerPaint)

            // Items
            fun drawRow(label: String, amount: Double, isBold: Boolean = false, isGreen: Boolean = false, isRed: Boolean = false) {
                yPos += 20f
                textPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.color = when {
                    isGreen -> Color.parseColor("#0F52BA")
                    isRed -> Color.parseColor("#C62828")
                    else -> Color.BLACK
                }
                canvas.drawText(label, startX + 12f, yPos, textPaint)
                canvas.drawText("Rs. ${amount.toInt()}", startX + colWidth - 75f, yPos, textPaint)
                canvas.drawLine(startX + 10f, yPos + 4f, startX + colWidth - 10f, yPos + 4f, dividerPaint)
            }

            drawRow("Tuition Fee (Monthly)", monthlyFee)
            if (arrears > 0) drawRow("Previous Arrears", arrears, isRed = true)
            if (annualCharges > 0) drawRow("Annual Charges", annualCharges)
            if (otherCharges > 0) drawRow("Other Charges", otherCharges)

            drawRow("PAYABLE WITHIN DUE DATE", totalWithinDueDate, isBold = true, isGreen = true)
            drawRow("Late Fee Surcharge", lateFeeFine, isRed = true)
            drawRow("PAYABLE AFTER DUE DATE", totalAfterDueDate, isBold = true, isRed = true)

            // Bank Information Note
            yPos += 20f
            textPaint.textSize = 7.5f
            textPaint.color = Color.DKGRAY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Bank: $bankName", startX + 12f, yPos, textPaint)
            yPos += 12f
            canvas.drawText("A/C: $accountNumber", startX + 12f, yPos, textPaint)
            yPos += 14f
            canvas.drawText("* Fee once paid is non-refundable / non-transferable.", startX + 12f, yPos, textPaint)

            // Signatures
            val sigY = 540f
            textPaint.color = Color.BLACK
            textPaint.textSize = 7.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawLine(startX + 12f, sigY, startX + 115f, sigY, dividerPaint)
            val threePartSign = if (activeOperatorName.isNotBlank()) "Received: ${getReceivedByLabel()}" else "Officer / Bank Stamp"
            canvas.drawText(threePartSign, startX + 14f, sigY + 12f, textPaint)

            canvas.drawLine(startX + colWidth - 110f, sigY, startX + colWidth - 12f, sigY, dividerPaint)
            canvas.drawText("Depositor Signature", startX + colWidth - 105f, sigY + 12f, textPaint)

            // Vertical Cut lines between columns
            if (i < 2) {
                val cutX = startX + colWidth + 7f
                var cutY = 20f
                while (cutY < 570f) {
                    canvas.drawLine(cutX, cutY, cutX, cutY + 8f, dashPaint)
                    cutY += 16f
                }
                textPaint.color = Color.GRAY
                textPaint.textSize = 7f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.save()
                canvas.rotate(-90f, cutX, 300f)
                canvas.drawText("✂  CUT HERE  ✂", cutX - 25f, 300f, textPaint)
                canvas.restore()
            }
        }

        pdfDocument.finishPage(page)

        val fileName = "BLS_3Copy_Challan_${student.studentName.replace(" ", "_")}_${student.id}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun exportClassBulkChallansToPdf(
        context: Context,
        studentsWithFees: List<Triple<Student, Double, Double>>,
        className: String,
        monthYear: String,
        dueDateStr: String = "10th of this month",
        lateFeeFine: Double = 200.0,
        bankName: String = "Bank Account / BLS Cash Counter",
        accountNumber: String = "Main School Account"
    ): File? {
        val pdfDocument = PdfDocument()

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val greenHeader = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        val cardBg = Paint().apply {
            color = Color.parseColor("#F8FAF8")
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val dashPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.8f
        }

        val copies = listOf("BANK / CASHIER COPY", "SCHOOL / OFFICE COPY", "STUDENT / PARENT COPY")
        val colWidth = 262f
        val startXs = listOf(14f, 290f, 566f)
        val issueDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        var pageIndex = 1
        for ((student, monthlyFee, arrears) in studentsWithFees) {
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, pageIndex++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val totalWithinDueDate = monthlyFee + arrears + student.annualCharges + student.otherCharges
            val totalAfterDueDate = totalWithinDueDate + lateFeeFine
            val challanNo = "BLS-${student.id}-${SimpleDateFormat("yyMM", Locale.getDefault()).format(Date())}"

            for (i in 0..2) {
                val startX = startXs[i]
                val copyTitle = copies[i]

                canvas.drawRect(startX, 14f, startX + colWidth, 580f, cardBg)
                canvas.drawRect(startX, 14f, startX + colWidth, 580f, borderPaint)

                canvas.drawRect(startX, 14f, startX + colWidth, 64f, greenHeader)

                var textX = startX + 12f
                try {
                    val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bls_logo)
                    if (logoBitmap != null) {
                        val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 38, 38, true)
                        canvas.drawBitmap(scaledLogo, startX + 8f, 20f, null)
                        textX = startX + 50f
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                textPaint.color = Color.WHITE
                textPaint.textSize = 10f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("BLENDED LEARNING SCHOOL", textX, 33f, textPaint)

                textPaint.textSize = 7f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Fee Voucher - $monthYear", textX, 46f, textPaint)

                val copyBg = Paint().apply {
                    color = Color.parseColor("#E8F5E9")
                    isAntiAlias = true
                }
                canvas.drawRect(startX, 64f, startX + colWidth, 80f, copyBg)
                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.textSize = 7.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(copyTitle, startX + 12f, 75f, textPaint)

                var yPos = 96f
                textPaint.color = Color.BLACK
                textPaint.textSize = 8f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Challan No: $challanNo", startX + 12f, yPos, textPaint)
                canvas.drawText("Issue: $issueDate", startX + colWidth - 85f, yPos, textPaint)

                yPos += 14f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.color = Color.parseColor("#B71C1C")
                canvas.drawText("Due Date: $dueDateStr", startX + 12f, yPos, textPaint)

                yPos += 6f
                canvas.drawLine(startX + 12f, yPos, startX + colWidth - 12f, yPos, dividerPaint)

                yPos += 16f
                textPaint.color = Color.BLACK
                textPaint.textSize = 8.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Student: ${student.studentName.uppercase()}", startX + 12f, yPos, textPaint)

                yPos += 13f
                textPaint.textSize = 8f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Father: ${student.fatherName}", startX + 12f, yPos, textPaint)

                yPos += 13f
                val regText = if (student.regNo.isNotBlank()) " | Reg: ${student.regNo}" else ""
                canvas.drawText("Class: ${student.className}$regText", startX + 12f, yPos, textPaint)

                yPos += 8f
                canvas.drawLine(startX + 12f, yPos, startX + colWidth - 12f, yPos, dividerPaint)

                yPos += 16f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("PARTICULARS", startX + 12f, yPos, textPaint)
                canvas.drawText("AMOUNT (PKR)", startX + colWidth - 80f, yPos, textPaint)

                yPos += 6f
                canvas.drawLine(startX + 12f, yPos, startX + colWidth - 12f, yPos, dividerPaint)

                fun drawItem(title: String, amount: Double) {
                    if (amount <= 0.0) return
                    yPos += 15f
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textPaint.color = Color.BLACK
                    canvas.drawText(title, startX + 12f, yPos, textPaint)
                    val amtStr = String.format(Locale.US, "%,.0f", amount)
                    canvas.drawText(amtStr, startX + colWidth - 50f, yPos, textPaint)
                }

                drawItem("Tuition Fee ($monthYear)", monthlyFee)
                if (arrears > 0) {
                    drawItem("Previous Arrears", arrears)
                }
                if (student.annualCharges > 0) {
                    drawItem("Annual Charges", student.annualCharges)
                }
                if (student.otherCharges > 0) {
                    drawItem("Other Charges", student.otherCharges)
                }

                yPos += 18f
                val totalBoxPaint = Paint().apply {
                    color = Color.parseColor("#E8F5E9")
                    isAntiAlias = true
                }
                canvas.drawRect(startX + 8f, yPos - 12f, startX + colWidth - 8f, yPos + 18f, totalBoxPaint)

                textPaint.color = Color.parseColor("#0F52BA")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textSize = 8.5f
                canvas.drawText("Payable By Due Date:", startX + 12f, yPos + 2f, textPaint)
                val totalStr = "Rs. ${String.format(Locale.US, "%,.0f", totalWithinDueDate)}"
                canvas.drawText(totalStr, startX + colWidth - 75f, yPos + 2f, textPaint)

                yPos += 28f
                textPaint.color = Color.parseColor("#B71C1C")
                textPaint.textSize = 8f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Late Fee Surcharge: Rs. ${lateFeeFine.toInt()}", startX + 12f, yPos, textPaint)

                yPos += 14f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("After Due Date: Rs. ${String.format(Locale.US, "%,.0f", totalAfterDueDate)}", startX + 12f, yPos, textPaint)

                yPos += 20f
                textPaint.textSize = 7.5f
                textPaint.color = Color.DKGRAY
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Bank: $bankName", startX + 12f, yPos, textPaint)
                yPos += 12f
                canvas.drawText("A/C: $accountNumber", startX + 12f, yPos, textPaint)
                yPos += 14f
                canvas.drawText("* Fee once paid is non-refundable / non-transferable.", startX + 12f, yPos, textPaint)

                val sigY = 540f
                textPaint.color = Color.BLACK
                textPaint.textSize = 7.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawLine(startX + 12f, sigY, startX + 115f, sigY, dividerPaint)
                val bulkChallanSign = if (activeOperatorName.isNotBlank()) "Received: ${getReceivedByLabel()}" else "Officer / Bank Stamp"
                canvas.drawText(bulkChallanSign, startX + 14f, sigY + 12f, textPaint)

                canvas.drawLine(startX + colWidth - 110f, sigY, startX + colWidth - 12f, sigY, dividerPaint)
                canvas.drawText("Depositor Signature", startX + colWidth - 105f, sigY + 12f, textPaint)

                if (i < 2) {
                    val cutX = startX + colWidth + 7f
                    var cutY = 20f
                    while (cutY < 570f) {
                        canvas.drawLine(cutX, cutY, cutX, cutY + 8f, dashPaint)
                        cutY += 16f
                    }
                    textPaint.color = Color.GRAY
                    textPaint.textSize = 7f
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.save()
                    canvas.rotate(-90f, cutX, 300f)
                    canvas.drawText("✂  CUT HERE  ✂", cutX - 25f, 300f, textPaint)
                    canvas.restore()
                }
            }
            pdfDocument.finishPage(page)
        }

        val safeClassName = className.replace(" ", "_").replace("/", "_")
        val fileName = "BLS_Bulk_Challans_${safeClassName}_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * 8. Class-wise Fee Collection & Recovery Audit Report (Landscape A4: 842 x 595)
     */
    data class ClassRecoveryAuditRow(
        val className: String,
        val totalEnrolled: Int,
        val targetAmount: Double,
        val collectedAmount: Double,
        val pendingAmount: Double,
        val recoveryPercentage: Double
    )

    fun exportClassWiseFeeRecoveryAuditToPdf(
        context: Context,
        students: List<Student>,
        transactions: List<Transaction>,
        monthYear: String
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply { isAntiAlias = true }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.6f
        }

        val activeStudents = students.filter { it.status == DomainConstants.STATUS_ACTIVE }
        val classGroups = activeStudents.groupBy { it.className }
            .toList()
            .sortedBy { it.first }

        val rows = classGroups.map { (clsName, sList) ->
            val totalEnrolled = sList.size
            val targetAmount = sList.sumOf { DomainConstants.calculateDiscountedFee(it.monthlyFee, it.discountType) }
            val classStudentIds = sList.map { it.id }.toSet()
            val collectedAmount = transactions
                .filter { it.isIncome && it.monthOfFee == monthYear && classStudentIds.contains(it.studentId) }
                .sumOf { it.amount }
            val pending = (targetAmount - collectedAmount).coerceAtLeast(0.0)
            val recoveryPct = if (targetAmount > 0) (collectedAmount / targetAmount) * 100.0 else 0.0
            ClassRecoveryAuditRow(clsName, totalEnrolled, targetAmount, collectedAmount, pending, recoveryPct)
        }

        val totalEnrolled = rows.sumOf { it.totalEnrolled }
        val totalTarget = rows.sumOf { it.targetAmount }
        val totalCollected = rows.sumOf { it.collectedAmount }
        val totalPending = rows.sumOf { it.pendingAmount }
        val overallRecovery = if (totalTarget > 0) (totalCollected / totalTarget) * 100.0 else 0.0

        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Header
        canvas.drawRect(0f, 0f, 842f, 80f, headerPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 18f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", 35f, 32f, textPaint)

        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Class-Wise Fee Collection & Recovery Audit Report - Cycle: $monthYear", 35f, 52f, textPaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val curTimestamp = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Audit Generated: $curTimestamp | Active Enrolled: $totalEnrolled Students", 35f, 68f, textPaint)

        // KPI Summary Box
        val kpiPaint = Paint().apply {
            color = Color.parseColor("#E8F5E9")
            isAntiAlias = true
        }
        canvas.drawRoundRect(30f, 92f, 812f, 136f, 8f, 8f, kpiPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MONTHLY TARGET: Rs. ${String.format(Locale.US, "%,.0f", totalTarget)}", 50f, 118f, textPaint)

        textPaint.color = Color.parseColor("#2E7D32")
        canvas.drawText("COLLECTED: Rs. ${String.format(Locale.US, "%,.0f", totalCollected)}", 260f, 118f, textPaint)

        textPaint.color = Color.parseColor("#C62828")
        canvas.drawText("OVERDUE / ARREARS: Rs. ${String.format(Locale.US, "%,.0f", totalPending)}", 470f, 118f, textPaint)

        textPaint.color = if (overallRecovery >= 80) Color.parseColor("#0F52BA") else Color.parseColor("#D4AF37")
        canvas.drawText("RECOVERY: ${String.format(Locale.US, "%.1f", overallRecovery)}%", 700f, 118f, textPaint)

        // Table Header
        val thPaint = Paint().apply {
            color = Color.parseColor("#2E7D32")
            isAntiAlias = true
        }
        canvas.drawRect(30f, 146f, 812f, 168f, thPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("S#", 38f, 160f, textPaint)
        canvas.drawText("Class Group", 70f, 160f, textPaint)
        canvas.drawText("Enrolled", 220f, 160f, textPaint)
        canvas.drawText("Target (Rs)", 310f, 160f, textPaint)
        canvas.drawText("Collected (Rs)", 430f, 160f, textPaint)
        canvas.drawText("Overdue (Rs)", 550f, 160f, textPaint)
        canvas.drawText("Recovery %", 670f, 160f, textPaint)
        canvas.drawText("Audit Status", 750f, 160f, textPaint)

        var y = 186f
        var sNo = 1
        val altRowPaint = Paint().apply { color = Color.parseColor("#FAFAFA") }

        rows.forEach { row ->
            if (sNo % 2 == 0) {
                canvas.drawRect(30f, y - 13f, 812f, y + 5f, altRowPaint)
            }

            textPaint.color = Color.BLACK
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f

            canvas.drawText(sNo.toString(), 38f, y, textPaint)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(row.className, 70f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("${row.totalEnrolled} students", 220f, y, textPaint)
            canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", row.targetAmount)}", 310f, y, textPaint)

            textPaint.color = Color.parseColor("#2E7D32")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", row.collectedAmount)}", 430f, y, textPaint)

            textPaint.color = if (row.pendingAmount > 0) Color.parseColor("#C62828") else Color.GRAY
            canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", row.pendingAmount)}", 550f, y, textPaint)

            textPaint.color = if (row.recoveryPercentage >= 80) Color.parseColor("#2E7D32") else Color.parseColor("#D4AF37")
            canvas.drawText("${String.format(Locale.US, "%.1f", row.recoveryPercentage)}%", 670f, y, textPaint)

            val statusText = when {
                row.recoveryPercentage >= 95 -> "Excellent"
                row.recoveryPercentage >= 75 -> "Satisfactory"
                row.recoveryPercentage >= 50 -> "Needs Follow-up"
                else -> "Critical Action"
            }
            textPaint.color = if (row.recoveryPercentage >= 75) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
            canvas.drawText(statusText, 750f, y, textPaint)

            canvas.drawLine(30f, y + 6f, 812f, y + 6f, linePaint)
            y += 20f
            sNo++
        }

        // Totals Row
        val totalBgPaint = Paint().apply {
            color = Color.parseColor("#E8F5E9")
            isAntiAlias = true
        }
        canvas.drawRect(30f, y - 10f, 812f, y + 14f, totalBgPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CAMPUS TOTAL:", 70f, y + 5f, textPaint)
        canvas.drawText("$totalEnrolled", 220f, y + 5f, textPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", totalTarget)}", 310f, y + 5f, textPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", totalCollected)}", 430f, y + 5f, textPaint)
        textPaint.color = Color.parseColor("#C62828")
        canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", totalPending)}", 550f, y + 5f, textPaint)
        textPaint.color = Color.parseColor("#0F52BA")
        canvas.drawText("${String.format(Locale.US, "%.1f", overallRecovery)}%", 670f, y + 5f, textPaint)

        // Signatures Section
        val sigY = 540f
        val sigLinePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.8f
        }
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        canvas.drawLine(40f, sigY, 190f, sigY, sigLinePaint)
        val recoveryPreparedBy = if (activeOperatorName.isNotBlank()) "Prepared by: ${getReceivedByLabel()}" else "Prepared by (Accountant)"
        canvas.drawText(recoveryPreparedBy, 45f, sigY + 14f, textPaint)

        canvas.drawLine(330f, sigY, 480f, sigY, sigLinePaint)
        canvas.drawText("Verified by (Admin Desk)", 350f, sigY + 14f, textPaint)

        canvas.drawLine(640f, sigY, 790f, sigY, sigLinePaint)
        canvas.drawText("Approved by (Principal)", 660f, sigY + 14f, textPaint)

        pdfDocument.finishPage(page)

        val fileName = "BLS_Class_Fee_Recovery_Audit_${monthYear.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * 9. Defaulters Master List with Parent Contacts & Dues (Portrait A4: 595 x 842)
     */
    data class DefaulterRowData(
        val student: Student,
        val monthlyFee: Double,
        val arrears: Double,
        val totalDues: Double,
        val contactNumber: String
    )

    fun exportDefaultersMasterListToPdf(
        context: Context,
        students: List<Student>,
        transactions: List<Transaction>,
        classFilter: String = "All Classes",
        currentMonth: String
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply { isAntiAlias = true }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#B71C1C")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 0.5f
        }

        val activeList = students.filter { it.status == DomainConstants.STATUS_ACTIVE }
        val scopedStudents = if (classFilter != "All Classes") {
            activeList.filter { it.className == classFilter }
        } else {
            activeList
        }

        val defaulters = mutableListOf<DefaulterRowData>()
        scopedStudents.forEach { s ->
            val monthly = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
            val sTx = transactions.filter { it.studentId == s.id }
            val arrears = DomainConstants.calculateMultiMonthArrears(s.admissionDate, monthly, sTx)
            val paidThisMonth = sTx.any { it.isIncome && it.monthOfFee == currentMonth }
            val currentDue = if (paidThisMonth) 0.0 else monthly
            val total = arrears + currentDue
            if (total > 0) {
                defaulters.add(DefaulterRowData(s, monthly, arrears, total, s.contactNumber))
            }
        }
        defaulters.sortByDescending { it.totalDues }

        val totalDefaultersCount = defaulters.size
        val totalOverdueSum = defaulters.sumOf { it.totalDues }

        val rowsPerPage = 22
        val totalPages = if (defaulters.isEmpty()) 1 else Math.max(1, Math.ceil(defaulters.size.toDouble() / rowsPerPage).toInt())

        var itemIdx = 0
        for (pageIdx in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIdx).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Header
            canvas.drawRect(0f, 0f, 595f, 80f, headerPaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 16f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", 30f, 30f, textPaint)

            textPaint.textSize = 11.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Defaulters Master Audit Sheet - Class: $classFilter ($currentMonth)", 30f, 50f, textPaint)

            textPaint.textSize = 9f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("Defaulters: $totalDefaultersCount | Total Outstanding Debt: Rs. ${String.format(Locale.US, "%,.0f", totalOverdueSum)}", 30f, 68f, textPaint)

            // Table Header
            val thY = 96f
            val thPaint = Paint().apply {
                color = Color.parseColor("#C62828")
                isAntiAlias = true
            }
            canvas.drawRect(25f, thY, 570f, thY + 20f, thPaint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            canvas.drawText("S#", 30f, thY + 13f, textPaint)
            canvas.drawText("Student & Father Name", 55f, thY + 13f, textPaint)
            canvas.drawText("Class", 215f, thY + 13f, textPaint)
            canvas.drawText("Contact Phone", 280f, thY + 13f, textPaint)
            canvas.drawText("Monthly", 370f, thY + 13f, textPaint)
            canvas.drawText("Overdue Dues", 435f, thY + 13f, textPaint)
            canvas.drawText("Action / Remarks", 500f, thY + 13f, textPaint)

            var y = thY + 32f
            val endIdx = Math.min(itemIdx + rowsPerPage, defaulters.size)

            while (itemIdx < endIdx) {
                val row = defaulters[itemIdx]
                textPaint.color = Color.BLACK
                textPaint.textSize = 8f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                canvas.drawText("${itemIdx + 1}", 30f, y, textPaint)

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val sName = if (row.student.studentName.length > 20) row.student.studentName.take(18) + ".." else row.student.studentName
                canvas.drawText(sName, 55f, y - 2f, textPaint)

                textPaint.textSize = 7f
                textPaint.color = Color.DKGRAY
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("F: ${row.student.fatherName}", 55f, y + 8f, textPaint)

                textPaint.textSize = 8f
                textPaint.color = Color.BLACK
                canvas.drawText(row.student.className, 215f, y, textPaint)
                canvas.drawText(row.contactNumber, 280f, y, textPaint)

                canvas.drawText("Rs. ${row.monthlyFee.toInt()}", 370f, y, textPaint)

                textPaint.color = Color.parseColor("#C62828")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", row.totalDues)}", 435f, y, textPaint)

                // Dotted line for teacher remarks
                canvas.drawLine(500f, y, 565f, y, linePaint)

                canvas.drawLine(25f, y + 12f, 570f, y + 12f, linePaint)
                y += 24f
                itemIdx++
            }

            // Footer
            textPaint.color = Color.GRAY
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Page $pageIdx of $totalPages | Confidential Defaulters Register | Blended Learning School", 120f, 825f, textPaint)

            pdfDocument.finishPage(page)
        }

        val fileName = "BLS_Defaulters_${classFilter.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * 10. Staff Salary Disbursement Sheet (Portrait A4: 595 x 842)
     */
    fun exportStaffSalaryDisbursementSheetToPdf(
        context: Context,
        transactions: List<Transaction>,
        monthYear: String
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply { isAntiAlias = true }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F52BA")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 0.5f
        }

        val salaryTx = transactions.filter {
            !it.isIncome && (it.category == DomainConstants.CAT_TEACHERS_PAY || 
                             it.category == DomainConstants.CAT_STAFF_SALARY || 
                             it.description.contains("Salary", ignoreCase = true))
        }

        val totalStaffPaid = salaryTx.size
        val totalDisbursed = salaryTx.sumOf { it.amount }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Header
        canvas.drawRect(0f, 0f, 595f, 80f, headerPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 16f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", 30f, 30f, textPaint)

        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Staff & Teachers Salary Disbursement Register - $monthYear", 30f, 52f, textPaint)

        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Disbursements: $totalStaffPaid Staff | Total Disbursed: Rs. ${String.format(Locale.US, "%,.0f", totalDisbursed)}", 30f, 68f, textPaint)

        // Table Header
        val thY = 96f
        val thPaint = Paint().apply {
            color = Color.parseColor("#2E7D32")
            isAntiAlias = true
        }
        canvas.drawRect(25f, thY, 570f, thY + 20f, thPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("S#", 30f, thY + 13f, textPaint)
        canvas.drawText("Voucher No", 55f, thY + 13f, textPaint)
        canvas.drawText("Staff / Teacher Name & Notes", 160f, thY + 13f, textPaint)
        canvas.drawText("Mode", 355f, thY + 13f, textPaint)
        canvas.drawText("Amount (Rs)", 405f, thY + 13f, textPaint)
        canvas.drawText("Staff Signature", 490f, thY + 13f, textPaint)

        var y = thY + 32f
        val txSdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        salaryTx.forEachIndexed { idx, tx ->
            textPaint.color = Color.BLACK
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            canvas.drawText("${idx + 1}", 30f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val vNo = if (tx.voucherNo.isNotBlank()) tx.voucherNo else "EXP-${tx.id}"
            canvas.drawText(vNo, 55f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val memo = if (tx.description.length > 32) tx.description.take(30) + ".." else tx.description
            canvas.drawText(memo, 160f, y - 2f, textPaint)

            textPaint.textSize = 7f
            textPaint.color = Color.DKGRAY
            canvas.drawText("Date: ${txSdf.format(Date(tx.date))}", 160f, y + 8f, textPaint)

            textPaint.textSize = 8f
            textPaint.color = Color.BLACK
            canvas.drawText(tx.paymentMode, 355f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = Color.parseColor("#C62828")
            canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", tx.amount)}", 405f, y, textPaint)

            // Signature line
            canvas.drawLine(490f, y, 560f, y, linePaint)

            canvas.drawLine(25f, y + 12f, 570f, y + 12f, linePaint)
            y += 24f
        }

        // Totals Row
        val totalBgPaint = Paint().apply { color = Color.parseColor("#E8F5E9") }
        canvas.drawRect(25f, y - 8f, 570f, y + 14f, totalBgPaint)

        textPaint.color = Color.parseColor("#0F52BA")
        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL SALARY DISBURSEMENTS:", 55f, y + 6f, textPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", totalDisbursed)}", 405f, y + 6f, textPaint)

        // Signatures
        val sigY = 770f
        val sigLinePaint = Paint().apply { color = Color.GRAY; strokeWidth = 0.8f }
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        canvas.drawLine(50f, sigY, 200f, sigY, sigLinePaint)
        val salaryDisbursedBy = if (activeOperatorName.isNotBlank()) "Prepared: ${getReceivedByLabel()}" else "Prepared by (Accountant)"
        canvas.drawText(salaryDisbursedBy, 55f, sigY + 14f, textPaint)

        canvas.drawLine(390f, sigY, 540f, sigY, sigLinePaint)
        canvas.drawText("Approved by (Principal)", 410f, sigY + 14f, textPaint)

        pdfDocument.finishPage(page)

        val fileName = "BLS_Staff_Payroll_${monthYear.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * 11. Custom Date Range General Ledger (Portrait A4: 595 x 842)
     */
    fun exportDateRangeLedgerToPdf(
        context: Context,
        transactions: List<Transaction>,
        startDate: Long,
        endDate: Long,
        paymentModeFilter: String = "All",
        studentsMap: Map<Int, Student> = emptyMap()
    ): File? {
        val sDateSdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val dateTitle = "${sDateSdf.format(Date(startDate))} to ${sDateSdf.format(Date(endDate))}"

        val filtered = transactions.filter { tx ->
            tx.date in startDate..endDate && (paymentModeFilter == "All" || tx.paymentMode.equals(paymentModeFilter, ignoreCase = true))
        }

        val totalIncome = filtered.filter { it.isIncome }.sumOf { it.amount }
        val totalExpense = filtered.filter { !it.isIncome }.sumOf { it.amount }

        return exportTransactionsToPdf(
            context = context,
            title = "Ledger: $dateTitle (${if (paymentModeFilter != "All") paymentModeFilter else "All Modes"})",
            transactions = filtered,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            studentsMap = studentsMap
        )
    }

    /**
     * 12. Category-wise Expense Breakdown Report (Portrait A4: 595 x 842)
     */
    fun exportExpenseCategoryBreakdownToPdf(
        context: Context,
        transactions: List<Transaction>,
        periodTitle: String
    ): File? {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply { isAntiAlias = true }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#B71C1C")
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 0.5f
        }

        val expenses = transactions.filter { !it.isIncome }
        val totalExpenseSum = expenses.sumOf { it.amount }

        val categoryGroups = expenses.groupBy { it.category }
            .map { (cat, txs) ->
                val sum = txs.sumOf { it.amount }
                val pct = if (totalExpenseSum > 0) (sum / totalExpenseSum) * 100.0 else 0.0
                Triple(cat, txs.size, Pair(sum, pct))
            }
            .sortedByDescending { it.third.first }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Header
        canvas.drawRect(0f, 0f, 595f, 80f, headerPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 16f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLENDED LEARNING SCHOOL (BLS)", 30f, 30f, textPaint)

        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Category-wise Expense Breakdown - $periodTitle", 30f, 52f, textPaint)

        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Total Expenditures: Rs. ${String.format(Locale.US, "%,.0f", totalExpenseSum)} across ${expenses.size} items", 30f, 68f, textPaint)

        // Table Header
        val thY = 96f
        val thPaint = Paint().apply {
            color = Color.parseColor("#C62828")
            isAntiAlias = true
        }
        canvas.drawRect(25f, thY, 570f, thY + 20f, thPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("S#", 30f, thY + 13f, textPaint)
        canvas.drawText("Expense Category Head", 60f, thY + 13f, textPaint)
        canvas.drawText("Vouchers", 230f, thY + 13f, textPaint)
        canvas.drawText("Total Spent (Rs)", 310f, thY + 13f, textPaint)
        canvas.drawText("Budget Share (%)", 420f, thY + 13f, textPaint)
        canvas.drawText("Distribution Bar", 495f, thY + 13f, textPaint)

        var y = thY + 32f
        val barBgPaint = Paint().apply { color = Color.parseColor("#EEEEEE") }
        val barFillPaint = Paint().apply { color = Color.parseColor("#C62828") }

        categoryGroups.forEachIndexed { idx, (cat, count, sumPct) ->
            textPaint.color = Color.BLACK
            textPaint.textSize = 8.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            canvas.drawText("${idx + 1}", 30f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(cat, 60f, y, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("$count items", 230f, y, textPaint)

            textPaint.color = Color.parseColor("#C62828")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", sumPct.first)}", 310f, y, textPaint)

            textPaint.color = Color.DKGRAY
            canvas.drawText("${String.format(Locale.US, "%.1f", sumPct.second)}%", 420f, y, textPaint)

            // Visual percentage bar
            val barWidth = 65f
            val filledWidth = (sumPct.second.toFloat() / 100f * barWidth).coerceIn(2f, barWidth)
            canvas.drawRoundRect(495f, y - 9f, 495f + barWidth, y - 1f, 3f, 3f, barBgPaint)
            canvas.drawRoundRect(495f, y - 9f, 495f + filledWidth, y - 1f, 3f, 3f, barFillPaint)

            canvas.drawLine(25f, y + 10f, 570f, y + 10f, linePaint)
            y += 24f
        }

        // Totals Row
        val totalBgPaint = Paint().apply { color = Color.parseColor("#FFEBEE") }
        canvas.drawRect(25f, y - 8f, 570f, y + 14f, totalBgPaint)

        textPaint.color = Color.parseColor("#B71C1C")
        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL SCHOOL EXPENSE:", 60f, y + 6f, textPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%,.0f", totalExpenseSum)}", 310f, y + 6f, textPaint)
        canvas.drawText("100.0%", 420f, y + 6f, textPaint)

        // Signatures
        val sigY = 770f
        val sigLinePaint = Paint().apply { color = Color.GRAY; strokeWidth = 0.8f }
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        canvas.drawLine(50f, sigY, 200f, sigY, sigLinePaint)
        val expenseBreakdownPreparedBy = if (activeOperatorName.isNotBlank()) "Prepared: ${getReceivedByLabel()}" else "Prepared by (Accountant)"
        canvas.drawText(expenseBreakdownPreparedBy, 55f, sigY + 14f, textPaint)

        canvas.drawLine(390f, sigY, 540f, sigY, sigLinePaint)
        canvas.drawText("Approved by (Principal)", 410f, sigY + 14f, textPaint)

        pdfDocument.finishPage(page)

        val fileName = "BLS_Expense_Breakdown_${periodTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val cached = File(context.cacheDir, fileName)
        return try {
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * 13. Export Class Recovery to CSV
     */
    fun exportClassRecoveryToCsv(
        context: Context,
        students: List<Student>,
        transactions: List<Transaction>,
        monthYear: String
    ): File? {
        val activeStudents = students.filter { it.status == DomainConstants.STATUS_ACTIVE }
        val classGroups = activeStudents.groupBy { it.className }.toList().sortedBy { it.first }

        val sb = StringBuilder()
        sb.append("BLENDED LEARNING SCHOOL (BLS) - Class-Wise Fee Recovery Audit\n")
        sb.append("Cycle: $monthYear,Generated: ${SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")
        sb.append("S#,Class Name,Enrolled,Target Expected (Rs),Collected (Rs),Overdue Dues (Rs),Recovery %\n")

        var totalEnrolled = 0
        var totalTarget = 0.0
        var totalCollected = 0.0
        var totalPending = 0.0

        classGroups.forEachIndexed { idx, (cls, sList) ->
            val enrolled = sList.size
            val target = sList.sumOf { DomainConstants.calculateDiscountedFee(it.monthlyFee, it.discountType) }
            val classIds = sList.map { it.id }.toSet()
            val collected = transactions.filter { it.isIncome && it.monthOfFee == monthYear && classIds.contains(it.studentId) }.sumOf { it.amount }
            val pending = (target - collected).coerceAtLeast(0.0)
            val pct = if (target > 0) (collected / target) * 100.0 else 0.0

            totalEnrolled += enrolled
            totalTarget += target
            totalCollected += collected
            totalPending += pending

            sb.append("${idx + 1},\"$cls\",$enrolled,$target,$collected,$pending,${String.format(Locale.US, "%.1f", pct)}%\n")
        }

        val overallPct = if (totalTarget > 0) (totalCollected / totalTarget) * 100.0 else 0.0
        sb.append("\nTOTALS,All Classes,$totalEnrolled,$totalTarget,$totalCollected,$totalPending,${String.format(Locale.US, "%.1f", overallPct)}%\n")

        val fileName = "BLS_Class_Recovery_${monthYear.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val cached = File(context.cacheDir, fileName)
        return try {
            cached.writeText(sb.toString())
            cached
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 14. Export Monthly 3-Part Fee Challan PDF (Landscape A4: School Copy | Accounts Copy | Parent Copy)
     * Due Date strictly set to 08th, with Rs. 300 late fee fine after 08th.
     */
    fun exportMonthly3PartFeeChallanPdf(
        context: Context,
        students: List<Student>,
        monthYear: String,
        previousDuesMap: Map<Int, Double> = emptyMap()
    ): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(200, 200, 200)
        }
        val dashedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(160, 160, 160)
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }

        val pageWidth = 842 // A4 Landscape width
        val pageHeight = 595 // A4 Landscape height
        val colWidth = 260
        val colGap = 15
        val leftMargin = 20

        try {
            students.forEachIndexed { pageIdx, student ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val discountedTuition = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
                val annualOther = student.annualCharges + student.otherCharges
                val arrears = previousDuesMap[student.id] ?: 0.0
                val totalWithinDue = discountedTuition + annualOther + arrears
                val lateFine = 300.0
                val totalAfterDue = totalWithinDue + lateFine

                val copies = listOf(
                    Pair("SCHOOL COPY", Color.rgb(27, 94, 32)), // Dark Green
                    Pair("ACCOUNTS COPY", Color.rgb(13, 71, 161)), // Dark Blue
                    Pair("STUDENT / PARENT COPY", Color.rgb(183, 28, 28)) // Dark Red
                )

                for (cIdx in 0..2) {
                    val colX = leftMargin + cIdx * (colWidth + colGap)
                    val (copyLabel, copyColor) = copies[cIdx]

                    // Column Border
                    strokePaint.color = Color.rgb(210, 210, 210)
                    canvas.drawRoundRect(
                        colX.toFloat(), 15f, (colX + colWidth).toFloat(), (pageHeight - 20).toFloat(),
                        8f, 8f, strokePaint
                    )

                    // Header Banner
                    paint.color = copyColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        (colX + 1).toFloat(), 16f, (colX + colWidth - 1).toFloat(), 48f,
                        8f, 8f, paint
                    )

                    // School Title
                    paint.color = Color.WHITE
                    paint.textSize = 10.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("BISMILLAH LOGICAL SCHOOL", (colX + colWidth / 2).toFloat(), 32f, paint)

                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("Affiliated & Registered Higher Secondary System", (colX + colWidth / 2).toFloat(), 43f, paint)

                    // Copy Badge
                    paint.color = Color.rgb(245, 245, 245)
                    canvas.drawRect((colX + 5).toFloat(), 53f, (colX + colWidth - 5).toFloat(), 70f, paint)
                    paint.color = copyColor
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(copyLabel, (colX + colWidth / 2).toFloat(), 65f, paint)

                    // Challan Meta
                    paint.textAlign = Paint.Align.LEFT
                    paint.textSize = 7.5f
                    paint.color = Color.rgb(80, 80, 80)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("Challan #: CHL-${student.regNo.ifBlank { student.id.toString() }}", (colX + 10).toFloat(), 84f, paint)
                    canvas.drawText("Billing Month: $monthYear", (colX + 10).toFloat(), 96f, paint)

                    // Due Date - HIGHLIGHTED RED
                    paint.color = Color.rgb(183, 28, 28)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("DUE DATE: 08th $monthYear", (colX + 10).toFloat(), 110f, paint)

                    // Student Details Box
                    paint.color = Color.rgb(240, 244, 248)
                    canvas.drawRoundRect((colX + 8).toFloat(), 118f, (colX + colWidth - 8).toFloat(), 175f, 4f, 4f, paint)

                    paint.color = Color.rgb(30, 30, 30)
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Student: ${student.studentName.take(22)}", (colX + 12).toFloat(), 132f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("Father: ${student.fatherName.take(22)}", (colX + 12).toFloat(), 145f, paint)
                    canvas.drawText("Class: ${student.className}", (colX + 12).toFloat(), 158f, paint)
                    canvas.drawText("Roll No: ${student.regNo.ifBlank { "N/A" }}", (colX + 130).toFloat(), 158f, paint)
                    canvas.drawText("Contact: ${student.contactNumber}", (colX + 12).toFloat(), 170f, paint)

                    // Particulars Table Header
                    paint.color = Color.rgb(230, 230, 230)
                    canvas.drawRect((colX + 8).toFloat(), 182f, (colX + colWidth - 8).toFloat(), 198f, paint)
                    paint.color = Color.rgb(20, 20, 20)
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Fee Particulars", (colX + 12).toFloat(), 193f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Amount (Rs)", (colX + colWidth - 12).toFloat(), 193f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    var currY = 212f
                    fun drawRow(label: String, valStr: String, isBold: Boolean = false, textColor: Int = Color.rgb(40, 40, 40)) {
                        paint.textSize = 7.5f
                        paint.color = textColor
                        paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        canvas.drawText(label, (colX + 12).toFloat(), currY, paint)
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(valStr, (colX + colWidth - 12).toFloat(), currY, paint)
                        paint.textAlign = Paint.Align.LEFT
                        currY += 14f
                    }

                    drawRow("Monthly Tuition Fee", "${student.monthlyFee.toInt()}")
                    if (student.discountType.isNotBlank() && student.discountType != "None") {
                        val discAmt = student.monthlyFee - discountedTuition
                        drawRow("Discount (${student.discountType})", "-${discAmt.toInt()}", false, Color.rgb(46, 125, 50))
                    }
                    if (annualOther > 0) {
                        drawRow("Annual & Other Charges", "${annualOther.toInt()}")
                    }
                    if (arrears > 0) {
                        drawRow("Previous Arrears", "${arrears.toInt()}", true, Color.rgb(198, 40, 40))
                    }

                    // Divider line
                    strokePaint.color = Color.rgb(180, 180, 180)
                    canvas.drawLine((colX + 8).toFloat(), currY, (colX + colWidth - 8).toFloat(), currY, strokePaint)
                    currY += 14f

                    // Payable within due date (08th)
                    paint.color = Color.rgb(232, 245, 233) // Light Green
                    canvas.drawRect((colX + 8).toFloat(), currY - 11f, (colX + colWidth - 8).toFloat(), currY + 6f, paint)
                    drawRow("Payable By 08th", "Rs. ${totalWithinDue.toInt()}", true, Color.rgb(27, 94, 32))

                    // Late Fee Fine (Rs. 300)
                    paint.color = Color.rgb(255, 235, 238) // Light Red
                    canvas.drawRect((colX + 8).toFloat(), currY - 9f, (colX + colWidth - 8).toFloat(), currY + 7f, paint)
                    drawRow("Late Fee Surcharge", "Rs. 300", true, Color.rgb(198, 40, 40))

                    // Payable after due date
                    paint.color = Color.rgb(255, 205, 210) // Darker Light Red
                    canvas.drawRect((colX + 8).toFloat(), currY - 9f, (colX + colWidth - 8).toFloat(), currY + 9f, paint)
                    drawRow("Payable After 08th", "Rs. ${totalAfterDue.toInt()}", true, Color.rgb(183, 28, 28))

                    // Terms Note
                    currY += 10f
                    paint.textSize = 6.5f
                    paint.color = Color.rgb(100, 100, 100)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    canvas.drawText("• Fee must be deposited on or before 08th of every month.", (colX + 10).toFloat(), currY, paint)
                    currY += 10f
                    canvas.drawText("• Late fee fine Rs. 300 strictly applied after 08th.", (colX + 10).toFloat(), currY, paint)
                    currY += 10f
                    canvas.drawText("• Fee once paid is non-refundable / non-transferable.", (colX + 10).toFloat(), currY, paint)

                    // Signature boxes
                    val sigY = (pageHeight - 40).toFloat()
                    strokePaint.color = Color.rgb(180, 180, 180)
                    canvas.drawLine((colX + 12).toFloat(), sigY, (colX + 105).toFloat(), sigY, strokePaint)
                    canvas.drawLine((colX + 160).toFloat(), sigY, (colX + 245).toFloat(), sigY, strokePaint)

                    paint.textSize = 6.5f
                    paint.color = Color.rgb(60, 60, 60)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val monthlyChallanSign = if (activeOperatorName.isNotBlank()) "Received: ${getReceivedByLabel()}" else "Cashier / Accounts"
                    canvas.drawText(monthlyChallanSign, (colX + 14).toFloat(), sigY + 11f, paint)
                    canvas.drawText("Principal Signature", (colX + 165).toFloat(), sigY + 11f, paint)

                    // Dashed cut-line between columns
                    if (cIdx < 2) {
                        val cutX = (colX + colWidth + colGap / 2).toFloat()
                        canvas.drawLine(cutX, 10f, cutX, (pageHeight - 10).toFloat(), dashedStrokePaint)
                        paint.textSize = 8f
                        paint.color = Color.GRAY
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("✂ CUT HERE ✂", cutX, (pageHeight / 2).toFloat(), paint)
                        paint.textAlign = Paint.Align.LEFT
                    }
                }

                pdfDocument.finishPage(page)
            }

            val fileName = "BLS_Fee_Challan_${monthYear.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
            val cached = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            return cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }

    /**
     * 15. Export Consolidated Family Fee Receipt PDF (for multiple siblings)
     */
    fun exportFamilyFeeSlipPdf(
        context: Context,
        fatherName: String,
        fatherPhone: String,
        familyVoucherNo: String,
        siblingItems: List<Triple<Student, Double, String>>, // Student, AmountPaid, Month
        totalPaid: Double,
        paymentMode: String
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(420, 650, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(200, 200, 200)
        }

        try {
            // Header
            paint.color = Color.rgb(27, 94, 32)
            canvas.drawRect(0f, 0f, 420f, 75f, paint)

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BISMILLAH LOGICAL SCHOOL", 210f, 32f, paint)

            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("CONSOLIDATED FAMILY FEE RECEIPT", 210f, 50f, paint)
            paint.textSize = 8.5f
            canvas.drawText("SIBLING & FAMILY TUITION RECORD", 210f, 65f, paint)

            paint.textAlign = Paint.Align.LEFT
            var currY = 95f

            // Metadata Card
            paint.color = Color.rgb(245, 247, 250)
            canvas.drawRoundRect(16f, currY, 404f, currY + 65f, 8f, 8f, paint)

            paint.color = Color.rgb(40, 40, 40)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Family Voucher #: $familyVoucherNo", 26f, currY + 18f, paint)
            canvas.drawText("Father: $fatherName", 26f, currY + 34f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Contact: $fatherPhone", 26f, currY + 50f, paint)
            canvas.drawText("Date: ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}", 220f, currY + 18f, paint)
            canvas.drawText("Payment Mode: $paymentMode", 220f, currY + 34f, paint)

            currY += 80f

            // Table Header
            paint.color = Color.rgb(230, 235, 245)
            canvas.drawRect(16f, currY, 404f, currY + 22f, paint)
            paint.color = Color.rgb(20, 20, 20)
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Student Name", 24f, currY + 15f, paint)
            canvas.drawText("Class", 160f, currY + 15f, paint)
            canvas.drawText("Month", 240f, currY + 15f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Amount Paid", 396f, currY + 15f, paint)
            paint.textAlign = Paint.Align.LEFT

            currY += 24f

            // Siblings Rows
            siblingItems.forEach { (student, amt, month) ->
                paint.color = Color.rgb(50, 50, 50)
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(student.studentName.take(20), 24f, currY + 14f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(student.className, 160f, currY + 14f, paint)
                canvas.drawText(month.take(12), 240f, currY + 14f, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Rs. ${amt.toInt()}", 396f, currY + 14f, paint)
                paint.textAlign = Paint.Align.LEFT

                currY += 20f
                canvas.drawLine(16f, currY, 404f, currY, strokePaint)
                currY += 4f
            }

            // Total Card
            currY += 10f
            paint.color = Color.rgb(232, 245, 233)
            canvas.drawRoundRect(16f, currY, 404f, currY + 36f, 8f, 8f, paint)
            paint.color = Color.rgb(27, 94, 32)
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("GRAND TOTAL PAID:", 26f, currY + 23f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Rs. ${totalPaid.toInt()}", 396f, currY + 23f, paint)
            paint.textAlign = Paint.Align.LEFT

            // Signatures
            val sigY = 600f
            canvas.drawLine(30f, sigY, 160f, sigY, strokePaint)
            canvas.drawLine(270f, sigY, 390f, sigY, strokePaint)
            paint.textSize = 7.5f
            paint.color = Color.rgb(80, 80, 80)
            val familyReceivedBy = if (activeOperatorName.isNotBlank()) "Received: ${getReceivedByLabel()}" else "Cashier / Accounts Officer"
            canvas.drawText(familyReceivedBy, 32f, sigY + 14f, paint)
            canvas.drawText("Principal Signature", 285f, sigY + 14f, paint)

            pdfDocument.finishPage(page)

            val fileName = "BLS_Family_FeeSlip_${familyVoucherNo}_${System.currentTimeMillis()}.pdf"
            val cached = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            return cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }

    /**
     * 16. Share or Upload Database Backup File Directly to Google Drive
     */
    fun shareBackupToGoogleDrive(context: Context, backupFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BLS School Cash Record Database Backup - ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}")
                putExtra(Intent.EXTRA_TEXT, "Bismillah Logical School system encrypted JSON database backup file.")
                setPackage("com.google.android.apps.docs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: If Google Drive package is not direct, open standard chooser
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
            val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BLS Database Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(chooserIntent, "Save to Google Drive / Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * 18. Export Official Expense / Payment Voucher PDF (Debit Voucher)
     */
    fun exportExpensePaymentVoucherPdf(
        context: Context,
        transaction: Transaction
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(500, 680, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(200, 200, 200)
        }

        try {
            // Header Banner (Navy Blue for Official Audit)
            paint.color = Color.rgb(27, 54, 93)
            canvas.drawRect(0f, 0f, 500f, 85f, paint)

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BISMILLAH LOGICAL SCHOOL", 250f, 32f, paint)

            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("OFFICIAL PAYMENT / DEBIT VOUCHER", 250f, 52f, paint)
            paint.textSize = 8f
            canvas.drawText("Audit & Financial Disbursement Document", 250f, 68f, paint)

            paint.textAlign = Paint.Align.LEFT
            var currY = 105f

            // Meta Info Box
            paint.color = Color.rgb(245, 247, 250)
            canvas.drawRoundRect(20f, currY, 480f, currY + 65f, 8f, 8f, paint)

            val vNo = transaction.voucherNo.ifBlank { "PV-${transaction.id}" }
            val dateStr = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date(transaction.date))

            paint.color = Color.rgb(40, 40, 40)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Voucher #: $vNo", 32f, currY + 22f, paint)
            canvas.drawText("Payment Mode: ${transaction.paymentMode}", 32f, currY + 42f, paint)

            canvas.drawText("Date: $dateStr", 260f, currY + 22f, paint)
            canvas.drawText("Prepared By: ${transaction.recordedBy.ifBlank { "Accounts Office" }}", 260f, currY + 42f, paint)

            currY += 85f

            // Voucher Details Box
            paint.color = Color.rgb(235, 240, 248)
            canvas.drawRect(20f, currY, 480f, currY + 26f, paint)

            paint.color = Color.rgb(20, 20, 20)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Particulars / Expenditure Account Head", 32f, currY + 17f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Amount (PKR)", 468f, currY + 17f, paint)
            paint.textAlign = Paint.Align.LEFT

            currY += 34f

            // Account Head & Details
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Head: ${transaction.category}", 32f, currY + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Rs. ${transaction.amount.toInt()}", 468f, currY + 12f, paint)
            paint.textAlign = Paint.Align.LEFT

            currY += 24f
            canvas.drawLine(20f, currY, 480f, currY, strokePaint)
            currY += 16f

            // Memo / Description
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Payment Purpose / Description:", 32f, currY, paint)
            currY += 16f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.rgb(60, 60, 60)
            val descLines = if (transaction.description.isBlank()) listOf("N/A") else transaction.description.chunked(60)
            for (line in descLines) {
                canvas.drawText(line, 32f, currY, paint)
                currY += 14f
            }

            currY += 15f

            // Total Card (Highlighted)
            paint.color = Color.rgb(255, 243, 224) // Light Amber
            canvas.drawRoundRect(20f, currY, 480f, currY + 60f, 8f, 8f, paint)

            paint.color = Color.rgb(198, 40, 40)
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("TOTAL PAID AMOUNT:", 32f, currY + 24f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Rs. ${transaction.amount.toInt()}", 468f, currY + 24f, paint)
            paint.textAlign = Paint.Align.LEFT

            paint.color = Color.rgb(80, 80, 80)
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            val words = DomainConstants.convertNumberToWords(transaction.amount)
            canvas.drawText("In Words: $words", 32f, currY + 44f, paint)

            // Audit Signature Block
            val sigY = 610f
            strokePaint.color = Color.rgb(160, 160, 160)
            canvas.drawLine(30f, sigY, 125f, sigY, strokePaint)
            canvas.drawLine(150f, sigY, 245f, sigY, strokePaint)
            canvas.drawLine(270f, sigY, 365f, sigY, strokePaint)
            canvas.drawLine(390f, sigY, 475f, sigY, strokePaint)

            paint.textSize = 7f
            paint.color = Color.rgb(70, 70, 70)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val voucherPreparedBy = if (activeOperatorName.isNotBlank()) "Prepared: ${getReceivedByLabel()}" else "Prepared By"
            canvas.drawText(voucherPreparedBy, 77f, sigY + 12f, paint)
            canvas.drawText("Checked By", 197f, sigY + 12f, paint)
            canvas.drawText("Approved (Principal)", 317f, sigY + 12f, paint)
            canvas.drawText("Receiver's Sign", 432f, sigY + 12f, paint)
            paint.textAlign = Paint.Align.LEFT

            pdfDocument.finishPage(page)

            val fileName = "BLS_Expense_Voucher_${vNo}_${System.currentTimeMillis()}.pdf"
            val cached = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            return cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }

    /**
     * 19. Export Teacher / Staff Monthly Salary Payslip PDF
     */
    fun exportStaffSalarySlipPdf(
        context: Context,
        transaction: Transaction,
        staffName: String = "",
        month: String = ""
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(480, 640, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(200, 200, 200)
        }

        try {
            // Header
            paint.color = Color.rgb(27, 94, 32) // Forest Green
            canvas.drawRect(0f, 0f, 480f, 80f, paint)

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BISMILLAH LOGICAL SCHOOL", 240f, 32f, paint)

            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("STAFF MONTHLY SALARY PAYSLIP", 240f, 52f, paint)
            paint.textSize = 8f
            canvas.drawText("Official Staff Salary Disbursement Voucher", 240f, 68f, paint)

            paint.textAlign = Paint.Align.LEFT
            var currY = 100f

            // Meta Info
            paint.color = Color.rgb(245, 248, 245)
            canvas.drawRoundRect(20f, currY, 460f, currY + 80f, 8f, 8f, paint)

            val vNo = transaction.voucherNo.ifBlank { "PAY-${transaction.id}" }
            val effectiveStaff = if (staffName.isNotBlank()) staffName else (transaction.studentName ?: "Teaching Staff")
            val effectiveMonth = if (month.isNotBlank()) month else (transaction.monthOfFee?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(transaction.date)))
            val dateStr = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date(transaction.date))

            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Staff Name: $effectiveStaff", 32f, currY + 22f, paint)
            canvas.drawText("Salary Month: $effectiveMonth", 32f, currY + 42f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Payment Mode: ${transaction.paymentMode}", 32f, currY + 62f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Payslip #: $vNo", 250f, currY + 22f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Date: $dateStr", 250f, currY + 42f, paint)
            canvas.drawText("Status: Disbursed", 250f, currY + 62f, paint)

            currY += 98f

            // Breakdown Table Header
            paint.color = Color.rgb(230, 242, 230)
            canvas.drawRect(20f, currY, 460f, currY + 24f, paint)
            paint.color = Color.rgb(20, 20, 20)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Salary Particulars", 32f, currY + 16f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Amount (PKR)", 448f, currY + 16f, paint)
            paint.textAlign = Paint.Align.LEFT

            currY += 32f

            // Row
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Net Monthly Salary Paid", 32f, currY + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Rs. ${transaction.amount.toInt()}", 448f, currY + 12f, paint)
            paint.textAlign = Paint.Align.LEFT

            currY += 24f
            canvas.drawLine(20f, currY, 460f, currY, strokePaint)
            currY += 16f

            if (transaction.description.isNotBlank()) {
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = Color.rgb(80, 80, 80)
                canvas.drawText("Remarks: ${transaction.description}", 32f, currY, paint)
                currY += 22f
            }

            // Total Box
            paint.color = Color.rgb(232, 245, 233)
            canvas.drawRoundRect(20f, currY, 460f, currY + 54f, 8f, 8f, paint)

            paint.color = Color.rgb(27, 94, 32)
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("NET DISBURSED:", 32f, currY + 22f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Rs. ${transaction.amount.toInt()}", 448f, currY + 22f, paint)
            paint.textAlign = Paint.Align.LEFT

            paint.color = Color.rgb(60, 60, 60)
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            val words = DomainConstants.convertNumberToWords(transaction.amount)
            canvas.drawText("In Words: $words", 32f, currY + 40f, paint)

            // Signatures
            val sigY = 570f
            canvas.drawLine(30f, sigY, 140f, sigY, strokePaint)
            canvas.drawLine(180f, sigY, 290f, sigY, strokePaint)
            canvas.drawLine(330f, sigY, 440f, sigY, strokePaint)

            paint.textSize = 7.5f
            paint.color = Color.rgb(70, 70, 70)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            val salarySlipOfficer = if (activeOperatorName.isNotBlank()) "Disbursed: ${getReceivedByLabel()}" else "Accounts Officer"
            canvas.drawText(salarySlipOfficer, 85f, sigY + 12f, paint)
            canvas.drawText("Principal Signature", 235f, sigY + 12f, paint)
            canvas.drawText("Employee Signature", 385f, sigY + 12f, paint)
            paint.textAlign = Paint.Align.LEFT

            pdfDocument.finishPage(page)

            val fileName = "BLS_SalarySlip_${vNo}_${System.currentTimeMillis()}.pdf"
            val cached = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            return cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }

    /**
     * 20. Export Staff & Teachers Monthly Payroll Sheet PDF (A4 Landscape Register)
     */
    fun exportStaffPayrollSheetPdf(
        context: Context,
        monthYear: String,
        salaryTransactions: List<Transaction>
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 842 // A4 Landscape
        val pageHeight = 595
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(200, 200, 200)
        }

        try {
            // Header
            paint.color = Color.rgb(27, 54, 93) // Navy
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 65f, paint)

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BISMILLAH LOGICAL SCHOOL", (pageWidth / 2).toFloat(), 28f, paint)

            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("STAFF & TEACHERS MONTHLY PAYROLL REGISTER", (pageWidth / 2).toFloat(), 45f, paint)
            paint.textSize = 8f
            canvas.drawText("Disbursement Month: $monthYear | Generated: ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}", (pageWidth / 2).toFloat(), 58f, paint)

            paint.textAlign = Paint.Align.LEFT
            var currY = 85f

            // Table Header Bar
            paint.color = Color.rgb(230, 238, 248)
            canvas.drawRect(24f, currY, (pageWidth - 24).toFloat(), currY + 24f, paint)

            paint.color = Color.rgb(20, 20, 20)
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Sr #", 32f, currY + 16f, paint)
            canvas.drawText("Voucher #", 68f, currY + 16f, paint)
            canvas.drawText("Staff / Teacher Name & Particulars", 160f, currY + 16f, paint)
            canvas.drawText("Payment Mode", 430f, currY + 16f, paint)
            canvas.drawText("Disbursed Date", 530f, currY + 16f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Amount (PKR)", 660f, currY + 16f, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Receiver's Signature", 685f, currY + 16f, paint)

            currY += 28f

            var totalSalaryDisbursed = 0.0
            var cashTotal = 0.0
            var bankTotal = 0.0

            salaryTransactions.forEachIndexed { idx, tx ->
                totalSalaryDisbursed += tx.amount
                if (tx.paymentMode.equals(DomainConstants.MODE_BANK, ignoreCase = true)) {
                    bankTotal += tx.amount
                } else {
                    cashTotal += tx.amount
                }

                paint.color = Color.rgb(40, 40, 40)
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                canvas.drawText("${idx + 1}", 32f, currY + 12f, paint)
                canvas.drawText(tx.voucherNo.ifBlank { "PAY-${tx.id}" }, 68f, currY + 12f, paint)

                val namePart = when {
                    !tx.studentName.isNullOrBlank() -> tx.studentName
                    tx.description.contains("to ", ignoreCase = true) -> {
                        val afterTo = tx.description.substringAfter("to ", "").substringBefore(" for").trim()
                        if (afterTo.isNotBlank()) afterTo else tx.description.take(30)
                    }
                    else -> tx.description.take(30).ifBlank { "Staff Member" }
                }
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(namePart.take(35), 160f, currY + 12f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(tx.paymentMode, 430f, currY + 12f, paint)
                canvas.drawText(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(tx.date)), 530f, currY + 12f, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Rs. ${tx.amount.toInt()}", 660f, currY + 12f, paint)
                paint.textAlign = Paint.Align.LEFT

                // Signature line
                canvas.drawLine(685f, currY + 14f, 800f, currY + 14f, strokePaint)

                currY += 20f
                canvas.drawLine(24f, currY, (pageWidth - 24).toFloat(), currY, strokePaint)
                currY += 4f
            }

            // Summary Footer Card
            currY += 12f
            paint.color = Color.rgb(240, 245, 250)
            canvas.drawRoundRect(24f, currY, (pageWidth - 24).toFloat(), currY + 40f, 6f, 6f, paint)

            paint.color = Color.rgb(27, 54, 93)
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Staff Count: ${salaryTransactions.size}", 36f, currY + 24f, paint)
            canvas.drawText("Cash Disbursed: Rs. ${cashTotal.toInt()}", 200f, currY + 24f, paint)
            canvas.drawText("Bank Disbursed: Rs. ${bankTotal.toInt()}", 420f, currY + 24f, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.rgb(198, 40, 40)
            canvas.drawText("GRAND TOTAL: Rs. ${totalSalaryDisbursed.toInt()}", (pageWidth - 36).toFloat(), currY + 24f, paint)
            paint.textAlign = Paint.Align.LEFT

            // Signatures
            val sigY = (pageHeight - 40).toFloat()
            strokePaint.color = Color.rgb(160, 160, 160)
            canvas.drawLine(50f, sigY, 200f, sigY, strokePaint)
            canvas.drawLine(340f, sigY, 500f, sigY, strokePaint)
            canvas.drawLine(640f, sigY, 790f, sigY, strokePaint)

            paint.textSize = 7.5f
            paint.color = Color.rgb(70, 70, 70)
            paint.textAlign = Paint.Align.CENTER
            val payrollPreparedBy = if (activeOperatorName.isNotBlank()) "Prepared: ${getReceivedByLabel()}" else "Prepared By Accountant"
            canvas.drawText(payrollPreparedBy, 125f, sigY + 12f, paint)
            canvas.drawText("Audited By Finance", 420f, sigY + 12f, paint)
            canvas.drawText("Approved By Principal", 715f, sigY + 12f, paint)
            paint.textAlign = Paint.Align.LEFT

            pdfDocument.finishPage(page)

            val fileName = "BLS_Payroll_Register_${monthYear.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
            val cached = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(cached))
            pdfDocument.close()
            return cached
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }

    /**
     * 21. Send Bulk SMS to Fee Defaulters with Due Date (08th) and Rs. 300 Late Fine Notice
     */
    fun sendBulkDefaultersSms(
        context: Context,
        phoneNumbers: List<String>,
        monthYear: String
    ) {
        if (phoneNumbers.isEmpty()) return
        val cleanPhones = phoneNumbers.map { it.replace(" ", "").replace("-", "").trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val separator = if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) "," else ";"
        val joinedNumbers = cleanPhones.joinToString(separator)
        val smsMessage = "Dear Parent, School fee for $monthYear is due. Please deposit tuition on or before the 08th. A late fee surcharge of Rs. 300 will apply after the 08th. Thank you - BLS Administration"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$joinedNumbers")
            putExtra("sms_body", smsMessage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
