package com.example.ui.students

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.AppViewModel
import com.example.ui.PendingFeeAlert
import com.example.ui.components.ExecutiveRecoveryTargetCard
import com.example.ui.components.PaymentSparkline
import com.example.ui.components.ShimmerStudentRow
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.*
import com.example.util.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.text.SimpleDateFormat
import java.util.*

enum class StudentSortField {
    NAME, DATE, AMOUNT
}

enum class StudentFilterCategory {
    ALL, DEFAULTERS_ONLY, PAID_THIS_MONTH
}

@Composable
fun StudentsTab(viewModel: AppViewModel) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val students by viewModel.filteredStudents.collectAsStateWithLifecycle()
    val selectedStudent by viewModel.selectedStudent.collectAsStateWithLifecycle()
    val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val allStudentsRaw by viewModel.studentsList.collectAsStateWithLifecycle()
    val pendingAlerts by viewModel.pendingFeeAlerts.collectAsStateWithLifecycle()

    var showAddForm by remember { mutableStateOf(false) }
    var showFeeCollectDialog by remember { mutableStateOf(false) }
    var showBulkFeeDialog by remember { mutableStateOf(false) }
    var showThreePartChallanDialog by remember { mutableStateOf(false) }
    var showClassBulkChallanDialog by remember { mutableStateOf(false) }
    var showPromoteClassDialog by remember { mutableStateOf(false) }
    var showBulkWhatsAppDialog by remember { mutableStateOf(false) }
    var studentForExitSettlement by remember { mutableStateOf<Student?>(null) }

    var studentSortField by rememberSaveable { mutableStateOf(StudentSortField.NAME) }
    var studentSortAscending by rememberSaveable { mutableStateOf(true) }

    var filterCategory by rememberSaveable { mutableStateOf(StudentFilterCategory.ALL) }
    var classFilter by rememberSaveable { mutableStateOf("All Classes") }
    var selectedAlphabet by rememberSaveable { mutableStateOf("") }

    val activeCount = remember(allStudentsRaw) {
        allStudentsRaw.count { it.status.equals("Active", ignoreCase = true) }
    }
    val leftCount = remember(allStudentsRaw) {
        allStudentsRaw.count { !it.status.equals("Active", ignoreCase = true) }
    }

    val currentMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }

    val totalExpectedFee = remember(allStudentsRaw) {
        allStudentsRaw
            .filter { it.status.equals("Active", ignoreCase = true) }
            .sumOf { DomainConstants.calculateDiscountedFee(it.monthlyFee, it.discountType) }
            .toLong()
    }

    val totalCollectedFee = remember(transactions, currentMonth) {
        transactions
            .filter { it.isIncome && (it.monthOfFee == currentMonth || (it.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(it.date)) == currentMonth)) }
            .sumOf { it.amount }
            .toLong()
    }

    val lowestClassRecovery = remember(allStudentsRaw, transactions, currentMonth) {
        val activeStudents = allStudentsRaw.filter { it.status.equals("Active", ignoreCase = true) }
        val classes = activeStudents.map { it.className.trim() }.filter { it.isNotBlank() }.distinct()
        classes.mapNotNull { cls ->
            val clsStudents = activeStudents.filter { it.className.trim().equals(cls, ignoreCase = true) }
            val clsExpected = clsStudents.sumOf { DomainConstants.calculateDiscountedFee(it.monthlyFee, it.discountType) }
            if (clsExpected > 0) {
                val clsStudentIds = clsStudents.map { it.id }.toSet()
                val clsCollected = transactions
                    .filter { it.studentId in clsStudentIds && it.isIncome && (it.monthOfFee == currentMonth || (it.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(it.date)) == currentMonth)) }
                    .sumOf { it.amount }
                val pct = ((clsCollected / clsExpected) * 100).toInt()
                cls to pct
            } else null
        }.minByOrNull { it.second }
    }

    val paidMap = remember(students, transactions, currentMonth) {
        students.associate { s ->
            val monthTxs = transactions.filter { tx ->
                tx.studentId == s.id && tx.isIncome && (
                    tx.monthOfFee == currentMonth ||
                    (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonth)
                )
            }
            val targetFee = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
            val isPaid = monthTxs.sumOf { it.amount } >= targetFee && targetFee > 0
            s.id to isPaid
        }
    }

    val defaultersCount = remember(students, paidMap) {
        students.count { paidMap[it.id] == false }
    }

    val paidCount = remember(students, paidMap) {
        students.count { paidMap[it.id] == true }
    }

    val availableClasses = remember(students) {
        listOf("All Classes") + students.map { it.className.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredByChips = remember(students, filterCategory, classFilter, paidMap, selectedAlphabet) {
        students.filter { s ->
            val matchesCategory = when (filterCategory) {
                StudentFilterCategory.ALL -> true
                StudentFilterCategory.DEFAULTERS_ONLY -> paidMap[s.id] == false
                StudentFilterCategory.PAID_THIS_MONTH -> paidMap[s.id] == true
            }
            val matchesClass = if (classFilter == "All Classes") true else s.className.equals(classFilter, ignoreCase = true)
            val matchesAlphabet = if (selectedAlphabet.isBlank()) true else s.studentName.trim().startsWith(selectedAlphabet, ignoreCase = true)
            matchesCategory && matchesClass && matchesAlphabet
        }
    }

    val sortedStudents = remember(filteredByChips, studentSortField, studentSortAscending) {
        val list = filteredByChips.toList()
        when (studentSortField) {
            StudentSortField.NAME -> if (studentSortAscending) list.sortedBy { it.studentName.lowercase() } else list.sortedByDescending { it.studentName.lowercase() }
            StudentSortField.DATE -> if (studentSortAscending) list.sortedBy { it.admissionDate } else list.sortedByDescending { it.admissionDate }
            StudentSortField.AMOUNT -> if (studentSortAscending) list.sortedBy { it.monthlyFee } else list.sortedByDescending { it.monthlyFee }
        }
    }

    var newlyAdmittedStudent by remember { mutableStateOf<Student?>(null) }
    var newlyAdmittedFeePaid by remember { mutableStateOf(0.0) }
    var newlyAdmittedFeeMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }

    if (showAddForm) {
        val nextRegNo = remember { viewModel.generateNextRegNo() }
        AddStudentForm(
            initialRegNo = nextRegNo,
            onDismiss = { showAddForm = false },
            onSubmit = { reg, n, fn, c, ph, adm, mon, ann, disc, oth, idCard, prevSchool, fatherIdCard, schoolCertSel, formBSel, fatherCnicSel, picsSel, initFee, initMode, photo ->
                viewModel.addStudent(
                    name = n,
                    fName = fn,
                    clsName = c,
                    contact = ph,
                    admFee = adm,
                    mthFee = mon,
                    annCharges = ann,
                    discType = disc,
                    othCharges = oth,
                    idCard = idCard,
                    prevSchool = prevSchool,
                    fatherIdCard = fatherIdCard,
                    schoolCertAttached = schoolCertSel,
                    formBAttached = formBSel,
                    fatherCnicAttached = fatherCnicSel,
                    picsAttached = picsSel,
                    regNo = reg,
                    photoUri = photo,
                    onSuccess = { savedStudent ->
                        AudioFeedback.playSuccessChime()
                        if (initFee > 0) {
                            val curMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                            viewModel.recordAdmissionInitialFee(
                                student = savedStudent,
                                totalPaid = initFee,
                                mode = initMode,
                                month = curMonth,
                                onSuccess = {
                                    Toast.makeText(context, "Rs. ${initFee.toInt()} received at counter ($initMode)!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        newlyAdmittedStudent = savedStudent
                        newlyAdmittedFeePaid = initFee
                        newlyAdmittedFeeMode = initMode
                    }
                )
                showAddForm = false
            }
        )
    }

    if (newlyAdmittedStudent != null) {
        val curMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
        AdmissionSuccessDialog(
            student = newlyAdmittedStudent!!,
            initialFeePaid = newlyAdmittedFeePaid,
            initialFeeMode = newlyAdmittedFeeMode,
            onDismiss = { newlyAdmittedStudent = null },
            onPrintAdmissionForm = {
                val file = ReportExporter.exportAdmissionFormToPdf(context, newlyAdmittedStudent!!)
                if (file != null) {
                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Admission_${newlyAdmittedStudent!!.studentName.replace(" ", "_")}")
                    Toast.makeText(context, "Admission Form! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Failed to compile Admission Form PDF", Toast.LENGTH_SHORT).show()
                }
            },
            onPrintChallan = {
                val std = newlyAdmittedStudent!!
                val discountedMonthly = DomainConstants.calculateDiscountedFee(std.monthlyFee, std.discountType)
                val file = ReportExporter.exportAdmissionChallanToPdf(
                    context = context,
                    student = std,
                    monthYear = curMonth,
                    admissionFee = std.admissionFee,
                    monthlyFee = std.monthlyFee,
                    discountedMonthlyFee = discountedMonthly,
                    discountType = std.discountType,
                    annualCharges = std.annualCharges,
                    otherCharges = std.otherCharges,
                    paidAtCounter = newlyAdmittedFeePaid,
                    dueDateStr = if (newlyAdmittedFeePaid > 0) "Paid at Counter ($newlyAdmittedFeeMode)" else "Due at Admission"
                )
                if (file != null) {
                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Admission_Challan_${std.studentName.replace(" ", "_")}")
                    Toast.makeText(context, "Admission Challan Created! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Failed to compile Admission Challan PDF", Toast.LENGTH_SHORT).show()
                }
            },
            onShareWhatsApp = {
                val cleanNumber = InputFormatUtils.sanitizePakistanPhoneForWhatsApp(newlyAdmittedStudent!!.contactNumber)
                val paidText = if (newlyAdmittedFeePaid > 0) "💵 *Admission Fee Paid:* Rs. ${newlyAdmittedFeePaid.toInt()} ($newlyAdmittedFeeMode)\n" else ""
                val message = """
*BLENDED LEARNING SCHOOL (BLS)*
*Official Admission Confirmation*
---------------------------------------
Dear Parent / Guardian,
We are pleased to inform you that your child's admission has been successfully confirmed at BLS School.

👤 *Student Name:* ${newlyAdmittedStudent!!.studentName}
🆔 *Registration No:* ${newlyAdmittedStudent!!.regNo}
📚 *Class Level:* ${newlyAdmittedStudent!!.className}
👨‍👦 *Father / Guardian:* ${newlyAdmittedStudent!!.fatherName}
📅 *Monthly Tuition Package:* Rs. ${newlyAdmittedStudent!!.monthlyFee.toInt()}
$paidText---------------------------------------
Welcome to the BLS School Community!

Warm regards,
*BLS Admissions Office*
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
            },
            onSendSms = {
                ReportExporter.sendSmsAdmissionWelcome(
                    context = context,
                    student = newlyAdmittedStudent!!,
                    initialPaid = newlyAdmittedFeePaid
                )
            }
        )
    }

    if (showFeeCollectDialog && selectedStudent != null) {
        FeePaymentDialog(
            student = selectedStudent!!,
            transactions = transactions,
            onDismiss = { showFeeCollectDialog = false },
            onConfirm = { amt, pMode, feeMonth, notes ->
                viewModel.recordFeePayment(
                    student = selectedStudent!!,
                    amount = amt,
                    mode = pMode,
                    month = feeMonth,
                    remarks = notes,
                    onSuccess = { savedTx ->
                        AudioFeedback.playSuccessChime()
                        val file = ReportExporter.exportFeeSlipToPdf(context, savedTx, selectedStudent!!)
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${savedTx.id}")
                            Toast.makeText(context, "Fee Payment Received! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Receipt saved, but failed to compile printable PDF.", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                showFeeCollectDialog = false
            }
        )
    }

    if (showThreePartChallanDialog && selectedStudent != null) {
        PrintThreePartChallanDialog(
            student = selectedStudent!!,
            transactions = transactions,
            onDismiss = { showThreePartChallanDialog = false },
            onExport = { month, dueDate, arrears ->
                val std = selectedStudent!!
                val monthly = DomainConstants.calculateDiscountedFee(std.monthlyFee, std.discountType)
                val file = ReportExporter.exportThreePartChallanToPdf(
                    context = context,
                    student = std,
                    monthYear = month,
                    monthlyFee = monthly,
                    arrears = arrears,
                    annualCharges = 0.0,
                    otherCharges = 0.0,
                    dueDateStr = dueDate
                )
                if (file != null) {
                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_3Part_Challan_${std.studentName.replace(" ", "_")}")
                    AudioFeedback.playSuccessChime()
                    Toast.makeText(context, "Challan Created! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    Toast.makeText(context, "Failed to compile printable Challan.", Toast.LENGTH_LONG).show()
                }
                showThreePartChallanDialog = false
            }
        )
    }

    if (studentForExitSettlement != null) {
        val targetStudent = studentForExitSettlement!!
        val alert = pendingAlerts.find { it.studentId == targetStudent.id }
        StudentExitSettlementDialog(
            student = targetStudent,
            alert = alert,
            onDismiss = { studentForExitSettlement = null },
            onConfirmStatusLeft = {
                viewModel.updateStudentStatus(targetStudent.id, DomainConstants.STATUS_LEFT)
                AudioFeedback.playSuccessChime()
                Toast.makeText(context, "${targetStudent.studentName} marked as Left", Toast.LENGTH_SHORT).show()
                studentForExitSettlement = null
            },
            onCollectPayment = {
                val s = targetStudent
                studentForExitSettlement = null
                viewModel.selectStudent(s)
                showFeeCollectDialog = true
            }
        )
    }

    if (showClassBulkChallanDialog) {
        BulkClassChallanDialog(
            allStudents = students,
            transactions = transactions,
            initialClass = if (classFilter != "All Classes") classFilter else null,
            onDismiss = { showClassBulkChallanDialog = false },
            onExport = { targetClass, targetMonth, dueDate ->
                val classStudents = students.filter { it.className.equals(targetClass, ignoreCase = true) }
                if (classStudents.isEmpty()) {
                    Toast.makeText(context, "No students found in $targetClass", Toast.LENGTH_SHORT).show()
                } else {
                    val studentsWithFees = classStudents.map { s ->
                        val monthly = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
                        val sTx = transactions.filter { it.studentId == s.id }
                        val arrears = DomainConstants.calculateMultiMonthArrears(s.admissionDate, monthly, sTx)
                        Triple(s, monthly, arrears)
                    }
                    val file = ReportExporter.exportClassBulkChallansToPdf(
                        context = context,
                        studentsWithFees = studentsWithFees,
                        className = targetClass,
                        monthYear = targetMonth,
                        dueDateStr = dueDate
                    )
                    if (file != null) {
                        val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Bulk_Challans_${targetClass.replace(" ", "_")}_$targetMonth")
                        AudioFeedback.playSuccessChime()
                        Toast.makeText(context, "Class Challans Generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                        ReportExporter.sharePdf(context, file)
                    } else {
                        Toast.makeText(context, "Failed to compile class challans PDF.", Toast.LENGTH_LONG).show()
                    }
                }
                showClassBulkChallanDialog = false
            }
        )
    }

    if (showBulkFeeDialog) {
        val allStudents by viewModel.studentsList.collectAsStateWithLifecycle()
        BulkFeeReceptionDialog(
            studentsList = allStudents,
            onDismiss = { showBulkFeeDialog = false },
            onConfirm = { batchList, pMode, notes ->
                viewModel.recordBulkFeePayments(
                    payments = batchList,
                    mode = pMode,
                    remarks = notes,
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        Toast.makeText(context, "Successfully batch-processed ${batchList.size} students' fee payments!", Toast.LENGTH_LONG).show()
                        showBulkFeeDialog = false
                    }
                )
            }
        )
    }

    if (showPromoteClassDialog) {
        BulkClassPromotionDialog(
            availableClasses = availableClasses,
            onDismiss = { showPromoteClassDialog = false },
            onPromote = { currentClass, nextClass, applyAnnual, annualAmount ->
                viewModel.bulkPromoteClassWithSessionRollover(currentClass, nextClass, applyAnnual, annualAmount) { count ->
                    AudioFeedback.playSuccessChime()
                    val extraMsg = if (applyAnnual) " and applied new session charges" else ""
                    Toast.makeText(context, "Successfully promoted $count students from $currentClass to $nextClass$extraMsg!", Toast.LENGTH_LONG).show()
                }
                showPromoteClassDialog = false
            }
        )
    }

    if (showBulkWhatsAppDialog) {
        BulkWhatsAppRemindersDialog(
            alerts = pendingAlerts,
            onDismiss = { showBulkWhatsAppDialog = false },
            onSendWhatsApp = { alert ->
                ReportExporter.sendWhatsAppFeeReminder(
                    context = context,
                    phoneNumber = alert.contactNumber,
                    studentName = alert.studentName,
                    className = alert.className,
                    month = alert.month,
                    currentMonthFee = alert.currentMonthPending,
                    arrears = alert.arrearsAmount,
                    totalPayable = alert.totalPendingAmount
                )
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            // Adaptive Dual-Pane Layout for Tablets / Large Screens
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Pane: Student List & Search (420dp width)
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
                ) {
                    StudentsListControlHeader(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        statusFilter = statusFilter,
                        onStatusFilterChange = { viewModel.setStatusFilter(it) },
                        filterCategory = filterCategory,
                        onFilterCategoryChange = { filterCategory = it },
                        classFilter = classFilter,
                        onClassFilterChange = { classFilter = it },
                        selectedAlphabet = selectedAlphabet,
                        onSelectAlphabet = { selectedAlphabet = it },
                        availableClasses = availableClasses,
                        totalCount = students.size,
                        activeCount = activeCount,
                        leftCount = leftCount,
                        defaultersCount = defaultersCount,
                        paidCount = paidCount,
                        onAddStudent = { showAddForm = true },
                        onBulkFee = { showBulkFeeDialog = true },
                        onClassChallans = { showClassBulkChallanDialog = true },
                        onPromoteClass = { showPromoteClassDialog = true },
                        onBulkWhatsAppReminders = { showBulkWhatsAppDialog = true }
                    )

                    StudentsSortingHeader(
                        studentSortField = studentSortField,
                        studentSortAscending = studentSortAscending,
                        onSortChange = { field, ascending ->
                            studentSortField = field
                            studentSortAscending = ascending
                        }
                    )

                    if (students.isEmpty()) {
                        EmptyStudentsPlaceholder(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ExecutiveRecoveryTargetCard(
                                    expectedTotal = totalExpectedFee,
                                    collectedTotal = totalCollectedFee,
                                    lowestClassRecovery = lowestClassRecovery
                                )
                            }
                            items(sortedStudents, key = { it.id }) { s ->
                                val isSelected = selectedStudent?.id == s.id
                                StudentRowCard(
                                    student = s,
                                    transactions = transactions,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectStudent(s) },
                                    onQuickFee = {
                                        viewModel.selectStudent(s)
                                        showFeeCollectDialog = true
                                    },
                                    onQuickWhatsApp = {
                                        val monthly = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
                                        val sTx = transactions.filter { it.studentId == s.id }
                                        val arrears = DomainConstants.calculateMultiMonthArrears(s.admissionDate, monthly, sTx)
                                        val curMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                                        ReportExporter.sendWhatsAppFeeReminder(
                                            context = context,
                                            student = s,
                                            monthYear = curMonth,
                                            currentMonthFee = monthly,
                                            arrears = arrears
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Right Pane: Detail View / Selected Student Ledger
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    selectedStudent?.let { student ->
                        val studentTx = transactions.filter { it.studentId == student.id }
                        IndividualStudentDetailCard(
                            student = student,
                            transactions = studentTx,
                            onClose = { viewModel.selectStudent(null) },
                            onCollectFee = { showFeeCollectDialog = true },
                            onPrintChallan = { showThreePartChallanDialog = true },
                            isWideScreen = true,
                            onUpdateStatus = { viewModel.updateStudentStatus(student.id, it) },
                            onUpdatePhoto = { viewModel.updateStudentPhoto(student.id, it) },
                            onOpenExitSettlement = { studentForExitSettlement = student }
                        )
                    } ?: run {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Select a Student", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Click any student from the left panel to inspect their detailed ledger, print admission slips, or receive fee payments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Standard Single-Pane Flow for Compact / Normal Screens
            Column(modifier = Modifier.fillMaxSize()) {
                selectedStudent?.let { student ->
                    val studentTx = transactions.filter { it.studentId == student.id }
                    IndividualStudentDetailCard(
                        student = student,
                        transactions = studentTx,
                        onClose = { viewModel.selectStudent(null) },
                        onCollectFee = { showFeeCollectDialog = true },
                        onPrintChallan = { showThreePartChallanDialog = true },
                        isWideScreen = false,
                        onUpdateStatus = { viewModel.updateStudentStatus(student.id, it) },
                        onUpdatePhoto = { viewModel.updateStudentPhoto(student.id, it) },
                        onOpenExitSettlement = { studentForExitSettlement = student }
                    )
                } ?: run {
                    StudentsListControlHeader(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        statusFilter = statusFilter,
                        onStatusFilterChange = { viewModel.setStatusFilter(it) },
                        filterCategory = filterCategory,
                        onFilterCategoryChange = { filterCategory = it },
                        classFilter = classFilter,
                        onClassFilterChange = { classFilter = it },
                        selectedAlphabet = selectedAlphabet,
                        onSelectAlphabet = { selectedAlphabet = it },
                        availableClasses = availableClasses,
                        totalCount = students.size,
                        activeCount = activeCount,
                        leftCount = leftCount,
                        defaultersCount = defaultersCount,
                        paidCount = paidCount,
                        onAddStudent = { showAddForm = true },
                        onBulkFee = { showBulkFeeDialog = true },
                        onClassChallans = { showClassBulkChallanDialog = true },
                        onPromoteClass = { showPromoteClassDialog = true },
                        onBulkWhatsAppReminders = { showBulkWhatsAppDialog = true }
                    )

                    if (students.isEmpty()) {
                        EmptyStudentsPlaceholder(modifier = Modifier.weight(1f))
                    } else {
                        StudentsSortingHeader(
                            studentSortField = studentSortField,
                            studentSortAscending = studentSortAscending,
                            onSortChange = { field, ascending ->
                                studentSortField = field
                                studentSortAscending = ascending
                            }
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ExecutiveRecoveryTargetCard(
                                    expectedTotal = totalExpectedFee,
                                    collectedTotal = totalCollectedFee,
                                    lowestClassRecovery = lowestClassRecovery
                                )
                            }
                            items(sortedStudents, key = { it.id }) { s ->
                                StudentRowCard(
                                    student = s,
                                    transactions = transactions,
                                    isSelected = false,
                                    onClick = { viewModel.selectStudent(s) },
                                    onQuickFee = {
                                        viewModel.selectStudent(s)
                                        showFeeCollectDialog = true
                                    },
                                    onQuickWhatsApp = {
                                        val monthly = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
                                        val sTx = transactions.filter { it.studentId == s.id }
                                        val arrears = DomainConstants.calculateMultiMonthArrears(s.admissionDate, monthly, sTx)
                                        val curMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                                        ReportExporter.sendWhatsAppFeeReminder(
                                            context = context,
                                            student = s,
                                            monthYear = curMonth,
                                            currentMonthFee = monthly,
                                            arrears = arrears
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentsListControlHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String = "Active",
    onStatusFilterChange: (String) -> Unit = {},
    filterCategory: StudentFilterCategory,
    onFilterCategoryChange: (StudentFilterCategory) -> Unit,
    classFilter: String,
    onClassFilterChange: (String) -> Unit,
    selectedAlphabet: String = "",
    onSelectAlphabet: (String) -> Unit = {},
    availableClasses: List<String>,
    totalCount: Int,
    activeCount: Int = 0,
    leftCount: Int = 0,
    defaultersCount: Int,
    paidCount: Int,
    onAddStudent: () -> Unit,
    onBulkFee: () -> Unit,
    onClassChallans: () -> Unit,
    onPromoteClass: () -> Unit = {},
    onBulkWhatsAppReminders: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title & Active/Left Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Students Directory", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        "$activeCount Active • $leftCount Alumni",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Quick Actions Row (Fits 100% on screen without scrolling or cut-off)
            var showMoreMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // New Admission Button (Primary Tint)
                Surface(
                    modifier = Modifier
                        .weight(1.22f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAddStudent() }
                        .testTag("add_student_button"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Admission", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }

                // Bulk Fee Reception
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBulkFee() }
                        .testTag("bulk_fee_button"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.financialColors.cash.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.financialColors.cash)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bulk Fee", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.financialColors.cash, maxLines = 1)
                    }
                }

                // Class Challans
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onClassChallans() },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Challans", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }

                // More Actions Menu Button (Promote, WhatsApp Broadcast)
                Box {
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showMoreMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Promote Class", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMoreMenu = false
                                onPromoteClass()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("WhatsApp Broadcast", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.financialColors.cash) },
                            onClick = {
                                showMoreMenu = false
                                onBulkWhatsAppReminders()
                            }
                        )
                    }
                }
            }

            // Compact Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search by name, reg no, father, or class...", fontSize = 12.5.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(15.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Combined Filter & Class Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Class Dropdown Chip
                var classMenuOpen by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { classMenuOpen = true },
                        label = { Text(if (classFilter == "All Classes") "Class: All" else classFilter, fontSize = 11.sp, fontWeight = if (classFilter != "All Classes") FontWeight.Bold else FontWeight.Medium) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (classFilter != "All Classes") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    )
                    DropdownMenu(
                        expanded = classMenuOpen,
                        onDismissRequest = { classMenuOpen = false }
                    ) {
                        availableClasses.forEach { cls ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        cls, 
                                        fontWeight = if (cls == classFilter) FontWeight.Bold else FontWeight.Normal,
                                        color = if (cls == classFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    ) 
                                },
                                onClick = {
                                    onClassFilterChange(cls)
                                    classMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // Status: Active
                FilterChip(
                    selected = statusFilter == "Active" && filterCategory == StudentFilterCategory.ALL,
                    onClick = {
                        onStatusFilterChange("Active")
                        onFilterCategoryChange(StudentFilterCategory.ALL)
                    },
                    label = { Text("Active ($activeCount)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.financialColors.cash.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.financialColors.cash
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )

                // Overdue Defaulters
                FilterChip(
                    selected = filterCategory == StudentFilterCategory.DEFAULTERS_ONLY,
                    onClick = {
                        onStatusFilterChange("Active")
                        onFilterCategoryChange(StudentFilterCategory.DEFAULTERS_ONLY)
                    },
                    label = { Text("Overdue ($defaultersCount)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.financialColors.expense.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.financialColors.expense
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )

                // Paid This Month
                FilterChip(
                    selected = filterCategory == StudentFilterCategory.PAID_THIS_MONTH,
                    onClick = {
                        onStatusFilterChange("Active")
                        onFilterCategoryChange(StudentFilterCategory.PAID_THIS_MONTH)
                    },
                    label = { Text("Paid ($paidCount)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.financialColors.cash.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.financialColors.cash
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )

                // Alumni / Left
                FilterChip(
                    selected = statusFilter == "Left",
                    onClick = { onStatusFilterChange("Left") },
                    label = { Text("Alumni ($leftCount)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )

                // All
                FilterChip(
                    selected = statusFilter == "All" && filterCategory == StudentFilterCategory.ALL,
                    onClick = {
                        onStatusFilterChange("All")
                        onFilterCategoryChange(StudentFilterCategory.ALL)
                    },
                    label = { Text("All ($totalCount)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )
            }

            // Quick A-Z Alphabet Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val alphabets = listOf("All") + ('A'..'Z').map { it.toString() }
                alphabets.forEach { letter ->
                    val isSelected = (letter == "All" && selectedAlphabet.isEmpty()) || letter.equals(selectedAlphabet, ignoreCase = true)
                    Surface(
                        onClick = {
                            onSelectAlphabet(if (letter == "All") "" else letter)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = letter,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentsSortingHeader(
    studentSortField: StudentSortField,
    studentSortAscending: Boolean,
    onSortChange: (StudentSortField, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Name Sort
        Row(
            modifier = Modifier
                .clickable {
                    if (studentSortField == StudentSortField.NAME) {
                        onSortChange(StudentSortField.NAME, !studentSortAscending)
                    } else {
                        onSortChange(StudentSortField.NAME, true)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Student Name",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (studentSortField == StudentSortField.NAME) FontWeight.Bold else FontWeight.Medium,
                color = if (studentSortField == StudentSortField.NAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = if (studentSortField == StudentSortField.NAME) {
                    if (studentSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                } else Icons.Default.Sort,
                contentDescription = "Sort by Name",
                modifier = Modifier.size(12.dp),
                tint = if (studentSortField == StudentSortField.NAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        // Admission Date Sort
        Row(
            modifier = Modifier
                .clickable {
                    if (studentSortField == StudentSortField.DATE) {
                        onSortChange(StudentSortField.DATE, !studentSortAscending)
                    } else {
                        onSortChange(StudentSortField.DATE, true)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Admission",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (studentSortField == StudentSortField.DATE) FontWeight.Bold else FontWeight.Medium,
                color = if (studentSortField == StudentSortField.DATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = if (studentSortField == StudentSortField.DATE) {
                    if (studentSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                } else Icons.Default.Sort,
                contentDescription = "Sort by Admission Date",
                modifier = Modifier.size(12.dp),
                tint = if (studentSortField == StudentSortField.DATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        // Fee Amount Sort
        Row(
            modifier = Modifier
                .clickable {
                    if (studentSortField == StudentSortField.AMOUNT) {
                        onSortChange(StudentSortField.AMOUNT, !studentSortAscending)
                    } else {
                        onSortChange(StudentSortField.AMOUNT, true)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Monthly Fee",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (studentSortField == StudentSortField.AMOUNT) FontWeight.Bold else FontWeight.Medium,
                color = if (studentSortField == StudentSortField.AMOUNT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = if (studentSortField == StudentSortField.AMOUNT) {
                    if (studentSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                } else Icons.Default.Sort,
                contentDescription = "Sort by Fee",
                modifier = Modifier.size(12.dp),
                tint = if (studentSortField == StudentSortField.AMOUNT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun EmptyStudentsPlaceholder(modifier: Modifier = Modifier) {
    EmptyStateView(
        icon = Icons.Default.School,
        title = "No Students Found",
        subtitle = "No students match the active filter or search query. Click + to add a new admission.",
        modifier = modifier
    )
}

@Composable
fun StudentRowCard(
    student: Student,
    transactions: List<Transaction>,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onQuickFee: (() -> Unit)? = null,
    onQuickWhatsApp: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val currentMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
    val discountedMonthly = remember(student) {
        DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
    }
    val paidThisMonth = remember(transactions, student, currentMonth, discountedMonthly) {
        val totalPaid = transactions.filter { tx ->
            tx.studentId == student.id && tx.isIncome && (
                tx.monthOfFee == currentMonth ||
                (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonth)
            )
        }.sumOf { it.amount }
        totalPaid >= discountedMonthly && discountedMonthly > 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("student_row_${student.id}"),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek squircle avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (paidThisMonth) Color(0xFFE8F5E9) else Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    if (student.photoUri.isNotBlank()) {
                        AsyncImage(
                            model = student.photoUri,
                            contentDescription = "Photo of ${student.studentName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(
                            text = student.studentName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (paidThisMonth) Color(0xFF2E7D32) else Color(0xFF0284C7)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.studentName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (student.regNo.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = student.regNo,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Class: ${student.className} • Father: ${student.fatherName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                // Status Badge on top right
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val advanceWallet = remember(transactions, student) {
                        DomainConstants.calculateStudentAdvanceWallet(student, transactions)
                    }
                    if (advanceWallet > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE0F2FE),
                            border = BorderStroke(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "Adv: Rs. ${advanceWallet.toInt()}",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (paidThisMonth) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(11.dp))
                                Text("Paid", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Rs. ${discountedMonthly.toInt()} Due",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (onQuickFee != null || onQuickWhatsApp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 0.7.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val phoneFormatted = InputFormatUtils.formatPhoneDisplay(student.contactNumber)
                    Text(
                        text = "📞 $phoneFormatted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onQuickWhatsApp != null && student.contactNumber.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onQuickWhatsApp()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFDCFCE7)),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                            }
                        }
                        if (onQuickFee != null) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onQuickFee()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.AddCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Receive Fee", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IndividualStudentDetailCard(
    student: Student,
    transactions: List<Transaction>,
    onClose: () -> Unit,
    onCollectFee: () -> Unit,
    onPrintChallan: () -> Unit = {},
    isWideScreen: Boolean = false,
    onUpdateStatus: ((String) -> Unit)? = null,
    onUpdatePhoto: ((String) -> Unit)? = null,
    onOpenExitSettlement: () -> Unit = {}
) {
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdatePhoto?.invoke(it.toString()) }
    }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (isWideScreen) 0.dp else 12.dp)
            .testTag("student_detail_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isWideScreen) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        "Student Ledger",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onPrintChallan,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Challan", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onCollectFee,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Collect Fee", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Student Demographics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Student Photo with Tap to Change
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (student.photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = student.photoUri,
                                    contentDescription = "Student Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                                    Text("Photo", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    student.studentName.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                // Interactive Status Dropdown Menu
                                Box {
                                    Surface(
                                        color = if (student.status == DomainConstants.STATUS_ACTIVE) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { statusMenuExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                student.status,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (student.status == DomainConstants.STATUS_ACTIVE) Color(0xFF166534) else Color(0xFF991B1B)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (student.status == DomainConstants.STATUS_ACTIVE) Color(0xFF166534) else Color(0xFF991B1B)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = statusMenuExpanded,
                                        onDismissRequest = { statusMenuExpanded = false }
                                    ) {
                                        DomainConstants.STUDENT_STATUSES.forEach { st ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        st, 
                                                        fontWeight = if (st == student.status) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (st == DomainConstants.STATUS_ACTIVE) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                    ) 
                                                },
                                                onClick = {
                                                    if (st == DomainConstants.STATUS_LEFT) {
                                                        onOpenExitSettlement()
                                                    } else {
                                                        onUpdateStatus?.invoke(st)
                                                    }
                                                    statusMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (student.regNo.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Reg #${student.regNo}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Bio Details Grid
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Father / Guardian", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(student.fatherName.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Class Group", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(student.className.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contact Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(student.contactNumber.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                if (student.contactNumber.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                                            val monthlyFee = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
                                            ReportExporter.sendWhatsAppFeeReminder(
                                                context = context,
                                                phoneNumber = student.contactNumber,
                                                studentName = student.studentName,
                                                className = student.className,
                                                month = currentMonth,
                                                currentMonthFee = monthlyFee,
                                                arrears = 0.0,
                                                totalPayable = monthlyFee
                                            )
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFF25D366), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "WhatsApp Parent", tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                        }
                        if (student.idCardNumber.isNotEmpty()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("B-Form / ID Card", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(student.idCardNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    if (student.fatherIdCardNumber.isNotEmpty() || student.previousSchoolName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (student.fatherIdCardNumber.isNotEmpty()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Father CNIC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.fatherIdCardNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (student.previousSchoolName.isNotEmpty()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Previous School", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.previousSchoolName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Verified Attachments:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("School Cert: ${if (student.schoolCertAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.schoolCertAttached) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Form-B: ${if (student.formBAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.formBAttached) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Father CNIC: ${if (student.fatherCnicAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.fatherCnicAttached) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Photographs: ${if (student.picsAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.picsAttached) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = ReportExporter.exportAdmissionFormToPdf(context, student)
                                if (file != null) {
                                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Admission_${student.studentName.replace(" ", "_")}")
                                    Toast.makeText(context, dest.pathMessage, Toast.LENGTH_LONG).show()
                                    ReportExporter.sharePdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to compile printable Admission Form", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Admission", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        Button(
                            onClick = onOpenExitSettlement,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B365D)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Clearance", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                val file = ReportExporter.exportSchoolLeavingCertificateToPdf(context, student)
                                if (file != null) {
                                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_SLC_${student.studentName.replace(" ", "_")}")
                                    Toast.makeText(context, "School Leaving Certificate generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                    ReportExporter.sharePdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to compile printable SLC Certificate", Toast.LENGTH_SHORT).show()
                                }
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("SLC Cert", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Fee Summary metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Monthly Tuition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rs. ${student.monthlyFee.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Admission Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rs. ${student.admissionFee.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (student.annualCharges > 0.0) {
                            Column {
                                Text("Annual Chgs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${student.annualCharges.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (student.otherCharges > 0.0) {
                            Column {
                                Text("Other Chgs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${student.otherCharges.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Column {
                            Text("Discount Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(student.discountType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.financialColors.goldBadge)
                        }
                    }

                    val advanceBalance = remember(transactions, student) {
                        DomainConstants.calculateStudentAdvanceWallet(student, transactions)
                    }
                    if (advanceBalance > 0.0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF81C784)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Text("Advance Wallet Credit (Overpayment):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B5E20))
                                }
                                Text("Rs. ${advanceBalance.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Payments Receipts Ledger",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${transactions.size} Records",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No payment transactions recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    transactions.forEach { tx ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            tx.category,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (tx.monthOfFee != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    tx.monthOfFee,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(tx.date))
                                    Text(
                                        "Mode: ${tx.paymentMode} • $dateStr • ${tx.recordedBy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Rs. ${tx.amount.toInt()}",
                                        color = MaterialTheme.financialColors.cash,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            val file = ReportExporter.exportFeeSlipToPdf(context, tx, student)
                                            if (file != null) {
                                                val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${tx.id}")
                                                Toast.makeText(context, "Re-issued Slip: ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                                ReportExporter.sharePdf(context, file)
                                            } else {
                                                Toast.makeText(context, "Failed to regenerate PDF Slip", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = "Print Fee Slip",
                                            tint = MaterialTheme.financialColors.cash,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeePaymentDialog(
    student: Student,
    transactions: List<Transaction> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String) -> Unit
) {
    val advanceWallet = remember(student, transactions) {
        DomainConstants.calculateStudentAdvanceWallet(student, transactions)
    }
    val initialAmount = remember(student) {
        val discounted = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
        discounted.toInt().toString()
    }
    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isAfter8th = currentDayOfMonth > 8
    var feeAmount by remember { mutableStateOf(initialAmount) }
    var lateFeeFine by remember { mutableStateOf(if (isAfter8th) "300" else "") }
    var selectedMonth by remember { mutableStateOf("") }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("") }

    val discountedExpected = remember(student) {
        DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
    }
    val enteredFee = feeAmount.toDoubleOrNull() ?: 0.0
    val remainingFee = discountedExpected - enteredFee

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
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        selectedMonth = currentMonthYear
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header with Student Name + Reg + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.studentName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (student.regNo.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = student.regNo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Class: ${student.className} • Father: ${student.fatherName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live breakdown card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            enteredFee == 0.0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            remainingFee > 0.0 -> Color(0xFFFFF3E0)
                            remainingFee == 0.0 -> Color(0xFFE8F5E9)
                            else -> Color(0xFFE3F2FD)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when {
                            enteredFee == 0.0 -> MaterialTheme.colorScheme.outlineVariant
                            remainingFee > 0.0 -> Color(0xFFFF9800)
                            remainingFee == 0.0 -> Color(0xFF4CAF50)
                            else -> Color(0xFF2196F3)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    enteredFee == 0.0 -> "Expected Tuition"
                                    remainingFee > 0.0 -> "⚠️ Partial Installment"
                                    remainingFee == 0.0 -> "✅ Full Tuition Payment"
                                    else -> "ℹ️ Advance / Excess Payment"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    enteredFee == 0.0 -> MaterialTheme.colorScheme.onSurface
                                    remainingFee > 0.0 -> Color(0xFFE65100)
                                    remainingFee == 0.0 -> Color(0xFF1B5E20)
                                    else -> Color(0xFF0D47A1)
                                }
                            )
                            Text(
                                "Monthly: Rs. ${discountedExpected.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (remainingFee > 0.0 && enteredFee > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Remaining Balance: Rs. ${remainingFee.toInt()} will be recorded as Arrears.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }
                }

                // Advance Wallet Credit Box
                if (advanceWallet > 0.0) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF81C784)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Column {
                                    Text("Advance Wallet Credit Available", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    Text("Surplus: Rs. ${advanceWallet.toInt()}", fontSize = 10.sp, color = Color(0xFF2E7D32))
                                }
                            }
                            TextButton(
                                onClick = {
                                    val netDue = maxOf(0.0, discountedExpected - advanceWallet)
                                    feeAmount = netDue.toInt().toString()
                                    val applied = minOf(discountedExpected, advanceWallet).toInt()
                                    remarks = "Applied Rs. $applied advance wallet credit"
                                }
                            ) {
                                Text("Apply Credit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            }
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = feeAmount,
                    onValueChange = { feeAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Payment Fee Amount (Rs) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { feeAmount = discountedExpected.toInt().toString() }) {
                            Text("Full Fee", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("fee_amt_input")
                )

                // Late fee / fine with 8th Cutoff & Waive toggle
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Month Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fee For Month *") },
                        trailingIcon = {
                            IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Month")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = selectedMonthExpanded,
                        onDismissRequest = { selectedMonthExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        monthsList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    selectedMonth = m
                                    selectedMonthExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Mode Toggle: Cash vs Bank
                Text("Payment Channel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        border = BorderStroke(1.5.dp, if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_CASH },
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cash", fontWeight = FontWeight.Bold, color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        border = BorderStroke(1.5.dp, if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_BANK },
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bankContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bank", fontWeight = FontWeight.Bold, color = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Remarks
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks / Slip Memo") },
                    placeholder = { Text("e.g. Paid by father / slip #") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Confirm Button
            Button(
                onClick = {
                    val baseAmt = feeAmount.toDoubleOrNull() ?: 0.0
                    val fineAmt = lateFeeFine.toDoubleOrNull() ?: 0.0
                    val totalAmt = baseAmt + fineAmt
                    val partialNote = if (remainingFee > 0 && enteredFee > 0) " (Partial - Bal Rs. ${remainingFee.toInt()})" else ""
                    val finalRemarks = when {
                        fineAmt > 0 -> (if (remarks.isNotBlank()) "$remarks (Includes Late Fine: Rs. ${fineAmt.toInt()})" else "Includes Late Fine: Rs. ${fineAmt.toInt()}") + partialNote
                        partialNote.isNotBlank() -> (if (remarks.isNotBlank()) "$remarks$partialNote" else "Partial Installment - Bal: Rs. ${remainingFee.toInt()}")
                        else -> remarks
                    }

                    if (totalAmt > 0 && selectedMonth.isNotBlank()) {
                        onConfirm(totalAmt, paymentMode, selectedMonth, finalRemarks)
                    }
                },
                enabled = (feeAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm & Issue Receipt", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun BulkFeeReceptionDialog(
    studentsList: List<Student>,
    onDismiss: () -> Unit,
    onConfirm: (List<Triple<Student, Double, String>>, String, String) -> Unit
) {
    var selectedStudentIds by remember { mutableStateOf(emptySet<Int>()) }
    var searchBulkQuery by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf("") }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("Bulk fee received") }
    var customAmounts by remember { mutableStateOf(emptyMap<Int, String>()) }

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
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        selectedMonth = currentMonthYear
    }

    val filteredStudents = remember(studentsList, searchBulkQuery) {
        if (searchBulkQuery.isBlank()) {
            studentsList
        } else {
            studentsList.filter {
                it.studentName.contains(searchBulkQuery, ignoreCase = true) ||
                it.className.contains(searchBulkQuery, ignoreCase = true) ||
                it.id.toString() == searchBulkQuery
            }
        }
    }

    fun getEffectiveAmount(s: Student): Double {
        val standard = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
        val customStr = customAmounts[s.id] ?: return standard
        return customStr.toDoubleOrNull() ?: standard
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .imePadding()
            .widthIn(max = 640.dp)
            .fillMaxWidth(0.94f)
            .padding(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bulk Fee Reception",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Dialog")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select multiple students to batch process and record fee payments for the active cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = searchBulkQuery,
                    onValueChange = { searchBulkQuery = it },
                    placeholder = { Text("Filter students by name or class...") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            if (selectedStudentIds.size == filteredStudents.size && filteredStudents.isNotEmpty()) {
                                selectedStudentIds = emptySet()
                            } else {
                                selectedStudentIds = filteredStudents.map { it.id }.toSet()
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = selectedStudentIds.isNotEmpty() && selectedStudentIds.size == filteredStudents.size,
                            onCheckedChange = { checked ->
                                selectedStudentIds = if (checked) {
                                    filteredStudents.map { it.id }.toSet()
                                } else {
                                    emptySet()
                                }
                            }
                        )
                        Text(
                            text = "Select All (${filteredStudents.size} listed)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (selectedStudentIds.isNotEmpty()) {
                        TextButton(onClick = { selectedStudentIds = emptySet() }) {
                            Text("Clear Selection (${selectedStudentIds.size})", color = MaterialTheme.financialColors.expense, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { s ->
                        val isChecked = selectedStudentIds.contains(s.id)
                        val standardFee = remember(s) {
                            DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType).toInt()
                        }
                        
                        val overrideText = customAmounts[s.id] ?: standardFee.toString()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    selectedStudentIds = if (isChecked) {
                                        selectedStudentIds - s.id
                                    } else {
                                        selectedStudentIds + s.id
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedStudentIds = if (checked) {
                                            selectedStudentIds + s.id
                                        } else {
                                            selectedStudentIds - s.id
                                        }
                                    }
                                )
                                Column {
                                    Text(
                                        text = s.studentName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Class: ${s.className} | Disc: ${s.discountType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.padding(start = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Rs.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = overrideText,
                                    onValueChange = { valText ->
                                        customAmounts = customAmounts + (s.id to valText)
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(76.dp).height(40.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedMonth,
                            onValueChange = {},
                            label = { Text("Fee Month") },
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            trailingIcon = {
                                IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = selectedMonthExpanded,
                            onDismissRequest = { selectedMonthExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            monthsList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { selectedMonth = m; selectedMonthExpanded = false }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            onClick = { paymentMode = DomainConstants.MODE_CASH },
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Cash Box",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (paymentMode == DomainConstants.MODE_CASH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Card(
                            onClick = { paymentMode = DomainConstants.MODE_BANK },
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Bank",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (paymentMode == DomainConstants.MODE_BANK) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("General Remarks Memo") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )

                val totalBatchFeeValue = remember(selectedStudentIds, customAmounts, studentsList) {
                    val targetStudents = studentsList.filter { selectedStudentIds.contains(it.id) }
                    targetStudents.sumOf { getEffectiveAmount(it) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.financialColors.bankContainer.copy(alpha = 0.25f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total to Receive (${selectedStudentIds.size} Selected):",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rs. ${totalBatchFeeValue.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.bank
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedStudentIds.isNotEmpty() && selectedMonth.isNotBlank()) {
                        val batchList = studentsList.filter { selectedStudentIds.contains(it.id) }.map { s ->
                            Triple(s, getEffectiveAmount(s), selectedMonth)
                        }
                        onConfirm(batchList, paymentMode, remarks)
                    }
                },
                enabled = selectedStudentIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.bank
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Process Payments (${selectedStudentIds.size})", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun PrintThreePartChallanDialog(
    student: Student,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onExport: (month: String, dueDate: String, arrears: Double) -> Unit
) {
    val currentMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }
    var dueDate by remember { mutableStateOf("10th of this month") }
    
    val discountedMonthly = remember(student) {
        DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
    }

    var arrearsInput by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .imePadding()
            .widthIn(max = 520.dp)
            .fillMaxWidth(0.92f)
            .padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Print 3-Part Fee Challan", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Generates official Pakistani standard 3-Copy Challan (Bank Copy, School Copy, Student Copy) on a single A4 page for ${student.studentName} (${student.className}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = selectedMonth,
                    onValueChange = { selectedMonth = it },
                    label = { Text("Fee Billing Month") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Fee Due Date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = arrearsInput,
                    onValueChange = { arrearsInput = it },
                    label = { Text("Previous Arrears (Rs.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                val arrVal = arrearsInput.toDoubleOrNull() ?: 0.0
                val totalWithin = discountedMonthly + arrVal + student.annualCharges + student.otherCharges
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Monthly Tuition: Rs. ${discountedMonthly.toInt()}", style = MaterialTheme.typography.bodySmall)
                        if (arrVal > 0) Text("Previous Arrears: Rs. ${arrVal.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.expense)
                        Text("Total Payable: Rs. ${totalWithin.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val arr = arrearsInput.toDoubleOrNull() ?: 0.0
                    onExport(selectedMonth, dueDate, arr)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Print / Export PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BulkClassChallanDialog(
    allStudents: List<Student>,
    transactions: List<Transaction>,
    initialClass: String? = null,
    onDismiss: () -> Unit,
    onExport: (targetClass: String, targetMonth: String, dueDate: String) -> Unit
) {
    val classList = remember(allStudents) {
        allStudents.map { it.className.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var selectedClass by remember { 
        mutableStateOf(if (!initialClass.isNullOrBlank() && initialClass in classList) initialClass else classList.firstOrNull() ?: "") 
    }
    var classExpanded by remember { mutableStateOf(false) }

    val currentMonthYear = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
    var selectedMonth by remember { mutableStateOf(currentMonthYear) }

    val defaultDueDate = remember {
        val cal = Calendar.getInstance()
        "10-${SimpleDateFormat("MM-yyyy", Locale.US).format(cal.time)}"
    }
    var dueDate by remember { mutableStateOf(defaultDueDate) }

    val classStudents = remember(allStudents, selectedClass) {
        allStudents.filter { it.className.equals(selectedClass, ignoreCase = true) }
    }

    val totalClassStrength = classStudents.size
    val totalEstimatedFee = remember(classStudents) {
        classStudents.sumOf { DomainConstants.calculateDiscountedFee(it.monthlyFee, it.discountType) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .imePadding()
            .widthIn(max = 520.dp)
            .fillMaxWidth(0.92f)
            .padding(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Class Bulk Challans",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Generates a multi-page PDF where each student has their own 3-part bank challan (Bank, School, and Parent copies) on an A4 landscape page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Class Selector Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (selectedClass.isBlank()) "Select Class..." else selectedClass,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Class") },
                        trailingIcon = {
                            IconButton(onClick = { classExpanded = !classExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Class")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { classExpanded = true }
                    )
                    DropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false }
                    ) {
                        classList.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls) },
                                onClick = {
                                    selectedClass = cls
                                    classExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = selectedMonth,
                    onValueChange = { selectedMonth = it },
                    label = { Text("Fee Billing Month (e.g. October 2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Payment Due Date (e.g. 10-10-2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Class Summary ($selectedClass):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Enrolled Students: $totalClassStrength",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Total Monthly Tuition: Rs. ${totalEstimatedFee.toInt()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Printable Pages: $totalClassStrength landscape A4 pages (3 copies each)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.financialColors.cash
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedClass.isNotBlank()) {
                        onExport(selectedClass, selectedMonth, dueDate)
                    }
                },
                enabled = selectedClass.isNotBlank() && totalClassStrength > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Bulk PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BulkClassPromotionDialog(
    availableClasses: List<String>,
    onDismiss: () -> Unit,
    onPromote: (fromClass: String, toClass: String, applyAnnualCharges: Boolean, annualChargesAmount: Double?) -> Unit
) {
    val cleanClasses = remember(availableClasses) {
        availableClasses.filter { it != "All Classes" }
    }
    var fromClass by remember { mutableStateOf(cleanClasses.firstOrNull() ?: "") }
    var toClass by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }
    var applyAnnualCharges by remember { mutableStateOf(false) }
    var annualChargesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bulk Class Promotion & Session Rollover", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Promote all enrolled students of a class to the next grade or graduate them to Alumni at the end of the academic session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current Class Picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromClass,
                        onValueChange = {},
                        label = { Text("Current Class (From)") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { fromExpanded = !fromExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = fromExpanded,
                        onDismissRequest = { fromExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        cleanClasses.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls) },
                                onClick = {
                                    fromClass = cls
                                    fromExpanded = false
                                }
                            )
                        }
                    }
                }

                // Next Class (or Graduation)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = toClass,
                        onValueChange = { toClass = it },
                        label = { Text("Promoted Class (To) or type new") },
                        placeholder = { Text("e.g. Class Two, Alumni") },
                        trailingIcon = {
                            IconButton(onClick = { toExpanded = !toExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = toExpanded,
                        onDismissRequest = { toExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        cleanClasses.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls) },
                                onClick = {
                                    toClass = cls
                                    toExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("🎓 Alumni (Graduated)", color = MaterialTheme.financialColors.goldBadge, fontWeight = FontWeight.Bold) },
                            onClick = {
                                toClass = "Alumni"
                                toExpanded = false
                            }
                        )
                    }
                }

                // Annual Session Charges Rollover Option
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { applyAnnualCharges = !applyAnnualCharges }
                        ) {
                            Checkbox(
                                checked = applyAnnualCharges,
                                onCheckedChange = { applyAnnualCharges = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Auto-Bill Annual Session Charges",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (applyAnnualCharges) {
                            OutlinedTextField(
                                value = annualChargesText,
                                onValueChange = { annualChargesText = it },
                                label = { Text("Annual Session Charges per Student (Rs.)") },
                                placeholder = { Text("e.g. 2500 (or leave empty to retain current rate)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ℹ️ Safe Operation Notice:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• All admission records, historical ledgers, and transactions remain intact.\n• Only the class label on each student profile will be transitioned from '$fromClass' to '$toClass'.",
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
                    if (fromClass.isNotBlank() && toClass.isNotBlank() && fromClass != toClass) {
                        onPromote(fromClass, toClass, applyAnnualCharges, annualChargesText.toDoubleOrNull())
                    }
                },
                enabled = fromClass.isNotBlank() && toClass.isNotBlank() && fromClass != toClass,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Promote All Students")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BulkWhatsAppRemindersDialog(
    alerts: List<PendingFeeAlert>,
    onDismiss: () -> Unit,
    onSendWhatsApp: (PendingFeeAlert) -> Unit
) {
    var sentStudentIds by remember { mutableStateOf(emptySet<Int>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f)
            .padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366))
                Spacer(modifier = Modifier.width(8.dp))
                Text("WhatsApp Reminders Queue", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "${alerts.size} students with pending dues. Tap 'Send' to dispatch pre-formatted WhatsApp notices to parents one by one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { if (alerts.isNotEmpty()) sentStudentIds.size.toFloat() / alerts.size else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF25D366),
                    trackColor = Color(0xFFE0E0E0)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${sentStudentIds.size} of ${alerts.size} dispatched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (alerts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No pending fee alerts! All students are clear.", color = MaterialTheme.financialColors.cash, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alerts, key = { it.studentId }) { alert ->
                            val isSent = sentStudentIds.contains(alert.studentId)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isSent) MaterialTheme.financialColors.cashContainer else MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, if (isSent) Color(0xFF81C784) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(alert.studentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Class: ${alert.className} | Ph: ${alert.contactNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Pending: Rs. ${alert.totalPendingAmount.toInt()} (${alert.month})", color = MaterialTheme.financialColors.expense, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            onSendWhatsApp(alert)
                                            sentStudentIds = sentStudentIds + alert.studentId
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSent) Color(0xFF4CAF50) else Color(0xFF25D366)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            if (isSent) Icons.Default.Check else Icons.Default.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isSent) "Sent" else "Send", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("Close Queue")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentExitSettlementDialog(
    student: Student,
    alert: PendingFeeAlert?,
    onDismiss: () -> Unit,
    onConfirmStatusLeft: () -> Unit,
    onCollectPayment: () -> Unit
) {
    val context = LocalContext.current
    val unpaidTuition = alert?.totalPendingAmount ?: 0.0
    val unpaidMonths = alert?.pendingMonthsList?.joinToString(", ") ?: ""
    val isCleared = unpaidTuition <= 0.0

    var leavingFeeText by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var conductText by remember { mutableStateOf("Good & Satisfactory") }
    var reasonText by remember { mutableStateOf("Parent Request / Migration") }

    val leavingProcessingFee = leavingFeeText.toDoubleOrNull() ?: 0.0
    val totalSettlementDue = unpaidTuition + leavingProcessingFee
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
                .padding(bottom = 16.dp)
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
                            .background(if (isCleared) Color(0xFFDCFCE7) else Color(0xFFFEE2E2), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCleared) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isCleared) Color(0xFF166534) else Color(0xFF991B1B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Student Exit & Clearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${student.studentName} • Class ${student.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Settlement Status Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isCleared) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)),
                    border = BorderStroke(1.dp, if (isCleared) Color(0xFFBBF7D0) else Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clearance Audit", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isCleared) Color(0xFF166534) else Color(0xFF92400E))
                            Surface(
                                color = if (isCleared) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isCleared) "ALL DUES CLEARED (NOC READY)" else "OUTSTANDING DUES PENDING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCleared) Color(0xFF166534) else Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!isCleared) {
                            Text(
                                text = "Unpaid Tuition Arrears: Rs. ${unpaidTuition.toInt()}" + if (unpaidMonths.isNotBlank()) " ($unpaidMonths)" else "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309)
                            )
                        } else {
                            Text(
                                text = "No unpaid dues or arrears found. The student accounts are fully reconciled.",
                                fontSize = 12.sp,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                }

                // SLC Reason & Conduct Fields
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason for Leaving") },
                    placeholder = { Text("e.g. Relocating / Completion of Studies") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = conductText,
                    onValueChange = { conductText = it },
                    label = { Text("Student Conduct & Character") },
                    placeholder = { Text("e.g. Good & Satisfactory") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = leavingFeeText,
                    onValueChange = { leavingFeeText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Optional SLC / Migration Fee (Rs)") },
                    placeholder = { Text("e.g. 500 (or leave 0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Printable Documents Section
                Text("Official Departure Documents", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                // 1. Closing Fee Challan (3-Copy Voucher)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val file = ReportExporter.exportStudentClosingChallanPdf(
                            context = context,
                            student = student,
                            monthlyArrears = unpaidTuition,
                            unpaidMonthsDescription = unpaidMonths,
                            leavingProcessingFee = leavingProcessingFee,
                            remarks = remarks
                        )
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Closing_Challan_${student.studentName.replace(" ", "_")}")
                            Toast.makeText(context, "Closing Challan generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Failed to compile Closing Challan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1. Closing Fee Challan (3-Part)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Landscape 3-copy settlement voucher with clearance status", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }

                // 2. School Leaving Certificate (SLC)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val file = ReportExporter.exportSchoolLeavingCertificateToPdf(
                            context = context,
                            student = student,
                            reason = reasonText,
                            conduct = conductText
                        )
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_SLC_${student.studentName.replace(" ", "_")}")
                            Toast.makeText(context, "School Leaving Certificate generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Failed to compile SLC Certificate", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. School Leaving Certificate (SLC)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Official migration & character clearance certificate", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    }
                }

                // 3. Share WhatsApp Clearance
                if (student.contactNumber.isNotBlank()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val msg = "Dear Parent, Departure clearance record for ${student.studentName} (Class ${student.className}, Reg #${student.regNo}) has been processed at Blended Learning School (BLS). Outstanding Dues: Rs. ${totalSettlementDue.toInt()}. Please collect the official School Leaving Certificate from the school office."
                            val clean = student.contactNumber.replace(" ", "").replace("-", "")
                            val formatted = if (clean.startsWith("0")) "92" + clean.substring(1) else clean
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=${android.net.Uri.encode(msg)}")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("3. Send Clearance via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Send departure summary & clearance notice to parent", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }

                if (!isCleared) {
                    Button(
                        onClick = {
                            onDismiss()
                            onCollectPayment()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.3f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Collect Due Fee", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        onConfirmStatusLeft()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1.3f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Left", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

