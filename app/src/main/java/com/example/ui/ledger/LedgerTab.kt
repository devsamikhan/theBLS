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
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Family", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
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
                        color = MaterialTheme.financialColors.payrollContainer,
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
                            Icon(Icons.Default.Badge, contentDescription = "Staff Payroll", tint = MaterialTheme.financialColors.payroll, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Payroll", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.payroll)
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

