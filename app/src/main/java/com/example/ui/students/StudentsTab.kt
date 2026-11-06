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
import com.example.ui.teachers.TeachersManagementView
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

    var selectedSection by rememberSaveable { mutableStateOf(0) } // 0 = Students Directory, 1 = Teachers & Staff

    var filterCategory by rememberSaveable { mutableStateOf(StudentFilterCategory.ALL) }
    var classFilter by rememberSaveable { mutableStateOf("All Classes") }
    var selectedAlphabet by rememberSaveable { mutableStateOf("") }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

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

    if (showFilterSheet) {
        StudentsFilterBottomSheet(
            onDismiss = { showFilterSheet = false },
            statusFilter = statusFilter,
            onStatusFilterChange = { viewModel.setStatusFilter(it) },
            filterCategory = filterCategory,
            onFilterCategoryChange = { filterCategory = it },
            classFilter = classFilter,
            onClassFilterChange = { classFilter = it },
            selectedAlphabet = selectedAlphabet,
            onSelectAlphabet = { selectedAlphabet = it },
            availableClasses = availableClasses,
            studentSortField = studentSortField,
            studentSortAscending = studentSortAscending,
            onSortChange = { field, asc ->
                studentSortField = field
                studentSortAscending = asc
            },
            onResetFilters = {
                viewModel.setStatusFilter("Active")
                filterCategory = StudentFilterCategory.ALL
                classFilter = "All Classes"
                selectedAlphabet = ""
                studentSortField = StudentSortField.NAME
                studentSortAscending = true
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Page Heading & Subheading
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
            ) {
                Text(
                    text = if (selectedSection == 0) "Students Directory" else "Teachers & Staff",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (selectedSection == 0) "Manage student enrollments, profiles and fee dues" else "Staff payroll budget, salary disbursement and records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Segmented Switcher Pill: Students Directory vs Teachers & Staff
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 0: Students Directory
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedSection = 0 },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedSection == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (selectedSection == 0) 1.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = if (selectedSection == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Students Directory",
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedSection == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedSection == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 1: Teachers & Staff
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedSection = 1 },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedSection == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (selectedSection == 1) 1.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = if (selectedSection == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Teachers & Staff",
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedSection == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedSection == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedSection == 1) {
                TeachersManagementView(
                    viewModel = viewModel,
                    isWideScreen = isWideScreen,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
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
                        onBulkWhatsAppReminders = { showBulkWhatsAppDialog = true },
                        onOpenFilterSheet = { showFilterSheet = true },
                        totalExpectedFee = totalExpectedFee,
                        totalCollectedFee = totalCollectedFee
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
                        onBulkWhatsAppReminders = { showBulkWhatsAppDialog = true },
                        onOpenFilterSheet = { showFilterSheet = true },
                        totalExpectedFee = totalExpectedFee,
                        totalCollectedFee = totalCollectedFee
                    )

                    if (students.isEmpty()) {
                        EmptyStudentsPlaceholder(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
    onBulkWhatsAppReminders: () -> Unit = {},
    onOpenFilterSheet: () -> Unit = {},
    totalExpectedFee: Long = 0L,
    totalCollectedFee: Long = 0L
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val isAnyFilterActive = filterCategory != StudentFilterCategory.ALL || classFilter != "All Classes" || statusFilter != "Active" || selectedAlphabet.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Unified Search & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search", fontSize = 12.5.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("student_search_input"),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Icon Button with Active Dot
            Surface(
                onClick = onOpenFilterSheet,
                shape = RoundedCornerShape(12.dp),
                color = if (isAnyFilterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isAnyFilterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Filter & Sort",
                        tint = if (isAnyFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    if (isAnyFilterActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }

            // Quick Actions 3-dots Menu Button
            Box {
                Surface(
                    onClick = { showMoreMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Admission", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showMoreMenu = false
                            onAddStudent()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bulk Fee Collection", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.financialColors.cash) },
                        onClick = {
                            showMoreMenu = false
                            onBulkFee()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Print Class Challans", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showMoreMenu = false
                            onClassChallans()
                        }
                    )
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

        // Row 2: Sleek Single-Line Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // All Active
            FilterChip(
                selected = statusFilter == "Active" && filterCategory == StudentFilterCategory.ALL && classFilter == "All Classes",
                onClick = {
                    onStatusFilterChange("Active")
                    onFilterCategoryChange(StudentFilterCategory.ALL)
                    onClassFilterChange("All Classes")
                    onSelectAlphabet("")
                },
                label = { Text("All ($activeCount)", fontSize = 11.5.sp) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(30.dp)
            )

            // Overdue Defaulters
            FilterChip(
                selected = filterCategory == StudentFilterCategory.DEFAULTERS_ONLY,
                onClick = {
                    onStatusFilterChange("Active")
                    onFilterCategoryChange(if (filterCategory == StudentFilterCategory.DEFAULTERS_ONLY) StudentFilterCategory.ALL else StudentFilterCategory.DEFAULTERS_ONLY)
                },
                label = { Text("Overdue ($defaultersCount)", fontSize = 11.5.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.financialColors.expenseContainer,
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
                    onFilterCategoryChange(if (filterCategory == StudentFilterCategory.PAID_THIS_MONTH) StudentFilterCategory.ALL else StudentFilterCategory.PAID_THIS_MONTH)
                },
                label = { Text("Paid ($paidCount)", fontSize = 11.5.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.financialColors.cashContainer,
                    selectedLabelColor = MaterialTheme.financialColors.cash
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(30.dp)
            )

            // Class Dropdown Chip
            var classMenuOpen by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = classFilter != "All Classes",
                    onClick = { classMenuOpen = true },
                    label = { Text(if (classFilter == "All Classes") "Class ▾" else "$classFilter ▾", fontSize = 11.5.sp) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )
                DropdownMenu(
                    expanded = classMenuOpen,
                    onDismissRequest = { classMenuOpen = false }
                ) {
                    availableClasses.forEach { cls ->
                        DropdownMenuItem(
                            text = { Text(cls, fontSize = 12.5.sp, fontWeight = if (cls == classFilter) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onClassFilterChange(cls)
                                classMenuOpen = false
                            }
                        )
                    }
                }
            }

            if (leftCount > 0) {
                FilterChip(
                    selected = statusFilter == "Left",
                    onClick = {
                        onStatusFilterChange(if (statusFilter == "Left") "Active" else "Left")
                    },
                    label = { Text("Alumni ($leftCount)", fontSize = 11.5.sp) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        // Row 3: Minimalist Recovery KPI Strip
        if (totalExpectedFee > 0L) {
            val rate = (totalCollectedFee.toFloat() / totalExpectedFee.toFloat()).coerceIn(0f, 1f)
            val pct = (rate * 100).toInt()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fee Recovery: Rs. ${totalCollectedFee.toInt()} / ${totalExpectedFee.toInt()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$pct%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pct >= 75) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.expense
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { rate },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (pct >= 75) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.expense,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
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
