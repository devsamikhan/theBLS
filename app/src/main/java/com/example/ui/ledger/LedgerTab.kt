package com.example.ui.ledger

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.AppViewModel
import com.example.ui.components.FinanceSummaryCard
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.*
import com.example.util.ReportExporter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.horizontalScroll
import com.example.util.AudioFeedback
import com.example.util.CurrencyVisualTransformation
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.components.FeeReceiptSuccessDialog
import com.example.ui.components.FamilyFeeReceiptSuccessDialog

enum class LedgerSortField {
    DATE, NAME, AMOUNT
}

private data class FamilyFeeSuccessData(
    val fatherName: String,
    val phone: String,
    val familyVoucherNo: String,
    val payments: List<Triple<Student, Double, String>>,
    val grandTotal: Double,
    val mode: String,
    val pdfFile: File?
)

enum class LedgerDateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class LedgerHeadFilter(val label: String, val categories: List<String>?) {
    ALL("All Heads", null),
    TUITION("Tuition Fee", listOf(DomainConstants.CAT_FEE_RECEIVED)),
    ADMISSION("Admission Fee", listOf(DomainConstants.CAT_ADMISSION_FEE)),
    ANNUAL("Annual Charges", listOf(DomainConstants.CAT_ANNUAL_CHARGES)),
    OTHER_CHARGES("Other Charges", listOf(DomainConstants.CAT_OTHER_CHARGES)),
    EXAM_FEE("Exam & Stationery", listOf(DomainConstants.CAT_EXAM_FEE, DomainConstants.CAT_STATIONERY_EXAM)),
    STAFF_SALARY("Staff Salaries", listOf(DomainConstants.CAT_TEACHERS_PAY, DomainConstants.CAT_STAFF_SALARY)),
    RENT("Building Rent", listOf(DomainConstants.CAT_BUILDING_RENT)),
    UTILITIES("Utility Bills", listOf(DomainConstants.CAT_UTILITY_BILLS, DomainConstants.CAT_INTERNET_COMM)),
    ENTERTAINMENT("Entertainment", listOf(DomainConstants.CAT_ENTERTAINMENT)),
    MAINTENANCE("Maintenance", listOf(DomainConstants.CAT_REPAIR_MAINTENANCE)),
    TRANSFERS("Transfers", listOf(DomainConstants.CAT_BANK_DEPOSIT, DomainConstants.CAT_CASH_DEPOSIT)),
    OTHER_EXPENSE("Other Expenses", listOf(DomainConstants.CAT_OTHER_EXPENSE))
}

@Composable
fun LedgerTab(viewModel: AppViewModel, showAdminPanel: Boolean) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cashTx by viewModel.cashLedger.collectAsStateWithLifecycle()
    val bankTx by viewModel.bankLedger.collectAsStateWithLifecycle()

    var activeLedgerType by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var ledgerSortField by rememberSaveable { mutableStateOf(LedgerSortField.DATE) }
    var ledgerSortAscending by rememberSaveable { mutableStateOf(false) }
    var selectedDateFilter by rememberSaveable { mutableStateOf(LedgerDateFilter.ALL) }
    var selectedHeadFilter by rememberSaveable { mutableStateOf(LedgerHeadFilter.ALL) }
    var ledgerSearchQuery by rememberSaveable { mutableStateOf("") }
    var showLedgerFilterSheet by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showReceiveFeeDialogDirect by remember { mutableStateOf(false) }
    var showFamilyFeeDialog by remember { mutableStateOf(false) }
    var showCashToBankDialog by remember { mutableStateOf(false) }

    var feePaymentSuccessData by remember { mutableStateOf<Triple<Student, Transaction, Double>?>(null) }
    var familyFeeSuccessData by remember { mutableStateOf<FamilyFeeSuccessData?>(null) }

    val (startOfToday, startOfWeek, startOfMonth) = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis

        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val week = cal.timeInMillis

        cal.timeInMillis = today
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val month = cal.timeInMillis

        Triple(today, week, month)
    }

    var expenseSuccessTx by remember { mutableStateOf<Transaction?>(null) }

    if (showExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showExpenseDialog = false },
            onSubmit = { amount, category, mode, details, payee, invNo ->
                viewModel.addExpense(
                    category = category,
                    amount = amount,
                    mode = mode,
                    desc = details,
                    payeeName = payee,
                    invoiceNo = invNo,
                    onSuccess = { savedTx ->
                        AudioFeedback.playSuccessChime()
                        expenseSuccessTx = savedTx
                    }
                )
                showExpenseDialog = false
            }
        )
    }

    expenseSuccessTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { expenseSuccessTx = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.financialColors.cash, modifier = Modifier.size(36.dp)) },
            title = { Text("Expense Recorded Successfully!", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Voucher #: ${tx.voucherNo}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("Amount: Rs. ${tx.amount.toInt()} (${tx.paymentMode})")
                    Text("Head: ${tx.category}")
                    if (tx.payeeName.isNotBlank()) Text("Paid To: ${tx.payeeName}")
                    if (tx.invoiceNo.isNotBlank()) Text("Bill / Inv #: ${tx.invoiceNo}")
                    if (tx.description.isNotBlank()) Text("Details: ${tx.description}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = ReportExporter.exportExpensePaymentVoucherPdf(context, tx)
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Voucher_${tx.voucherNo.ifBlank { tx.id.toString() }}")
                            Toast.makeText(context, "Saved to Downloads! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        }
                        expenseSuccessTx = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print Voucher PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseSuccessTx = null }) {
                    Text("Done")
                }
            }
        )
    }

    if (showReceiveFeeDialogDirect) {
        ReceiveFeeDialogDirect(
            viewModel = viewModel,
            onDismiss = { showReceiveFeeDialogDirect = false },
            onConfirm = { student, amount, category, mode, month, remarks, remainingArrears ->
                viewModel.recordFeePayment(
                    student = student,
                    amount = amount,
                    mode = mode,
                    month = month,
                    category = category,
                    remarks = remarks,
                    onSuccess = { savedTx ->
                        AudioFeedback.playSuccessChime()
                        val file = ReportExporter.exportFeeSlipToPdf(context, savedTx, student)
                        if (file != null) {
                            ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${savedTx.id}")
                        }
                        feePaymentSuccessData = Triple(student, savedTx, remainingArrears)
                    }
                )
                showReceiveFeeDialogDirect = false
            }
        )
    }

    if (showFamilyFeeDialog) {
        FamilyFeeCollectionDialog(
            viewModel = viewModel,
            onDismiss = { showFamilyFeeDialog = false },
            onSuccess = { fatherName, phone, familyVoucherNo, payments, grandTotal, mode ->
                viewModel.recordBulkFeePayments(
                    payments = payments,
                    mode = mode,
                    remarks = "Family fee collection (Voucher: $familyVoucherNo)",
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        val file = ReportExporter.exportFamilyFeeSlipPdf(
                            context = context,
                            fatherName = fatherName,
                            fatherPhone = phone,
                            familyVoucherNo = familyVoucherNo,
                            siblingItems = payments,
                            totalPaid = grandTotal,
                            paymentMode = mode
                        )
                        if (file != null) {
                            ReportExporter.savePdfToDownloads(context, file, "BLS_FamilyFee_${fatherName.replace(" ", "_")}")
                        }
                        familyFeeSuccessData = FamilyFeeSuccessData(
                            fatherName = fatherName,
                            phone = phone,
                            familyVoucherNo = familyVoucherNo,
                            payments = payments,
                            grandTotal = grandTotal,
                            mode = mode,
                            pdfFile = file
                        )
                    }
                )
                showFamilyFeeDialog = false
            }
        )
    }

    feePaymentSuccessData?.let { (student, tx, remArrears) ->
        FeeReceiptSuccessDialog(
            student = student,
            transaction = tx,
            remainingArrears = remArrears,
            onDismiss = { feePaymentSuccessData = null },
            onShareWhatsApp = {
                ReportExporter.sendWhatsAppFeeReceipt(context, student, tx, remArrears)
            },
            onSharePdf = {
                val file = ReportExporter.exportFeeSlipToPdf(context, tx, student)
                if (file != null) {
                    ReportExporter.sharePdf(context, file)
                }
            }
        )
    }

    familyFeeSuccessData?.let { data ->
        FamilyFeeReceiptSuccessDialog(
            fatherName = data.fatherName,
            phoneNumber = data.phone,
            familyVoucherNo = data.familyVoucherNo,
            siblingItems = data.payments,
            totalPaid = data.grandTotal,
            paymentMode = data.mode,
            onDismiss = { familyFeeSuccessData = null },
            onShareWhatsApp = {
                ReportExporter.sendWhatsAppFamilyFeeReceipt(
                    context, data.fatherName, data.phone, data.familyVoucherNo, data.payments, data.grandTotal, data.mode
                )
            },
            onSharePdf = {
                if (data.pdfFile != null) {
                    ReportExporter.sharePdf(context, data.pdfFile)
                }
            }
        )
    }

    if (showCashToBankDialog) {
        val metrics by viewModel.financialMetrics.collectAsStateWithLifecycle()
        CashToBankDepositDialog(
            availableCash = metrics.cashBalance,
            availableBank = metrics.bankBalance,
            onDismiss = { showCashToBankDialog = false },
            onConfirm = { amt, bankDetails, slipNo, remarks ->
                viewModel.recordCashToBankDeposit(
                    amount = amt,
                    bankDetails = bankDetails,
                    slipNo = slipNo,
                    remarks = remarks,
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        Toast.makeText(context, "Rs. ${amt.toInt()} deposited to Bank Account successfully!", Toast.LENGTH_LONG).show()
                    }
                )
                showCashToBankDialog = false
            },
            onConfirmWithdrawal = { amt, bankDetails, slipNo, remarks ->
                viewModel.recordBankToCashWithdrawal(
                    amount = amt,
                    bankDetails = bankDetails,
                    chequeOrRefNo = slipNo,
                    remarks = remarks,
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        Toast.makeText(context, "Rs. ${amt.toInt()} withdrawn to Cash Box successfully!", Toast.LENGTH_LONG).show()
                    }
                )
                showCashToBankDialog = false
            }
        )
    }

    if (showLedgerFilterSheet) {
        LedgerFilterBottomSheet(
            onDismiss = { showLedgerFilterSheet = false },
            dateFilter = selectedDateFilter,
            onDateFilterChange = { selectedDateFilter = it },
            headFilter = selectedHeadFilter,
            onHeadFilterChange = { selectedHeadFilter = it },
            sortField = ledgerSortField,
            sortAscending = ledgerSortAscending,
            onSortChange = { field, asc ->
                ledgerSortField = field
                ledgerSortAscending = asc
            },
            onResetFilters = {
                selectedDateFilter = LedgerDateFilter.ALL
                selectedHeadFilter = LedgerHeadFilter.ALL
                ledgerSortField = LedgerSortField.DATE
                ledgerSortAscending = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        val metrics by viewModel.financialMetrics.collectAsStateWithLifecycle()
        val pendingFeeAlerts by viewModel.pendingFeeAlerts.collectAsStateWithLifecycle()
        val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()
        val students by viewModel.studentsList.collectAsStateWithLifecycle()
        val studentsMap = remember(students) { students.associateBy { it.id } }

        val currentMonthYear = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
        val totalMonthlyRevenue = remember(transactions, currentMonthYear) {
            transactions.filter { tx ->
                tx.isIncome && (
                    tx.monthOfFee == currentMonthYear ||
                    (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonthYear)
                )
            }.sumOf { tx -> tx.amount }
        }

        val pendingFeesToday = remember(pendingFeeAlerts) {
            pendingFeeAlerts.sumOf { it.pendingAmount }
        }

        // Page Heading & Subheading
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
        ) {
            Text(
                text = "Financial Ledger",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Real-time cash flow, bank transactions and expense tracking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Modern Minimalist Financial Metric Strip (Compact, Clean, ~44dp, Responsive)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Metric 1: Monthly Revenue
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.financialColors.cash, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text("Revenue", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "Rs. ${totalMonthlyRevenue.toInt()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.financialColors.cash,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                // Metric 2: Pending Overdue Fees
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.financialColors.expense, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text("Overdue", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "Rs. ${pendingFeesToday.toInt()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.financialColors.expense,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                // Metric 3: Net Cash Balance
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text("Net Ledger", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "Rs. ${metrics.netBalance.toInt()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Modern Minimalist Ledger Control Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Sleek Segmented Switcher (32dp height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(
                    Triple(DomainConstants.MODE_CASH, "Cash", Icons.Default.Payments),
                    Triple(DomainConstants.MODE_BANK, "Bank", Icons.Default.AccountBalance),
                    Triple("Expense Hub", "Expenses", Icons.Default.ReceiptLong),
                    Triple("All", "All", Icons.Default.AccountBalanceWallet)
                ).forEach { (modeKey, label, icon) ->
                    val isSelected = activeLedgerType == modeKey
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { activeLedgerType = modeKey },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isSelected) 1.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Search Bar + Filter Sheet Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = ledgerSearchQuery,
                    onValueChange = { ledgerSearchQuery = it },
                    placeholder = { Text("Search", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (ledgerSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { ledgerSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                )

                // Dedicated Filter Bottom Sheet Button
                val hasActiveFilters = selectedDateFilter != LedgerDateFilter.ALL || selectedHeadFilter != LedgerHeadFilter.ALL || ledgerSortField != LedgerSortField.DATE || ledgerSortAscending
                FilledTonalIconButton(
                    onClick = { showLedgerFilterSheet = true },
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (hasActiveFilters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Ledger Filters",
                            tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        if (hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }
            }

            // 3. Compact Quick Action Pills (28dp height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Receive Fee Pill
                Surface(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showReceiveFeeDialogDirect = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.financialColors.cashContainer.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.financialColors.cash)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fee", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.financialColors.cash)
                    }
                }

                // Family Fee Pill
                Surface(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFamilyFeeDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3E5F5),
                    border = BorderStroke(0.5.dp, Color(0xFFCE93D8).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF6A1B9A))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Family", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A148C))
                    }
                }

                // Expense Pill
                Surface(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showExpenseDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.financialColors.expense)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Expense", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.financialColors.expense)
                    }
                }

                // Transfer Pill
                Surface(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCashToBankDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.financialColors.bankContainer.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.financialColors.bank.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.financialColors.bank)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transfer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.financialColors.bank)
                    }
                }

                // Active Filter summary tag (if date filter is applied)
                if (selectedDateFilter != LedgerDateFilter.ALL) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { selectedDateFilter = LedgerDateFilter.ALL }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedDateFilter.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Active Filter summary tag (if head filter is applied)
                if (selectedHeadFilter != LedgerHeadFilter.ALL) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { selectedHeadFilter = LedgerHeadFilter.ALL }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedHeadFilter.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        val activeList = when (activeLedgerType) {
            DomainConstants.MODE_CASH -> cashTx
            DomainConstants.MODE_BANK -> bankTx
            "Expense Hub" -> transactions.filter { !it.isIncome }
            else -> transactions
        }

        val filteredActiveList = remember(activeList, selectedDateFilter, selectedHeadFilter, ledgerSearchQuery, startOfToday, startOfWeek, startOfMonth) {
            activeList.filter { tx ->
                val matchesDate = when (selectedDateFilter) {
                    LedgerDateFilter.ALL -> true
                    LedgerDateFilter.TODAY -> tx.date >= startOfToday
                    LedgerDateFilter.THIS_WEEK -> tx.date >= startOfWeek
                    LedgerDateFilter.THIS_MONTH -> tx.date >= startOfMonth
                }
                val matchesHead = when {
                    selectedHeadFilter.categories == null -> true
                    else -> selectedHeadFilter.categories!!.any { cat -> tx.category.equals(cat, ignoreCase = true) }
                }
                val matchesSearch = if (ledgerSearchQuery.isBlank()) true else {
                    (tx.studentName?.contains(ledgerSearchQuery, ignoreCase = true) == true) ||
                    tx.category.contains(ledgerSearchQuery, ignoreCase = true) ||
                    tx.description.contains(ledgerSearchQuery, ignoreCase = true) ||
                    tx.payeeName.contains(ledgerSearchQuery, ignoreCase = true) ||
                    tx.invoiceNo.contains(ledgerSearchQuery, ignoreCase = true) ||
                    tx.voucherNo.contains(ledgerSearchQuery, ignoreCase = true) ||
                    tx.amount.toInt().toString().contains(ledgerSearchQuery)
                }
                matchesDate && matchesHead && matchesSearch
            }
        }

        val sortedActiveList = remember(filteredActiveList, ledgerSortField, ledgerSortAscending) {
            val list = filteredActiveList.toList()
            when (ledgerSortField) {
                LedgerSortField.DATE -> if (ledgerSortAscending) list.sortedBy { it.date } else list.sortedByDescending { it.date }
                LedgerSortField.NAME -> {
                    if (ledgerSortAscending) {
                        list.sortedBy { (it.studentName ?: it.category).lowercase() }
                    } else {
                        list.sortedByDescending { (it.studentName ?: it.category).lowercase() }
                    }
                }
                LedgerSortField.AMOUNT -> if (ledgerSortAscending) list.sortedBy { it.amount } else list.sortedByDescending { it.amount }
            }
        }

        // Active Head Filter Summary Banner (if a specific head is selected)
        if (selectedHeadFilter != LedgerHeadFilter.ALL) {
            val headTotal = remember(filteredActiveList) {
                filteredActiveList.sumOf { it.amount }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${selectedHeadFilter.label} History (${filteredActiveList.size} entries)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rs. ${headTotal.toInt()}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { selectedHeadFilter = LedgerHeadFilter.ALL },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear head filter",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 1. Title + Entry Count + Action Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val ledgerTitle = when (activeLedgerType) {
                    DomainConstants.MODE_CASH -> "Cash Ledger"
                    DomainConstants.MODE_BANK -> "Bank Account"
                    else -> "All Transactions"
                }
                Text(
                    ledgerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${sortedActiveList.size} entries",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Quick Export Action Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        if (sortedActiveList.isNotEmpty()) {
                            val headTitle = if (selectedHeadFilter == LedgerHeadFilter.ALL) "All Heads" else selectedHeadFilter.label
                            val dateTitle = selectedDateFilter.label
                            val reportTitle = "$headTitle ($dateTitle)"
                            val totalInc = sortedActiveList.filter { it.isIncome }.sumOf { it.amount }
                            val totalExp = sortedActiveList.filter { !it.isIncome }.sumOf { it.amount }
                            val file = ReportExporter.exportTransactionsToPdf(
                                context = context,
                                title = reportTitle,
                                transactions = sortedActiveList,
                                totalIncome = totalInc,
                                totalExpense = totalExp,
                                studentsMap = studentsMap
                            )
                            if (file != null) {
                                val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Ledger_${headTitle.replace(" ", "_")}")
                                Toast.makeText(context, "Ledger PDF exported! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                ReportExporter.sharePdf(context, file)
                            } else {
                                Toast.makeText(context, "Failed to export Ledger PDF", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No transactions to export", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (sortedActiveList.any { it.category == DomainConstants.CAT_STAFF_SALARY }) {
                    Surface(
                        color = Color(0xFF1B365D).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            val salaryTxs = sortedActiveList.filter { it.category == DomainConstants.CAT_STAFF_SALARY }
                            if (salaryTxs.isNotEmpty()) {
                                val curMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                                val file = ReportExporter.exportStaffPayrollSheetPdf(context, curMonth, salaryTxs)
                                if (file != null) {
                                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Payroll_${curMonth.replace(" ", "_")}")
                                    AudioFeedback.playSuccessChime()
                                    Toast.makeText(context, "Payroll Register Generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                    ReportExporter.sharePdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to compile payroll register.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = "Staff Payroll", tint = Color(0xFF1B365D), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Payroll", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
                        }
                    }
                }
            }
        }

        // 2. Dedicated Responsive Sorting Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Sort by:",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name Sort
                Row(
                    modifier = Modifier.clickable {
                        if (ledgerSortField == LedgerSortField.NAME) {
                            ledgerSortAscending = !ledgerSortAscending
                        } else {
                            ledgerSortField = LedgerSortField.NAME
                            ledgerSortAscending = true
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Name",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (ledgerSortField == LedgerSortField.NAME) FontWeight.Bold else FontWeight.Medium,
                        color = if (ledgerSortField == LedgerSortField.NAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (ledgerSortField == LedgerSortField.NAME) {
                            if (ledgerSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                        } else Icons.Default.Sort,
                        contentDescription = "Sort by Name",
                        modifier = Modifier.size(12.dp),
                        tint = if (ledgerSortField == LedgerSortField.NAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Date Sort
                Row(
                    modifier = Modifier.clickable {
                        if (ledgerSortField == LedgerSortField.DATE) {
                            ledgerSortAscending = !ledgerSortAscending
                        } else {
                            ledgerSortField = LedgerSortField.DATE
                            ledgerSortAscending = true
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (ledgerSortField == LedgerSortField.DATE) FontWeight.Bold else FontWeight.Medium,
                        color = if (ledgerSortField == LedgerSortField.DATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (ledgerSortField == LedgerSortField.DATE) {
                            if (ledgerSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                        } else Icons.Default.Sort,
                        contentDescription = "Sort by Date",
                        modifier = Modifier.size(12.dp),
                        tint = if (ledgerSortField == LedgerSortField.DATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Amount Sort
                Row(
                    modifier = Modifier.clickable {
                        if (ledgerSortField == LedgerSortField.AMOUNT) {
                            ledgerSortAscending = !ledgerSortAscending
                        } else {
                            ledgerSortField = LedgerSortField.AMOUNT
                            ledgerSortAscending = true
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Amount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (ledgerSortField == LedgerSortField.AMOUNT) FontWeight.Bold else FontWeight.Medium,
                        color = if (ledgerSortField == LedgerSortField.AMOUNT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (ledgerSortField == LedgerSortField.AMOUNT) {
                            if (ledgerSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                        } else Icons.Default.Sort,
                        contentDescription = "Sort by Amount",
                        modifier = Modifier.size(12.dp),
                        tint = if (ledgerSortField == LedgerSortField.AMOUNT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        if (sortedActiveList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (activeList.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.ReceiptLong,
                        title = "No $activeLedgerType Transactions",
                        subtitle = "There are no transactions recorded in $activeLedgerType ledger yet.",
                        actionText = "Record Expense",
                        onActionClick = { showExpenseDialog = true }
                    )
                } else {
                    EmptyStateView(
                        icon = Icons.Default.SearchOff,
                        title = "No Matches Found",
                        subtitle = "No entries match the active search or date filter.",
                        actionText = "Clear Filter",
                        onActionClick = {
                            ledgerSearchQuery = ""
                            selectedDateFilter = LedgerDateFilter.ALL
                            selectedHeadFilter = LedgerHeadFilter.ALL
                        }
                    )
                }
            }
        } else {
            val runningBalances: Map<Int, Double> = remember(activeList) {
                val map = mutableMapOf<Int, Double>()
                var running = 0.0
                val chronological = activeList.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })
                for (t in chronological) {
                    val delta = if (t.isIncome) t.amount else -t.amount
                    running += delta
                    map[t.id] = running
                }
                map
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedActiveList, key = { it.id }) { tx ->
                    val studentObj = (tx.studentId?.let { studentsMap[it] })
                        ?: (tx.studentName?.let { name -> students.firstOrNull { it.studentName.equals(name, ignoreCase = true) } })
                    val studentClass = studentObj?.className?.takeIf { it.isNotBlank() }

                    LedgerEntryRow(
                        transaction = tx,
                        showDelete = showAdminPanel,
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteTransaction(tx)
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Transaction #${tx.id} deleted",
                                    actionLabel = "UNDO",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreTransaction(tx)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onPrintSlip = if (tx.isIncome && (tx.studentId != null || studentObj != null)) {
                            { transaction ->
                                val targetStudent = studentObj ?: viewModel.studentsList.value.find { it.id == transaction.studentId }
                                if (targetStudent != null) {
                                    val file = ReportExporter.exportFeeSlipToPdf(context, transaction, targetStudent)
                                    if (file != null) {
                                        val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${transaction.id}")
                                        Toast.makeText(context, "Re-issued Slip! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                        ReportExporter.sharePdf(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to regenerate PDF Slip.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val fallbackStudent = Student(
                                        id = transaction.studentId ?: 0,
                                        studentName = transaction.studentName ?: "Unknown Student",
                                        fatherName = "N/A",
                                        className = "N/A",
                                        contactNumber = "N/A",
                                        admissionFee = 0.0,
                                        monthlyFee = transaction.amount,
                                        annualCharges = 0.0,
                                        discountType = "None",
                                        otherCharges = 0.0
                                    )
                                    val file = ReportExporter.exportFeeSlipToPdf(context, transaction, fallbackStudent)
                                    if (file != null) {
                                        val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${transaction.id}")
                                        Toast.makeText(context, "Re-issued Minimal Slip! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                        ReportExporter.sharePdf(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to regenerate PDF Slip.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else null,
                        onPrintExpenseVoucher = if (!tx.isIncome) {
                            { transaction ->
                                if (transaction.category == DomainConstants.CAT_STAFF_SALARY) {
                                    val staffName = if (!transaction.studentName.isNullOrBlank()) transaction.studentName else {
                                        if (transaction.description.contains("to ", ignoreCase = true)) {
                                            val extracted = transaction.description.substringAfter("to ", "").substringBefore(" for").trim()
                                            if (extracted.isNotBlank()) extracted else "Teaching Staff"
                                        } else "Teaching Staff"
                                    }
                                    val month = transaction.monthOfFee?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(transaction.date))
                                    val file = ReportExporter.exportStaffSalarySlipPdf(context, transaction, staffName, month)
                                    if (file != null) {
                                        val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_SalarySlip_${transaction.voucherNo.ifBlank { transaction.id.toString() }}")
                                        AudioFeedback.playSuccessChime()
                                        Toast.makeText(context, "Salary Slip Generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                        ReportExporter.sharePdf(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to compile salary slip.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val file = ReportExporter.exportExpensePaymentVoucherPdf(context, transaction)
                                    if (file != null) {
                                        val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Voucher_${transaction.voucherNo.ifBlank { transaction.id.toString() }}")
                                        AudioFeedback.playSuccessChime()
                                        Toast.makeText(context, "Expense Voucher Generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                        ReportExporter.sharePdf(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to compile voucher PDF.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else null,
                        studentClass = studentClass,
                        runningBalance = runningBalances[tx.id]
                    )
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
    )
}
}

@Composable
fun LedgerEntryRow(
    transaction: Transaction,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onPrintSlip: ((Transaction) -> Unit)? = null,
    onPrintExpenseVoucher: ((Transaction) -> Unit)? = null,
    studentClass: String? = null,
    runningBalance: Double? = null
) {
    val isIncome = transaction.isIncome
    val accentColor = if (isIncome) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.expense
    val containerColor = if (isIncome) MaterialTheme.financialColors.cashContainer else MaterialTheme.financialColors.expenseContainer
    val dateFormatted = remember(transaction.date) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(transaction.date))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Compact Squircle Icon + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(containerColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title Line + Voucher Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val titleText = if (isIncome) {
                            transaction.studentName ?: transaction.category
                        } else {
                            transaction.category
                        }
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (transaction.voucherNo.isNotBlank()) {
                            Surface(
                                color = containerColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "#${transaction.voucherNo}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    // Subtitle: Mode • Class / Payee • Details
                    val detailLine = buildString {
                        append(transaction.paymentMode)
                        if (isIncome && !studentClass.isNullOrBlank()) {
                            append(" • Cl: $studentClass")
                        } else if (!isIncome && transaction.payeeName.isNotBlank()) {
                            append(" • ${transaction.payeeName}")
                        } else if (transaction.description.isNotBlank()) {
                            append(" • ${transaction.description}")
                        }
                        append(" • $dateFormatted")
                    }
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Amount + Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isIncome) "+" else "-"} Rs. ${transaction.amount.toInt()}",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    if (runningBalance != null) {
                        Text(
                            text = "Bal: ${runningBalance.toInt()}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (onPrintSlip != null) {
                    IconButton(
                        onClick = { onPrintSlip(transaction) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Print Fee Slip",
                            tint = MaterialTheme.financialColors.cash,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else if (!isIncome && onPrintExpenseVoucher != null) {
                    IconButton(
                        onClick = { onPrintExpenseVoucher(transaction) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = "Print Expense Voucher / Payslip",
                            tint = if (transaction.category == DomainConstants.CAT_STAFF_SALARY) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.expense,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (showDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.financialColors.expense.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFilterBottomSheet(
    onDismiss: () -> Unit,
    dateFilter: LedgerDateFilter,
    onDateFilterChange: (LedgerDateFilter) -> Unit,
    headFilter: LedgerHeadFilter,
    onHeadFilterChange: (LedgerHeadFilter) -> Unit,
    sortField: LedgerSortField,
    sortAscending: Boolean,
    onSortChange: (LedgerSortField, Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Ledger Filters & Sort",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onResetFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Section 1: Date Range
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Date Range",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LedgerDateFilter.values().forEach { filter ->
                        val isSelected = dateFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDateFilterChange(filter) },
                            label = { Text(filter.label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // Section 2: Financial Head / Category
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Category / Account Head",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LedgerHeadFilter.values().forEach { h ->
                        val isSelected = headFilter == h
                        FilterChip(
                            selected = isSelected,
                            onClick = { onHeadFilterChange(h) },
                            label = {
                                Text(
                                    h.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }

            // Section 3: Sort Field & Direction
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Sort Transactions By",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedgerSortField.values().forEach { field ->
                        val isSelected = sortField == field
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onSortChange(field, !sortAscending)
                                } else {
                                    onSortChange(field, false)
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (field) {
                                            LedgerSortField.DATE -> "Date"
                                            LedgerSortField.NAME -> "Name"
                                            LedgerSortField.AMOUNT -> "Amount"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Done Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply & View Results", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSubmit: (amount: Double, category: String, mode: String, details: String, payeeName: String, invoiceNo: String) -> Unit
) {
    var expenseAmount by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var payeeNameInput by remember { mutableStateOf("") }
    var invoiceNoInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }

    val categories = DomainConstants.EXPENSE_CATEGORIES

    LaunchedEffect(Unit) {
        expenseCategory = categories[0]
    }

    var staffName by remember { mutableStateOf("") }
    var salaryMonth by remember { mutableStateOf("") }
    var customExpenseHead by remember { mutableStateOf("") }

    val isSalaryCategory = expenseCategory == DomainConstants.CAT_TEACHERS_PAY || expenseCategory == DomainConstants.CAT_STAFF_SALARY
    val isOtherCategory = expenseCategory == DomainConstants.CAT_OTHER_EXPENSE
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.financialColors.expenseContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Record Expense", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Operating expense & payment voucher", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = expenseAmount,
                    onValueChange = { expenseAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Expense cash out amount (Rs) *") },
                    placeholder = { Text("e.g. 5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = expenseCategory,
                        onValueChange = {},
                        label = { Text("Expense Category *") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = !categoryExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = { expenseCategory = cat; categoryExpanded = false }
                            )
                        }
                    }
                }

                if (isSalaryCategory) {
                    OutlinedTextField(
                        value = staffName,
                        onValueChange = { staffName = it },
                        label = { Text("Staff / Teacher Name *") },
                        placeholder = { Text("e.g. Sir Ali / Ma'am Fatima") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = salaryMonth,
                        onValueChange = { salaryMonth = it },
                        label = { Text("Salary Period / Month") },
                        placeholder = { Text("e.g. September 2026") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (isOtherCategory) {
                    OutlinedTextField(
                        value = customExpenseHead,
                        onValueChange = { customExpenseHead = it },
                        label = { Text("Specific Head / Sub-Category") },
                        placeholder = { Text("e.g. Generator Fuel, Sanitation, Lab Assets") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Payee / Recipient Name
                OutlinedTextField(
                    value = payeeNameInput,
                    onValueChange = { payeeNameInput = it },
                    label = { Text("Paid To / Vendor / Person (Optional)") },
                    placeholder = { Text("e.g. LESCO / Ali Stationery / Mr. Imran") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Bill / Invoice Number
                OutlinedTextField(
                    value = invoiceNoInput,
                    onValueChange = { invoiceNoInput = it },
                    label = { Text("Bill / Invoice / Receipt # (Optional)") },
                    placeholder = { Text("e.g. INV-9812 / Bill-044") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Deduct balance from:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_CASH },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cash Box", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_BANK },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bankContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bank", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                OutlinedTextField(
                    value = descInput,
                    onValueChange = { descInput = it },
                    label = { Text("Details / Remarks *") },
                    placeholder = { Text("e.g. Electricity bill / Stationery purchase") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }

                val amt = expenseAmount.toDoubleOrNull() ?: 0.0
                val canSubmit = amt > 0 && expenseCategory.isNotBlank() && (descInput.isNotBlank() || staffName.isNotBlank() || customExpenseHead.isNotBlank() || payeeNameInput.isNotBlank())
                Button(
                    onClick = {
                        val finalCategory = if (isOtherCategory && customExpenseHead.isNotBlank()) customExpenseHead.trim() else expenseCategory
                        val finalDesc = if (isSalaryCategory && staffName.isNotBlank()) {
                            "Teacher: $staffName | Period: ${salaryMonth.ifBlank { "Current" }} | $descInput".trim()
                        } else if (isOtherCategory && customExpenseHead.isNotBlank()) {
                            "[$customExpenseHead] $descInput".trim()
                        } else {
                            descInput
                        }
                        if (amt > 0 && finalCategory.isNotBlank()) {
                            val resolvedPayee = if (isSalaryCategory && payeeNameInput.isBlank() && staffName.isNotBlank()) staffName.trim() else payeeNameInput.trim()
                            onSubmit(
                                amt,
                                finalCategory,
                                paymentMode,
                                if (finalDesc.isNotBlank()) finalDesc else "Expense disbursement",
                                resolvedPayee,
                                invoiceNoInput.trim()
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.expense),
                    shape = RoundedCornerShape(10.dp),
                    enabled = canSubmit
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Expense", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveFeeDialogDirect(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Student, Double, String, String, String, String, Double) -> Unit
) {
    val students by viewModel.studentsList.collectAsStateWithLifecycle()
    val allTx by viewModel.transactionsList.collectAsStateWithLifecycle()
    
    var studentQuery by remember { mutableStateOf("") }
    var selectedStudentDirect by remember { mutableStateOf<Student?>(null) }
    
    var feeCategory by remember { mutableStateOf(DomainConstants.CAT_FEE_RECEIVED) } 
    var categoryExpanded by remember { mutableStateOf(false) }
    
    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isAfter8th = currentDayOfMonth > 8
    var amountInput by remember { mutableStateOf("") }
    var lateFeeFine by remember { mutableStateOf(if (isAfter8th) "300" else "") }
    var selectedMonth by remember { mutableStateOf("") }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("") }

    val categoriesMap = mapOf(
        "Monthly Tuition Fee" to DomainConstants.CAT_FEE_RECEIVED,
        "Admission Fee" to DomainConstants.CAT_ADMISSION_FEE,
        "Annual Charges" to DomainConstants.CAT_ANNUAL_CHARGES,
        "Other Charges" to DomainConstants.CAT_OTHER_CHARGES,
        "Exam Fee / Paper Charges" to DomainConstants.CAT_EXAM_FEE
    )
    val displayCategories = categoriesMap.keys.toList()

    val filteredList = remember(studentQuery, students) {
        if (studentQuery.isBlank()) emptyList() else {
            students.filter { 
                it.studentName.contains(studentQuery, ignoreCase = true) || 
                it.regNo.contains(studentQuery, ignoreCase = true) ||
                it.fatherName.contains(studentQuery, ignoreCase = true)
            }.take(5)
        }
    }

    val monthsList = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val mSdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        
        cal.add(Calendar.MONTH, -3)
        for (i in 0..6) {
            list.add(mSdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        list
    }

    LaunchedEffect(Unit) {
        selectedMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
    }

    fun updatePreloadedAmount(student: Student, categoryDisplayName: String) {
        val mappedCategory = categoriesMap[categoryDisplayName] ?: DomainConstants.CAT_FEE_RECEIVED
        val amt = when (mappedCategory) {
            DomainConstants.CAT_FEE_RECEIVED -> {
                val sTx = allTx.filter { it.studentId == student.id }
                val discounted = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
                val arrears = DomainConstants.calculateMultiMonthArrears(student.admissionDate, discounted, sTx)
                discounted + arrears
            }
            DomainConstants.CAT_ADMISSION_FEE -> student.admissionFee
            DomainConstants.CAT_ANNUAL_CHARGES -> student.annualCharges
            DomainConstants.CAT_OTHER_CHARGES -> student.otherCharges
            else -> 0.0
        }
        amountInput = if (amt > 0) amt.toInt().toString() else ""
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Receive Fee & Income", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Student tuition receipt & revenue entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedStudentDirect == null) {
                    Text("Select Registered Student *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = studentQuery,
                        onValueChange = { studentQuery = it },
                        placeholder = { Text("Search") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    if (filteredList.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                filteredList.forEach { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedStudentDirect = s
                                                updatePreloadedAmount(s, "Monthly Tuition Fee")
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(s.studentName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("Father: ${s.fatherName} | Class: ${s.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (s.regNo.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFF81C784), shape = RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(s.regNo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    } else if (studentQuery.isNotBlank()) {
                        Text("No matching students found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.expense)
                    }
                } else {
                    val s = selectedStudentDirect!!
                    val studentTx = remember(s, allTx) { allTx.filter { it.studentId == s.id } }
                    val baseTuition = remember(s) { DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType) }
                    val multiMonthArrears = remember(s, studentTx) {
                        DomainConstants.calculateMultiMonthArrears(s.admissionDate, baseTuition, studentTx)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.studentName.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Class: ${s.className} | Father: ${s.fatherName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { selectedStudentDirect = null }) {
                                Text("Change", color = MaterialTheme.financialColors.expense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Arrears & Tuition Breakdown Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (multiMonthArrears > 0) MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.35f) else MaterialTheme.financialColors.cashContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (multiMonthArrears > 0) MaterialTheme.financialColors.expense.copy(alpha = 0.4f) else MaterialTheme.financialColors.cash.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Monthly Tuition Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${baseTuition.toInt()}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (multiMonthArrears > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️ Previous Unpaid Arrears:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.expense)
                                    Text("Rs. ${multiMonthArrears.toInt()}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.financialColors.expense)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total Outstanding Due:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Rs. ${(baseTuition + multiMonthArrears).toInt()}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.financialColors.expense)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { amountInput = (baseTuition + multiMonthArrears).toInt().toString() },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Total (Fee + Arrears)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { amountInput = baseTuition.toInt().toString() },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Tuition Only", fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Text("✅ All past dues cleared. Standard monthly billing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.cash)
                            }
                        }
                    }

                    var displayCategoryText by remember { mutableStateOf("Monthly Tuition Fee") }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = displayCategoryText,
                            onValueChange = {},
                            label = { Text("Fee Category Type *") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { categoryExpanded = !categoryExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            displayCategories.forEach { dCat ->
                                DropdownMenuItem(
                                    text = { Text(dCat) },
                                    onClick = {
                                        displayCategoryText = dCat
                                        feeCategory = categoriesMap[dCat] ?: DomainConstants.CAT_FEE_RECEIVED
                                        categoryExpanded = false
                                        updatePreloadedAmount(s, dCat)
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Collect Amount (Rs) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CurrencyVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val enteredAmt = amountInput.toDoubleOrNull() ?: 0.0
                    val expectedAmt = if (feeCategory == DomainConstants.CAT_FEE_RECEIVED) (baseTuition + multiMonthArrears) else enteredAmt
                    if (feeCategory == DomainConstants.CAT_FEE_RECEIVED && enteredAmt > 0) {
                        if (enteredAmt < expectedAmt) {
                            val remaining = expectedAmt - enteredAmt
                            Surface(
                                color = MaterialTheme.financialColors.amberContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.financialColors.amber.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.financialColors.amber, modifier = Modifier.size(16.dp))
                                    Text(
                                        "⚠️ Partial Payment: Rs. ${remaining.toInt()} will be carried forward as Arrears.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.financialColors.amber
                                    )
                                }
                            }
                        } else if (enteredAmt == expectedAmt) {
                            Surface(
                                color = MaterialTheme.financialColors.cashContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.financialColors.cash, modifier = Modifier.size(16.dp))
                                    Text(
                                        "✅ Full Clearance: Current tuition and past arrears cleared.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.financialColors.cash
                                    )
                                }
                            }
                        }
                    }

                    if (isAfter8th) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFF9800)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                                    Text(
                                        "Due date (08th) has passed. Automatic Rs. 300 late fee surcharge is applicable.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        lateFeeFine = if (lateFeeFine.isNotBlank() && lateFeeFine != "0") "" else "300"
                                    }
                                ) {
                                    Text(
                                        if (lateFeeFine.isNotBlank() && lateFeeFine != "0") "Waive Fine" else "Apply Fine",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = lateFeeFine,
                        onValueChange = { lateFeeFine = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Late Fee / Fine (Rs) - Surcharge after 8th") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CurrencyVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedMonth,
                            onValueChange = {},
                            label = { Text("Fee Month Period") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = selectedMonthExpanded,
                            onDismissRequest = { selectedMonthExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            monthsList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = { selectedMonth = m; selectedMonthExpanded = false }
                                )
                            }
                        }
                    }

                    Text("Deposit In Ledger:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentMode = DomainConstants.MODE_CASH },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.cashContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cash Box", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentMode = DomainConstants.MODE_BANK },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bankContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bank", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Receipt Memo Remarks") },
                        placeholder = { Text("e.g. Paid in full via parent") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }

                val canConfirm = selectedStudentDirect != null && (amountInput.toDoubleOrNull() ?: 0.0) > 0
                Button(
                    onClick = {
                        val targetStudent = selectedStudentDirect
                        val baseAmt = amountInput.toDoubleOrNull() ?: 0.0
                        val fineAmt = lateFeeFine.toDoubleOrNull() ?: 0.0
                        val totalAmt = baseAmt + fineAmt
                        val finalRemarks = if (fineAmt > 0) {
                            if (remarks.isNotBlank()) "$remarks [Includes Late Fine: Rs. ${fineAmt.toInt()}]" else "Includes Late Fine: Rs. ${fineAmt.toInt()}"
                        } else remarks

                        val remainingArrears = if (feeCategory == DomainConstants.CAT_FEE_RECEIVED) {
                            val sTx = allTx.filter { it.studentId == targetStudent?.id }
                            val discounted = DomainConstants.calculateDiscountedFee(targetStudent?.monthlyFee ?: 0.0, targetStudent?.discountType)
                            val pastArrears = DomainConstants.calculateMultiMonthArrears(targetStudent?.admissionDate ?: 0L, discounted, sTx)
                            val totalOwed = discounted + pastArrears
                            maxOf(0.0, totalOwed - baseAmt)
                        } else 0.0

                        if (targetStudent != null && totalAmt > 0 && selectedMonth.isNotBlank()) {
                            onConfirm(targetStudent, totalAmt, feeCategory, paymentMode, selectedMonth, finalRemarks, remainingArrears)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(10.dp),
                    enabled = canConfirm,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm & Record", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashToBankDepositDialog(
    availableCash: Double,
    availableBank: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, bankDetails: String, slipNo: String, remarks: String) -> Unit,
    onConfirmWithdrawal: ((amount: Double, bankDetails: String, slipNo: String, remarks: String) -> Unit)? = null
) {
    var isDepositMode by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var bankAccountDetails by remember { mutableStateOf("School Main Bank Account") }
    var slipNo by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
    val maxAvailable = if (isDepositMode) availableCash else availableBank
    val isOverLimit = enteredAmount > maxAvailable && maxAvailable > 0
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(if (isDepositMode) MaterialTheme.financialColors.bankContainer else MaterialTheme.financialColors.cashContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDepositMode) Icons.Default.AccountBalance else Icons.Default.Payments,
                            contentDescription = null,
                            tint = if (isDepositMode) MaterialTheme.financialColors.bank else MaterialTheme.financialColors.cash,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isDepositMode) "Deposit Cash to Bank" else "Withdraw Cash from Bank",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDepositMode) "Counter cash transfer to school bank account" else "Petty cash replenishment from bank account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Two-way Direction Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { isDepositMode = true },
                        colors = CardDefaults.cardColors(containerColor = if (isDepositMode) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cash to Bank", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).clickable { isDepositMode = false },
                        colors = CardDefaults.cardColors(containerColor = if (!isDepositMode) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (!isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bank to Cash", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (!isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Balance Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDepositMode) MaterialTheme.financialColors.cashContainer.copy(alpha = 0.4f) else MaterialTheme.financialColors.bankContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isDepositMode) "Available Cash Box Balance:" else "Available Bank Balance:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Rs. ${maxAvailable.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = if (isDepositMode) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.bank,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Icon(
                            imageVector = if (isDepositMode) Icons.Default.Payments else Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = if (isDepositMode) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.bank,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = if (isDepositMode)
                        "Transfer physical cash from the school cash counter into the bank account. Automatically updates both balances."
                    else
                        "Withdraw funds from bank account to replenish the school cash drawer. Automatically updates both balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(if (isDepositMode) "Deposit Amount (Rs) *" else "Withdrawal Amount (Rs) *") },
                    placeholder = { Text("e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    isError = isOverLimit,
                    supportingText = {
                        if (isOverLimit) {
                            Text("Warning: Amount exceeds available balance (Rs. ${maxAvailable.toInt()})", color = MaterialTheme.financialColors.expense, fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = bankAccountDetails,
                    onValueChange = { bankAccountDetails = it },
                    label = { Text("Bank & Branch Details *") },
                    placeholder = { Text("e.g. HBL / Meezan School Branch") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = slipNo,
                    onValueChange = { slipNo = it },
                    label = { Text(if (isDepositMode) "Deposit Slip # / Ref" else "Cheque # / ATM Ref") },
                    placeholder = { Text(if (isDepositMode) "e.g. SLIP-89214" else "e.g. CHQ-55014") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Authorized By / Remarks") },
                    placeholder = { Text("e.g. Authorized by Principal / Accountant") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (enteredAmount > 0) {
                            if (isDepositMode) {
                                onConfirm(enteredAmount, bankAccountDetails, slipNo, remarks)
                            } else {
                                onConfirmWithdrawal?.invoke(enteredAmount, bankAccountDetails, slipNo, remarks) ?: onConfirm(enteredAmount, bankAccountDetails, slipNo, remarks)
                            }
                        }
                    },
                    enabled = enteredAmount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDepositMode) MaterialTheme.financialColors.bank else MaterialTheme.financialColors.cash),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isDepositMode) "Confirm Deposit" else "Confirm Withdrawal", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

