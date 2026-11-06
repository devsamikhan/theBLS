package com.example.ui.teachers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DomainConstants
import com.example.data.Teacher
import com.example.data.Transaction
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.util.AudioFeedback
import com.example.util.CurrencyVisualTransformation
import com.example.util.InputFormatUtils
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TeachersManagementView(
    viewModel: AppViewModel,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val teachers by viewModel.teachersList.collectAsStateWithLifecycle()
    val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedStatusFilter by rememberSaveable { mutableStateOf("All") } // "All", "Active", "Left"

    var showAddEditDialog by remember { mutableStateOf(false) }
    var teacherToEdit by remember { mutableStateOf<Teacher?>(null) }
    var teacherToPay by remember { mutableStateOf<Teacher?>(null) }
    var teacherForHistory by remember { mutableStateOf<Teacher?>(null) }
    var teacherToDelete by remember { mutableStateOf<Teacher?>(null) }

    val currentMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }

    // Analytics calculations
    val totalTeachersCount = teachers.size
    val activeTeachersCount = remember(teachers) { teachers.count { it.status.equals("Active", ignoreCase = true) } }
    val leftTeachersCount = remember(teachers) { teachers.count { !it.status.equals("Active", ignoreCase = true) } }

    val monthlySalaryBudget = remember(teachers) {
        teachers.filter { it.status.equals("Active", ignoreCase = true) }.sumOf { it.monthlySalary }
    }

    val disbursedThisMonth = remember(transactions, currentMonth) {
        transactions
            .filter { !it.isIncome && (it.category == DomainConstants.CAT_STAFF_SALARY || it.category == DomainConstants.CAT_TEACHERS_PAY) && (it.monthOfFee == currentMonth || (it.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(it.date)) == currentMonth)) }
            .sumOf { it.amount }
    }

    // Map teacherId -> boolean indicating if salary is paid for currentMonth
    val paidThisMonthMap = remember(teachers, transactions, currentMonth) {
        teachers.associate { t ->
            val hasPaid = transactions.any { tx ->
                !tx.isIncome &&
                (tx.category == DomainConstants.CAT_STAFF_SALARY || tx.category == DomainConstants.CAT_TEACHERS_PAY) &&
                (tx.teacherId == t.id || (tx.payeeName.isNotBlank() && tx.payeeName.equals(t.name, ignoreCase = true))) &&
                (tx.monthOfFee == currentMonth || (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonth))
            }
            t.id to hasPaid
        }
    }

    // Filter teachers based on search query and status filter
    val filteredTeachers = remember(teachers, searchQuery, selectedStatusFilter) {
        teachers.filter { t ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                t.name.contains(searchQuery, ignoreCase = true) ||
                t.designation.contains(searchQuery, ignoreCase = true) ||
                t.contactNumber.contains(searchQuery, ignoreCase = true) ||
                t.cnic.contains(searchQuery, ignoreCase = true)
            }
            val matchesStatus = when (selectedStatusFilter) {
                "Active" -> t.status.equals("Active", ignoreCase = true)
                "Left" -> !t.status.equals("Active", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesStatus
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Sleek Minimalist Staff Metric Strip (Compact ~44dp, Responsive Equal-Weight)
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
                // Metric 1: Staff Count
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text("Staff", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "$activeTeachersCount Active",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
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

                // Metric 2: Monthly Budget
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(7.dp).background(MaterialTheme.financialColors.cash, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text("Budget", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "Rs. ${monthlySalaryBudget.toInt()}",
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

                // Metric 3: Disbursed
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(7.dp).background(MaterialTheme.financialColors.expense, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text("Disbursed", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            "Rs. ${disbursedThisMonth.toInt()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.financialColors.expense,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Responsive Controls: Search Bar + Add Staff Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            )

            // Primary Add Staff Button
            Button(
                onClick = {
                    teacherToEdit = null
                    showAddEditDialog = true
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Staff", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 3. Status Filter Chips Row (Responsive 3-Column Segment)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple("All", "All Staff", teachers.size),
                Triple("Active", "Active", activeTeachersCount),
                Triple("Left", "Left", leftTeachersCount)
            ).forEach { (filterKey, label, count) ->
                val isSelected = selectedStatusFilter == filterKey
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedStatusFilter = filterKey },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        ) {
                            Text(
                                count.toString(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Teachers List / Empty State
        if (filteredTeachers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (teachers.isEmpty()) "No Teachers or Staff Added Yet" else "No matching staff found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (teachers.isEmpty()) "Click '+ Add Staff' above to register teachers, peons, and administrative staff." else "Try a different name, designation, or change filter chips.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTeachers, key = { it.id }) { teacher ->
                    val isPaid = paidThisMonthMap[teacher.id] ?: false
                    TeacherCard(
                        teacher = teacher,
                        isPaidThisMonth = isPaid,
                        currentMonth = currentMonth,
                        onPaySalary = { teacherToPay = teacher },
                        onPrintPayslip = {
                            val latestTx = transactions.firstOrNull { tx ->
                                !tx.isIncome &&
                                (tx.category == DomainConstants.CAT_STAFF_SALARY || tx.category == DomainConstants.CAT_TEACHERS_PAY) &&
                                (tx.teacherId == teacher.id || (tx.payeeName.isNotBlank() && tx.payeeName.equals(teacher.name, ignoreCase = true)))
                            }
                            if (latestTx != null) {
                                val file = ReportExporter.exportStaffSalarySlipPdf(context, latestTx, teacher.name, latestTx.monthOfFee ?: currentMonth)
                                if (file != null) {
                                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Payslip_${teacher.name.replace(" ", "_")}")
                                    Toast.makeText(context, "Payslip PDF Saved! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                    ReportExporter.sharePdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to compile printable payslip.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No salary disbursement recorded yet for ${teacher.name}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEdit = {
                            teacherToEdit = teacher
                            showAddEditDialog = true
                        },
                        onToggleStatus = {
                            val newStatus = if (teacher.status.equals("Active", ignoreCase = true)) "Left" else "Active"
                            viewModel.updateTeacherStatus(teacher.id, newStatus)
                            Toast.makeText(context, "${teacher.name} status updated to '$newStatus'", Toast.LENGTH_SHORT).show()
                        },
                        onViewHistory = {
                            teacherForHistory = teacher
                        },
                        onDelete = {
                            teacherToDelete = teacher
                        }
                    )
                }
            }
        }
    }

    // ==================== DIALOGS ====================

    // Add / Edit Teacher Dialog
    if (showAddEditDialog) {
        AddEditTeacherDialog(
            teacher = teacherToEdit,
            onDismiss = {
                showAddEditDialog = false
                teacherToEdit = null
            },
            onSave = { name, desig, phone, cnic, qual, salary, joinDate, addr ->
                if (teacherToEdit == null) {
                    viewModel.addTeacher(
                        name = name,
                        designation = desig,
                        contact = phone,
                        cnic = cnic,
                        qualification = qual,
                        monthlySalary = salary,
                        joiningDate = joinDate,
                        address = addr,
                        onSuccess = {
                            AudioFeedback.playSuccessChime()
                            Toast.makeText(context, "Staff '${it.name}' added successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    val updated = teacherToEdit!!.copy(
                        name = name,
                        designation = desig,
                        contactNumber = phone,
                        cnic = cnic,
                        qualification = qual,
                        monthlySalary = salary,
                        joiningDate = joinDate,
                        address = addr
                    )
                    viewModel.updateTeacher(updated) {
                        AudioFeedback.playSuccessChime()
                        Toast.makeText(context, "Staff details updated!", Toast.LENGTH_SHORT).show()
                    }
                }
                showAddEditDialog = false
                teacherToEdit = null
            }
        )
    }

    // Pay Salary Dialog
    teacherToPay?.let { teacher ->
        PayTeacherSalaryDialog(
            teacher = teacher,
            transactions = transactions,
            defaultMonth = currentMonth,
            onDismiss = { teacherToPay = null },
            onConfirm = { amount, month, mode, remarks ->
                viewModel.recordStaffSalary(
                    staffName = teacher.name,
                    amount = amount,
                    month = month,
                    mode = mode,
                    remarks = remarks,
                    teacherId = teacher.id,
                    onSuccess = { savedTx ->
                        AudioFeedback.playSuccessChime()
                        val file = ReportExporter.exportStaffSalarySlipPdf(context, savedTx, teacher.name, month)
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Payslip_${teacher.name.replace(" ", "_")}_$month")
                            Toast.makeText(context, "Salary Paid! Payslip Created: ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Salary recorded successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                teacherToPay = null
            }
        )
    }

    // Salary History Dialog
    teacherForHistory?.let { teacher ->
        val teacherSalaryTxs = remember(transactions, teacher) {
            transactions.filter { tx ->
                !tx.isIncome &&
                (tx.category == DomainConstants.CAT_STAFF_SALARY || tx.category == DomainConstants.CAT_TEACHERS_PAY) &&
                (tx.teacherId == teacher.id || (tx.payeeName.isNotBlank() && tx.payeeName.equals(teacher.name, ignoreCase = true)))
            }.sortedByDescending { it.date }
        }

        TeacherSalaryHistoryDialog(
            teacher = teacher,
            salaryTransactions = teacherSalaryTxs,
            onDismiss = { teacherForHistory = null },
            onPrintPayslip = { tx ->
                val file = ReportExporter.exportStaffSalarySlipPdf(context, tx, teacher.name, tx.monthOfFee ?: "")
                if (file != null) {
                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Payslip_${teacher.name.replace(" ", "_")}_${tx.voucherNo}")
                    Toast.makeText(context, "Payslip PDF Saved! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    teacherToDelete?.let { teacher ->
        AlertDialog(
            onDismissRequest = { teacherToDelete = null },
            title = { Text("Delete Staff Member?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove '${teacher.name}' (${teacher.designation}) from the staff registry? Past financial vouchers will remain preserved in ledger.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTeacher(teacher) {
                            Toast.makeText(context, "${teacher.name} removed.", Toast.LENGTH_SHORT).show()
                        }
                        teacherToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.expense)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { teacherToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =================================================================
// SUB-COMPONENTS
// =================================================================

@Composable
private fun TeacherStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = contentColor.copy(alpha = 0.85f))
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = contentColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 9.sp, color = contentColor.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

@Composable
private fun TeacherCard(
    teacher: Teacher,
    isPaidThisMonth: Boolean,
    currentMonth: String,
    onPaySalary: () -> Unit,
    onPrintPayslip: () -> Unit,
    onEdit: () -> Unit,
    onToggleStatus: () -> Unit,
    onViewHistory: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val isActive = teacher.status.equals("Active", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Compact Squircle Avatar + Staff Name & Role
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = teacher.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Name + Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            teacher.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (!isActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("Left", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    // Role + Phone + WhatsApp icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            teacher.designation.ifBlank { "Staff" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        if (teacher.contactNumber.isNotBlank()) {
                            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text(
                                teacher.contactNumber,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "WhatsApp",
                                tint = MaterialTheme.financialColors.whatsapp,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable {
                                        val cleanNum = InputFormatUtils.sanitizePakistanPhoneForWhatsApp(teacher.contactNumber)
                                        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Salary Tag + Salary Pay Status Button + 3-dots Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Interactive Pay Status Button: 1-tap pays salary if unpaid!
                if (isPaidThisMonth) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.financialColors.cashContainer.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { onPrintPayslip() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.financialColors.cash, modifier = Modifier.size(11.dp))
                            Text("Paid", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.financialColors.cash)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.financialColors.amberContainer.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.financialColors.amber.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { onPaySalary() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "Rs. ${teacher.monthlySalary.toInt()}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.financialColors.amber
                            )
                            Icon(Icons.Default.Add, contentDescription = "Pay Salary", tint = MaterialTheme.financialColors.amber, modifier = Modifier.size(11.dp))
                        }
                    }
                }

                // 3-dots Menu
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pay Salary") },
                            onClick = {
                                showMenu = false
                                onPaySalary()
                            },
                            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.financialColors.cash) }
                        )
                        DropdownMenuItem(
                            text = { Text("Print Payslip") },
                            onClick = {
                                showMenu = false
                                onPrintPayslip()
                            },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Salary History") },
                            onClick = {
                                showMenu = false
                                onViewHistory()
                            },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Details") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isActive) "Mark as Left" else "Mark as Active") },
                            onClick = {
                                showMenu = false
                                onToggleStatus()
                            },
                            leadingIcon = { Icon(if (isActive) Icons.Default.PersonOff else Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Staff", color = MaterialTheme.financialColors.expense) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditTeacherDialog(
    teacher: Teacher?,
    onDismiss: () -> Unit,
    onSave: (name: String, desig: String, phone: String, cnic: String, qual: String, salary: Double, joinDate: Long, addr: String) -> Unit
) {
    var name by remember { mutableStateOf(teacher?.name ?: "") }
    var designation by remember { mutableStateOf(teacher?.designation ?: "") }
    var phone by remember { mutableStateOf(teacher?.contactNumber ?: "") }
    var cnic by remember { mutableStateOf(teacher?.cnic ?: "") }
    var qualification by remember { mutableStateOf(teacher?.qualification ?: "") }
    var salaryStr by remember { mutableStateOf(if (teacher != null && teacher.monthlySalary > 0) teacher.monthlySalary.toInt().toString() else "") }
    var address by remember { mutableStateOf(teacher?.address ?: "") }

    val quickDesignations = listOf(
        "Senior Teacher", "Junior Teacher", "Science Teacher", "Mathematics Teacher",
        "English Teacher", "Urdu Teacher", "Islamiyat Teacher", "Computer Teacher",
        "Headmaster / Principal", "Accountant", "Peon / Aaya", "Security Guard"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (teacher == null) "Add New Staff Member" else "Edit Staff Details", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = { Text("Designation / Role *") },
                    placeholder = { Text("e.g. Science Teacher") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Quick Designation chips
                Text("Suggested roles:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickDesignations.forEach { desig ->
                        SuggestionChip(
                            onClick = { designation = desig },
                            label = { Text(desig, fontSize = 10.5.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Number *") },
                    placeholder = { Text("03001234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = salaryStr,
                    onValueChange = { salaryStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Monthly Basic Salary (Rs) *") },
                    placeholder = { Text("e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = qualification,
                    onValueChange = { qualification = it },
                    label = { Text("Qualification (Optional)") },
                    placeholder = { Text("e.g. M.Sc, B.Ed, M.A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it },
                    label = { Text("CNIC Number (Optional)") },
                    placeholder = { Text("38403-xxxxxxx-x") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Remarks (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) return@Button
                    val sal = salaryStr.toDoubleOrNull() ?: 0.0
                    onSave(
                        name.trim(),
                        designation.trim().ifBlank { "Staff" },
                        phone.trim(),
                        cnic.trim(),
                        qualification.trim(),
                        sal,
                        teacher?.joiningDate ?: System.currentTimeMillis(),
                        address.trim()
                    )
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save")
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
fun PayTeacherSalaryDialog(
    teacher: Teacher,
    transactions: List<Transaction> = emptyList(),
    defaultMonth: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, month: String, mode: String, remarks: String) -> Unit
) {
    var amountStr by remember { mutableStateOf(teacher.monthlySalary.toInt().toString()) }
    var selectedMonth by remember { mutableStateOf(defaultMonth) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("") }
    var monthDropdownExpanded by remember { mutableStateOf(false) }

    val recentMonths = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.US)
        for (i in -1..3) {
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            list.add(fmt.format(c.time))
        }
        list.distinct()
    }

    val existingSalaryTx = remember(transactions, teacher.id, selectedMonth) {
        transactions.firstOrNull { tx ->
            !tx.isIncome &&
            (tx.category == DomainConstants.CAT_STAFF_SALARY || tx.category == DomainConstants.CAT_TEACHERS_PAY) &&
            (tx.teacherId == teacher.id || (tx.payeeName.isNotBlank() && tx.payeeName.equals(teacher.name, ignoreCase = true))) &&
            (tx.monthOfFee == selectedMonth || (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == selectedMonth))
        }
    }
    var confirmDuplicatePayment by remember(selectedMonth) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.financialColors.cash)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disburse Salary", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Teacher Card Header
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${teacher.designation} • Base Salary: Rs. ${teacher.monthlySalary.toInt()}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Duplicate Payment Alert
                if (existingSalaryTx != null) {
                    val dateFormatted = remember(existingSalaryTx.date) {
                        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(existingSalaryTx.date))
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.financialColors.goldContainer,
                        border = BorderStroke(1.dp, MaterialTheme.financialColors.goldBadge),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.financialColors.goldBadge, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salary Already Paid For $selectedMonth", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.financialColors.goldBadge)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Rs. ${existingSalaryTx.amount.toInt()} was disbursed on $dateFormatted (Voucher #${existingSalaryTx.voucherNo}).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = confirmDuplicatePayment,
                                    onCheckedChange = { confirmDuplicatePayment = it },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("I confirm this is an intentional additional payment", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // Month Picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Salary Month") },
                        trailingIcon = {
                            IconButton(onClick = { monthDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Month")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    DropdownMenu(
                        expanded = monthDropdownExpanded,
                        onDismissRequest = { monthDropdownExpanded = false }
                    ) {
                        recentMonths.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    selectedMonth = m
                                    monthDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Disbursed Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Mode Selector
                Text("Payment Channel:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMode == DomainConstants.MODE_CASH,
                        onClick = { paymentMode = DomainConstants.MODE_CASH },
                        label = { Text("Cash Box") },
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paymentMode == DomainConstants.MODE_BANK,
                        onClick = { paymentMode = DomainConstants.MODE_BANK },
                        label = { Text("School Bank") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Remarks / Advance adjustment
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Deductions / Advance / Remarks (Optional)") },
                    placeholder = { Text("e.g. Full salary paid after Rs. 2000 advance adjustment") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) return@Button
                    onConfirm(amt, selectedMonth, paymentMode, remarks.trim())
                },
                enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0 && (existingSalaryTx == null || confirmDuplicatePayment),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash)
            ) {
                Text(
                    if (existingSalaryTx != null) "Confirm Additional Payment" else "Confirm & Print Payslip",
                    color = Color.White
                )
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
fun TeacherSalaryHistoryDialog(
    teacher: Teacher,
    salaryTransactions: List<Transaction>,
    onDismiss: () -> Unit,
    onPrintPayslip: (Transaction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${teacher.name} - Salary History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(teacher.designation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            if (salaryTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No salary payments on record for this staff member.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(salaryTransactions, key = { it.id }) { tx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tx.monthOfFee ?: SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(tx.date))} • ${tx.paymentMode} • ${tx.voucherNo}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (tx.description.isNotBlank()) {
                                        Text(tx.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Rs. ${tx.amount.toInt()}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.financialColors.cash
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { onPrintPayslip(tx) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = "Reprint", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
