package com.example.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.theme.financialColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PrintThreePartChallanDialog(
    student: Student,
    transactions: List<Transaction> = emptyList(),
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
    transactions: List<Transaction> = emptyList(),
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
