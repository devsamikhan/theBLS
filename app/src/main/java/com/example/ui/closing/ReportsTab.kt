package com.example.ui.closing
import com.example.ui.closing.dialogs.*

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.AppViewModel
import com.example.ui.components.ClassRecoveryProgressSection
import com.example.ui.components.CloudSyncSettingsDialog
import com.example.ui.students.FeePaymentDialog
import com.example.ui.theme.*
import com.example.util.ReportExporter
import com.example.util.AudioFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportsTab(viewModel: AppViewModel, showAdminPanel: Boolean) {
    val alerts by viewModel.pendingFeeAlerts.collectAsStateWithLifecycle()
    val rawTransactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    val metrics by viewModel.financialMetrics.collectAsStateWithLifecycle()

    val currentClosing by viewModel.currentDayClosing.collectAsStateWithLifecycle()
    val closingHistory by viewModel.dailyClosingsList.collectAsStateWithLifecycle()
    val students by viewModel.studentsList.collectAsStateWithLifecycle()

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshDailyClosing()
    }

    var showQuickFeeCollectByAlertStudent by remember { mutableStateOf<Student?>(null) }
    var showFeeDialogByAlert by remember { mutableStateOf(false) }

    var showMonthlyPdfSelector by remember { mutableStateOf(false) }
    var showStudentPdfSelector by remember { mutableStateOf(false) }
    var showSecuritySettingsDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var show3PartChallanDialog by remember { mutableStateOf(false) }
    var showClassAuditDialog by remember { mutableStateOf(false) }
    var showDefaultersAuditDialog by remember { mutableStateOf(false) }
    var showStaffPayrollDialog by remember { mutableStateOf(false) }
    var showCustomDateRangeDialog by remember { mutableStateOf(false) }
    var showExpenseBreakdownDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var postClosingBackupFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val restoreFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonString.isNullOrBlank()) {
                    pendingRestoreJson = jsonString
                } else {
                    Toast.makeText(context, "Selected backup file was empty.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (postClosingBackupFile != null) {
        val backupFile = postClosingBackupFile!!
        AlertDialog(
            onDismissRequest = { postClosingBackupFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Daily Closing Sealed!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Today's cash and bank registers are balanced and locked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Automated Cloud Backup: Secure your school financial ledger directly to Google Drive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = backupFile
                        postClosingBackupFile = null
                        AudioFeedback.playSuccessChime()
                        Toast.makeText(context, "Opening Google Drive for cloud backup...", Toast.LENGTH_SHORT).show()
                        ReportExporter.shareBackupToGoogleDrive(context, file)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup to Google Drive")
                }
            },
            dismissButton = {
                TextButton(onClick = { postClosingBackupFile = null }) {
                    Text("Done / Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .imePadding()
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.financialColors.expense)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Database Restore", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Warning: Restoring this backup will replace current local database records with the backup file data. Make sure you have exported any recent changes before continuing.\n\nDo you want to proceed?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingRestoreJson!!
                        pendingRestoreJson = null
                        viewModel.importDatabaseFromJson(json) { success, message ->
                            if (success) {
                                AudioFeedback.playSuccessChime()
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.expense)
                ) {
                    Text("Yes, Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val onExportBackupClick: () -> Unit = {
        coroutineScope.launch {
            val json = viewModel.exportDatabaseToJson()
            val file = ReportExporter.exportBackupFile(context, json)
            if (file != null) {
                val res = ReportExporter.saveBackupToDownloads(context, file)
                AudioFeedback.playSuccessChime()
                Toast.makeText(context, "Database Backup Created! ${res.pathMessage}", Toast.LENGTH_LONG).show()
                ReportExporter.shareBackupFile(context, file)
            } else {
                Toast.makeText(context, "Failed to compile backup JSON.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onGoogleDriveBackupClick: () -> Unit = {
        coroutineScope.launch {
            val json = viewModel.exportDatabaseToJson()
            val file = ReportExporter.exportBackupFile(context, json)
            if (file != null) {
                AudioFeedback.playSuccessChime()
                Toast.makeText(context, "Opening Google Drive for Cloud Backup...", Toast.LENGTH_SHORT).show()
                ReportExporter.shareBackupToGoogleDrive(context, file)
            } else {
                Toast.makeText(context, "Failed to compile backup JSON.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onRestoreBackupClick: () -> Unit = {
        restoreFileLauncher.launch("*/*")
    }

    if (showSecuritySettingsDialog) {
        SecuritySettingsDialog(
            currentAdminPin = viewModel.getAdminPin(),
            currentAccountantPin = viewModel.getAccountantPin(),
            onDismiss = { showSecuritySettingsDialog = false },
            onSave = { currAdmin, newAdmin, newAccountant ->
                viewModel.updateSecurityPins(currAdmin, newAdmin, newAccountant)
            }
        )
    }

    if (showCloudSyncDialog) {
        CloudSyncSettingsDialog(
            initialUrl = viewModel.getFirebaseUrl(),
            initialSecret = viewModel.getFirebaseAuthSecret(),
            onDismiss = { showCloudSyncDialog = false },
            onTestConnection = { url, secret ->
                viewModel.testCloudSync(url, secret)
            },
            onSaveConfig = { url, secret ->
                viewModel.updateCloudSyncConfig(url, secret)
            }
        )
    }

    if (showMonthlyPdfSelector) {
        val filteredForMonth = { chosenMonth: String ->
            rawTransactions.filter { it.monthOfFee == chosenMonth }
        }
        MonthlyPdfSelectorDialog(
            transactions = rawTransactions,
            onDismiss = { showMonthlyPdfSelector = false },
            onExport = { chosenMonth, autoDownload ->
                val txs = filteredForMonth(chosenMonth)
                val inc = txs.filter { it.isIncome }.sumOf { it.amount }
                val exp = txs.filter { !it.isIncome }.sumOf { it.amount }
                val file = ReportExporter.exportMonthlySummaryToPdf(
                    context = context,
                    transactions = txs,
                    totalIncome = inc,
                    totalExpense = exp,
                    monthYearString = chosenMonth
                )
                if (file != null) {
                    if (autoDownload) {
                        val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Monthly_Summary_${chosenMonth}")
                        Toast.makeText(context, if (result.success) "Downloaded! ${result.pathMessage}" else result.pathMessage, Toast.LENGTH_LONG).show()
                    } else {
                        ReportExporter.sharePdf(context, file)
                    }
                } else {
                    Toast.makeText(context, "Error compiling monthly report", Toast.LENGTH_SHORT).show()
                }
                showMonthlyPdfSelector = false
            }
        )
    }

    if (show3PartChallanDialog) {
        val availableClasses = remember(students) {
            listOf("All Classes") + students.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val currentMonthYear = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
        var selectedClass by remember { mutableStateOf("All Classes") }
        var billingMonth by remember { mutableStateOf(currentMonthYear) }
        var includeArrears by remember { mutableStateOf(true) }
        var classExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { show3PartChallanDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("3-Part Fee Challans", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Includes School Copy, Accounts Copy, and Student Copy. Due date is set to the 08th, with an automatic Rs. 300 late fee surcharge applied after.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedClass,
                            onValueChange = {},
                            label = { Text("Class") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { classExpanded = !classExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = classExpanded, onDismissRequest = { classExpanded = false }) {
                            availableClasses.forEach { cls ->
                                DropdownMenuItem(text = { Text(cls) }, onClick = { selectedClass = cls; classExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = billingMonth,
                        onValueChange = { billingMonth = it },
                        label = { Text("Billing Month") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { includeArrears = !includeArrears }
                    ) {
                        Checkbox(
                            checked = includeArrears,
                            onCheckedChange = { includeArrears = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Auto-calculate & print previous unpaid arrears",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetStudents = students.filter { s ->
                            s.status == DomainConstants.STATUS_ACTIVE &&
                            (selectedClass == "All Classes" || s.className == selectedClass)
                        }
                        if (targetStudents.isNotEmpty()) {
                            val duesMap = if (includeArrears) {
                                targetStudents.associate { s ->
                                    val sTx = rawTransactions.filter { it.studentId == s.id }
                                    val monthly = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
                                    s.id to DomainConstants.calculateMultiMonthArrears(s.admissionDate, monthly, sTx)
                                }
                            } else emptyMap()

                            val file = ReportExporter.exportMonthly3PartFeeChallanPdf(context, targetStudents, billingMonth, duesMap)
                            if (file != null) {
                                val res = ReportExporter.savePdfToDownloads(context, file, "BLS_3Part_Challans_${selectedClass.replace(" ", "_")}")
                                AudioFeedback.playSuccessChime()
                                Toast.makeText(context, "3-Part Challans Generated! ${res.pathMessage}", Toast.LENGTH_LONG).show()
                                ReportExporter.sharePdf(context, file)
                            } else {
                                Toast.makeText(context, "Error generating challans.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No active students found in this class.", Toast.LENGTH_SHORT).show()
                        }
                        show3PartChallanDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print Challans")
                }
            },
            dismissButton = {
                TextButton(onClick = { show3PartChallanDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStudentPdfSelector) {
        StudentPdfSelectorDialog(
            students = students,
            transactions = rawTransactions,
            onDismiss = { showStudentPdfSelector = false },
            onExport = { targetStudent, autoDownload ->
                val file = ReportExporter.exportStudentPaymentHistoryToPdf(
                    context = context,
                    student = targetStudent,
                    transactions = rawTransactions
                )
                if (file != null) {
                    if (autoDownload) {
                        val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Student_${targetStudent.studentName}")
                        Toast.makeText(context, if (result.success) "Downloaded! ${result.pathMessage}" else result.pathMessage, Toast.LENGTH_LONG).show()
                    } else {
                        ReportExporter.sharePdf(context, file)
                    }
                } else {
                    Toast.makeText(context, "Error compiling student tuition log", Toast.LENGTH_SHORT).show()
                }
                showStudentPdfSelector = false
            }
        )
    }

    if (showFeeDialogByAlert && showQuickFeeCollectByAlertStudent != null) {
        FeePaymentDialog(
            student = showQuickFeeCollectByAlertStudent!!,
            onDismiss = { showFeeDialogByAlert = false; showQuickFeeCollectByAlertStudent = null },
            onConfirm = { amount, mode, month, notes ->
                viewModel.recordFeePayment(showQuickFeeCollectByAlertStudent!!, amount, mode, month, remarks = notes)
                showFeeDialogByAlert = false
                showQuickFeeCollectByAlertStudent = null
            }
        )
    }

    if (showClassAuditDialog) {
        ClassFeeRecoveryAuditDialog(
            transactions = rawTransactions,
            onDismiss = { showClassAuditDialog = false },
            onExportPdf = { chosenMonth ->
                val file = ReportExporter.exportClassWiseFeeRecoveryAuditToPdf(
                    context = context,
                    students = students,
                    transactions = rawTransactions,
                    monthYear = chosenMonth
                )
                if (file != null) {
                    val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Class_Recovery_Audit_${chosenMonth.replace(" ", "_")}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "Recovery Audit Generated! ${result.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Error compiling recovery audit PDF", Toast.LENGTH_SHORT).show()
                }
                showClassAuditDialog = false
            },
            onExportCsv = { chosenMonth ->
                val file = ReportExporter.exportClassRecoveryToCsv(
                    context = context,
                    students = students,
                    transactions = rawTransactions,
                    monthYear = chosenMonth
                )
                if (file != null) {
                    val result = ReportExporter.saveCsvToDownloads(context, file, "BLS_Class_Recovery_${chosenMonth.replace(" ", "_")}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "CSV Sheet Created! ${result.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.shareCsv(context, file)
                } else {
                    Toast.makeText(context, "Error compiling CSV", Toast.LENGTH_SHORT).show()
                }
                showClassAuditDialog = false
            }
        )
    }

    if (showDefaultersAuditDialog) {
        val availableClasses = remember(students) {
            listOf("All Classes") + students.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val currentMonthYear = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
        DefaultersAuditDialog(
            availableClasses = availableClasses,
            defaultMonth = currentMonthYear,
            onDismiss = { showDefaultersAuditDialog = false },
            onExport = { targetClass, targetMonth ->
                val file = ReportExporter.exportDefaultersMasterListToPdf(
                    context = context,
                    students = students,
                    transactions = rawTransactions,
                    classFilter = targetClass,
                    currentMonth = targetMonth
                )
                if (file != null) {
                    val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Defaulters_${targetClass.replace(" ", "_")}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "Defaulters Sheet Generated! ${result.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Error compiling defaulters PDF", Toast.LENGTH_SHORT).show()
                }
                showDefaultersAuditDialog = false
            },
            onSendBulkSms = { targetClass, targetMonth ->
                val activeDefaulters = students.filter { s ->
                    s.status == DomainConstants.STATUS_ACTIVE &&
                    (targetClass == "All Classes" || s.className == targetClass)
                }.filter { s ->
                    val paid = rawTransactions.filter { it.studentId == s.id && it.monthOfFee == targetMonth && it.isIncome }.sumOf { it.amount }
                    val expected = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
                    paid < expected
                }
                val phoneList = activeDefaulters.map { it.contactNumber }.filter { it.isNotBlank() }
                if (phoneList.isNotEmpty()) {
                    ReportExporter.sendBulkDefaultersSms(context, phoneList, targetMonth)
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "SMS alert loaded for ${phoneList.size} fee defaulters.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No defaulters with registered phone numbers found.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showStaffPayrollDialog) {
        StaffPayrollDialog(
            transactions = rawTransactions,
            onDismiss = { showStaffPayrollDialog = false },
            onExport = { chosenMonth ->
                val file = ReportExporter.exportStaffSalaryDisbursementSheetToPdf(
                    context = context,
                    transactions = rawTransactions,
                    monthYear = chosenMonth
                )
                if (file != null) {
                    val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Staff_Payroll_${chosenMonth.replace(" ", "_")}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "Staff Payroll Generated! ${result.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Error compiling payroll register", Toast.LENGTH_SHORT).show()
                }
                showStaffPayrollDialog = false
            }
        )
    }

    if (showCustomDateRangeDialog) {
        CustomDateRangeLedgerDialog(
            onDismiss = { showCustomDateRangeDialog = false },
            onExport = { startMillis, endMillis, mode ->
                val file = ReportExporter.exportDateRangeLedgerToPdf(
                    context = context,
                    transactions = rawTransactions,
                    startDate = startMillis,
                    endDate = endMillis,
                    paymentModeFilter = mode,
                    studentsMap = students.associateBy { it.id }
                )
                if (file != null) {
                    val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Custom_Ledger_${mode}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "Date-Range Ledger Generated! ${result.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Error compiling ledger document", Toast.LENGTH_SHORT).show()
                }
                showCustomDateRangeDialog = false
            }
        )
    }

    if (showExpenseBreakdownDialog) {
        ExpenseBreakdownDialog(
            transactions = rawTransactions,
            onDismiss = { showExpenseBreakdownDialog = false },
            onExport = { periodTitle, filteredTxs ->
                val file = ReportExporter.exportExpenseCategoryBreakdownToPdf(
                    context = context,
                    transactions = filteredTxs,
                    periodTitle = periodTitle
                )
                if (file != null) {
                    val result = ReportExporter.savePdfToDownloads(context, file, "BLS_Expense_Breakdown_${periodTitle.replace(" ", "_")}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "Expense Breakdown Generated! ${result.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Error compiling expense analysis", Toast.LENGTH_SHORT).show()
                }
                showExpenseBreakdownDialog = false
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            // 2-Column Adaptive Layout on Tablets & Landscape
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Daily Business Closing & Pending Fee Alerts
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DailyClosingSectionCard(
                        currentClosing = currentClosing,
                        rawTransactions = rawTransactions,
                        onCloseDay = {
                            viewModel.closeDayAction { backupFile ->
                                postClosingBackupFile = backupFile
                            }
                        },
                        context = context,
                        students = students
                    )

                    ClassRecoveryProgressSection(students = students, transactions = rawTransactions)

                    PendingFeeAlertsSection(
                        alerts = alerts,
                        students = students,
                        onPayNow = { matchedStudent ->
                            showQuickFeeCollectByAlertStudent = matchedStudent
                            showFeeDialogByAlert = true
                        }
                    )
                }

                // Right Column: Admin PDF Report Center & CSV
                if (showAdminPanel) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AdminReportCenterCard(
                            rawTransactions = rawTransactions,
                            metrics = metrics,
                            closingHistory = closingHistory,
                            students = students,
                            context = context,
                            onShowMonthly = { showMonthlyPdfSelector = true },
                            onShowStudent = { showStudentPdfSelector = true },
                            onShowClassAudit = { showClassAuditDialog = true },
                            onShowDefaultersAudit = { showDefaultersAuditDialog = true },
                            onShowStaffPayroll = { showStaffPayrollDialog = true },
                            onShow3PartChallan = { show3PartChallanDialog = true },
                            onShowCustomDateRange = { showCustomDateRangeDialog = true },
                            onShowExpenseBreakdown = { showExpenseBreakdownDialog = true },
                            onShowSecuritySettings = { showSecuritySettingsDialog = true },
                            onShowCloudSyncSettings = { showCloudSyncDialog = true },
                            onGoogleDriveBackup = onGoogleDriveBackupClick,
                            onExportBackup = onExportBackupClick,
                            onRestoreBackup = onRestoreBackupClick,
                            onCheckUpdates = {
                                viewModel.checkForAppUpdates(silent = false) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Standard Single-Column Scrollable on Compact / Phone Screens
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DailyClosingSectionCard(
                        currentClosing = currentClosing,
                        rawTransactions = rawTransactions,
                        onCloseDay = {
                            viewModel.closeDayAction { backupFile ->
                                postClosingBackupFile = backupFile
                            }
                        },
                        context = context,
                        students = students
                    )
                }

                item {
                    ClassRecoveryProgressSection(students = students, transactions = rawTransactions)
                }

                if (showAdminPanel) {
                    item {
                        AdminReportCenterCard(
                            rawTransactions = rawTransactions,
                            metrics = metrics,
                            closingHistory = closingHistory,
                            students = students,
                            context = context,
                            onShowMonthly = { showMonthlyPdfSelector = true },
                            onShowStudent = { showStudentPdfSelector = true },
                            onShowClassAudit = { showClassAuditDialog = true },
                            onShowDefaultersAudit = { showDefaultersAuditDialog = true },
                            onShowStaffPayroll = { showStaffPayrollDialog = true },
                            onShow3PartChallan = { show3PartChallanDialog = true },
                            onShowCustomDateRange = { showCustomDateRangeDialog = true },
                            onShowExpenseBreakdown = { showExpenseBreakdownDialog = true },
                            onShowSecuritySettings = { showSecuritySettingsDialog = true },
                            onShowCloudSyncSettings = { showCloudSyncDialog = true },
                            onGoogleDriveBackup = onGoogleDriveBackupClick,
                            onExportBackup = onExportBackupClick,
                            onRestoreBackup = onRestoreBackupClick,
                            onCheckUpdates = {
                                viewModel.checkForAppUpdates(silent = false) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }

                item {
                    PendingFeeAlertsSection(
                        alerts = alerts,
                        students = students,
                        onPayNow = { matchedStudent ->
                            showQuickFeeCollectByAlertStudent = matchedStudent
                            showFeeDialogByAlert = true
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun DailyClosingSectionCard(
    currentClosing: com.example.data.DailyClosing?,
    rawTransactions: List<Transaction>,
    onCloseDay: () -> Unit,
    context: android.content.Context,
    students: List<Student> = emptyList()
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            currentClosing?.let { closing ->
                val isClosed = closing.isClosed
                val statusText = if (isClosed) "Closed • ${closing.closedBy}" else "Active Business Day"
                val statusColor = if (isClosed) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.goldBadge
                val statusBg = if (isClosed) MaterialTheme.financialColors.cashContainer else MaterialTheme.financialColors.goldContainer

                // Header Row: Title & Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Daily Balance Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Date: ${closing.dateString}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = statusBg.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Balance Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cash Box Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                "Cash Box (In Hand)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Rs. ${closing.closingCash.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.financialColors.cash
                            )
                        }
                    }

                    // Bank Balance Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                "Bank Balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Rs. ${closing.closingBank.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.financialColors.bank
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Action Button or Sealed Label
                if (!isClosed) {
                    Button(
                        onClick = onCloseDay,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign & Close Day Balance", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Day logs sealed. Admin review required to edit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Outlined CSV Export Button
                OutlinedButton(
                    onClick = {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val dayTxs = rawTransactions.filter { 
                            sdf.format(Date(it.date)) == closing.dateString
                        }
                        val file = ReportExporter.exportClosingToCsv(
                            context = context,
                            transactions = dayTxs,
                            titleWord = "Closing_${closing.dateString.replace("/", "_")}",
                            studentsMap = students.associateBy { it.id }
                        )
                        if (file != null) {
                            val res = ReportExporter.saveCsvToDownloads(context, file, "BLS_Closing_Sheet_${closing.dateString.replace("/", "_")}")
                            Toast.makeText(context, res.pathMessage, Toast.LENGTH_LONG).show()
                            ReportExporter.shareCsv(context, file)
                        } else {
                            Toast.makeText(context, "Failed to compile spreadsheet data", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.financialColors.cash)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Daily Sheet (CSV)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }

            } ?: run {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private data class ReportTileItem(
    val title: String,
    val description: String,
    val badgeText: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconBgColor: Color,
    val iconTintColor: Color,
    val primaryButtonText: String,
    val onPrimaryAction: () -> Unit,
    val secondaryAction: (() -> Unit)? = null
)

@Composable
private fun ReportGridTile(
    item: ReportTileItem,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable { item.onPrimaryAction() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Squircle Icon & Format Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(item.iconBgColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = item.iconTintColor, modifier = Modifier.size(18.dp))
                }
                Surface(
                    color = item.iconBgColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        item.badgeText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = item.iconTintColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title & Subtitle Description
            Column {
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    item.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row: Primary Tap Indicator & Optional Share Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = item.iconTintColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            item.primaryButtonText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = item.iconTintColor,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = item.iconTintColor,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                if (item.secondaryAction != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = item.secondaryAction,
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminReportCenterCard(
    rawTransactions: List<Transaction>,
    metrics: com.example.ui.FinancialMetrics,
    closingHistory: List<com.example.data.DailyClosing>,
    students: List<Student>,
    context: android.content.Context,
    onShowMonthly: () -> Unit,
    onShowStudent: () -> Unit,
    onShowClassAudit: () -> Unit = {},
    onShowDefaultersAudit: () -> Unit = {},
    onShowStaffPayroll: () -> Unit = {},
    onShow3PartChallan: () -> Unit = {},
    onShowCustomDateRange: () -> Unit = {},
    onShowExpenseBreakdown: () -> Unit = {},
    onShowSecuritySettings: () -> Unit = {},
    onShowCloudSyncSettings: () -> Unit = {},
    onGoogleDriveBackup: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onCheckUpdates: () -> Unit = {}
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val categoryTabs = listOf("All Reports", "Cashflow", "Students & Staff", "Security")

    val financialReports = remember(rawTransactions, metrics, closingHistory) {
        listOf(
            ReportTileItem(
                title = "General Ledger",
                description = "Complete cash & bank register",
                badgeText = "PDF",
                icon = Icons.Default.ReceiptLong,
                iconBgColor = Color(0xFFE8F5E9),
                iconTintColor = Color(0xFF2E7D32),
                primaryButtonText = "Download",
                onPrimaryAction = {
                    val file = ReportExporter.exportTransactionsToPdf(
                        context = context,
                        title = "Full School General Ledger",
                        transactions = rawTransactions,
                        totalIncome = metrics.totalIncome,
                        totalExpense = metrics.totalExpense,
                        studentsMap = students.associateBy { it.id }
                    )
                    if (file != null) {
                        val res = ReportExporter.savePdfToDownloads(context, file, "BLS_General_Ledger")
                        Toast.makeText(context, if (res.success) "Downloaded! ${res.pathMessage}" else res.pathMessage, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error compiling document", Toast.LENGTH_LONG).show()
                    }
                },
                secondaryAction = {
                    val file = ReportExporter.exportTransactionsToPdf(
                        context = context,
                        title = "Full School General Ledger",
                        transactions = rawTransactions,
                        totalIncome = metrics.totalIncome,
                        totalExpense = metrics.totalExpense,
                        studentsMap = students.associateBy { it.id }
                    )
                    if (file != null) ReportExporter.sharePdf(context, file)
                }
            ),
            ReportTileItem(
                title = "Monthly Summary",
                description = "Income & expense breakdown",
                badgeText = "PDF",
                icon = Icons.Default.DateRange,
                iconBgColor = Color(0xFFE3F2FD),
                iconTintColor = Color(0xFF1976D2),
                primaryButtonText = "Select Month",
                onPrimaryAction = onShowMonthly
            ),
            ReportTileItem(
                title = "Custom Date Ledger",
                description = "Date range cashflow analysis",
                badgeText = "PDF",
                icon = Icons.Default.CalendarMonth,
                iconBgColor = Color(0xFFE0F7FA),
                iconTintColor = Color(0xFF00838F),
                primaryButtonText = "Pick Range",
                onPrimaryAction = onShowCustomDateRange
            ),
            ReportTileItem(
                title = "Expense Breakdown",
                description = "Campus expense distribution",
                badgeText = "PDF",
                icon = Icons.Default.PieChart,
                iconBgColor = Color(0xFFFFEBEE),
                iconTintColor = Color(0xFFC62828),
                primaryButtonText = "Analyze",
                onPrimaryAction = onShowExpenseBreakdown
            ),
            ReportTileItem(
                title = "Daily Closing Log",
                description = "Daily shift close history",
                badgeText = "PDF",
                icon = Icons.Default.History,
                iconBgColor = Color(0xFFFFF3E0),
                iconTintColor = Color(0xFFE65100),
                primaryButtonText = "Download",
                onPrimaryAction = {
                    val file = ReportExporter.exportDailyClosingHistoryToPdf(context, closingHistory)
                    if (file != null) {
                        val res = ReportExporter.savePdfToDownloads(context, file, "BLS_Daily_Closings_Log")
                        Toast.makeText(context, if (res.success) "Downloaded! ${res.pathMessage}" else res.pathMessage, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error compiling document", Toast.LENGTH_LONG).show()
                    }
                },
                secondaryAction = {
                    val file = ReportExporter.exportDailyClosingHistoryToPdf(context, closingHistory)
                    if (file != null) ReportExporter.sharePdf(context, file)
                }
            ),
            ReportTileItem(
                title = "Google Sheets CSV",
                description = "Spreadsheet export",
                badgeText = "EXCEL",
                icon = Icons.Default.TableChart,
                iconBgColor = Color(0xFFE8F5E9),
                iconTintColor = Color(0xFF1B5E20),
                primaryButtonText = "Export CSV",
                onPrimaryAction = {
                    val file = ReportExporter.exportClosingToCsv(
                        context = context,
                        transactions = rawTransactions,
                        titleWord = "Full_Ledger",
                        studentsMap = students.associateBy { it.id }
                    )
                    if (file != null) {
                        val res = ReportExporter.saveCsvToDownloads(context, file, "BLS_Full_Ledger_GoogleSheet")
                        Toast.makeText(context, res.pathMessage, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error compiling document", Toast.LENGTH_SHORT).show()
                    }
                },
                secondaryAction = {
                    val file = ReportExporter.exportClosingToCsv(
                        context = context,
                        transactions = rawTransactions,
                        titleWord = "Full_Ledger",
                        studentsMap = students.associateBy { it.id }
                    )
                    if (file != null) ReportExporter.shareCsv(context, file)
                }
            )
        )
    }

    val studentReports = remember(students, rawTransactions) {
        listOf(
            ReportTileItem(
                title = "Student Fee Card",
                description = "Individual student fee ledger",
                badgeText = "PDF",
                icon = Icons.Default.School,
                iconBgColor = Color(0xFFF3E5F5),
                iconTintColor = Color(0xFF7B1FA2),
                primaryButtonText = "Find Student",
                onPrimaryAction = onShowStudent
            ),
            ReportTileItem(
                title = "Class Recovery Audit",
                description = "Class-wise collection audit",
                badgeText = "AUDIT",
                icon = Icons.Default.FactCheck,
                iconBgColor = Color(0xFFE0F2F1),
                iconTintColor = Color(0xFF00695C),
                primaryButtonText = "Audit Sheet",
                onPrimaryAction = onShowClassAudit
            ),
            ReportTileItem(
                title = "Defaulters Register",
                description = "Outstanding arrears list",
                badgeText = "DUE LIST",
                icon = Icons.Default.Warning,
                iconBgColor = Color(0xFFFFEBEE),
                iconTintColor = Color(0xFFC62828),
                primaryButtonText = "Defaulters",
                onPrimaryAction = onShowDefaultersAudit
            ),
            ReportTileItem(
                title = "Staff Salary Register",
                description = "Faculty payroll disbursement",
                badgeText = "PAYROLL",
                icon = Icons.Default.Badge,
                iconBgColor = Color(0xFFEDE7F6),
                iconTintColor = Color(0xFF512DA8),
                primaryButtonText = "Payroll PDF",
                onPrimaryAction = onShowStaffPayroll
            ),
            ReportTileItem(
                title = "3-Part Fee Challans",
                description = "3-Copy challans (08 Due)",
                badgeText = "08 DUE",
                icon = Icons.Default.ReceiptLong,
                iconBgColor = Color(0xFFFFF3E0),
                iconTintColor = Color(0xFFE65100),
                primaryButtonText = "Challans",
                onPrimaryAction = onShow3PartChallan
            )
        )
    }

    val activeReports = when (selectedCategoryIndex) {
        1 -> financialReports
        2 -> studentReports
        3 -> emptyList()
        else -> financialReports + studentReports
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Official Reports & Statements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Financial statements, audit sheets & exports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryTabs.forEachIndexed { index, title ->
                        FilterChip(
                            selected = selectedCategoryIndex == index,
                            onClick = { selectedCategoryIndex = index },
                            label = { Text(title, fontSize = 11.5.sp, fontWeight = if (selectedCategoryIndex == index) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (activeReports.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // 2-Column Grid Action Tiles
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        activeReports.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { item ->
                                    ReportGridTile(
                                        item = item,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // System Administration & Security Card (shown in All or Security tab)
        if (selectedCategoryIndex == 0 || selectedCategoryIndex == 3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFEDE7F6), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF512DA8), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("System Administration & Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Access control PINs & cloud sync settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Security Passcodes Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Login Passcodes", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            Text("Admin & Accountant Access PINs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = onShowSecuritySettings,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change PIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Multi-Device Cloud Sync Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Device Cloud Sync", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            Text("Firebase Realtime Database Setup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = onShowCloudSyncSettings,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF0288D1))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cloud Setup", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0288D1))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Offline Database Snapshot Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Backup & Snapshot", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            Text("Encrypted JSON export / restore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onGoogleDriveBackup,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Drive", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            IconButton(
                                onClick = onExportBackup,
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Export Backup", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = onRestoreBackup,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Restore Backup", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // App Version & Updates Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("App Version & Updates", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("v${com.example.BuildConfig.VERSION_NAME}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                            Text("GitHub: devsamikhan/theBLS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Update", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingFeeAlertsSection(
    alerts: List<com.example.ui.PendingFeeAlert>,
    students: List<Student>,
    onPayNow: (Student) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val totalPendingAmount = remember(alerts) { alerts.sumOf { it.totalPendingAmount } }

    if (alerts.isEmpty()) {
        PendingAlertsEmptyCard()
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Fee Recovery Alerts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.financialColors.expense.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "${alerts.size} Pending",
                                        color = MaterialTheme.financialColors.expense,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "Total Arrears: Rs. ${totalPendingAmount.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.financialColors.expense,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (expanded) "Collapse" else "View All (${alerts.size})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        alerts.forEach { alert ->
                            PendingAlertRowCard(alert = alert, students = students, onPayNow = onPayNow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAlertsEmptyCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.financialColors.cash)
                Text(
                    "All active students have cleared tuition for this month cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PendingAlertRowCard(
    alert: com.example.ui.PendingFeeAlert,
    students: List<Student>,
    onPayNow: (Student) -> Unit
) {
    val matchedStudent = remember(alert, students) {
        students.find { it.id == alert.studentId }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.studentName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${alert.className} • Father: ${alert.fatherName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    "Due: Rs. ${alert.totalPendingAmount.toInt()} (${alert.month})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.financialColors.expense,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
            if (matchedStudent != null) {
                Button(
                    onClick = { onPayNow(matchedStudent) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Collect", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

